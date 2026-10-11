package com.example.titans_creation.climb;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.titans_creation.giantmonster.GiantMonster;
import com.example.titans_creation.giantmonster.GiantMonsterState;
import com.example.titans_creation.giantmonster.part.GiantMonsterSurfaceHit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 攀爬与滞留（由装备驱动的攀爬系统）。
 *
 * <p>设计要点（按需求）：
 * <ol>
 *   <li><b>没有装备就没有攀爬</b>：未装备攀爬手套与利爪之靴时，本系统完全不介入，玩家无法攀爬。</li>
 *   <li><b>自动启用、无需热键</b>：能力由"是否装备了对应物品"决定，穿脱装备即启用/失效，没有开关按键。</li>
 *   <li><b>攀爬手套</b>：紧贴方块或怪物、并<b>按住朝该表面方向的移动键</b>时向上攀爬；松开则缓慢下落。</li>
 *   <li><b>利爪之靴</b>：紧贴方块或怪物时保持高度不下落（无需按键），但本身不能攀爬。
 *       滞留有时长上限，用尽后进入冷却；冷却期间抓不住会正常下落。</li>
 *   <li><b>两件一起</b>：既能攀爬也能保持高度；由于贴墙滞留被视为"站在地面"，
 *       <b>跳跃会立即重置滞留时长与冷却</b>（可以贴着墙连续起跳）。</li>
 * </ol>
 *
 * <p>本类在<b>客户端与服务端运行同一套判定</b>：客户端用于本地预测（与原版爬梯子的方式一致），
 * 服务端为权威判定。输入来源：客户端直接读按键，服务端读 {@link ClimbGearScanner} 的输入缓存（由输入包更新）。
 */
public final class ClimbManager {

    // ==================== 可调参数 ====================

    /** 方块贴面判定距离（格，从玩家碰撞箱向外探测）。 */
    private static final double BLOCK_CONTACT = 0.25D;
    /** 怪物部位贴面判定距离（格）。 */
    private static final double MONSTER_CONTACT = 0.6D;
    /** 向上攀爬速度（格/tick）。 */
    private static final double CLIMB_SPEED = 0.16D;
    /** 松开攀爬键后的缓慢下落速度（格/tick，负值）。 */
    private static final double SLOW_FALL_SPEED = -0.08D;
    /** 滞留时长上限（tick，5 秒）。 */
    private static final int MAX_GRIP_TICKS = 100;
    /** 滞留用尽后的冷却（tick，2 秒）。 */
    private static final int GRIP_COOLDOWN_TICKS = 40;
    /** 跳跃后暂停吸附的 tick 数（让跳跃能正常上升）。 */
    private static final int JUMP_GRACE_TICKS = 8;
    /** 跳跃冲量（与原版跳跃一致）。 */
    private static final double JUMP_IMPULSE = 0.42D;
    /** 判定"移动键朝着表面"的方向阈值（点积）：约 ±75° 以内视为朝表面。 */
    private static final double PRESS_DOT = 0.25D;
    /** 按住前进键时，只要不是背对表面即视为可攀爬（约 ±107°）。 */
    private static final double FORWARD_DOT = -0.3D;
    /** 怪物搜索半径（格）。 */
    private static final double MONSTER_SEARCH = 4.0D;

    /** 玩家 UUID -> 攀爬状态。 */
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ClimbManager() {
    }

    /** 每名玩家的攀爬状态。 */
    private static final class State {
        /** 已滞留的 tick 数。 */
        int gripTicks;
        /** 滞留冷却剩余 tick 数。 */
        int cooldown;
        /** 跳跃后的免吸附余量。 */
        int jumpGrace;
        /** 跳跃按键是否已被处理（防止按住不停重置）。 */
        boolean jumpLatched;

        void resetSession() {
            this.gripTicks = 0;
            this.jumpGrace = 0;
            this.jumpLatched = false;
        }
    }

    // ==================== 入口 ====================

    /** 服务端：每 tick 处理全部玩家。 */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                tickPlayer(player);
            }
        }
    }

    /** 客户端：对本机玩家做本地预测（与服务器同一套判定）。 */
    public static void tickClient(Player player) {
        if (player != null && player.isAlive() && !player.isSpectator() && !player.isPassenger()) {
            tickPlayer(player);
        }
    }

    /** 玩家退出时清理。 */
    public static void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
            ClimbGearScanner.clearInput(playerId);
        }
    }

    public static void clearAll() {
        STATES.clear();
        ClimbGearScanner.clearAllInputs();
    }

    // ==================== 核心逻辑 ====================

    private static void tickPlayer(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying()) {
            return;
        }

        // ① 装备扫描：没有攀爬装备就完全不具备攀爬能力
        EnumSet<ClimbAbility> abilities = ClimbGearScanner.scan(player);
        boolean hasGloves = abilities.contains(ClimbAbility.CLIMB);
        boolean hasBoots = abilities.contains(ClimbAbility.GRIP);
        if (!hasGloves && !hasBoots) {
            STATES.remove(player.getUUID());
            return;
        }

        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (state.cooldown > 0) {
            state.cooldown--;
        }
        // 跳跃后短暂不吸附，让跳跃能正常上升
        if (state.jumpGrace > 0) {
            state.jumpGrace--;
            return;
        }

        // ② 是否紧贴方块或怪物（可能同时贴住多个面，全部收集后逐个判定）
        java.util.List<Vec3> normals = findHuggedNormals(player);
        if (normals.isEmpty()) {
            state.resetSession();
            return;
        }

        float[] input = ClimbGearScanner.getInput(player.getUUID());
        double xxa = input[0];
        double zza = input[1];
        boolean jumpPressed = input[2] > 0.5F;
        if (!jumpPressed) {
            state.jumpLatched = false;
        }

        Vec3 motion = player.getDeltaMovement();
        boolean pressingToward = false;
        for (Vec3 normal : normals) {
            if (isPressingToward(player, normal, xxa, zza)) {
                pressingToward = true;
                break;
            }
        }
        boolean gripping = hasBoots && state.cooldown <= 0 && state.gripTicks < MAX_GRIP_TICKS;

        // ③ 两件装备一起使用时：贴墙滞留视为站在地面 → 跳跃立即重置滞留时长与冷却
        if (hasGloves && gripping && jumpPressed && !state.jumpLatched) {
            state.jumpLatched = true;
            state.gripTicks = 0;
            state.cooldown = 0;
            state.jumpGrace = JUMP_GRACE_TICKS;
            player.setDeltaMovement(motion.x, JUMP_IMPULSE, motion.z);
            player.fallDistance = 0.0F;
            player.hasImpulse = true;
            player.hurtMarked = true;
            return;
        }

        // ④ 攀爬手套 + 按住朝表面的移动键 → 向上攀爬
        if (hasGloves && pressingToward) {
            player.setDeltaMovement(motion.x, CLIMB_SPEED, motion.z);
            player.fallDistance = 0.0F;
            player.hasImpulse = true;
            // 主动攀爬时逐渐恢复滞留时长
            state.gripTicks = Math.max(0, state.gripTicks - 2);
            return;
        }

        // ⑤ 利爪之靴：贴墙滞留（保持高度，不下落）
        if (gripping) {
            state.gripTicks++;
            if (motion.y < 0.0D) {
                player.setDeltaMovement(motion.x, 0.0D, motion.z);
            }
            player.fallDistance = 0.0F;
            player.setOnGround(true);
            player.hasImpulse = true;
            return;
        }
        if (hasBoots && state.gripTicks >= MAX_GRIP_TICKS && state.cooldown <= 0) {
            // 滞留时长用尽 → 进入冷却
            state.cooldown = GRIP_COOLDOWN_TICKS;
        }

        // ⑥ 只装备攀爬手套（或滞留在冷却中）→ 缓慢下落
        if (hasGloves) {
            if (motion.y < SLOW_FALL_SPEED) {
                player.setDeltaMovement(motion.x, SLOW_FALL_SPEED, motion.z);
                player.hasImpulse = true;
            }
            player.fallDistance = 0.0F;
        }
    }

    // ==================== 贴面检测 ====================

    /**
     * 找出玩家紧贴的<b>全部</b>表面，返回由各表面指向玩家的水平单位法线。
     *
     * <p>之所以返回列表：站在方块角落时可能同时贴住两个面，只取第一个会导致
     * "某些朝向按 W 不生效"的问题（必须把每个面都拿来做方向判定）。
     */
    private static java.util.List<Vec3> findHuggedNormals(Player player) {
        java.util.List<Vec3> normals = new java.util.ArrayList<>(4);
        Vec3 monsterNormal = findMonsterNormal(player);
        if (monsterNormal != null) {
            normals.add(monsterNormal);
        }
        AABB box = player.getBoundingBox();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            AABB probe = new AABB(
                    box.minX + dir.getStepX() * BLOCK_CONTACT, box.minY,
                    box.minZ + dir.getStepZ() * BLOCK_CONTACT,
                    box.maxX + dir.getStepX() * BLOCK_CONTACT, box.maxY - 0.1D,
                    box.maxZ + dir.getStepZ() * BLOCK_CONTACT);
            if (hasCollision(player.level(), probe)) {
                normals.add(new Vec3(-dir.getStepX(), 0.0D, -dir.getStepZ()));
            }
        }
        return normals;
    }

    /** 巨型怪物部位表面的水平法线。 */
    @Nullable
    private static Vec3 findMonsterNormal(Player player) {
        GiantMonster monster = findNearbyMonster(player);
        if (monster == null) {
            return null;
        }
        GiantMonsterSurfaceHit hit = monster.getPartBoxes().probeContact(
                player.getBoundingBox().getCenter(), MONSTER_CONTACT, MONSTER_CONTACT, 90.0D);
        if (hit == null) {
            return null;
        }
        Vec3 normal = hit.normal();
        if (normal.lengthSqr() < 1.0E-6D || Math.abs(normal.y) > 0.9D) {
            // 顶面/底面无法"朝着它按键"，不作为可攀爬表面
            return null;
        }
        return new Vec3(normal.x, 0.0D, normal.z).normalize();
    }

    @Nullable
    private static GiantMonster findNearbyMonster(Player player) {
        GiantMonster best = null;
        double bestDistance = MONSTER_SEARCH * MONSTER_SEARCH;
        AABB area = player.getBoundingBox().inflate(MONSTER_SEARCH);
        for (Entity entity : player.level().getEntities(player, area, e -> e instanceof GiantMonster)) {
            if (entity instanceof GiantMonster monster && monster.isAlive()
                    && monster.getState() != GiantMonsterState.DEATH) {
                double distance = monster.distanceToSqr(player);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = monster;
                }
            }
        }
        return best;
    }

    /** 指定的立方体范围内是否存在有碰撞体积的方块。 */
    private static boolean hasCollision(Level level, AABB box) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!state.getCollisionShape(level, pos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 玩家是否"想朝这个表面攀爬"。
     *
     * <p>判定分两种，满足其一即可（这样任何一面都只需按一个键）：
     * <ol>
     *   <li><b>方向对着表面</b>：输入方向与"指向表面"的夹角在 ±75° 以内（含斜向 W+A / W+D）；</li>
     *   <li><b>按住前进键</b>：只要不是背对表面（夹角小于约 107°）就算数 —— 因为墙面在玩家侧方时，
     *       只按 W 的方向与墙面垂直，若只按第 1 条判定就会出现"某些面 W 无效"的问题。</li>
     * </ol>
     *
     * @param normal 由表面指向玩家的水平单位法线
     */
    private static boolean isPressingToward(Player player, Vec3 normal, double xxa, double zza) {
        if (Math.abs(xxa) < 0.01D && Math.abs(zza) < 0.01D) {
            return false;
        }
        Vec3 toward = new Vec3(-normal.x, 0.0D, -normal.z);
        if (toward.lengthSqr() < 1.0E-4D) {
            return false;
        }
        toward = toward.normalize();

        Vec3 intent = intentVector(player, xxa, zza);
        if (intent.lengthSqr() < 1.0E-4D) {
            return false;
        }
        intent = intent.normalize();

        // ① 输入方向朝表面（含斜向）
        if (intent.dot(toward) > PRESS_DOT) {
            return true;
        }
        // ② 按住前进键，且视线没有背对表面
        if (zza > 0.1D) {
            Vec3 look = new Vec3(-Math.sin(Math.toRadians(player.getYRot())), 0.0D,
                    Math.cos(Math.toRadians(player.getYRot()))).normalize();
            if (look.dot(toward) > FORWARD_DOT) {
                return true;
            }
        }
        return false;
    }

    /** 由按键输入（xxa = 左正，zza = 前正）与玩家朝向换算出的世界水平方向。与 {@code Entity.getInputVector} 完全一致。 */
    private static Vec3 intentVector(Player player, double xxa, double zza) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        return forward.scale(zza).add(left.scale(xxa));
    }
}
