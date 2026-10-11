package com.example.titans_creation.item.accessory;

import net.minecraft.world.item.ItemStack;

/**
 * 本模组的饰品槽位定义（共 5 格）。
 *
 * <p><b>未安装 Curios 时</b>：由本模组自己的装备栏 UI 使用这 5 个槽位。
 * <b>安装了 Curios 时</b>：不显示本模组的 UI，物品改为装备到 Curios 的对应槽位
 * （见 {@link AccessoryType} 的说明），这 5 个槽位不会被使用。
 *
 * <p>枚举顺序同时决定了 UI 中的排列顺序，请勿随意调整（网络包用序号传输）。
 */
public enum AccessorySlot {

    /** 背部（1 格）。 */
    BACK("back", AccessoryType.BACK),
    /** 左手手套。 */
    GLOVES_LEFT("gloves_left", AccessoryType.GLOVES),
    /** 右手手套。 */
    GLOVES_RIGHT("gloves_right", AccessoryType.GLOVES),
    /** 左鞋。 */
    SHOES_LEFT("shoes_left", AccessoryType.SHOES),
    /** 右鞋。 */
    SHOES_RIGHT("shoes_right", AccessoryType.SHOES);

    /** 全部槽位（顺序即 UI 顺序）。 */
    public static final AccessorySlot[] VALUES = values();
    /** 槽位数量。 */
    public static final int COUNT = VALUES.length;

    private final String id;
    private final AccessoryType type;

    AccessorySlot(String id, AccessoryType type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public AccessoryType getType() {
        return type;
    }

    /** 语言键，例如 {@code titans_creation.accessory.slot.gloves_left}。 */
    public String getTranslationKey() {
        return "titans_creation.accessory.slot." + id;
    }

    /** 按序号取槽位（网络包/UI 使用）；越界返回 null。 */
    public static AccessorySlot byIndex(int index) {
        return index >= 0 && index < COUNT ? VALUES[index] : null;
    }

    /** 该槽位是否可以放入这个物品。 */
    public boolean accepts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof AccessoryItem accessory) {
            return accessory.canEquipIn(this);
        }
        // 兼容：任何属于本槽位分类物品标签的物品也可以装备（方便后续用数据包扩展）
        return AccessoryTags.accepts(this, stack);
    }
}
