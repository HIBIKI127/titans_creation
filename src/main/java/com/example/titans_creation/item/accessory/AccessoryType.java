package com.example.titans_creation.item.accessory;

import net.minecraft.network.chat.Component;

/**
 * 饰品槽位大类。
 *
 * <p>与 Curios 的对应关系（{@code CuriosCompat}）：
 * <ul>
 *   <li>{@link #GLOVES} 手套 → Curios 自带槽位 {@code curios:hands}</li>
 *   <li>{@link #BACK} 背部 → Curios 自带槽位 {@code curios:back}</li>
 *   <li>{@link #SHOES} 鞋子 → Curios 没有自带脚部槽位，由本模组通过数据包注册 {@code titans_creation:feet}</li>
 * </ul>
 */
public enum AccessoryType {

    /** 手套（左右两格）。 */
    GLOVES("gloves"),
    /** 鞋子（左右两格）。 */
    SHOES("shoes"),
    /** 背部（一格）。 */
    BACK("back");

    private final String id;

    AccessoryType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** 语言键，例如 {@code titans_creation.accessory.type.gloves}。 */
    public String getTranslationKey() {
        return "titans_creation.accessory.type." + id;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
