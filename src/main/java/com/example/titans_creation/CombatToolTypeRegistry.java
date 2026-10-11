package com.example.titans_creation;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗工具类型注册表。
 *
 * 预注册了原版四种工具类型（剑、斧、镐、锄），每种类型使用默认战斗参数。
 * 自定义武器/工具可通过 {@link #register(CombatToolType)} 在模组初始化时注册。
 *
 * 使用 {@link #getForStack(ItemStack)} 查询物品对应的工具类型。
 * 查询按注册顺序遍历，先匹配的优先，因此自定义类型的匹配器应比通用匹配器更精确。
 */
public final class CombatToolTypeRegistry {

    private CombatToolTypeRegistry() {}

    private static final Map<net.minecraft.resources.ResourceLocation, CombatToolType> TYPES = new LinkedHashMap<>();

    // ======================== 预注册的原版工具类型 ========================

    /** 剑：标准战斗参数。 */
    public static final CombatToolType SWORD = register(CombatToolType.builder("sword")
            .matcher(stack -> stack.getItem() instanceof SwordItem)
            .damageMultiplier(1.0F)
            .attackRange(4.0)
            .swingDurationTicks(8)
            .cooldownTicks(8)
            .baseHalfAngleDeg(60.0F)
            .maxExtraDeg(120.0F)
            .maxLaunchPower(0.9F)
            .minLaunchPower(0.42F)
            .maxStunTicks(10)
            .minStunTicks(2)
            .build());

    /** 斧：较高伤害，较慢攻速，较大范围，支持伐树。 */
    public static final CombatToolType AXE = register(CombatToolType.builder("axe")
            .matcher(stack -> stack.getItem() instanceof AxeItem)
            .damageMultiplier(1.25F)
            .attackRange(4.5)
            .swingDurationTicks(10)
            .cooldownTicks(12)
            .baseHalfAngleDeg(55.0F)
            .maxExtraDeg(100.0F)
            .maxLaunchPower(1.0F)
            .minLaunchPower(0.5F)
            .maxStunTicks(12)
            .minStunTicks(3)
            .canFellTrees(true)
            .build());

    /** 镐：较快攻速，较小范围。 */
    public static final CombatToolType PICKAXE = register(CombatToolType.builder("pickaxe")
            .matcher(stack -> stack.getItem() instanceof PickaxeItem)
            .damageMultiplier(0.85F)
            .attackRange(3.5)
            .swingDurationTicks(6)
            .cooldownTicks(6)
            .baseHalfAngleDeg(65.0F)
            .maxExtraDeg(140.0F)
            .maxLaunchPower(0.7F)
            .minLaunchPower(0.35F)
            .maxStunTicks(8)
            .minStunTicks(2)
            .build());

    /** 锄：最快攻速，最低伤害。 */
    public static final CombatToolType HOE = register(CombatToolType.builder("hoe")
            .matcher(stack -> stack.getItem() instanceof HoeItem)
            .damageMultiplier(0.7F)
            .attackRange(3.5)
            .swingDurationTicks(5)
            .cooldownTicks(5)
            .baseHalfAngleDeg(70.0F)
            .maxExtraDeg(150.0F)
            .maxLaunchPower(0.5F)
            .minLaunchPower(0.3F)
            .maxStunTicks(6)
            .minStunTicks(1)
            .build());

    // ======================== API ========================

    /**
     * 注册一个新的工具类型。
     * 应在模组初始化阶段调用（如 FMLCommonSetupEvent）。
     *
     * @return 注册的类型（便于赋值给 static final 字段）
     */
    public static CombatToolType register(CombatToolType type) {
        TYPES.put(type.getId(), type);
        return type;
    }

    /**
     * 查询物品对应的工具类型。
     *
     * @return 匹配的类型；无匹配返回 null
     */
    public static CombatToolType getForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (CombatToolType type : TYPES.values()) {
            if (type.matches(stack)) {
                return type;
            }
        }
        return null;
    }

    /** 判断物品是否为战斗工具。 */
    public static boolean isCombatTool(ItemStack stack) {
        return getForStack(stack) != null;
    }

    /** 获取所有已注册的类型（用于调试/展示）。 */
    public static List<CombatToolType> getAllTypes() {
        return new ArrayList<>(TYPES.values());
    }
}
