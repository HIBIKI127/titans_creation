package com.example.titans_creation.item.accessory;

import com.example.titans_creation.client.accessory.ClientAccessoryData;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 读取某个装备槽位当前物品的统一入口。
 *
 * <p>优先读 Curios（装了 Curios 时物品都在它那边），否则读本模组的 5 格装备栏；
 * 客户端读同步下来的数据。
 */
public final class AccessoryLookup {

    private AccessoryLookup() {
    }

    public static ItemStack get(Player player, AccessorySlot slot) {
        if (player == null || slot == null) {
            return ItemStack.EMPTY;
        }
        if (CuriosCompat.isLoaded()) {
            ItemStack fromCurios = CuriosEquipHelper.getEquipped(player, slot);
            if (!fromCurios.isEmpty()) {
                return fromCurios;
            }
        }
        if (player.level().isClientSide()) {
            return ClientAccessoryData.get(slot);
        }
        return AccessoryInventory.get(player).get(slot);
    }
}
