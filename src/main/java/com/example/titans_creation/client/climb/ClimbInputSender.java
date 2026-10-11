package com.example.titans_creation.client.climb;

import com.example.titans_creation.CombatNetwork;
import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.climb.ClimbGearScanner;
import com.example.titans_creation.climb.ClimbManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端攀爬输入：
 * <ol>
 *   <li>把本机玩家的按键状态写入本地输入缓存，供 {@link ClimbManager} 做本地预测
 *       （客户端与服务端跑同一套判定，保证手感与原版爬梯子一致）；</li>
 *   <li>把按键状态同步给服务端（变化时立即发；按住期间每 4 tick 补发一次，
 *       因为服务端的 {@code xxa/zza} 每 tick 会衰减，单纯依赖原版输入包会导致长按失效）。</li>
 * </ol>
 *
 * <p>攀爬已改为<b>由装备自动启用</b>，因此这里不再有任何"开关攀爬"的热键。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClimbInputSender {

    /** 按住时补发输入的间隔（tick）。 */
    private static final int HEARTBEAT_TICKS = 4;

    private static float lastXxa;
    private static float lastZza;
    private static boolean lastJumping;
    private static int heartbeat;

    private ClimbInputSender() {
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

        float xxa = player.input.leftImpulse;
        float zza = player.input.forwardImpulse;
        boolean jumping = player.input.jumping;

        // 本地缓存：客户端预测使用
        ClimbGearScanner.setInput(player.getUUID(), xxa, zza, jumping);

        boolean changed = Math.abs(xxa - lastXxa) > 1.0E-4F
                || Math.abs(zza - lastZza) > 1.0E-4F
                || jumping != lastJumping;
        boolean active = Math.abs(xxa) > 1.0E-4F || Math.abs(zza) > 1.0E-4F || jumping;
        if (changed || (active && ++heartbeat >= HEARTBEAT_TICKS)) {
            heartbeat = 0;
            lastXxa = xxa;
            lastZza = zza;
            lastJumping = jumping;
            CombatNetwork.CHANNEL.sendToServer(new CombatNetwork.ClimbInputPacket(xxa, zza, jumping));
        }

        // 本地预测
        ClimbManager.tickClient(player);
    }
}
