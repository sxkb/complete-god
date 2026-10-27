package com.divinering;

import com.divinering.client.DivineRingClient;
import com.divinering.network.DivineRingNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Public, mod-agnostic entry point for the divine ring.
 *
 * <h2>Standalone use</h2>
 * The bundled {@code DivineRingMod} calls {@link #install(IEventBus)} on the client.
 *
 * <h2>Merging into another mod</h2>
 * Copy the {@code com.divinering} package (and, optionally, ignore the demo
 * {@code DivineRingMod}) into the host mod, then call:
 *
 * <pre>{@code
 * DivineRing.install(modEventBus);       // show the local player by default
 * DivineRing.install(modEventBus, false); // renderer only, show() explicitly
 * }</pre>
 *
 * <p>The facade is deliberately free of hard-coded mod ids, network channels,
 * mixins and access transformers. None of these methods need to be called from a
 * server thread; on a dedicated server they become no-ops.</p>
 */
public final class DivineRing {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DivineRing() {
    }

    /**
     * Registers the optional server-sync channel and its login/logout listeners.
     *
     * <p>Call this from the host mod's constructor on both physical sides if other
     * players should see the ring. It is deliberately separate from
     * {@link #install(IEventBus)} because install is client-only.</p>
     */
    public static void initCommon(IEventBus modEventBus) {
        if (modEventBus == null) {
            throw new IllegalArgumentException("modEventBus must not be null");
        }
        DivineRingNetwork.register();
    }

    /**
     * Installs the client renderer and enables the local player's ring.
     * Safe to call exactly once from the host mod's constructor.
     */
    public static void install(IEventBus modEventBus) {
        install(modEventBus, true);
    }

    /**
     * Installs the client renderer.
     *
     * @param showLocalPlayerByDefault when true, the local player automatically gets
     *                                 a ring; when false, call {@link #show(Player)}
     *                                 from the host mod's own logic.
     */
    public static void install(IEventBus modEventBus, boolean showLocalPlayerByDefault) {
        if (!isClient()) {
            LOGGER.debug("[DivineRing] Ignoring install on a dedicated server.");
            return;
        }
        if (modEventBus == null) {
            throw new IllegalArgumentException("modEventBus must not be null");
        }
        DivineRingClient.install(modEventBus, showLocalPlayerByDefault);
    }

    /**
     * Registers a "Config" button for this mod in the Forge Mods list. Must be called
     * from the client constructor (the K keybind works even without this).
     */
    public static void registerConfigScreen() {
        if (isClient()) {
            DivineRingClient.registerConfigScreen();
        }
    }

    /** Shows the ring for a player on this client. */
    public static void show(Player player) {
        if (isClient() && player != null) {
            DivineRingClient.show(player);
        }
    }

    /** Hides the ring for a player on this client, even when auto-show is enabled. */
    public static void hide(Player player) {
        if (isClient() && player != null) {
            DivineRingClient.hide(player);
        }
    }

    /** Returns whether this client currently has a visible ring for the player. */
    public static boolean isShown(Player player) {
        return isClient() && player != null && DivineRingClient.isShown(player);
    }

    /**
     * Replaces the visual settings for one player. This also ensures a ring state
     * exists for that player, but does not override an explicit {@link #hide(Player)}.
     */
    public static void setSettings(Player player, RingSettings settings) {
        if (isClient() && player != null && settings != null) {
            DivineRingClient.setSettings(player, settings);
        }
    }

    /** Returns the player's settings, or the current default when not shown. */
    public static RingSettings getSettings(Player player) {
        if (!isClient() || player == null) {
            return RingSettings.DEFAULT;
        }
        return DivineRingClient.getSettings(player);
    }

    /** Changes the settings used by rings created afterwards. */
    public static void setDefaultSettings(RingSettings settings) {
        if (settings != null) {
            if (isClient()) {
                DivineRingClient.setDefaultSettings(settings);
            }
        }
    }

    public static RingSettings defaultSettings() {
        return isClient() ? DivineRingClient.defaultSettings() : RingSettings.DEFAULT;
    }

    private static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
