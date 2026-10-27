package com.divinering;

import com.divinering.client.DivineRingClient;
import com.divinering.network.DivineRingNetwork;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * Public, mod-agnostic entry point for the divine ring (Fabric variant).
 *
 * <p>Merging into another mod: copy the {@code com.divinering} package into the host
 * mod (ignoring the demo {@code DivineRingMod}), then call:</p>
 *
 * <pre>{@code
 * DivineRing.initCommon();        // optional server sync channel (call on init)
 * DivineRing.install();           // show the local player by default
 * DivineRing.install(false);      // renderer only, show() explicitly
 * }</pre>
 *
 * <p>The facade is deliberately free of hard-coded mod ids, mixins and access
 * transformers. On a dedicated server the install/show calls become no-ops.</p>
 */
public final class DivineRing {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DivineRing() {
    }

    /**
     * Registers the optional server-sync channel and its join/disconnect listeners.
     *
     * <p>Call this from the host mod's initializer on both physical sides if other
     * players should see the ring.</p>
     */
    public static void initCommon() {
        DivineRingNetwork.register();
    }

    /**
     * Installs the client renderer and enables the local player's ring.
     * Safe to call exactly once from the host mod's client initializer.
     */
    public static void install() {
        install(true);
    }

    /**
     * Installs the client renderer.
     *
     * @param showLocalPlayerByDefault when true, the local player automatically gets
     *                                 a ring; when false, call {@link #show(Player)}
     *                                 from the host mod's own logic.
     */
    public static void install(boolean showLocalPlayerByDefault) {
        if (!isClient()) {
            LOGGER.debug("[DivineRing] Ignoring install on a dedicated server.");
            return;
        }
        DivineRingClient.install(showLocalPlayerByDefault);
    }

    /**
     * Fabric has no Mods-list config screen; the ring config screen is opened from
     * the host mod's own -1 config entry. Kept for API parity.
     */
    public static void registerConfigScreen() {
        // no-op on Fabric
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
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
