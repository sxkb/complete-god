package com.divinering.network;

import com.divinering.RingSettings;
import com.divinering.client.DivineRingClient;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional server synchronization for ring visibility, appearance and rotation speed
 * (Fabric variant, classic buf-based networking).
 *
 * <p>The channel is deliberately small and namespaced ({@code divinering:sync}), so it
 * can coexist with a host mod's own packets after a source merge. The server only
 * stores/rebroadcasts state; it never renders or ticks the ring.</p>
 *
 * <p>Wire format (same as the standalone Forge mod): C2S = enabled + settings;
 * S2C = playerId + enabled + settings. If the remote side lacks the channel, the
 * client-only ring still works; only cross-player synchronization is disabled.</p>
 */
public final class DivineRingNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation CHANNEL = new ResourceLocation("divinering", "sync");

    private static final Map<UUID, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static boolean registered;

    private DivineRingNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                    (client, handler, buf, responseSender) -> {
                        UUID playerId = buf.readUUID();
                        boolean enabled = buf.readBoolean();
                        RingSettings settings = readSettings(buf);
                        client.execute(() ->
                                DivineRingClient.applyServerState(playerId, enabled, settings));
                    });
        }

        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    boolean enabled = buf.readBoolean();
                    RingSettings settings = readSettings(buf);
                    ServerState state = new ServerState(enabled, settings);
                    SERVER_STATES.put(player.getUUID(), state);
                    broadcast(player, player.getUUID(), state);
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            for (Map.Entry<UUID, ServerState> entry : SERVER_STATES.entrySet()) {
                sendTo(player, entry.getKey(), entry.getValue());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SERVER_STATES.remove(handler.player.getUUID()));

        LOGGER.info("[DivineRing] Server sync channel registered.");
    }

    /** True when the local client is connected to a network session. */
    public static boolean isRemotePresent(Minecraft mc) {
        return registered && mc != null && mc.getConnection() != null;
    }

    /** Called from the client after the local state changes. */
    public static void sendUpdate(boolean enabled, RingSettings settings) {
        if (!registered) {
            return;
        }
        try {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(enabled);
            writeSettings(buf, settings);
            ClientPlayNetworking.send(CHANNEL, buf);
        } catch (Throwable throwable) {
            LOGGER.debug("[DivineRing] sendUpdate skipped: {}", throwable.toString());
        }
    }

    private static void broadcast(ServerPlayer sender, UUID playerId, ServerState state) {
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendTo(target, playerId, state);
        }
    }

    private static void sendTo(ServerPlayer target, UUID playerId, ServerState state) {
        if (!ServerPlayNetworking.canSend(target, CHANNEL)) {
            return;
        }
        try {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUUID(playerId);
            buf.writeBoolean(state.enabled());
            writeSettings(buf, state.settings());
            ServerPlayNetworking.send(target, CHANNEL, buf);
        } catch (Throwable throwable) {
            LOGGER.debug("[DivineRing] sendTo skipped: {}", throwable.toString());
        }
    }

    private record ServerState(boolean enabled, RingSettings settings) {
    }

    private static void writeSettings(FriendlyByteBuf buffer, RingSettings settings) {
        buffer.writeVarInt(settings.hotColor());
        buffer.writeVarInt(settings.darkColor());
        buffer.writeVarInt(settings.glowColor());
        buffer.writeFloat(settings.scale());
        buffer.writeFloat(settings.height());
        buffer.writeFloat(settings.distance());
        buffer.writeFloat(settings.spinSpeed());
        buffer.writeFloat(settings.bob());
        buffer.writeFloat(settings.sway());
        buffer.writeFloat(settings.tilt());
        buffer.writeFloat(settings.opacity());
        buffer.writeFloat(settings.aura());
        buffer.writeBoolean(settings.embers());
        buffer.writeBoolean(settings.faceCamera());
    }

    private static RingSettings readSettings(FriendlyByteBuf buffer) {
        return RingSettings.builder()
                .hotColor(buffer.readVarInt())
                .darkColor(buffer.readVarInt())
                .glowColor(buffer.readVarInt())
                .scale(buffer.readFloat())
                .height(buffer.readFloat())
                .distance(buffer.readFloat())
                .spinSpeed(buffer.readFloat())
                .bob(buffer.readFloat())
                .sway(buffer.readFloat())
                .tilt(buffer.readFloat())
                .opacity(buffer.readFloat())
                .aura(buffer.readFloat())
                .embers(buffer.readBoolean())
                .faceCamera(buffer.readBoolean())
                .build();
    }
}
