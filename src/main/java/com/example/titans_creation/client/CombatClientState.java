package com.example.titans_creation.client;

import com.example.titans_creation.CombatToolType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端战斗模式状态。
 *
 * combatMode: 是否启用了第二种（方向）战斗方式。 hasDirection: 是否有挥动方向。 true
 * 时光标显示三角形指针（玩家正在移动视角）。 false 时光标显示空心圆中心点（自动回中，无方向）。 swingAngle:
 * 挥动方向角度（度），从正上方顺时针测量，范围 [0, 360)。 attackHeld: 攻击键（左键）是否被长按。 accumulatedArc:
 * 长按攻击键期间累积的视角移动弧度（度）。超过阈值时触发挥舞。 swingToolType:
 * 当前/最近一次挥舞使用的工具类型。挥舞参数（时长、弧度等）均从此类型获取。
 *
 * 挥舞生命周期（客户端镜像，用于拖影渲染与手感冷却）： swingTick 从 0 计到 busyTicks-1。 0 ..
 * swingDurationTicks-1：挥舞进行中（isActive），每 tick 采样拖影点。 swingDurationTicks ..
 * busyTicks-1：收招冷却（不可开始新挥舞）。 swingTick >= busyTicks：空闲，可开始新挥舞。
 *
 * 挥舞参数（时长、弧度等）由 {@link CombatToolType} 提供，支持不同工具类型独立配置。
 */
public final class CombatClientState {

    private CombatClientState() {
    }

    /**
     * 拖影点的存活时长（tick），用于淡出。
     */
    public static final int TRAIL_LIFETIME_TICKS = 10;
    /**
     * 拖影点最大数量（防止无限增长）。
     */
    public static final int MAX_TRAIL_POINTS = 96;

    /**
     * 默认工具类型参数（无工具时的回退值）。
     */
    private static final int DEFAULT_SWING_TICKS = 8;
    private static final int DEFAULT_COOLDOWN_TICKS = 8;
    private static final float DEFAULT_BASE_HALF_ANGLE = 60.0F;
    private static final float DEFAULT_MAX_EXTRA = 120.0F;

    private static boolean combatMode = false;
    private static boolean hasDirection = false;
    private static float swingAngle = 0.0F;

    // 攻击键长按状态与累积弧度
    private static boolean attackHeld = false;
    private static float accumulatedArcDeg = 0.0F;

    // 当前挥舞的工具类型（挥舞开始时锁定，影响时长/弧度等参数）
    private static CombatToolType swingToolType = null;

    private static int swingTick = 0;
    private static float extraRangeDeg = 0.0F;

    private static Vec3 forward0 = Vec3.ZERO;
    private static Vec3 swingDir = Vec3.ZERO;

    private static final List<TrailPoint> trailPoints = new ArrayList<>();

    public static boolean isCombatMode() {
        return combatMode;
    }

    public static boolean hasDirection() {
        return hasDirection;
    }

    public static float getSwingAngle() {
        return swingAngle;
    }

    public static boolean isAttackHeld() {
        return attackHeld;
    }

    public static float getAccumulatedArcDeg() {
        return accumulatedArcDeg;
    }

    public static CombatToolType getSwingToolType() {
        return swingToolType;
    }

    public static void toggleCombatMode() {
        combatMode = !combatMode;
        if (combatMode) {
            hasDirection = false;
            swingTick = getBusyTicks(); // 空闲
            extraRangeDeg = 0.0F;
            attackHeld = false;
            accumulatedArcDeg = 0.0F;
            swingToolType = null;
            trailPoints.clear();
        }
    }

    public static void setDirection(float angleDegrees) {
        hasDirection = true;
        swingAngle = ((angleDegrees % 360.0F) + 360.0F) % 360.0F;
    }

    /**
     * 自动回中：清除挥动方向，光标回到中心点。
     */
    public static void clearDirection() {
        hasDirection = false;
    }

    /**
     * 设置攻击键长按状态。释放时清零累积弧度。
     */
    public static void setAttackHeld(boolean held) {
        attackHeld = held;
        if (!held) {
            accumulatedArcDeg = 0.0F;
        }
    }

    /**
     * 累积视角移动弧度（仅当攻击键长按时）。
     */
    public static void accumulateArc(float arcDeg) {
        if (attackHeld) {
            accumulatedArcDeg += arcDeg;
        }
    }

    /**
     * 重置累积弧度（挥舞触发后调用）。
     */
    public static void resetAccumulatedArc() {
        accumulatedArcDeg = 0.0F;
    }

    // ============ 按工具类型获取参数 ============
    public static int getSwingDurationTicks() {
        return swingToolType != null ? swingToolType.getSwingDurationTicks() : DEFAULT_SWING_TICKS;
    }

    public static int getCooldownTicks() {
        return swingToolType != null ? swingToolType.getCooldownTicks() : DEFAULT_COOLDOWN_TICKS;
    }

    public static int getBusyTicks() {
        return getSwingDurationTicks() + getCooldownTicks();
    }

    public static float getBaseHalfAngleDeg() {
        return swingToolType != null ? swingToolType.getBaseHalfAngleDeg() : DEFAULT_BASE_HALF_ANGLE;
    }

    public static float getMaxExtraDeg() {
        return swingToolType != null ? swingToolType.getMaxExtraDeg() : DEFAULT_MAX_EXTRA;
    }

    // ============ 挥舞生命周期 ============
    public static boolean isSwingActive() {
        return swingTick < getSwingDurationTicks();
    }

    public static boolean isBusy() {
        return swingTick < getBusyTicks();
    }

    public static float getSwingProgress() {
        if (!isSwingActive()) {
            return 1.0F;
        }
        return (float) swingTick / getSwingDurationTicks();
    }

    public static int getSwingTick() {
        return swingTick;
    }

    public static float getCooldownProgress() {
        if (swingTick >= getBusyTicks()) {
            return 1.0F;
        }
        if (swingTick < getSwingDurationTicks()) {
            return 0.0F;
        }
        return (float) (swingTick - getSwingDurationTicks()) / getCooldownTicks();
    }

    public static float getExtraRangeDeg() {
        return extraRangeDeg;
    }

    public static Vec3 getForward0() {
        return forward0;
    }

    public static Vec3 getSwingDir() {
        return swingDir;
    }

    /**
     * 尝试开始一次挥舞。仅当未忙碌、且已确定方向时成功。 成功后记录挥舞几何与工具类型，并重置计数器。
     *
     * @param toolType 当前手持工具的类型（决定挥舞参数）
     * @return true 表示成功开始挥舞
     */
    public static boolean tryStartSwing(Vec3 fwd0, Vec3 swDir, CombatToolType toolType) {
        if (isBusy() || !hasDirection) {
            return false;
        }
        swingToolType = toolType;
        forward0 = fwd0;
        swingDir = swDir;
        swingTick = 0;
        extraRangeDeg = 0.0F;
        return true;
    }

    public static void advanceSwingTick() {
        if (swingTick < getBusyTicks()) {
            swingTick++;
            if (swingTick >= getBusyTicks()) {
                // 进入空闲：清空挥舞几何
                forward0 = Vec3.ZERO;
                swingDir = Vec3.ZERO;
                extraRangeDeg = 0.0F;
            }
        }
    }

    public static void accumulateExtraRange(float viewPhiDeg) {
        if (!isSwingActive()) {
            return;
        }
        float candidate = Math.max(0.0F, viewPhiDeg);
        if (candidate > extraRangeDeg) {
            extraRangeDeg = Math.min(getMaxExtraDeg(), candidate);
        }
    }

    // ============ 拖影点 ============
    public static List<TrailPoint> getTrailPoints() {
        return trailPoints;
    }

    public static void addTrailPoint(Vec3 pos, long tick, float progress) {
        if (trailPoints.size() >= MAX_TRAIL_POINTS) {
            trailPoints.remove(0);
        }
        trailPoints.add(new TrailPoint(pos, tick, progress));
    }

    public static void cullTrail(long currentTick) {
        trailPoints.removeIf(p -> (currentTick - p.birthTick) > TRAIL_LIFETIME_TICKS);
    }

    public static void clearTrail() {
        trailPoints.clear();
    }

    /**
     * 拖影点：世界坐标 + 出生 tick + 挥舞进度（用于着色与淡出）。
     */
    public static final class TrailPoint {

        public final Vec3 position;
        public final long birthTick;
        public final float progress;

        public TrailPoint(Vec3 position, long birthTick, float progress) {
            this.position = position;
            this.birthTick = birthTick;
            this.progress = progress;
        }
    }
}
