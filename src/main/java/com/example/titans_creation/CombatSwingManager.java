package com.example.titans_creation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端战斗挥舞管理器。
 *
 * 一次挥舞在 toolType.getSwingDurationTicks() 个 tick 内完成，伤害沿"挥舞起点 -> 挥舞终点"
 * 方向逐片施加：每个 tick 处理一个角度切片 [phiStart, phiEnd]，对该切片内尚未命中过的实体
 * 造成一次完整伤害。
 *
 * 切片计算使用游标推进法（phiCursor），确保无论 extraDeg 如何增长，切片之间不会出现缝隙。
 *
 * 离面检查：目标方向在挥舞平面法线方向上的分量过大时（超过 MAX_OFF_PLANE_SIN），
 * 视为偏离挥舞平面，不会被命中。修复纵向挥舞时攻击宽度过大的问题。
 *
 * 纵向挥舞特殊效果（由 toolType 的参数决定）：
 * - 指针位于上方 90° 区间（从下向上挥舞）：击飞目标，越接近正上方击飞越高。
 * - 指针位于下方 90° 区间（从上向下挥舞）：击晕目标（强制停留在原地），越接近正下方击晕时间越长。
 *
 * 所有战斗参数（伤害倍率、攻击距离、挥舞时长、冷却、弧度、击飞/击晕）均由 {@link CombatToolType} 提供，
 * 支持不同工具类型独立配置。
 */
public final class CombatSwingManager {

    private CombatSwingManager() {}

    /** 切片向外冗余角度（度），避免切片之间出现缝隙。 */
    public static final float SLICE_MARGIN_DEG = 2.0F;

    /** 离面检查阈值：目标方向在挥舞平面法线上的分量超过此值时不命中。sin(45°)≈0.707。 */
    public static final double MAX_OFF_PLANE_SIN = 0.707;

    /** 纵向挥舞特殊效果触发角度（度）：指针在此角度范围内触发特殊效果。 */
    public static final float VERTICAL_ZONE_DEG = 45.0F;

    private static final Map<UUID, ServerSwingState> activeSwings = new HashMap<>();
    /** 被击晕的实体：UUID -> 击晕结束时的 gameTime。 */
    private static final Map<UUID, Long> stunnedEntities = new HashMap<>();

    /**
     * 开始一次挥舞。若该玩家已有进行中的挥舞则忽略（由客户端冷却保证不会频繁触发）。
     *
     * @param swingAngleScreen 挥动方向的屏幕角度（度，顺时针，正上方为 0）
     * @param toolType 工具类型（决定伤害倍率、攻击距离、挥舞时长、弧度、击飞/击晕等）
     */
    public static void startSwing(ServerPlayer player, Vec3 forward0, Vec3 swingDir,
                                  float swingAngleScreen, CombatToolType toolType)
    {
        if (activeSwings.containsKey(player.getUUID()))
        {
            return;
        }
        float strength = player.getAttackStrengthScale(0.0F);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * strength
                * toolType.getDamageMultiplier();
        if (damage <= 0.0F)
        {
            damage = 1.0F;
        }
        DamageSource source = player.damageSources().playerAttack(player);
        ServerSwingState state = new ServerSwingState(forward0, swingDir, damage, source,
                swingAngleScreen, toolType);
        activeSwings.put(player.getUUID(), state);
        player.resetAttackStrengthTicker();
    }

    /**
     * 服务端每 tick 末调用：推进所有进行中的挥舞。
     */
    public static void tickAll(MinecraftServer server)
    {
        if (activeSwings.isEmpty())
        {
            return;
        }
        Iterator<Map.Entry<UUID, ServerSwingState>> it = activeSwings.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, ServerSwingState> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerSwingState state = entry.getValue();
            if (player == null || !player.isAlive() || state.tick >= state.toolType.getSwingDurationTicks())
            {
                it.remove();
                continue;
            }
            tickSwing(player, state);
            if (state.tick >= state.toolType.getSwingDurationTicks())
            {
                it.remove();
            }
        }
    }

    /**
     * 每 tick 处理被击晕的实体：强制停留在原地，直到击晕时间结束。
     */
    public static void tickStuns(MinecraftServer server)
    {
        if (stunnedEntities.isEmpty())
        {
            return;
        }
        long currentGameTime = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = stunnedEntities.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, Long> entry = it.next();
            if (currentGameTime >= entry.getValue())
            {
                it.remove();
                continue;
            }
            for (ServerLevel level : server.getAllLevels())
            {
                Entity entity = level.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living && living.isAlive())
                {
                    living.setDeltaMovement(Vec3.ZERO);
                    living.hurtMarked = true;
                    break;
                }
                else if (entity != null && !entity.isAlive())
                {
                    it.remove();
                    break;
                }
            }
        }
    }

    private static void tickSwing(ServerPlayer player, ServerSwingState state)
    {
        Level level = player.level();
        CombatToolType toolType = state.toolType;

        // 累积视角拖动带来的额外范围（仅正向增长，不收缩）
        Vec3 currentLook = player.getLookAngle().normalize();
        double a = currentLook.dot(state.forward0);
        double b = currentLook.dot(state.swingDir);
        float viewPhiDeg = (float) Math.toDegrees(Math.atan2(b, a));
        float candidate = Math.max(0.0F, viewPhiDeg);
        if (candidate > state.extraDeg)
        {
            state.extraDeg = Math.min(toolType.getMaxExtraDeg(), candidate);
        }

        // 使用游标推进法计算当前 tick 的切片
        float endPhi = toolType.getBaseHalfAngleDeg() + state.extraDeg;
        float remainingArc = endPhi - state.phiCursor;
        int remainingTicks = toolType.getSwingDurationTicks() - state.tick;
        float sliceWidth = remainingArc / remainingTicks;
        float phiStart = state.phiCursor - SLICE_MARGIN_DEG;
        float phiEnd = state.phiCursor + sliceWidth + SLICE_MARGIN_DEG;
        state.phiCursor += sliceWidth;

        // 挥舞平面法线（用于离面检查）
        Vec3 planeNormal = state.forward0.cross(state.swingDir);
        if (planeNormal.lengthSqr() < 1.0E-12)
        {
            planeNormal = new Vec3(0.0, 0.0, 1.0);
        }
        else
        {
            planeNormal = planeNormal.normalize();
        }

        Vec3 eyePos = player.getEyePosition();
        double attackRange = toolType.getAttackRange();
        AABB searchBox = player.getBoundingBox().inflate(attackRange);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.isPickable());

        for (LivingEntity target : targets)
        {
            int id = target.getId();
            if (state.hitEntities.contains(id))
            {
                continue;
            }
            Vec3 targetCenter = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            Vec3 toTarget = targetCenter.subtract(eyePos);
            double dist = toTarget.length();
            if (dist > attackRange || dist < 1.0E-4)
            {
                continue;
            }
            Vec3 dir = toTarget.scale(1.0 / dist);

            // 离面检查
            double offPlane = Math.abs(dir.dot(planeNormal));
            if (offPlane > MAX_OFF_PLANE_SIN)
            {
                continue;
            }

            double ta = dir.dot(state.forward0);
            double tb = dir.dot(state.swingDir);
            float phi = (float) Math.toDegrees(Math.atan2(tb, ta));
            if (phi >= phiStart && phi <= phiEnd)
            {
                target.hurt(state.source, state.damage);
                state.hitEntities.add(id);

                // 纵向挥舞特殊效果
                applyVerticalSwingEffect(player, target, state.swingAngleScreen, level, toolType);

                // 命中粒子
                if (level instanceof ServerLevel serverLevel)
                {
                    serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                            targetCenter.x, targetCenter.y, targetCenter.z, 5,
                            0.2, 0.2, 0.2, 0.0);
                }
            }
        }

        // 注：挥舞弧线的视觉效果由客户端 CombatTrailRenderer 绘制高亮拖影，
        // 服务端不再生成原版扫击粒子（ParticleTypes.CRIT）。

        state.tick++;
    }

    /**
     * 纵向挥舞特殊效果（参数由 toolType 决定）：
     * - 指针在上方 90° 区间：击飞（toolType.canLaunch() 时生效）
     * - 指针在下方 90° 区间：击晕（toolType.canStun() 时生效）
     */
    private static void applyVerticalSwingEffect(ServerPlayer player, LivingEntity target,
                                                  float swingAngleScreen, Level level, CombatToolType toolType)
    {
        float angleFromUp = Math.min(swingAngleScreen, 360.0F - swingAngleScreen);
        float angleFromDown = Math.abs(swingAngleScreen - 180.0F);

        if (angleFromUp <= VERTICAL_ZONE_DEG && toolType.canLaunch())
        {
            // 击飞
            float t = angleFromUp / VERTICAL_ZONE_DEG;
            float launchPower = toolType.getMinLaunchPower()
                    + (toolType.getMaxLaunchPower() - toolType.getMinLaunchPower()) * (1.0F - t);
            target.push(0.0, launchPower, 0.0);
            target.hurtMarked = true;

            if (level instanceof ServerLevel serverLevel)
            {
                Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
                serverLevel.sendParticles(ParticleTypes.POOF,
                        pos.x, pos.y, pos.z, 8,
                        0.3, 0.1, 0.3, 0.05);
            }
        }
        else if (angleFromDown <= VERTICAL_ZONE_DEG && toolType.canStun())
        {
            // 击晕
            float t = angleFromDown / VERTICAL_ZONE_DEG;
            int stunTicks = (int) (toolType.getMinStunTicks()
                    + (toolType.getMaxStunTicks() - toolType.getMinStunTicks()) * (1.0F - t));
            long currentGameTime = level.getGameTime();
            long stunEnd = currentGameTime + stunTicks;

            Long existing = stunnedEntities.get(target.getUUID());
            if (existing == null || stunEnd > existing)
            {
                stunnedEntities.put(target.getUUID(), stunEnd);
            }

            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;

            if (level instanceof ServerLevel serverLevel)
            {
                Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.8, 0.0);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        pos.x, pos.y, pos.z, 6,
                        0.3, 0.1, 0.3, 0.1);
            }
        }
    }

    /** 单玩家的服务端挥舞状态。 */
    private static final class ServerSwingState {
        final Vec3 forward0;
        final Vec3 swingDir;
        final float damage;
        final DamageSource source;
        final float swingAngleScreen;
        final CombatToolType toolType;
        int tick = 0;
        float extraDeg = 0.0F;
        float phiCursor;
        final Set<Integer> hitEntities = new HashSet<>();

        ServerSwingState(Vec3 forward0, Vec3 swingDir, float damage, DamageSource source,
                         float swingAngleScreen, CombatToolType toolType)
        {
            this.forward0 = forward0;
            this.swingDir = swingDir;
            this.damage = damage;
            this.source = source;
            this.swingAngleScreen = swingAngleScreen;
            this.toolType = toolType;
            this.phiCursor = -toolType.getBaseHalfAngleDeg();
        }
    }
}
