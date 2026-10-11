package com.example.titans_creation.client.grapple;

import com.example.titans_creation.CombatNetwork;
import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.client.CombatKeyMappings;
import com.example.titans_creation.grapple.GrappleManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端抓钩：手套功能键触发 + 拉动本地预测。
 *
 * <p>左手套功能键 / 右手套功能键分别触发对应手套槽位里的主动装备
 * （攀爬手套是被动效果，不需要触发；抓钩手弩是主动效果，需要按键）。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GrappleClientEvents {

    private GrappleClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        // 手套功能键：左手套 / 右手套
        if (minecraft.screen == null) {
            while (CombatKeyMappings.LEFT_GLOVE.consumeClick()) {
                CombatNetwork.CHANNEL.sendToServer(new CombatNetwork.GloveTriggerPacket(true));
            }
            while (CombatKeyMappings.RIGHT_GLOVE.consumeClick()) {
                CombatNetwork.CHANNEL.sendToServer(new CombatNetwork.GloveTriggerPacket(false));
            }
        }

        // 本地预测：只有拉动阶段才朝锚点拉动（飞行阶段玩家正常活动）
        if (ClientGrappleData.isPulling()) {
            GrappleManager.applyPull(player, ClientGrappleData.getPosition(), ClientGrappleData.getPullSpeed());
        }
    }

    /** 退出服务器/切换世界时清掉本地的抓钩状态，避免残留绳索。 */
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGrappleData.clear();
    }
}
