package com.divinering.client;

import com.divinering.RingSettings;
import com.divinering.client.render.DivineRingRenderer;
import com.divinering.network.DivineRingNetwork;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side state and event glue (Fabric variant). Only loaded on the physical client.
 *
 * <p>The toggle (G) and config (K) keybinds are intentionally removed: visibility is
 * controlled by the host mod (the ring config screen via the -1 config, and the
 * {@code /god on} state that forces the ring to show).</p>
 */
public final class DivineRingClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, RingState> STATES = new ConcurrentHashMap<>();

    private static volatile RingSettings defaultSettings = RingSettings.DEFAULT;
    private static boolean installed;
    private static boolean autoShowLocal = true;
    private static boolean publishedThisSession;
    private static boolean shaderPackActive;

    private DivineRingClient() {
    }

    public static void install(boolean showLocalPlayerByDefault) {
        if (installed) {
            LOGGER.debug("[DivineRing] install() called more than once; keeping the first installation.");
            return;
        }
        installed = true;
        autoShowLocal = showLocalPlayerByDefault;

        DivineRingConfig.load();
        defaultSettings = DivineRingConfig.settings();

        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onLoggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onLoggingOut());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            // Keep an early view/pose fallback. The authoritative depth snapshot is
            // refreshed at LAST, after entities/weather and before Iris' final pass.
            DivineRingRenderer.captureView(ctx.matrixStack().last().pose());
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            DivineRingRenderer.captureView(ctx.matrixStack().last().pose());
        });
        WorldRenderEvents.LAST.register(ctx -> {
            // Fabric's LAST is emitted before Iris' RETURN finalizer, after entities
            // and weather. Capture there so the Iris depth texture contains the local
            // player body as well as terrain. Do not overwrite the reliable view pose
            // captured at AFTER_ENTITIES.
            if (shaderPackActive) {
                DivineRingRenderer.captureDepthSnapshot();
            }
        });
        WorldRenderEvents.END.register(ctx -> {
            // END is emitted at LevelRenderer.renderLevel RETURN. With Iris this is
            // after its final level composite, matching Forge's AFTER_LEVEL.
            Matrix4f projection = ctx.projectionMatrix();
            if (shaderPackActive) {
                DivineRingRenderer.renderAfterPostProcessing(ctx.camera(), ctx.tickDelta(),
                        projection, STATES, defaultSettings);
            } else {
                DivineRingRenderer.renderAfterLevel(ctx.camera(), ctx.tickDelta(),
                        projection, STATES, defaultSettings);
            }
        });

        Minecraft mc = Minecraft.getInstance();
        if (autoShowLocal && mc.player != null) {
            STATES.put(mc.player.getUUID(),
                    new RingState(defaultSettings, DivineRingConfig.isEnabled()));
        }

        LOGGER.info("[DivineRing] Gold ring renderer installed (autoShowLocal={}, enabled={}).",
                autoShowLocal, DivineRingConfig.isEnabled());
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        boolean activeShaderPack = DivineRingShaderCompat.isShaderPackActive();
        if (activeShaderPack != shaderPackActive) {
            shaderPackActive = activeShaderPack;
            LOGGER.info("[DivineRing] Shader-pack render path {}", shaderPackActive
                    ? "enabled (Oculus depth bridge + AFTER_LEVEL)"
                    : "disabled (native depth + AFTER_LEVEL)");
        }

        if (mc.level == null || mc.player == null) {
            if (mc.level == null) {
                STATES.clear();
                publishedThisSession = false;
            }
            return;
        }

        UUID local = mc.player.getUUID();
        if (autoShowLocal && !STATES.containsKey(local)) {
            STATES.put(local, new RingState(defaultSettings, DivineRingConfig.isEnabled()));
        }

        if (!publishedThisSession) {
            publishLocalState();
            publishedThisSession = true;
        }

        // Drop remote states for players who left. The local state is retained so an
        // explicit hide() survives dimension changes.
        if (mc.player.tickCount % 100 == 0) {
            STATES.keySet().removeIf(id -> !id.equals(local) && mc.level.getPlayerByUUID(id) == null);
        }
    }

    private static void onLoggingIn() {
        publishedThisSession = false;
    }

    private static void onLoggingOut() {
        STATES.clear();
        publishedThisSession = false;
    }

    public static void openConfigScreen(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new DivineRingConfigScreen(parent));
    }

    private static void publishLocalState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isNetworkPresent(mc)) {
            return;
        }
        RingState state = STATES.get(mc.player.getUUID());
        boolean enabled = state != null && state.visible;
        RingSettings settings = state != null && state.settings != null
                ? state.settings : defaultSettings;
        DivineRingNetwork.sendUpdate(enabled, settings);
    }

    private static boolean isNetworkPresent(Minecraft mc) {
        return DivineRingNetwork.isRemotePresent(mc);
    }

    public static void show(Player player) {
        updateState(player.getUUID(), true, null);
        if (player == Minecraft.getInstance().player) {
            syncLocalIfPossible();
        }
    }

    public static void hide(Player player) {
        updateState(player.getUUID(), false, null);
        if (player == Minecraft.getInstance().player) {
            syncLocalIfPossible();
        }
    }

    public static boolean isShown(Player player) {
        RingState state = STATES.get(player.getUUID());
        return state != null && state.visible;
    }

    public static void setSettings(Player player, RingSettings settings) {
        updateState(player.getUUID(), null, settings);
        if (player == Minecraft.getInstance().player) {
            syncLocalIfPossible();
        }
    }

    public static RingSettings getSettings(Player player) {
        RingState state = STATES.get(player.getUUID());
        return state == null || state.settings == null ? defaultSettings : state.settings;
    }

    public static RingSettings defaultSettings() {
        return defaultSettings;
    }

    public static void setDefaultSettings(RingSettings settings) {
        if (settings != null) {
            defaultSettings = settings;
        }
    }

    /**
     * Applies a local edit and optionally broadcasts it. Does not write the JSON config;
     * the config screen owns persistence.
     */
    public static void applyLocal(boolean enabled, RingSettings settings, boolean sync) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        updateState(mc.player.getUUID(), enabled, settings);
        if (sync) {
            syncLocalIfPossible();
        }
    }

    /** Live slider preview: changes only this client's local ring, no sync, no file write. */
    public static void previewLocal(boolean enabled, RingSettings settings) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        updateState(mc.player.getUUID(), enabled, settings);
    }

    /** Called by the network layer for states broadcast by the server. */
    public static void applyServerState(UUID playerId, boolean enabled, RingSettings settings) {
        updateState(playerId, enabled, settings);
    }

    private static void updateState(UUID playerId, Boolean visible, RingSettings settings) {
        STATES.compute(playerId, (id, state) -> {
            if (state == null) {
                state = new RingState(settings != null ? settings : defaultSettings,
                        visible != null && visible);
            } else {
                if (settings != null) {
                    state.settings = settings;
                }
                if (visible != null) {
                    state.visible = visible;
                }
            }
            return state;
        });
    }

    private static void syncLocalIfPossible() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isNetworkPresent(mc)) {
            return;
        }
        RingState state = STATES.get(mc.player.getUUID());
        if (state == null) {
            return;
        }
        DivineRingNetwork.sendUpdate(state.visible, state.settings);
    }
}
