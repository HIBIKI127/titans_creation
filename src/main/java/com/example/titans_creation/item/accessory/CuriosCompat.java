package com.example.titans_creation.item.accessory;

import net.minecraftforge.fml.ModList;

/**
 * Curios 兼容探测。
 *
 * <p>本类<b>不引用任何 Curios 的类</b>，只查询模组是否加载，因此未安装 Curios 时也能安全运行。
 * 物品与 Curios 槽位的对应关系完全由数据包（物品标签）完成，不需要 Java 代码依赖。
 */
public final class CuriosCompat {

    /** Curios 的模组 id。 */
    public static final String MOD_ID = "curios";

    private CuriosCompat() {
    }

    /** 是否安装了 Curios。 */
    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    /**
     * 是否使用本模组自己的装备栏 UI。
     *
     * <p>安装了 Curios 时返回 false —— 此时物品装备在 Curios 的槽位里，
     * 不额外显示我们自己的装备栏（需求：与饰品模组兼容时不重复显示 UI）。
     */
    public static boolean useOwnAccessoryUi() {
        return !isLoaded();
    }
}
