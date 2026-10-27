package com.divinering.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
/**
 * Captures every raw GL state bit this renderer touches and restores it afterwards.
 *
 * <p>Minecraft's GlStateManager caches state aggressively. All changes here are made
 * through raw GL while the cached values stay untouched, and every value is restored
 * to exactly what it was on entry, so vanilla and other mods see a consistent cache.</p>
 */
final class GlStateGuard implements AutoCloseable {

    private final int program;
    private final int vao;
    private final int arrayBuffer;

    private final boolean blend;
    private final boolean cull;
    private final boolean depthTest;
    private final boolean depthMask;

    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int blendEquationRgb;
    private final int blendEquationAlpha;
    private final int depthFunc;

    private boolean closed;

    GlStateGuard() {
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        GL11.glDepthMask(depthMask);
        GL11.glDepthFunc(depthFunc);
        if (depthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }

        if (cull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL20.glUseProgram(program);
    }
}
