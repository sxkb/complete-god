package com.divinering.network;

import com.divinering.RingSettings;
import com.divinering.client.DivineRingClient;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Optional server synchronization for ring visibility, appearance and rotation speed.
 *
 * <p>The channel is deliberately small and namespaced ({@code divinering:sync}), so it
 * can coexist with a host mod's own packets after a source merge. The server only
 * stores/rebroadcasts state; it never renders or ticks the ring.</p>
 *
 * <p>If the host mod does not call {@link com.divinering.DivineRing#initCommon}, the
 * client-only ring still works; only cross-player synchronization is disabled.</p>
 */
public final class DivineRingNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_NAME = new ResourceLocation("divinering", "sync");

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private static final Map<UUID, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static boolean registered;

    private DivineRingNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(UpdateMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateMessage::encode)
                .decoder(UpdateMessage::decode)
                .consumerMainThread(UpdateMessage::handle)
                .add();
        CHANNEL.messageBuilder(StateMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StateMessage::encode)
                .decoder(StateMessage::decode)
                .consumerMainThread(StateMessage::handle)
                .add();
        MinecraftForge.EVENT_BUS.addListener(DivineRingNetwork::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(DivineRingNetwork::onPlayerLoggedOut);
        LOGGER.info("[DivineRing] Server sync channel registered.");
    }

    /** True when the remote side negotiated this channel. */
    public static boolean isRemotePresent(net.minecraft.network.Connection connection) {
        return registered && connection != null && CHANNEL.isRemotePresent(connection);
    }

    /** Called from the client after the local state changes. */
    public static void sendUpdate(boolean enabled, RingSettings settings) {
        if (!registered) {
            return;
        }
        CHANNEL.sendToServer(new UpdateMessage(enabled, settings));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (Map.Entry<UUID, ServerState> entry : SERVER_STATES.entrySet()) {
            sendTo(player, entry.getKey(), entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SERVER_STATES.remove(player.getUUID());
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
        if (!isRemotePresent(target.connection.connection)) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new StateMessage(playerId, state.enabled(), state.settings()));
    }

    private record ServerState(boolean enabled, RingSettings settings) {
    }

    record UpdateMessage(boolean enabled, RingSettings settings) {

        static void encode(UpdateMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.enabled);
            writeSettings(buffer, message.settings);
        }

        static UpdateMessage decode(FriendlyByteBuf buffer) {
            boolean enabled = buffer.readBoolean();
            return new UpdateMessage(enabled, readSettings(buffer));
        }

        static void handle(UpdateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerState state = new ServerState(message.enabled, message.settings);
                SERVER_STATES.put(player.getUUID(), state);
                broadcast(player, player.getUUID(), state);
            }
            context.setPacketHandled(true);
        }
    }

    record StateMessage(UUID playerId, boolean enabled, RingSettings settings) {

        static void encode(StateMessage message, FriendlyByteBuf buffer) {
            buffer.writeUUID(message.playerId);
            buffer.writeBoolean(message.enabled);
            writeSettings(buffer, message.settings);
        }

        static StateMessage decode(FriendlyByteBuf buffer) {
            UUID playerId = buffer.readUUID();
            boolean enabled = buffer.readBoolean();
            return new StateMessage(playerId, enabled, readSettings(buffer));
        }

        static void handle(StateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    DivineRingClient.applyServerState(message.playerId, message.enabled, message.settings));
            context.setPacketHandled(true);
        }
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
