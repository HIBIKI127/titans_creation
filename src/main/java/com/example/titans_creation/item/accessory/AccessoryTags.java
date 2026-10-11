package com.example.titans_creation.item.accessory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.example.titans_creation.TitansCreationMod;

/**
 * 饰品槽位的物品标签。
 *
 * <p>除了让物品实现 {@link AccessoryItem} 之外，
 * 任何物品只要被加进对应标签（数据包也可追加），就能装备到对应槽位：
 * <ul>
 *   <li>{@code titans_creation:accessory/gloves}</li>
 *   <li>{@code titans_creation:accessory/shoes}</li>
 *   <li>{@code titans_creation:accessory/back}</li>
 * </ul>
 */
public final class AccessoryTags {

    public static final TagKey<Item> GLOVES = tag("accessory/gloves");
    public static final TagKey<Item> SHOES = tag("accessory/shoes");
    public static final TagKey<Item> BACK = tag("accessory/back");

    private AccessoryTags() {
    }

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, path));
    }

    /** 该槽位对应的物品标签。 */
    public static TagKey<Item> of(AccessoryType type) {
        return switch (type) {
            case GLOVES -> GLOVES;
            case SHOES -> SHOES;
            case BACK -> BACK;
        };
    }

    /** 物品是否属于该槽位的物品标签。 */
    public static boolean accepts(AccessorySlot slot, ItemStack stack) {
        return stack.is(of(slot.getType()));
    }
}
