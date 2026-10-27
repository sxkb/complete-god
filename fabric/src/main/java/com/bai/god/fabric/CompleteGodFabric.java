package com.bai.god.fabric;

import com.bai.god.God;
import com.bai.god.GodEvents;
import com.divinering.DivineRing;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class CompleteGodFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        God.init(new FabricPlatform());
        DivineRing.initCommon();
        FabricEvents.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GodEvents.registerCommands(dispatcher));
        God.LOGGER.info("完整神 Fabric 入口初始化完成");
    }
}
