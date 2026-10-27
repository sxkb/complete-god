package com.bai.god.forge;

import com.bai.god.God;
import com.bai.god.GodEvents;
import com.bai.god.platform.Platforms;
import com.divinering.DivineRing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(God.MOD_ID)
public class CompleteGodForge {

    public CompleteGodForge() {
        God.init(new ForgePlatform());
        ForgeNetwork.register();
        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        // 神环（Divine Ring）服务端同步频道：客户端/服务端都要注册，其他玩家才能看到你的神环。
        DivineRing.initCommon(FMLJavaModLoadingContext.get().getModEventBus());
        God.LOGGER.info("完整神 Forge 入口初始化完成（已并入神环 Divine Ring）");
    }
}
