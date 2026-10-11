package com.example.titans_creation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 战斗工具类型定义。
 *
 * 每种工具类型（剑、斧、镐、锄、自定义武器）拥有独立的战斗参数，包括： - 伤害倍率、攻击距离 - 挥舞时长、冷却时长 - 攻击弧形角度（基础半角 +
 * 最大额外角度） - 纵向挥舞特殊效果（击飞力度、击晕时长）
 *
 * 使用 {@link Builder} 创建实例，通过
 * {@link CombatToolTypeRegistry#register(CombatToolType)} 注册。 自定义武器/工具只需注册新的
 * CombatToolType 并提供 {@link #matches(ItemStack)} 匹配器即可。
 */
public final class CombatToolType {

    private final ResourceLocation id;
    private final Predicate<ItemStack> matcher;
    private final float damageMultiplier;
    private final double attackRange;
    private final int swingDurationTicks;
    private final int cooldownTicks;
    private final float baseHalfAngleDeg;
    private final float maxExtraDeg;
    private final float maxLaunchPower;
    private final float minLaunchPower;
    private final int maxStunTicks;
    private final int minStunTicks;
    private final boolean canFellTrees;

    private CombatToolType(Builder b) {
        this.id = b.id;
        this.matcher = b.matcher;
        this.damageMultiplier = b.damageMultiplier;
        this.attackRange = b.attackRange;
        this.swingDurationTicks = b.swingDurationTicks;
        this.cooldownTicks = b.cooldownTicks;
        this.baseHalfAngleDeg = b.baseHalfAngleDeg;
        this.maxExtraDeg = b.maxExtraDeg;
        this.maxLaunchPower = b.maxLaunchPower;
        this.minLaunchPower = b.minLaunchPower;
        this.maxStunTicks = b.maxStunTicks;
        this.minStunTicks = b.minStunTicks;
        this.canFellTrees = b.canFellTrees;
    }

    public ResourceLocation getId() {
        return id;
    }

    /**
     * 判断物品栈是否属于此工具类型。
     */
    public boolean matches(ItemStack stack) {
        return matcher.test(stack);
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public double getAttackRange() {
        return attackRange;
    }

    public int getSwingDurationTicks() {
        return swingDurationTicks;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /**
     * 挥舞 + 冷却总时长。
     */
    public int getBusyTicks() {
        return swingDurationTicks + cooldownTicks;
    }

    public float getBaseHalfAngleDeg() {
        return baseHalfAngleDeg;
    }

    public float getMaxExtraDeg() {
        return maxExtraDeg;
    }

    public float getMaxLaunchPower() {
        return maxLaunchPower;
    }

    public float getMinLaunchPower() {
        return minLaunchPower;
    }

    public int getMaxStunTicks() {
        return maxStunTicks;
    }

    public int getMinStunTicks() {
        return minStunTicks;
    }

    /**
     * 是否有击飞效果（maxLaunchPower > 0）。
     */
    public boolean canLaunch() {
        return maxLaunchPower > 0.0F;
    }

    /**
     * 是否有击晕效果（maxStunTicks > 0）。
     */
    public boolean canStun() {
        return maxStunTicks > 0;
    }

    /**
     * 是否支持伐树（斧类工具专用）。
     */
    public boolean canFellTrees() {
        return canFellTrees;
    }

    /**
     * 创建构建器。
     *
     * @param name 工具类型名称（将转为 modid:titans_creation 命名空间的 ResourceLocation）
     */
    public static Builder builder(String name) {
        return new Builder(ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, name));
    }

    public static final class Builder {

        private final ResourceLocation id;
        private Predicate<ItemStack> matcher = stack -> false;
        private float damageMultiplier = 1.0F;
        private double attackRange = 4.0;
        private int swingDurationTicks = 8;
        private int cooldownTicks = 8;
        private float baseHalfAngleDeg = 60.0F;
        private float maxExtraDeg = 120.0F;
        private float maxLaunchPower = 0.9F;
        private float minLaunchPower = 0.42F;
        private int maxStunTicks = 10;
        private int minStunTicks = 2;
        private boolean canFellTrees = false;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /**
         * 设置物品匹配器，判断哪些物品属于此工具类型。
         */
        public Builder matcher(Predicate<ItemStack> matcher) {
            this.matcher = matcher;
            return this;
        }

        public Builder damageMultiplier(float v) {
            this.damageMultiplier = v;
            return this;
        }

        public Builder attackRange(double v) {
            this.attackRange = v;
            return this;
        }

        public Builder swingDurationTicks(int v) {
            this.swingDurationTicks = v;
            return this;
        }

        public Builder cooldownTicks(int v) {
            this.cooldownTicks = v;
            return this;
        }

        public Builder baseHalfAngleDeg(float v) {
            this.baseHalfAngleDeg = v;
            return this;
        }

        public Builder maxExtraDeg(float v) {
            this.maxExtraDeg = v;
            return this;
        }

        /**
         * 击飞力度（正上方时）。设为 0 禁用击飞。
         */
        public Builder maxLaunchPower(float v) {
            this.maxLaunchPower = v;
            return this;
        }

        /**
         * 击飞力度（偏离正上方 45° 时）。
         */
        public Builder minLaunchPower(float v) {
            this.minLaunchPower = v;
            return this;
        }

        /**
         * 击晕 tick 数（正下方时）。设为 0 禁用击晕。
         */
        public Builder maxStunTicks(int v) {
            this.maxStunTicks = v;
            return this;
        }

        /** 击晕 tick 数（偏离正下方 45° 时）。 */
        public Builder minStunTicks(int v) {
            this.minStunTicks = v;
            return this;
        }

        /** 是否支持伐树（斧类工具专用）。 */
        public Builder canFellTrees(boolean v) {
            this.canFellTrees = v;
            return this;
        }

        public CombatToolType build() {
            return new CombatToolType(this);
        }
    }
}
