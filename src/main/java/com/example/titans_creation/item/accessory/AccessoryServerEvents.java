package com.example.titans_creation.item.accessory;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 饰品栏的服务端事件：登录/重生/切换维度时同步内容，退出时清理缓存。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccessoryServerEvents {

    private AccessoryServerEvents() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AccessoryManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AccessoryManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AccessoryManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // 死亡重生：把饰品数据带到新实体上
        AccessoryInventory.copyData(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AccessoryInventory.invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AccessoryInventory.invalidateAll();
    }
}
