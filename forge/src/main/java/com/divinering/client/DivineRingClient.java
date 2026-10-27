package com.divinering.client;

import com.divinering.RingSettings;
import com.divinering.client.render.DivineRingRenderer;
import com.divinering.network.DivineRingNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side state, key handling and event glue. Only loaded on the physical client.
 */
public final class DivineRingClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, RingState> STATES = new ConcurrentHashMap<>();

    // 神环开关按键（G）与调整按键（K）均已移除：
    // 平时显隐由神环配置界面（-1 配置 → 神环设置）控制，/god on 开启超越一切后强制显现。

    private static volatile RingSettings defaultSettings = RingSettings.DEFAULT;
    private static boolean installed;
    private static boolean autoShowLocal = true;
    private static boolean configScreenRegistered;
    private static boolean publishedThisSession;
    private static boolean shaderPackActive;

    private DivineRingClient() {
    }

    public static void install(IEventBus modEventBus, boolean showLocalPlayerByDefault) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        if (installed) {
            LOGGER.debug("[DivineRing] install() called more than once; keeping the first installation.");
            return;
        }
        installed = true;
        autoShowLocal = showLocalPlayerByDefault;

        DivineRingConfig.load();
        defaultSettings = DivineRingConfig.settings();

        modEventBus.addListener(DivineRingClient::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(DivineRingClient::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(DivineRingClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(DivineRingClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(DivineRingClient::onLoggingOut);

        Minecraft mc = Minecraft.getInstance();
        if (autoShowLocal && mc.player != null) {
            STATES.put(mc.player.getUUID(),
                    new RingState(defaultSettings, DivineRingConfig.isEnabled()));
        }

        LOGGER.info("[DivineRing] Gold ring renderer installed (autoShowLocal={}, enabled={}).",
                autoShowLocal, DivineRingConfig.isEnabled());
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 神环不再注册任何按键：显隐由宿主（-1 配置 / /god on）控制。
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            DivineRingRenderer.captureView(event.getPoseStack().last().pose());
        }

        if (STATES.isEmpty()) {
            return;
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            // Weather is rendered after all entities and entity batching, but before
            // Iris' final level composite. Capturing here makes the Oculus depth
            // texture include the local player body.
            if (shaderPackActive) {
                DivineRingRenderer.captureDepthSnapshot();
            }
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            // Fallback for levels where AFTER_WEATHER was not dispatched.
            DivineRingRenderer.captureView(event.getPoseStack().last().pose());
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (shaderPackActive) {
                DivineRingRenderer.renderAfterPostProcessing(event, STATES, defaultSettings);
            } else {
                DivineRingRenderer.renderAfterLevel(event, STATES, defaultSettings);
            }
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        publishedThisSession = false;
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        publishedThisSession = false;
    }

    @SuppressWarnings("removal")
    public static void registerConfigScreen() {
        if (configScreenRegistered) {
            return;
        }
        configScreenRegistered = true;
        try {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, parent) -> new DivineRingConfigScreen(parent)));
        } catch (Throwable throwable) {
            LOGGER.warn("[DivineRing] Could not register the Mods-list config screen; "
                    + "the K keybind still opens it.", throwable);
        }
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
        ClientPacketListener connection = mc.getConnection();
        return connection != null && DivineRingNetwork.isRemotePresent(connection.getConnection());
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
