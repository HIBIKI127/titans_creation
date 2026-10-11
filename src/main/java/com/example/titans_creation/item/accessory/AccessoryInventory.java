package com.example.titans_creation.item.accessory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家的饰品栏存储（服务端权威）。
 *
 * <p>数据写在玩家的持久化 NBT（{@code getPersistentData()}）中，因此会随存档保存。
 * 内存中按 UUID 缓存一份，玩家退出时清理（见 {@code AccessoryServerEvents}）。
 *
 * <p>Curios 已安装时本存储不会被使用（物品装备在 Curios 的槽位里）。
 */
public final class AccessoryInventory {

    /** 持久化 NBT 中的键名。 */
    public static final String NBT_KEY = "TitansCreationAccessories";

    private static final Map<UUID, AccessoryInventory> CACHE = new HashMap<>();

    private final ItemStack[] stacks = new ItemStack[AccessorySlot.COUNT];

    private AccessoryInventory() {
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    /** 取（必要时从持久化数据加载）玩家的饰品栏。 */
    public static AccessoryInventory get(Player player) {
        return CACHE.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    private static AccessoryInventory load(Player player) {
        AccessoryInventory inventory = new AccessoryInventory();
        CompoundTag data = player.getPersistentData();
        if (data.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = data.getCompound(NBT_KEY);
            for (AccessorySlot slot : AccessorySlot.VALUES) {
                if (tag.contains(slot.getId(), Tag.TAG_COMPOUND)) {
                    inventory.stacks[slot.ordinal()] = ItemStack.of(tag.getCompound(slot.getId()));
                }
            }
        }
        return inventory;
    }

    /** 写回玩家的持久化数据（每次修改后调用）。 */
    public void save(Player player) {
        CompoundTag tag = new CompoundTag();
        for (AccessorySlot slot : AccessorySlot.VALUES) {
            ItemStack stack = this.stacks[slot.ordinal()];
            if (!stack.isEmpty()) {
                tag.put(slot.getId(), stack.save(new CompoundTag()));
            }
        }
        player.getPersistentData().put(NBT_KEY, tag);
    }

    /** 清理某个玩家的缓存（退出登录时调用）。 */
    public static void invalidate(UUID playerId) {
        CACHE.remove(playerId);
    }

    /** 清理全部缓存（服务器停止时调用）。 */
    public static void invalidateAll() {
        CACHE.clear();
    }

    /** 把旧玩家（死亡重生/维度切换前）的数据复制给新玩家。 */
    public static void copyData(Player from, Player to) {
        CompoundTag data = from.getPersistentData();
        if (data.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            to.getPersistentData().put(NBT_KEY, data.getCompound(NBT_KEY).copy());
        }
        CACHE.put(to.getUUID(), load(to));
    }

    public ItemStack get(AccessorySlot slot) {
        ItemStack stack = this.stacks[slot.ordinal()];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /** 设置槽位内容并立即写回持久化数据。 */
    public void set(AccessorySlot slot, ItemStack stack, Player player) {
        this.stacks[slot.ordinal()] = stack == null ? ItemStack.EMPTY : stack;
        save(player);
    }

    /** 全部槽位内容（按 {@link AccessorySlot} 顺序）。 */
    public ItemStack[] copyStacks() {
        ItemStack[] copy = new ItemStack[AccessorySlot.COUNT];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = this.stacks[i] == null ? ItemStack.EMPTY : this.stacks[i].copy();
        }
        return copy;
    }
}
