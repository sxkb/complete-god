package com.divinering.client.render;

import com.divinering.RingSettings;
import com.divinering.client.RingState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Map;
import java.util.UUID;

/**
 * World-space renderer for the divine ring.
 *
 * <p>It runs at {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES}, builds its own MVP
 * matrix and draws two private VAOs with a private GLSL program. It never creates a
 * RenderType, never calls the particle engine and never uses a vanilla vertex format.</p>
 */
public final class DivineRingRenderer {

    private static final DivineRingShader SHADER = new DivineRingShader();
    private static final Matrix4f CAPTURED_VIEW = new Matrix4f();
    private static boolean hasCapturedView;

    private DivineRingRenderer() {
    }

    /** Stores the camera view from AFTER_ENTITIES, the last stage with a valid world pose. */
    public static void captureView(Matrix4f view) {
        CAPTURED_VIEW.set(view);
        hasCapturedView = true;
    }

    /** Called at AFTER_ENTITIES when a shader pack is active. */
    public static void captureDepthSnapshot() {
        DivineRingDepthSnapshot.capture();
    }

    public static void render(RenderLevelStageEvent event, Map<UUID, RingState> states,
                              RingSettings fallbackSettings) {
        renderInternal(event, states, fallbackSettings,
                event.getPoseStack().last().pose(), false);
    }

    /**
     * Without a shader pack, AFTER_LEVEL still guarantees that all entities (including
     * the local player) have been submitted. Some render mods batch entities and emit
     * them after AFTER_ENTITIES; drawing there would let the body overwrite the ring.
     */
    public static void renderAfterLevel(RenderLevelStageEvent event,
                                        Map<UUID, RingState> states,
                                        RingSettings fallbackSettings) {
        if (!hasCapturedView) {
            return;
        }
        renderInternal(event, states, fallbackSettings, CAPTURED_VIEW, false);
    }
    public static void renderAfterPostProcessing(RenderLevelStageEvent event,
                                                 Map<UUID, RingState> states,
                                                 RingSettings fallbackSettings) {
        if (!hasCapturedView) {
            return;
        }
        renderInternal(event, states, fallbackSettings, CAPTURED_VIEW, true);
    }
    private static void renderInternal(RenderLevelStageEvent event, Map<UUID, RingState> states,
                                       RingSettings fallbackSettings, Matrix4f view,
                                       boolean afterPostProcessing) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null || states.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        Matrix4f projection = event.getProjectionMatrix();
        float time = (level.getGameTime() + partialTick) * 0.05f;

        for (Map.Entry<UUID, RingState> entry : states.entrySet()) {
            RingState state = entry.getValue();
            if (state == null || !state.visible) {
                continue;
            }

            Player player = level.getPlayerByUUID(entry.getKey());
            if (player == null || player.isInvisible() || player.isDeadOrDying()
                    || player.isSpectator() || player.isSleeping()) {
                continue;
            }
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                // A ring physically behind the player is outside a first-person frustum;
                // skip it instead of letting it clip through the near plane.
                continue;
            }

            RingSettings settings = state.settings != null ? state.settings : fallbackSettings;

            float phase = player.getId() * 0.6180339f;
            double px = Mth.lerp(partialTick, player.xo, player.getX());
            double py = Mth.lerp(partialTick, player.yo, player.getY());
            double pz = Mth.lerp(partialTick, player.zo, player.getZ());
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);

            long now = System.nanoTime();
            float dt = state.lastRenderNanos == 0L
                    ? 0.0f
                    : Math.min(0.10f, (now - state.lastRenderNanos) / 1_000_000_000.0f);
            state.lastRenderNanos = now;

            if (!state.initialized) {
                state.smoothYaw = bodyYaw;
                state.initialized = true;
            } else if (dt > 0.0f) {
                float follow = 1.0f - (float) Math.exp(-dt * 11.0f);
                state.smoothYaw += Mth.wrapDegrees(bodyYaw - state.smoothYaw) * follow;
            }
            float smoothYaw = state.smoothYaw;

            float bob = settings.bob() * Mth.sin(time * 1.25f + phase);
            float sway = settings.sway() * Mth.cos(time * 0.72f + phase);
            double backX = Math.sin(Math.toRadians(smoothYaw));
            double backZ = -Math.cos(Math.toRadians(smoothYaw));
            double rightX = Math.cos(Math.toRadians(smoothYaw));
            double rightZ = Math.sin(Math.toRadians(smoothYaw));

            double ringX = px + backX * settings.distance() + rightX * sway;
            double ringZ = pz + backZ * settings.distance() + rightZ * sway;
            double ringY = py + settings.height()
                    + (player.getBbHeight() - 1.8f) * 0.45f + bob;

            double cameraX = camera.getPosition().x;
            double cameraY = camera.getPosition().y;
            double cameraZ = camera.getPosition().z;
            double dx = cameraX - ringX;
            double dy = cameraY - ringY;
            double dz = cameraZ - ringZ;
            float cameraDistance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Fade if the third-person camera is almost inside the glyph.
            float fadeT = Mth.clamp((cameraDistance - 0.30f) / 0.85f, 0.0f, 1.0f);
            float fade = fadeT * fadeT * (3.0f - 2.0f * fadeT);
            float alpha = settings.opacity() * fade;
            if (alpha <= 0.002f) {
                continue;
            }

            float modelYaw;
            if (settings.faceCamera()) {
                modelYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            } else {
                // Local +Z is the glyph's face normal. Rotating by 180 - yaw makes
                // that normal point out of the player's back.
                modelYaw = 180.0f - smoothYaw;
            }

            float wobbleTilt = settings.tilt() + Mth.sin(time * 0.90f + phase) * 0.8f;
            float ringAngle = (float) Math.toRadians(time * settings.spinSpeed());
            // Outer ring + blades: counter-clockwise. Middle ring + wings + chips:
            // clockwise. Inner ring + central spear: no rotation at all.
            float angleOuter = ringAngle;
            float angleMiddle = -ringAngle;
            float scale = settings.scale() * (1.0f + 0.012f * Mth.sin(time * 1.10f + phase));

            Matrix4f model = new Matrix4f();
            model.translate((float) (ringX - cameraX), (float) (ringY - cameraY), (float) (ringZ - cameraZ));
            model.rotateY((float) Math.toRadians(modelYaw));
            model.rotateX((float) Math.toRadians(wobbleTilt));
            model.scale(scale);

            Matrix4f mvp = new Matrix4f(projection).mul(view).mul(model);
            draw(mvp, time, alpha, settings.aura() * fade, angleOuter, angleMiddle,
                    settings, afterPostProcessing);
        }
    }

    private static void draw(Matrix4f mvp, float time, float alpha, float aura,
                             float angleOuter, float angleMiddle, RingSettings settings,
                             boolean afterPostProcessing) {
        if (!SHADER.ensureInitialized()) {
            return;
        }
        try (GlStateGuard ignored = new GlStateGuard()) {
            SHADER.begin(mvp, time, alpha, aura, angleOuter, angleMiddle,
                    settings.embers(), settings);

            boolean manualDepth = afterPostProcessing && DivineRingDepthSnapshot.isValid();
            int screenWidth = Minecraft.getInstance().getMainRenderTarget().width;
            int screenHeight = Minecraft.getInstance().getMainRenderTarget().height;
            SHADER.setManualDepth(manualDepth, screenWidth, screenHeight);
            if (manualDepth) {
                // The main depth buffer contains the player body; the shader compares
                // against the terrain-only snapshot instead.
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }

            int previousActiveTexture = 0;
            int previousTexture = 0;
            if (manualDepth) {
                previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + DivineRingDepthSnapshot.textureUnit());
                previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, DivineRingDepthSnapshot.texture());
            }

            try {
                if (aura > 0.002f) {
                    SHADER.drawAura();
                }
                // Three additive silhouette passes give every line a soft neon halo, not
                // just the painted rim inside the geometry.
                SHADER.drawGlow(1.014f, 0.20f);
                SHADER.drawGlow(1.036f, 0.085f);
                SHADER.drawGlow(1.068f, 0.035f);
                SHADER.drawEmblem();
            } finally {
                if (manualDepth) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                    GL13.glActiveTexture(previousActiveTexture);
                }
            }
        }
    }
}
