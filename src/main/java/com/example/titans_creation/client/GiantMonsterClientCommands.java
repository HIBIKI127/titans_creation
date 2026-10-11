package com.example.titans_creation.client;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端指令：打开巨型怪物配置菜单。
 *
 * <p>使用 {@code /giantconfig} 打开配置主菜单（仅本地客户端指令，不影响服务端）。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, value = Dist.CLIENT)
public final class GiantMonsterClientCommands {

    private GiantMonsterClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("giantconfig")
                .executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    // 指令打开时不需要返回上级界面（关闭即回到游戏）
                    GiantMonsterScreen.setConfigReturnScreen(null);
                    minecraft.execute(() -> minecraft.setScreen(new GiantMonsterMainMenuScreen()));
                    return 1;
                }));
    }
}
