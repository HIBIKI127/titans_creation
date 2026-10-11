package com.example.titans_creation.item.accessory;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 饰品/装备物品的基类：声明它属于哪一类槽位，并在悬浮提示中说明可装备的槽位。
 */
public class AccessoryItemBase extends Item implements AccessoryItem {

    private final AccessoryType type;

    public AccessoryItemBase(AccessoryType type, Rarity rarity) {
        super(new Item.Properties().stacksTo(1).rarity(rarity));
        this.type = type;
    }

    @Override
    public AccessoryType accessoryType() {
        return this.type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("titans_creation.accessory.tooltip.slot",
                this.type.getDisplayName()).withStyle(ChatFormatting.GRAY));
    }
}
