package com.example.titans_creation.grapple;

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
 * 抓钩手弩：手套类饰品，可主动发射抓钩把玩家拉向命中点。
 *
 * <ul>
 *   <li>手持时右键 → 发射 / 收回；</li>
 *   <li>装备在装备栏（左/右手套槽）时按对应的手套功能键 → 发射 / 收回；</li>
 *   <li>钩爪长度上限 25 格，<b>需要飞行一段距离才能命中</b>（不是瞬发）；
 *       命中生物 / 怪物 / 方块后把玩家以固定速度拉过去；</li>
 *   <li>命中移动中的生物 / 怪物时，会<b>持续朝目标的当前位置</b>拉动；</li>
 *   <li>拉动途中再次触发即收回，并<b>保留玩家惯性</b>（不会立刻停下）。</li>
 * </ul>
 *
 * <p>基础拉动速度 16 格/秒，基础发射速度 30 格/秒；
 * 每在锻造台升级一次，拉动速度 +20%、发射速度 +25%。
 */
public class GrappleCrossbowItem extends AccessoryItemBase {

    /** 基础拉动速度（格/秒）。 */
    public static final double BASE_PULL_SPEED = 16.0D;
    /** 每次升级的拉动速度倍率（+20%）。 */
    public static final double PULL_UPGRADE_MULTIPLIER = 1.20D;
    /** 基础发射速度（格/秒）。 */
    public static final double BASE_FLIGHT_SPEED = 30.0D;
    /** 每次升级的发射速度倍率（+25%）。 */
    public static final double FLIGHT_UPGRADE_MULTIPLIER = 1.25D;

    private final int tier;

    public GrappleCrossbowItem(int tier, Rarity rarity) {
        super(AccessoryType.GLOVES, rarity);
        this.tier = Math.max(0, tier);
    }

    /** 升级等级：0 = 基础，1 = 铁质，2 = 钻石。 */
    public int getGrappleTier() {
        return this.tier;
    }

    /** 拉动速度（格/tick）。 */
    public double pullSpeedPerTick() {
        return BASE_PULL_SPEED / 20.0D * Math.pow(PULL_UPGRADE_MULTIPLIER, this.tier);
    }

    /** 拉动速度（格/秒，用于提示文字）。 */
    public double pullSpeedPerSecond() {
        return BASE_PULL_SPEED * Math.pow(PULL_UPGRADE_MULTIPLIER, this.tier);
    }

    /** 发射（飞行）速度（格/tick）。 */
    public double flightSpeedPerTick() {
        return BASE_FLIGHT_SPEED / 20.0D * Math.pow(FLIGHT_UPGRADE_MULTIPLIER, this.tier);
    }

    /** 发射速度（格/秒，用于提示文字）。 */
    public double flightSpeedPerSecond() {
        return BASE_FLIGHT_SPEED * Math.pow(FLIGHT_UPGRADE_MULTIPLIER, this.tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("titans_creation.grapple.tooltip.fire").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("titans_creation.grapple.tooltip.flight",
                String.format("%.1f", flightSpeedPerSecond())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("titans_creation.grapple.tooltip.pull",
                String.format("%.1f", pullSpeedPerSecond())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("titans_creation.grapple.tooltip.retract").withStyle(ChatFormatting.GRAY));
    }
}
