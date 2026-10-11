package com.example.titans_creation.item.accessory;

import java.util.List;

import com.example.titans_creation.CombatNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * 饰品栏的服务端逻辑。
 *
 * <p>三类操作：
 * <ol>
 *   <li><b>面板点击</b>：光标（容器菜单 carried 物品）与饰品槽位之间搬运，手感与背包一致；</li>
 *   <li><b>右键快速装备</b>：把手中的饰品装进第一个空的对应栏位（栏位满了则失败）；</li>
 *   <li><b>Shift+左键快速穿戴</b>：把背包槽位里的饰品装进第一个空的对应栏位。</li>
 * </ol>
 *
 * <p>安装了 Curios 时，装备目标改为 Curios 的槽位（见 {@link CuriosEquipHelper}）。
 */
public final class AccessoryManager {

    private AccessoryManager() {
    }

    // ==================== 面板点击 ====================

    /**
     * 处理一次装备栏点击（客户端发来的槽位序号）。
     *
     * <p>不限制容器菜单类型：装备栏面板会注入到生存模式背包与创造模式背包
     * （创造模式使用的是另一个菜单类），此前只接受 {@code InventoryMenu} 会导致创造模式无法装备。
     */
    public static void handleClick(ServerPlayer player, int slotIndex) {
        AccessorySlot slot = AccessorySlot.byIndex(slotIndex);
        if (slot == null) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }
        AccessoryInventory inventory = AccessoryInventory.get(player);
        ItemStack current = inventory.get(slot);
        ItemStack carried = menu.getCarried();

        if (carried.isEmpty()) {
            if (current.isEmpty()) {
                return;
            }
            menu.setCarried(current.copy());
            inventory.set(slot, ItemStack.EMPTY, player);
        } else if (!slot.accepts(carried)) {
            // 该槽位不能放这个物品：忽略
            return;
        } else if (current.isEmpty()) {
            inventory.set(slot, carried.copy(), player);
            menu.setCarried(ItemStack.EMPTY);
        } else if (ItemStack.isSameItemSameTags(current, carried)) {
            int max = Math.min(current.getMaxStackSize(), 64);
            int move = Math.min(max - current.getCount(), carried.getCount());
            if (move <= 0) {
                return;
            }
            ItemStack merged = current.copy();
            merged.grow(move);
            inventory.set(slot, merged, player);
            carried.shrink(move);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        } else {
            inventory.set(slot, carried.copy(), player);
            menu.setCarried(current.copy());
        }

        // 让服务端把「变化的槽位 + carried 物品」同步给客户端
        menu.broadcastChanges();
        sync(player);
    }

    // ==================== 快速装备 ====================

    /**
     * 把一件饰品装进第一个空的、可接受它的饰品槽位。
     *
     * <p>对应栏位已满（没有空位）时返回 false —— 此时不装备。
     * 调用方负责把物品从来源处移除。
     */
    public static boolean tryEquip(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (CuriosCompat.isLoaded()) {
            return CuriosEquipHelper.tryEquip(player, stack);
        }
        AccessoryInventory inventory = AccessoryInventory.get(player);
        for (AccessorySlot slot : AccessorySlot.VALUES) {
            if (inventory.get(slot).isEmpty() && slot.accepts(stack)) {
                inventory.set(slot, stack.copy(), player);
                return true;
            }
        }
        return false;
    }

    /** 右键快速装备：把指定手上的饰品装备到空槽位。 */
    public static boolean equipFromHand(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !tryEquip(player, held)) {
            return false;
        }
        player.setItemInHand(hand, ItemStack.EMPTY);
        broadcastInventory(player);
        sync(player);
        return true;
    }

    /** Shift+左键快速穿戴：把玩家背包中某个槽位的饰品装备到空槽位。 */
    public static boolean equipFromMenuSlot(ServerPlayer player, int menuSlotIndex) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.getSlot(menuSlotIndex);
        // 只处理玩家自己背包里的物品（避免动到创造模式物品列表等虚拟槽位）
        if (!(slot.container instanceof Inventory) || !slot.hasItem()) {
            return false;
        }
        if (!tryEquip(player, slot.getItem())) {
            return false;
        }
        slot.set(ItemStack.EMPTY);
        menu.broadcastChanges();
        sync(player);
        return true;
    }

    /** 把玩家背包（含快捷栏）的改动同步给客户端。 */
    private static void broadcastInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    /** 把饰品栏内容同步给客户端。 */
    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<ItemStack> stacks = List.of(AccessoryInventory.get(player).copyStacks());
        CombatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CombatNetwork.AccessorySyncPacket(stacks));
    }
}
