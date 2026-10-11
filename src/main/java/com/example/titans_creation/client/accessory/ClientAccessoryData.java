package com.example.titans_creation.client.accessory;

import java.util.List;

import com.example.titans_creation.item.accessory.AccessorySlot;

import net.minecraft.world.item.ItemStack;

/**
 * 客户端饰品栏数据缓存：由服务端的 {@code AccessorySyncPacket} 填充。
 */
public final class ClientAccessoryData {

    private static List<ItemStack> stacks = List.of();
    private static boolean panelOpen = true;

    private ClientAccessoryData() {
    }

    public static void setStacks(List<ItemStack> value) {
        stacks = value == null ? List.of() : List.copyOf(value);
    }

    /** 取某个槽位的物品（未同步时返回空）。 */
    public static ItemStack get(AccessorySlot slot) {
        int index = slot.ordinal();
        return index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

    /** 装备栏是否展开。 */
    public static boolean isPanelOpen() {
        return panelOpen;
    }

    public static void setPanelOpen(boolean open) {
        panelOpen = open;
    }
}
