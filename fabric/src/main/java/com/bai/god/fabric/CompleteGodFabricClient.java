package com.bai.god.fabric;

import com.bai.god.God;
import com.bai.god.GodClient;
import com.bai.god.client.render.GodConfigScreen;
import com.divinering.DivineRing;
import com.divinering.client.DivineRingConfig;
import com.divinering.client.DivineRingConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;

public class CompleteGodFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(GodClient.createKeyMapping());
        ClientTickEvents.END_CLIENT_TICK.register(GodClient::onClientTick);
        FabricNetwork.registerClient();
        GodClient.init();

        // 神环（Divine Ring 1.2.1）并入（Fabric 端）：平时显隐由神环配置开关决定；
        // /god on 开启超越一切状态后强制显现，/god off 恢复平时选择。
        boolean normalVisible = DivineRingConfig.isEnabled();
        DivineRing.install(normalVisible);
        GodClient.setGodStateListener(isGod -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (isGod) {
                    DivineRing.show(mc.player);
                } else if (DivineRingConfig.isEnabled()) {
                    DivineRing.show(mc.player);
                } else {
                    DivineRing.hide(mc.player);
                }
            }
        });

        // -1 配置界面加入"神环设置"入口（神环按键已全部移除，这里与 /god on 是唯二入口）。
        GodConfigScreen.ringConfigOpener = parent ->
                Minecraft.getInstance().setScreen(new DivineRingConfigScreen(parent));

        God.LOGGER.info("完整神 Fabric 客户端入口初始化完成");
    }
}
