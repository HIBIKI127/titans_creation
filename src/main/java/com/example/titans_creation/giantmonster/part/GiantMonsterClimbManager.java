package com.example.titans_creation.giantmonster.part;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.example.titans_creation.giantmonster.GiantMonster;

/**
 * 巨型怪物身体交互（服务端）：<b>碰撞推出</b>与<b>站在可行走部位表面</b>。
 *
 * <p><b>攀爬已迁移</b>：装备驱动的攀爬与滞留（攀爬手套 / 利爪之靴、同时支持方块与怪物表面）
 * 现在由 {@link com.example.titans_creation.climb.ClimbManager} 负责，并且不再有攀爬热键。
 * 本类只保留与怪物部位强相关的两件事：
 * <ol>
 *   <li><b>碰撞</b>：「有碰撞体积」的部位会把玩家沿最近面法线推出，使身体真正挡路；</li>
 *   <li><b>站立</b>：向脚下探测部位表面，若倾角 ≤ max_stand_angle 则视为可行走面，
 *       把玩家贴合到该面上并取消下坠（站在怪物身上，属于既有行为，与攀爬装备无关）。</li>
 * </ol>
 *
 * <p>位置同步统一使用 {@code player.connection.teleport(...)}：它会下发
 * {@code ClientboundPlayerPositionPacket}；只调用 {@code setPos} 会被客户端下一 tick 的位置覆盖。
 */
public final class GiantMonsterClimbManager {

    /** 搜索怪物的水平半径（格）。 */
    private static final double SEARCH_RADIUS = 48.0D;

    private GiantMonsterClimbManager() {
    }

    // ==================== 主循环 ====================

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                tickPlayer(level, player);
            }
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        GiantMonster monster = findNearbyMonster(level, player);
        if (monster == null) {
            return;
        }
        GiantMonsterClimbConfig config = monster.getClimbConfig();
        if (!config.isEnabled()) {
            return;
        }

        pushOutOfSolid(monster, player, config);
        if (config.isAllowStanding()) {
            standOnSurface(monster, player, config);
        }
    }

    // ==================== 碰撞推出 ====================

    /** 把玩家从「有碰撞体积」的部位中推出。 */
    private static void pushOutOfSolid(GiantMonster monster, ServerPlayer player, GiantMonsterClimbConfig config) {
        AABB playerBox = player.getBoundingBox();
        Vec3 center = playerBox.getCenter();
        for (GiantMonsterPartInstance part : monster.getPartBoxes().all()) {
            if (!part.getDefinition().hasCollision()) {
                continue;
            }
            OrientedBox box = part.getBox();
            if (box == null || !box.intersects(playerBox) || !box.contains(center)) {
                continue;
            }
            Vec3 target = box.pushOut(center, config.getPushOutMargin());
            Vec3 delta = target.subtract(center);
            if (delta.lengthSqr() < 1.0E-6D) {
                continue;
            }
            Vec3 feet = player.position().add(delta);
            forcePosition(player, feet.x, feet.y, feet.z);
            return;
        }
    }

    // ==================== 站立 ====================

    /** 若脚下（探测距离内）存在倾角足够小的部位表面，则把玩家贴合上去。 */
    private static void standOnSurface(GiantMonster monster, ServerPlayer player, GiantMonsterClimbConfig config) {
        if (player.getAbilities().flying || player.isPassenger() || player.isFallFlying()) {
            return;
        }
        // 正在向上跳：不吸附，允许玩家跳离怪物身体
        if (player.getDeltaMovement().y > 0.05D) {
            return;
        }
        double probe = config.getGroundProbeDistance();
        Vec3 from = player.position().add(0.0D, 0.1D, 0.0D);
        GiantMonsterSurfaceHit hit = monster.getPartBoxes().probe(from, new Vec3(0.0D, -1.0D, 0.0D),
                probe + 0.2D, config.getMaxStandAngle());
        // 倾角过大（或没探到表面）时不做任何处理，也不再弹出提示文字
        if (hit == null || !hit.walkable()) {
            return;
        }
        double surfaceY = hit.point().y;
        if (player.getY() - surfaceY > probe) {
            return;
        }
        forcePosition(player, player.getX(), surfaceY, player.getZ());
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.0D, motion.z);
        player.fallDistance = 0.0F;
        player.setOnGround(true);
    }

    /**
     * 服务端权威地移动玩家。
     *
     * <p>必须用 {@code connection.teleport(...)}：它会下发 {@code ClientboundPlayerPositionPacket}。
     */
    private static void forcePosition(ServerPlayer player, double x, double y, double z) {
        if (player.position().distanceToSqr(x, y, z) < 1.0E-6D) {
            return;
        }
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
    }

    // ==================== 工具 ====================

    /** 调试/指令用：查找玩家附近的巨型怪物。 */
    @Nullable
    public static GiantMonster findMonsterFor(ServerPlayer player) {
        return player != null && player.level() instanceof ServerLevel level
                ? findNearbyMonster(level, player)
                : null;
    }

    /** 调试/指令用：向下探测脚下的部位表面（判断能否站立）。 */
    @Nullable
    public static GiantMonsterSurfaceHit probeGround(GiantMonster monster, ServerPlayer player) {
        GiantMonsterClimbConfig config = monster.getClimbConfig();
        Vec3 from = player.position().add(0.0D, 0.1D, 0.0D);
        return monster.getPartBoxes().probe(from, new Vec3(0.0D, -1.0D, 0.0D),
                config.getGroundProbeDistance() + 0.2D, config.getMaxStandAngle());
    }

    /** 调试/指令用：探测玩家四周接触到的部位表面（判断能否攀爬）。 */
    @Nullable
    public static GiantMonsterSurfaceHit probeContact(GiantMonster monster, ServerPlayer player) {
        GiantMonsterClimbConfig config = monster.getClimbConfig();
        return monster.getPartBoxes().probeContact(player.getBoundingBox().getCenter(),
                config.getContactDistance(), config.getContactDistance(), config.getMaxStandAngle());
    }

    @Nullable
    private static GiantMonster findNearbyMonster(ServerLevel level, ServerPlayer player) {
        GiantMonster best = null;
        double bestDistance = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof GiantMonster monster) || !monster.isAlive()) {
                continue;
            }
            double distance = monster.distanceToSqr(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = monster;
            }
        }
        return best;
    }
}
