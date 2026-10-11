package com.example.titans_creation.climb;

import java.util.EnumSet;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 读取 Curios 槽位的兼容类。
 *
 * <p><b>本类引用了 Curios 的 API，因此只允许在 {@link com.example.titans_creation.item.accessory.CuriosCompat#isLoaded()}
 * 为 true 时被加载</b>（调用点已在 {@link ClimbGearScanner} 中做了判断），未安装 Curios 时永远不会被加载。
 */
public final class CuriosGearScanner {

    private CuriosGearScanner() {
    }

    /** 把 Curios 槽位里的攀爬装备能力加入结果集。 */
    public static void collect(Player player, EnumSet<ClimbAbility> abilities) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return;
        }
        for (SlotResult result : handler.findCurios(
                stack -> stack.getItem() instanceof ClimbGear)) {
            ItemStack stack = result.stack();
            if (stack.getItem() instanceof ClimbGear gear) {
                abilities.add(gear.climbAbility());
            }
        }
    }
}
