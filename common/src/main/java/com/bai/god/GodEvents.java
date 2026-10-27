package com.bai.god;

import com.bai.god.feature.GodModeSystem;
import com.bai.god.feature.GodPlayerJoinHandler;
import com.bai.god.feature.HiddenAchievementSystem;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class GodEvents {

    private GodEvents() {
    }

    public static void onServerStarting() {
        GodModeSystem.initialize();
        HiddenAchievementSystem.initialize();
        GodPlayerJoinHandler.initialize();
    }

    public static void onServerStarted(MinecraftServer server) {
        GodModeSystem.setServer(server);
    }

    public static void onServerStopping() {
        GodModeSystem.clearServer();
    }

    public static void onServerTick(MinecraftServer server) {
        GodModeSystem.onServerTick(server);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        GodModeSystem.onPlayerJoin(player);
        GodPlayerJoinHandler.handlePlayerJoin(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        GodModeSystem.onPlayerLeave(player);
    }

    public static void onPlayerClone(ServerPlayer player, boolean wasDeath) {
        GodPlayerJoinHandler.handlePlayerClone(player, wasDeath);
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        GodPlayerJoinHandler.handlePlayerJoin(player);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 追加式注册：若 /god 根已被其他模组注册（如言出法随 /god shen），
        // 仅追加 on/off 分支，不整体覆盖根节点，保证两个模组命令共存。
        var onNode = Commands.literal("on")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFailure(
                                Component.literal("该命令只能由玩家执行"));
                        return 0;
                    }
                    GodModeSystem.activateGodMode(player);
                    return 1;
                })
                .build();
        var offNode = Commands.literal("off")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFailure(
                                Component.literal("该命令只能由玩家执行"));
                        return 0;
                    }
                    GodModeSystem.deactivateGodMode(player);
                    return 1;
                })
                .build();
        var godRoot = dispatcher.getRoot().getChild("god");
        if (godRoot != null) {
            if (godRoot.getChild("on") == null) {
                godRoot.addChild(onNode);
            }
            if (godRoot.getChild("off") == null) {
                godRoot.addChild(offNode);
            }
        } else {
            dispatcher.register(Commands.literal("god")
                    .then(Commands.literal("on")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    ctx.getSource().sendFailure(
                                            Component.literal("该命令只能由玩家执行"));
                                    return 0;
                                }
                                GodModeSystem.activateGodMode(player);
                                return 1;
                            }))
                    .then(Commands.literal("off")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    ctx.getSource().sendFailure(
                                            Component.literal("该命令只能由玩家执行"));
                                    return 0;
                                }
                                GodModeSystem.deactivateGodMode(player);
                                return 1;
                            })));
        }
        God.LOGGER.info("完整神 服务端事件与 /god 指令已接线");
    }
}
