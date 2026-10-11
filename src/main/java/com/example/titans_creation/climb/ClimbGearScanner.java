package com.example.titans_creation.climb;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家当前生效的攀爬能力（每 tick 由装备栏内容扫描得出）。
 */
public final class ClimbGearScanner {

    private ClimbGearScanner() {
    }

    /**
     * 扫描玩家已装备的攀爬装备。
     *
     * <p>客户端读同步下来的装备栏数据，服务端读玩家持久化数据；
     * 装了 Curios 时另外读取 Curios 的槽位（客户端与服务端都可用）。
     */
    public static EnumSet<ClimbAbility> scan(net.minecraft.world.entity.player.Player player) {
        EnumSet<ClimbAbility> abilities = EnumSet.noneOf(ClimbAbility.class);
        if (player == null) {
            return abilities;
        }
        if (player.level().isClientSide()) {
            for (com.example.titans_creation.item.accessory.AccessorySlot slot
                    : com.example.titans_creation.item.accessory.AccessorySlot.VALUES) {
                collect(abilities, com.example.titans_creation.client.accessory.ClientAccessoryData.get(slot));
            }
        } else {
            com.example.titans_creation.item.accessory.AccessoryInventory inventory =
                    com.example.titans_creation.item.accessory.AccessoryInventory.get(player);
            for (com.example.titans_creation.item.accessory.AccessorySlot slot
                    : com.example.titans_creation.item.accessory.AccessorySlot.VALUES) {
                collect(abilities, inventory.get(slot));
            }
        }
        if (com.example.titans_creation.item.accessory.CuriosCompat.isLoaded()) {
            CuriosGearScanner.collect(player, abilities);
        }
        return abilities;
    }

    private static void collect(EnumSet<ClimbAbility> abilities, net.minecraft.world.item.ItemStack stack) {
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ClimbGear gear) {
            abilities.add(gear.climbAbility());
        }
    }

    // ==================== 服务端输入缓存 ====================

    /** 玩家 UUID -> 最近一次收到的移动/跳跃输入 {xxa, zza, jumping}。 */
    private static final Map<UUID, float[]> INPUTS = new HashMap<>();

    /** 记录玩家输入（客户端本地每 tick 写入，服务端由输入包写入）。 */
    public static void setInput(UUID playerId, float xxa, float zza, boolean jumping) {
        if (playerId != null) {
            INPUTS.put(playerId, new float[]{xxa, zza, jumping ? 1.0F : 0.0F});
        }
    }

    /** 取玩家输入；没有记录时返回全 0。 */
    public static float[] getInput(UUID playerId) {
        float[] input = playerId == null ? null : INPUTS.get(playerId);
        return input == null ? ZERO_INPUT : input;
    }

    public static void clearInput(UUID playerId) {
        if (playerId != null) {
            INPUTS.remove(playerId);
        }
    }

    public static void clearAllInputs() {
        INPUTS.clear();
    }

    private static final float[] ZERO_INPUT = new float[]{0.0F, 0.0F, 0.0F};
}
