package com.divinering.client.render;

import com.divinering.RingSettings;
import com.mojang.logging.LogUtils;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.FloatBuffer;

/**
 * Owns one raw OpenGL 3.2 core shader program, one VAO/VBO for the emblem and one
 * VAO/VBO for the additive aura/embers. No Minecraft ShaderInstance, RenderType,
 * BufferBuilder or particle system participates.
 *
 * <p>The GLSL is deliberately embedded: a host mod can copy the Java package without
 * also having to merge resource files or a shader registration event. Compilation is
 * lazy (first frame, render thread) and failures are logged once without crashing the
 * game.</p>
 */
final class DivineRingShader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int STRIDE = MeshData.FLOATS_PER_VERTEX * Float.BYTES;

    private static final String VERTEX_SHADER = """
            #version 150
            in vec3 aPos;
            in vec2 aUV;
            in vec4 aData;

            uniform mat4 uMvp;
            uniform float uTime;
            uniform float uMode;
            uniform float uGlowScale;
            uniform float uAngleOuter;
            uniform float uAngleMiddle;

            out vec2 vUV;
            out vec4 vData;

            mat2 rotate2d(float angle) {
                float c = cos(angle);
                float s = sin(angle);
                return mat2(c, s, -s, c);
            }

            void main() {
                vec3 p = aPos;
                float part = aData.x;
                float seed = aData.w;

                // Three independently animated rings:
                //   group 0 = outer ring + swept blades       (counter-clockwise)
                //   group 1 = middle ring + wings + chips     (clockwise)
                //   group 2 = inner ring + central spear      (stationary)
                float group = 2.0;
                if (part < 1.5 || (part > 3.5 && part < 4.5)) {
                    group = 0.0;
                } else if ((part > 1.5 && part < 2.5)
                        || (part > 4.5 && part < 5.5)
                        || (part > 6.5 && part < 7.5)) {
                    group = 1.0;
                }
                if (group < 0.5) {
                    p.xy = rotate2d(uAngleOuter) * p.xy;
                } else if (group < 1.5) {
                    p.xy = rotate2d(uAngleMiddle) * p.xy;
                }

                if (uMode > 1.5) {
                    // Bloom silhouette pass: a slightly inflated copy of the glyph is
                    // drawn additively, three times at different radii.
                    p.xy *= uGlowScale;
                } else if (uMode > 0.5 && part > 7.5 && part < 8.5) {
                    // Shader-animated embers, still ordinary triangles in a custom VBO.
                    float life = fract(uTime * 0.08 + seed * 3.17);
                    p.x += sin(uTime * 0.60 + seed * 41.0) * 0.012;
                    p.y += life * 0.42 - 0.10;
                    p.z += 0.020;
                } else if (part > 6.5 && part < 7.5) {
                    // Detached ring chips drift very slightly.
                    p.xy += vec2(sin(uTime * 0.45 + seed * 27.0),
                                 cos(uTime * 0.37 + seed * 19.0)) * 0.0035;
                } else if (part < 99.0) {
                    // A tiny out-of-plane ripple gives the flat glyph a breathing depth.
                    p.z += sin(uTime * 1.20 + aUV.y * 3.2 + seed * 17.0) * 0.0035;
                }

                vUV = aUV;
                vData = aData;
                gl_Position = uMvp * vec4(p, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150
            in vec2 vUV;
            in vec4 vData;

            uniform float uTime;
            uniform float uAlpha;
            uniform float uAura;
            uniform float uEmbers;
            uniform float uMode;
            uniform vec3 uHot;
            uniform vec3 uDark;
            uniform vec3 uGlow;
            uniform sampler2D uSceneDepth;
            uniform int uManualDepth;
            uniform vec2 uScreenSize;

            out vec4 fragColor;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float valueNoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash21(i);
                float b = hash21(i + vec2(1.0, 0.0));
                float c = hash21(i + vec2(0.0, 1.0));
                float d = hash21(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(vec2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += a * valueNoise(p);
                    p = p * 2.03 + vec2(17.1, 9.2);
                    a *= 0.5;
                }
                return v;
            }

            void main() {
                float part = vData.x;
                float seed = vData.w;

                // Iris/Oculus' final pass does not leave a valid world depth buffer in
                // the main target. The renderer captures the pre-composite depth into a
                // private texture and compares it manually here.
                if (uManualDepth == 1 && uScreenSize.x > 0.0 && uScreenSize.y > 0.0) {
                    float sceneDepth = texture(uSceneDepth, gl_FragCoord.xy / uScreenSize).r;
                    if (gl_FragCoord.z > sceneDepth + 0.0008) {
                        discard;
                    }
                }

                if (uMode > 1.5) {
                    if (part > 99.0) {
                        discard;
                    }
                    // Additive bloom silhouette. The renderer draws three of these at
                    // progressively larger scales, producing a soft halo around every
                    // arc, blade and chip.
                    fragColor = vec4(uGlow * 1.05, uAlpha);
                    return;
                }

                if (uMode > 0.5) {
                    if (part > 99.0) {
                        // Additive aura: soft ring bloom, inner core and eight muted spokes.
                        float r = length(vUV);
                        float ang = atan(vUV.y, vUV.x);
                        float ring = exp(-pow((r - 1.0) / 0.095, 2.0));
                        float middle = exp(-pow((r - 0.44) / 0.075, 2.0)) * 0.42;
                        float core = exp(-r * r * 8.5) * 0.22;
                        float haze = exp(-pow((r - 0.95) / 0.34, 2.0)) * 0.10;
                        float spokes = pow(max(0.0, cos(ang * 8.0 + uTime * 0.09)), 18.0)
                                * smoothstep(0.25, 1.35, r)
                                * (1.0 - smoothstep(1.35, 1.56, r)) * 0.09;
                        float pulse = 0.86 + 0.14 * sin(uTime * 2.1);
                        float alpha = (ring * 0.26 + middle + core + haze + spokes) * uAura * pulse;
                        alpha *= 1.0 - smoothstep(1.42, 1.58, r);
                        vec3 col = mix(uGlow, uHot, 0.24) * (1.35 + ring * 0.75);
                        fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
                        return;
                    }
                    if (part > 7.5 && part < 8.5) {
                        // Custom embers, animated in the vertex shader.
                        float life = fract(uTime * 0.08 + seed * 3.17);
                        float fade = smoothstep(0.0, 0.12, life)
                                * (1.0 - smoothstep(0.70, 1.0, life));
                        float twinkle = 0.55 + 0.45 * sin(uTime * 4.0 + seed * 40.0);
                        vec3 col = uGlow * (1.45 + twinkle * 0.70);
                        fragColor = vec4(col, fade * uEmbers * 0.90);
                        return;
                    }
                    fragColor = vec4(0.0);
                    return;
                }

                if (part > 99.0 || uAlpha < 0.001) {
                    discard;
                }

                float edge = abs(vData.y);
                float aa = max(fwidth(edge), 0.0015) * 1.7;
                float mask = 1.0 - smoothstep(1.0 - aa, 1.0, edge);
                if (mask < 0.02) {
                    discard;
                }

                vec2 uv = vUV;
                float ang = atan(uv.y, uv.x);
                vec2 gradDir = normalize(vec2(-0.58, 0.81));

                // Palette: warm gold toward the upper-left, dark bronze toward the
                // lower-right. The actual colours come from RingSettings uniforms.
                float light = clamp(0.53 - dot(normalize(uv + vec2(1.0e-4)), gradDir) * 0.38
                        + 0.06 * sin(uTime * 0.25 + ang * 1.3), 0.0, 1.0);
                vec3 col = mix(uDark, uHot, light);

                // Bright ribbon rim.
                float rim = smoothstep(0.54, 0.98, edge);
                col += uGlow * rim * 0.38;

                // Alternating energy flow along rings and blades.
                float freq = (part > 3.5 && part < 4.5) ? 24.0 : 15.0;
                float speed = (part > 1.5 && part < 2.5) ? -2.0 : 1.5;
                float flow = pow(0.5 + 0.5 * sin(ang * freq + vData.z * 6.28318
                        - uTime * speed + seed * 23.0), 12.0);
                col += uGlow * flow * (0.18 + 0.13 * rim);

                // Hot centre line on the vertical spear.
                if (part > 5.5 && part < 6.5) {
                    col += uGlow * pow(1.0 - clamp(abs(uv.x) / 0.022, 0.0, 1.0), 6.0) * 0.35;
                }

                // Subtle metallic mottling and moving grain.
                float n = fbm(uv * 8.0 + vec2(seed * 19.0, uTime * 0.05));
                col *= 0.88 + 0.20 * n;

                // The lower middle arc is actively corroded: holes open and close slowly.
                float erode = 0.0;
                if (part > 1.5 && part < 2.5 && uv.y < 0.0) {
                    float grain = fbm(uv * 28.0 + vec2(seed * 11.0, uTime * 0.08));
                    erode = smoothstep(0.55, 0.80, grain)
                            * smoothstep(0.0, 0.35, -uv.y) * 0.55;
                }

                float alpha = uAlpha * mask * (1.0 - erode);
                alpha *= 0.94 + 0.06 * sin(uTime * 2.3 + seed * 31.0);

                // A touch of edge shading makes the glyph read as a solid object, not a sprite.
                col *= mix(0.72, 1.0, smoothstep(0.0, 0.30, 1.0 - edge));
                fragColor = vec4(col, alpha);
            }
            """;

    private int program;
    private int emblemVao;
    private int emblemVbo;
    private int emblemVertices;
    private int auraVao;
    private int auraVbo;
    private int auraVertices;

    private int uMvp;
    private int uTime;
    private int uAlpha;
    private int uAura;
    private int uEmbers;
    private int uMode;
    private int uHot;
    private int uDark;
    private int uGlow;
    private int uGlowScale;
    private int uAngleOuter;
    private int uAngleMiddle;
    private int uSceneDepth;
    private int uManualDepth;
    private int uScreenSize;

    private float emblemAlpha;
    private boolean initialized;
    private boolean failed;

    boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            compileAndLink();
            upload(DivineRingGeometry.buildEmblem(), true);
            upload(DivineRingGeometry.buildAura(), false);
            initialized = true;
            LOGGER.info("[DivineRing] Custom GLSL program compiled; emblem={} vertices, aura={} vertices.",
                    emblemVertices, auraVertices);
            return true;
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[DivineRing] Could not initialise the custom OpenGL ring renderer. "
                    + "The ring will stay hidden this session.", t);
            destroy();
            return false;
        }
    }

    void begin(Matrix4f mvp, float time, float alpha, float aura,
               float angleOuter, float angleMiddle,
               boolean embers, RingSettings settings) {
        GL20.glUseProgram(program);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            mvp.get(buffer);
            GL20.glUniformMatrix4fv(uMvp, false, buffer);
        }
        emblemAlpha = alpha;
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uAlpha, alpha);
        GL20.glUniform1f(uAura, aura);
        GL20.glUniform1f(uEmbers, embers ? 1.0f : 0.0f);
        GL20.glUniform1f(uGlowScale, 1.0f);
        GL20.glUniform1f(uAngleOuter, angleOuter);
        GL20.glUniform1f(uAngleMiddle, angleMiddle);
        GL20.glUniform1i(uManualDepth, 0);
        GL20.glUniform1i(uSceneDepth, 7);
        GL20.glUniform2f(uScreenSize, 1.0f, 1.0f);
        setColor(uHot, settings.hotColor());
        setColor(uDark, settings.darkColor());
        setColor(uGlow, settings.glowColor());
    }

    void setManualDepth(boolean enabled, int screenWidth, int screenHeight) {
        GL20.glUniform1i(uManualDepth, enabled ? 1 : 0);
        GL20.glUniform2f(uScreenSize, Math.max(1, screenWidth), Math.max(1, screenHeight));
    }

    void drawAura() {
        GL20.glUniform1f(uMode, 1.0f);
        GL20.glUniform1f(uGlowScale, 1.0f);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                GL11.GL_ONE, GL11.GL_ONE);
        GL30.glBindVertexArray(auraVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, auraVertices);
    }

    /**
     * Additive inflated-silhouette bloom pass. Call two or three times with increasing
     * scale and decreasing strength before {@link #drawEmblem()}.
     */
    void drawGlow(float scale, float strength) {
        GL20.glUniform1f(uMode, 2.0f);
        GL20.glUniform1f(uGlowScale, scale);
        GL20.glUniform1f(uAlpha, emblemAlpha * strength);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                GL11.GL_ONE, GL11.GL_ONE);
        GL30.glBindVertexArray(emblemVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, emblemVertices);
    }

    void drawEmblem() {
        GL20.glUniform1f(uMode, 0.0f);
        GL20.glUniform1f(uGlowScale, 1.0f);
        GL20.glUniform1f(uAlpha, emblemAlpha);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glBindVertexArray(emblemVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, emblemVertices);
    }

    private void setColor(int location, int rgb) {
        GL20.glUniform3f(location,
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f);
    }

    private void compileAndLink() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int p = GL20.glCreateProgram();
        if (p == 0) {
            throw new IllegalStateException("glCreateProgram returned 0");
        }
        GL20.glAttachShader(p, vertex);
        GL20.glAttachShader(p, fragment);
        GL20.glBindAttribLocation(p, 0, "aPos");
        GL20.glBindAttribLocation(p, 1, "aUV");
        GL20.glBindAttribLocation(p, 2, "aData");
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("Divine ring shader link failed:\n" + log);
        }
        program = p;

        uMvp = GL20.glGetUniformLocation(p, "uMvp");
        uTime = GL20.glGetUniformLocation(p, "uTime");
        uAlpha = GL20.glGetUniformLocation(p, "uAlpha");
        uAura = GL20.glGetUniformLocation(p, "uAura");
        uEmbers = GL20.glGetUniformLocation(p, "uEmbers");
        uMode = GL20.glGetUniformLocation(p, "uMode");
        uHot = GL20.glGetUniformLocation(p, "uHot");
        uDark = GL20.glGetUniformLocation(p, "uDark");
        uGlow = GL20.glGetUniformLocation(p, "uGlow");
        uGlowScale = GL20.glGetUniformLocation(p, "uGlowScale");
        uAngleOuter = GL20.glGetUniformLocation(p, "uAngleOuter");
        uAngleMiddle = GL20.glGetUniformLocation(p, "uAngleMiddle");
        uSceneDepth = GL20.glGetUniformLocation(p, "uSceneDepth");
        uManualDepth = GL20.glGetUniformLocation(p, "uManualDepth");
        uScreenSize = GL20.glGetUniformLocation(p, "uScreenSize");

        if (uMvp < 0 || uTime < 0 || uAlpha < 0 || uAura < 0 || uEmbers < 0
                || uMode < 0 || uHot < 0 || uDark < 0 || uGlow < 0 || uGlowScale < 0
                || uAngleOuter < 0 || uAngleMiddle < 0
                || uSceneDepth < 0 || uManualDepth < 0 || uScreenSize < 0) {
            throw new IllegalStateException("One or more divine ring uniforms were optimized out");
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException("glCreateShader returned 0");
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Divine ring shader compile failed:\n" + log);
        }
        return shader;
    }

    private void upload(MeshData mesh, boolean emblem) {
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        int floats = mesh.vertexCount() * MeshData.FLOATS_PER_VERTEX;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(floats);
        buffer.put(mesh.raw(), 0, floats).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        if (emblem) {
            emblemVao = vao;
            emblemVbo = vbo;
            emblemVertices = mesh.vertexCount();
        } else {
            auraVao = vao;
            auraVbo = vbo;
            auraVertices = mesh.vertexCount();
        }
    }

    private void destroy() {
        if (emblemVbo != 0) {
            GL15.glDeleteBuffers(emblemVbo);
            emblemVbo = 0;
        }
        if (auraVbo != 0) {
            GL15.glDeleteBuffers(auraVbo);
            auraVbo = 0;
        }
        if (emblemVao != 0) {
            GL30.glDeleteVertexArrays(emblemVao);
            emblemVao = 0;
        }
        if (auraVao != 0) {
            GL30.glDeleteVertexArrays(auraVao);
            auraVao = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }
}
