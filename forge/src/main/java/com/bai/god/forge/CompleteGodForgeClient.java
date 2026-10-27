package com.bai.god.forge;

import com.bai.god.GodClient;
import com.bai.god.God;
import com.bai.god.client.render.GodConfigScreen;
import com.divinering.DivineRing;
import com.divinering.client.DivineRingConfig;
import com.divinering.client.DivineRingConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod.EventBusSubscriber(modid = God.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompleteGodForgeClient {

    private CompleteGodForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(GodClient.createKeyMapping());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
        GodClient.init();

        // 神环（Divine Ring 1.2.1）并入：安装渲染器。
        // 平时是否显现 = 神环配置开关（-1 配置 → 神环设置 里切换，保存即生效）；
        // /god on 开启超越一切状态后强制显现，/god off 恢复平时选择。
        boolean normalVisible = DivineRingConfig.isEnabled();
        DivineRing.install(FMLJavaModLoadingContext.get().getModEventBus(), normalVisible);

        // god 状态联动：超越一切 → 强制显现；解除 → 回到平时选择。
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
    }

    @Mod.EventBusSubscriber(modid = God.MOD_ID, value = Dist.CLIENT)
    public static final class ClientTickHandler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                GodClient.onClientTick(Minecraft.getInstance());
            }
        }
    }
}
