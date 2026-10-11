package com.example.titans_creation.item.accessory;

/**
 * 由本模组的饰品/装备物品实现，用于声明它能装备到哪一类槽位。
 *
 * <p>手套类物品返回 {@link AccessoryType#GLOVES}，即可放入左右两个手套槽；以此类推。
 */
public interface AccessoryItem {

    /** 该饰品所属的槽位大类。 */
    AccessoryType accessoryType();

    /** 是否能装备进指定槽位（默认：大类匹配即可）。 */
    default boolean canEquipIn(AccessorySlot slot) {
        return slot != null && slot.getType() == this.accessoryType();
    }
}
