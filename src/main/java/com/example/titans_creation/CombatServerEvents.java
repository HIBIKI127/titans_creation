package com.example.titans_creation;

import com.example.titans_creation.climb.ClimbManager;
import com.example.titans_creation.giantmonster.GiantMonsterSpawnManager;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbManager;
import com.example.titans_creation.grapple.GrappleManager;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 服务端战斗与生成事件处理。
 *
 * <ul>
 *   <li>每个服务端 tick 末推进所有进行中的方向挥舞，由 {@link CombatSwingManager} 执行
 *       “起点 -&gt; 终点”逐片渐进 AoE 伤害；</li>
 *   <li>同时驱动巨型怪物的生成机制（{@link GiantMonsterSpawnManager}）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID)
public final class CombatServerEvents {

    private CombatServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
        {
            return;
        }
        CombatSwingManager.tickAll(server);
        CombatSwingManager.tickStuns(server);
        GiantMonsterSpawnManager.tick(server);
        GiantMonsterClimbManager.tick(server);
        // 装备驱动的攀爬与滞留（攀爬手套 / 利爪之靴）
        ClimbManager.tick(server);
        // 抓钩的拉动推进
        GrappleManager.tick(server);
    }

    /** 玩家离线时清理服务端缓存的战斗模式状态、攀附状态与攀爬/抓钩状态。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() != null)
        {
            CombatServerState.clear(event.getEntity().getUUID());
            ClimbManager.clear(event.getEntity().getUUID());
            GrappleManager.clear(event.getEntity().getUUID());
        }
    }
}
