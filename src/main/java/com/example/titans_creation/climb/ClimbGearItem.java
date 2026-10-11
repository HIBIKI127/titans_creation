package com.example.titans_creation.climb;

import java.util.List;

import javax.annotation.Nullable;

import com.example.titans_creation.item.accessory.AccessoryItemBase;
import com.example.titans_creation.item.accessory.AccessoryType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 攀爬装备物品：既是饰品（可放进对应装备栏槽位），又提供一项攀爬能力。
 *
 * <p>能力在装备后<b>自动生效</b>，穿脱装备即启用或失效，不需要任何按键开关。
 */
public class ClimbGearItem extends AccessoryItemBase implements ClimbGear {

    private final ClimbAbility ability;

    public ClimbGearItem(AccessoryType type, ClimbAbility ability, Rarity rarity) {
        super(type, rarity);
        this.ability = ability;
    }

    @Override
    public ClimbAbility climbAbility() {
        return this.ability;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(this.ability == ClimbAbility.CLIMB
                ? "titans_creation.climb.tooltip.climbing_gloves"
                : "titans_creation.climb.tooltip.claw_boots").withStyle(ChatFormatting.AQUA));
    }
}
