package com.example.titans_creation.item.accessory;

import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.grapple.GrappleCrossbowItem;
import com.example.titans_creation.grapple.GrappleManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 饰品交互事件：手持饰品右键即可快速装备。
 *
 * <p>对应栏位已满时不装备（物品留在手上），并取消这次右键交互。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccessoryInteractEvents {

    private AccessoryInteractEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 手持抓钩手弩：右键 = 发射 / 收回（主动效果优先于快速装备）
        ItemStack held = player.getItemInHand(event.getHand());
        if (held.getItem() instanceof GrappleCrossbowItem crossbow) {
            GrappleManager.toggle(player, crossbow);
            event.setCanceled(true);
            return;
        }
        if (AccessoryManager.equipFromHand(player, event.getHand())) {
            // 成功装备：消耗这次右键（不再触发物品自身的使用效果）
            event.setCanceled(true);
        }
    }
}
