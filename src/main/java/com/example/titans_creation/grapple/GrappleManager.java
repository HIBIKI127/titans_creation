package com.example.titans_creation.grapple;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.titans_creation.CombatNetwork;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * 抓钩的发射、飞行、拉动与收回（服务端权威 + 客户端本地预测）。
 *
 * <p>两个阶段：
 * <ol>
 *   <li><b>飞行（{@link #PHASE_FLYING}）</b>：钩爪从玩家眼睛出发，沿视线以固定速度前进，
 *       每 tick 检测与方块 / 生物 / 怪物的碰撞；飞出 25 格仍未命中则消失。
 *       速度由手弩的等级决定（基础 30 格/秒，每升级 +25%）。</li>
 *   <li><b>拉动（{@link #PHASE_PULLING}）</b>：命中后把玩家以固定速度拉向锚点
 *       （基础 16 格/秒，每升级 +20%）。<b>若命中生物 / 怪物，锚点每 tick 跟随目标当前位置</b>，
 *       因此移动中的目标不会"甩开"玩家。</li>
 * </ol>
 *
 * <p>拉动途中再次触发即"收回"：只清除钩爪状态、<b>不修改玩家速度</b>，因此会保留原有惯性。
 */
public final class GrappleManager {

    /** 钩爪最大长度（格）。 */
    public static final double MAX_LENGTH = 25.0D;
    /** 判定到达的距离（格）。 */
    private static final double ARRIVE_DISTANCE = 1.5D;
    /** 飞行阶段的最长 tick 数（安全上限）。 */
    private static final int MAX_FLIGHT_TICKS = 60;
    /** 拉动阶段的最长 tick 数（安全上限）。 */
    private static final int MAX_PULL_TICKS = 120;

    /** 无钩爪。 */
    public static final int PHASE_NONE = 0;
    /** 钩爪飞行中。 */
    public static final int PHASE_FLYING = 1;
    /** 已命中，正在拉动玩家。 */
    public static final int PHASE_PULLING = 2;

    /** 玩家 UUID -> 钩爪状态。 */
    private static final Map<UUID, Hook> HOOKS = new HashMap<>();

    private GrappleManager() {
    }

    private static final class Hook {
        int phase;
        /** 飞行中：钩爪当前位置；拉动中：锚点位置（命中实体时每 tick 更新）。 */
        Vec3 position;
        final Vec3 direction;
        final double flightSpeed;
        final double pullSpeed;
        double travelled;
        /** 命中的实体 id（-1 表示命中的是方块或已失效）。 */
        int entityId = -1;
        int ticks;

        Hook(Vec3 position, Vec3 direction, double flightSpeed, double pullSpeed) {
            this.position = position;
            this.direction = direction;
            this.flightSpeed = flightSpeed;
            this.pullSpeed = pullSpeed;
            this.phase = PHASE_FLYING;
        }
    }

    // ==================== 查询 ====================

    public static boolean isActive(Player player) {
        return player != null && HOOKS.containsKey(player.getUUID());
    }

    public static boolean isPulling(Player player) {
        Hook hook = player == null ? null : HOOKS.get(player.getUUID());
        return hook != null && hook.phase == PHASE_PULLING;
    }

    /** 钩爪当前位置（客户端渲染绳索的终点）。 */
    @Nullable
    public static Vec3 getPosition(Player player) {
        Hook hook = player == null ? null : HOOKS.get(player.getUUID());
        return hook == null ? null : hook.position;
    }

    // ==================== 发射 / 收回（服务端） ====================

    /** 发射抓钩：钩爪进入飞行阶段。 */
    public static void fire(ServerPlayer player, GrappleCrossbowItem crossbow) {
        Vec3 from = player.getEyePosition(1.0F);
        Vec3 direction = player.getLookAngle().normalize();
        HOOKS.put(player.getUUID(), new Hook(from, direction,
                crossbow.flightSpeedPerTick(), crossbow.pullSpeedPerTick()));
        sync(player);
    }

    /** 收回抓钩：保留玩家当前惯性（不修改速度）。 */
    public static void retract(ServerPlayer player) {
        if (player != null && HOOKS.remove(player.getUUID()) != null) {
            sync(player);
        }
    }

    /** 触发切换：已有钩爪（飞行中或拉动中）则收回，否则发射。 */
    public static void toggle(ServerPlayer player, GrappleCrossbowItem crossbow) {
        if (isActive(player)) {
            retract(player);
        } else {
            fire(player, crossbow);
        }
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            HOOKS.remove(playerId);
        }
    }

    public static void clearAll() {
        HOOKS.clear();
    }

    // ==================== 每 tick ====================

    /** 服务端：推进所有玩家的钩爪。 */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                tickHook(player);
            }
        }
    }

    private static void tickHook(ServerPlayer player) {
        Hook hook = HOOKS.get(player.getUUID());
        if (hook == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()) {
            retract(player);
            return;
        }
        int limit = hook.phase == PHASE_FLYING ? MAX_FLIGHT_TICKS : MAX_PULL_TICKS;
        if (++hook.ticks > limit) {
            retract(player);
            return;
        }
        if (hook.phase == PHASE_FLYING) {
            advanceFlight(player, hook);
        } else {
            advancePull(player, hook);
        }
        // 状态可能在推进过程中被清除（命中失败 / 到达），这里按当前状态同步
        sync(player);
    }

    // ==================== 飞行阶段 ====================

    private static void advanceFlight(ServerPlayer player, Hook hook) {
        Vec3 from = hook.position;
        Vec3 to = from.add(hook.direction.scale(hook.flightSpeed));

        BlockHitResult blockHit = player.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockPos = blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation();

        double searchDistance = hook.flightSpeed + 1.0D;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, from, to,
                new AABB(from, to).inflate(1.0D),
                entity -> entity != player && !entity.isSpectator() && entity.isPickable(),
                searchDistance * searchDistance);
        Entity hitEntity = entityHit == null ? null : entityHit.getEntity();
        Vec3 entityPos = entityHit == null ? null : entityHit.getLocation();

        boolean hitBlock = blockPos != null
                && (entityPos == null || from.distanceToSqr(blockPos) <= from.distanceToSqr(entityPos));
        if (hitBlock) {
            hook.phase = PHASE_PULLING;
            hook.position = blockPos;
            hook.entityId = -1;
            return;
        }
        if (hitEntity != null) {
            hook.phase = PHASE_PULLING;
            hook.entityId = hitEntity.getId();
            hook.position = centerOf(hitEntity);
            return;
        }

        // 未命中：继续飞行
        hook.position = to;
        hook.travelled += hook.flightSpeed;
        if (hook.travelled >= MAX_LENGTH) {
            HOOKS.remove(player.getUUID());
        }
    }

    // ==================== 拉动阶段 ====================

    private static void advancePull(ServerPlayer player, Hook hook) {
        // 命中生物 / 怪物：持续跟随目标当前位置，避免目标移动后把玩家甩在原地
        if (hook.entityId >= 0) {
            Entity entity = player.level().getEntity(hook.entityId);
            if (entity != null && entity.isAlive()) {
                hook.position = centerOf(entity);
            }
        }
        if (centerOf(player).distanceTo(hook.position) <= ARRIVE_DISTANCE) {
            HOOKS.remove(player.getUUID());
            return;
        }
        applyPull(player, hook.position, hook.pullSpeed);
    }

    /**
     * 把玩家朝锚点拉动（客户端与服务端使用同一套算法，保证预测一致）。
     *
     * @param speed 拉动速度（格/tick）
     */
    public static void applyPull(Player player, Vec3 anchor, double speed) {
        Vec3 center = centerOf(player);
        Vec3 delta = anchor.subtract(center);
        double distance = delta.length();
        if (distance < 1.0E-4D) {
            return;
        }
        player.setDeltaMovement(delta.scale(speed / distance));
        player.fallDistance = 0.0F;
        player.hasImpulse = true;
    }

    private static Vec3 centerOf(Entity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    // ==================== 同步 ====================

    /** 把钩爪状态同步给该玩家（客户端据此做本地预测与绳索渲染）。 */
    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Hook hook = HOOKS.get(player.getUUID());
        CombatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CombatNetwork.GrappleSyncPacket(
                        hook == null ? PHASE_NONE : hook.phase,
                        hook == null ? 0.0D : hook.position.x,
                        hook == null ? 0.0D : hook.position.y,
                        hook == null ? 0.0D : hook.position.z,
                        hook == null ? 0.0D : hook.pullSpeed));
    }
}
