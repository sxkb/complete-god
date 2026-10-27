package com.divinering.client.render;

import com.divinering.client.DivineRingShaderCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * Copies the world depth buffer at AFTER_ENTITIES into a private depth texture.
 *
 * <p>Iris/Oculus' final composite writes the final colour into the main target but
 * does not leave a usable world depth buffer there. Drawing the ring at AFTER_LEVEL
 * with the normal depth test would therefore make it visible through terrain. This
 * snapshot lets the ring shader do a manual depth comparison against the real world
 * depth captured before Iris' final pass.</p>
 */
final class DivineRingDepthSnapshot {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEPTH_TEXTURE_UNIT = 7;

    private static int framebuffer;
    private static int sourceFramebuffer;
    private static int depthTexture;
    private static int width;
    private static int height;
    private static boolean valid;
    private static boolean loggedFailure;
    private static boolean loggedOculusDepth;

    private DivineRingDepthSnapshot() {
    }

    static void capture() {
        int oculusTexture = DivineRingShaderCompat.oculusDepthTexture();
        int oculusWidth = DivineRingShaderCompat.oculusDepthWidth();
        int oculusHeight = DivineRingShaderCompat.oculusDepthHeight();
        if (oculusTexture != 0 && oculusWidth > 0 && oculusHeight > 0
                && captureOculusDepth(oculusTexture, oculusWidth, oculusHeight)) {
            return;
        }
        captureBoundFramebuffer();
    }

    private static boolean captureOculusDepth(int texture, int w, int h) {
        if (!ensure(w, h)) {
            return false;
        }
        if (sourceFramebuffer == 0) {
            sourceFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sourceFramebuffer);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // Clear stale errors from the shader pack before checking our own blit.
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
        int error = GL11.GL_NO_ERROR;
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            error = GL11.glGetError();
        } else {
            error = status;
        }
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, 0, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);

        valid = error == GL11.GL_NO_ERROR;
        if (valid && !loggedOculusDepth) {
            loggedOculusDepth = true;
            LOGGER.info("[DivineRing] Captured Oculus depth texture {} ({}x{}).",
                    texture, w, h);
        } else if (!valid && !loggedFailure) {
            loggedFailure = true;
            LOGGER.warn("[DivineRing] Could not copy Oculus depth texture {} ({}x{}): 0x{}; "
                            + "falling back to the bound framebuffer.",
                    texture, w, h, Integer.toHexString(error));
        }
        return valid;
    }

    private static void captureBoundFramebuffer() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
        if (mainTarget == null) {
            valid = false;
            return;
        }

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int w = viewport[2] > 0 ? viewport[2] : mainTarget.width;
        int h = viewport[3] > 0 ? viewport[3] : mainTarget.height;
        if (!ensure(w, h)) {
            valid = false;
            return;
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int source = previousDraw != 0 ? previousDraw : mainTarget.frameBufferId;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        int error = GL11.glGetError();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);

        valid = error == GL11.GL_NO_ERROR;
        if (!valid && !loggedFailure) {
            loggedFailure = true;
            LOGGER.warn("[DivineRing] Could not copy the shader-pack depth buffer "
                    + "(GL error {}); the ring will fall back to plain depth testing.", error);
        }
    }

    static boolean isValid() {
        return valid && depthTexture != 0;
    }

    static int texture() {
        return depthTexture;
    }

    static int width() {
        return width;
    }

    static int height() {
        return height;
    }

    static int textureUnit() {
        return DEPTH_TEXTURE_UNIT;
    }

    private static boolean ensure(int w, int h) {
        if (w <= 0 || h <= 0) {
            return false;
        }
        if (depthTexture != 0 && width == w && height == h) {
            return true;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        destroy();
        depthTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthTexture, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL13.glActiveTexture(previousActiveTexture);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.warn("[DivineRing] Depth-copy framebuffer incomplete: 0x{}",
                    Integer.toHexString(status));
            destroy();
            return false;
        }
        width = w;
        height = h;
        return true;
    }

    private static void destroy() {
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (sourceFramebuffer != 0) {
            GL30.glDeleteFramebuffers(sourceFramebuffer);
            sourceFramebuffer = 0;
        }
        if (depthTexture != 0) {
            GL11.glDeleteTextures(depthTexture);
            depthTexture = 0;
        }
        width = 0;
        height = 0;
        valid = false;
    }

    static void bindForSampling() {
        // The active texture and binding are restored by DivineRingShader after the pass.
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + DEPTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
    }
}
