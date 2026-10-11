package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.example.titans_creation.CombatServerState;

/**
 * 巨型怪物生成管理器（服务端）。
 *
 * <p>按每只怪物的 {@link GiantMonsterSpawnConfig} 逐条检查 10 类生成条件，
 * 满足时在玩家附近的可加载区块内生成怪物。所有条件相互独立、可分别关闭。
 *
 * <p>与设计一致：本管理器<b>不接入原版刷怪循环</b>（怪物也不会被写入任何群系刷怪表），
 * 因此对原版生物生成没有任何影响。
 *
 * <p>注意：生成计数与冷却为运行时状态（服务器重启后重置），配置文件本身才是唯一权威来源。
 */
public final class GiantMonsterSpawnManager {

    /** 每次尝试生成时随机取点的次数。 */
    private static final int POSITION_ATTEMPTS = 16;
    /** 距离玩家过近的硬性保护（格），避免贴脸生成。 */
    private static final int ABSOLUTE_MIN_DISTANCE = 16;

    /** 怪物 id -> 距上次检查经过的 tick 数。 */
    private static final Map<String, Integer> INTERVAL_COUNTERS = new HashMap<>();
    /** 怪物 id -> 上次成功生成时的服务器 tick。 */
    private static final Map<String, Long> LAST_SPAWN_TICK = new HashMap<>();

    private GiantMonsterSpawnManager() {
    }

    /** 一次生成尝试的结果。 */
    public record SpawnResult(boolean success, String message) {
    }

    // ==================== 周期检查 ====================

    /** 服务端每 tick 调用：按各自的检查间隔与冷却决定是否尝试生成。 */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (String id : GiantMonsterConfigManager.ids()) {
            GiantMonsterDefinition definition = GiantMonsterConfigManager.get(id);
            if (definition == null) {
                continue;
            }
            GiantMonsterSpawnConfig config = definition.getSpawn();
            if (!config.isEnabled()) {
                INTERVAL_COUNTERS.remove(id);
                continue;
            }

            int counter = INTERVAL_COUNTERS.getOrDefault(id, 0) + 1;
            if (counter < config.getCheckInterval()) {
                INTERVAL_COUNTERS.put(id, counter);
                continue;
            }
            INTERVAL_COUNTERS.put(id, 0);

            long now = server.getTickCount();
            Long last = LAST_SPAWN_TICK.get(id);
            if (last != null && now - last < config.getCooldownTicks()) {
                continue;
            }

            SpawnResult result = attempt(server, id, definition);
            if (result.success()) {
                LAST_SPAWN_TICK.put(id, now);
            }
        }
    }

    /** 由指令调用：立即执行一次生成尝试，并返回结果说明（便于调试生成配置）。 */
    public static SpawnResult forceSpawn(MinecraftServer server, String id) {
        GiantMonsterDefinition definition = GiantMonsterConfigManager.get(id);
        if (definition == null) {
            return new SpawnResult(false, "未找到怪物配置: " + id);
        }
        return attempt(server, id, definition);
    }

    /** 清空运行时计数（配置重载时调用）。 */
    public static void resetRuntimeState() {
        INTERVAL_COUNTERS.clear();
        LAST_SPAWN_TICK.clear();
    }

    // ==================== 生成流程 ====================

    private static SpawnResult attempt(MinecraftServer server, String id, GiantMonsterDefinition definition) {
        GiantMonsterSpawnConfig config = definition.getSpawn();
        if (!config.isEnabled()) {
            return new SpawnResult(false, "该怪物的生成机制未启用");
        }
        EntityType<? extends Mob> type = GiantMonsterRegistry.getEntityType(id);
        if (type == null) {
            return new SpawnResult(false, "该怪物没有注册实体类型: " + id);
        }
        if (config.getMaxPerWorld() <= 0) {
            return new SpawnResult(false, "全世界上限为 0（等同于禁用）");
        }
        if (countAlive(server, type) >= config.getMaxPerWorld()) {
            return new SpawnResult(false, "已达全世界上限 " + config.getMaxPerWorld());
        }

        String reason = "没有找到合适的位置";
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionReason = dimensionFailure(config, level);
            if (dimensionReason != null) {
                reason = dimensionReason;
                continue;
            }

            List<ServerPlayer> candidates = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (playerStateFailure(config, player) == null) {
                    candidates.add(player);
                }
            }
            if (candidates.size() < config.getMinPlayers()) {
                reason = "满足玩家状态条件的玩家不足（需要 " + config.getMinPlayers() + " 人）";
                continue;
            }
            if (candidates.size() > config.getMaxPlayers()) {
                reason = "满足玩家状态条件的玩家过多（上限 " + config.getMaxPlayers() + " 人）";
                continue;
            }

            int minDistance = Math.max(ABSOLUTE_MIN_DISTANCE, config.getMinDistanceToPlayer());
            int maxDistance = Math.max(minDistance + 1, config.getMaxDistanceToPlayer());

            for (int i = 0; i < POSITION_ATTEMPTS; i++) {
                ServerPlayer player = candidates.get(level.random.nextInt(candidates.size()));
                if (config.getMaxPerPlayer() > 0
                        && countNearPlayer(level, type, player, maxDistance) >= config.getMaxPerPlayer()) {
                    reason = "该玩家附近的怪物已达上限 " + config.getMaxPerPlayer();
                    continue;
                }

                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                int distance = minDistance + level.random.nextInt(maxDistance - minDistance);
                int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
                int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int y = chooseY(config, surfaceY, level);
                BlockPos pos = new BlockPos(x, y, z);

                if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    reason = "目标位置所在区块尚未加载";
                    continue;
                }
                String conditionFailure = conditionFailure(config, level, pos);
                if (conditionFailure != null) {
                    reason = conditionFailure;
                    continue;
                }
                if (config.getMinDistanceBetween() > 0
                        && tooCloseToExisting(server, type, pos, config.getMinDistanceBetween())) {
                    reason = "与已有怪物距离小于 " + config.getMinDistanceBetween() + " 格";
                    continue;
                }

                Mob mob = type.create(level);
                if (mob == null) {
                    reason = "创建实体失败";
                    continue;
                }
                mob.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                if (!level.noCollision(mob)) {
                    reason = "生成位置被方块阻挡";
                    continue;
                }
                if (!level.addFreshEntity(mob)) {
                    reason = "实体加入世界失败";
                    continue;
                }
                return new SpawnResult(true, "已在 " + level.dimension().location()
                        + " (" + x + ", " + y + ", " + z + ") 生成 " + id
                        + "（距玩家 " + distance + " 格）");
            }
        }
        return new SpawnResult(false, reason);
    }

    /** 选择生成高度：启用高度条件时在区间内随机，否则取地表。 */
    private static int chooseY(GiantMonsterSpawnConfig config, int surfaceY, ServerLevel level) {
        if (!config.isHeightEnabled()) {
            return surfaceY;
        }
        int min = config.getHeightMin();
        int max = config.getHeightMax();
        if (max <= min) {
            return min;
        }
        return min + level.random.nextInt(max - min + 1);
    }

    // ==================== 条件判定 ====================

    /** 维度条件；不满足时返回原因，满足时返回 null。 */
    private static String dimensionFailure(GiantMonsterSpawnConfig config, ServerLevel level) {
        List<String> list = config.getDimensionList();
        if (list.isEmpty()) {
            return null;
        }
        String id = level.dimension().location().toString();
        boolean matched = containsIgnoreCase(list, id);
        boolean allowed = config.getDimensionMode() == GiantMonsterSpawnConfig.ListMode.WHITELIST
                ? matched
                : !matched;
        return allowed ? null : "维度 " + id + " 不在允许列表中";
    }

    /** 检查除维度与玩家状态外的位置相关条件。 */
    private static String conditionFailure(GiantMonsterSpawnConfig config, ServerLevel level, BlockPos pos) {
        if (!biomeAllowed(config, level, pos)) {
            return "群系不匹配（当前: " + biomeName(level, pos) + "）";
        }
        if (!timeAllowed(config, level)) {
            return "时间不匹配（当前时刻 " + (level.getDayTime() % 24000L) + "）";
        }
        if (!heightAllowed(config, level, pos)) {
            return "高度不匹配（当前 Y=" + pos.getY() + "）";
        }
        if (!lightAllowed(config, level, pos)) {
            return "亮度不匹配（方块 " + level.getBrightness(LightLayer.BLOCK, pos)
                    + " / 天空 " + level.getBrightness(LightLayer.SKY, pos) + "）";
        }
        if (!weatherAllowed(config, level)) {
            return "天气不匹配";
        }
        if (!moonAllowed(config, level)) {
            return "月相不匹配（当前月相 " + level.getMoonPhase() + "）";
        }
        if (!terrainAllowed(config, level, pos)) {
            return "地形净空不足（需要 "
                    + config.getClearanceX() + "×" + config.getClearanceY() + "×" + config.getClearanceZ() + "）";
        }
        return null;
    }

    private static boolean biomeAllowed(GiantMonsterSpawnConfig config, ServerLevel level, BlockPos pos) {
        List<String> list = config.getBiomeList();
        if (list.isEmpty()) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(pos);
        boolean matched = false;
        for (String value : list) {
            if (GiantMonsterSpawnOptions.isTag(value)) {
                if (GiantMonsterSpawnOptions.isInTag(biome, value)) {
                    matched = true;
                    break;
                }
            } else {
                ResourceLocation wanted = GiantMonsterSpawnOptions.parseId(value);
                if (wanted != null && biome.unwrapKey()
                        .map(key -> key.location().equals(wanted))
                        .orElse(false)) {
                    matched = true;
                    break;
                }
            }
        }
        return config.getBiomeMode() == GiantMonsterSpawnConfig.ListMode.WHITELIST ? matched : !matched;
    }

    private static String biomeName(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
    }

    private static boolean timeAllowed(GiantMonsterSpawnConfig config, ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return switch (config.getTimeMode()) {
            case ANY -> true;
            case DAY -> time < 13000L;
            case NIGHT -> time >= 13000L;
            case CUSTOM -> inWrappingRange(time, config.getTimeMin(), config.getTimeMax());
        };
    }

    /** 支持跨午夜的时间区间（如 22000 ~ 2000）。 */
    private static boolean inWrappingRange(long value, int min, int max) {
        if (min <= max) {
            return value >= min && value <= max;
        }
        return value >= min || value <= max;
    }

    private static boolean heightAllowed(GiantMonsterSpawnConfig config, ServerLevel level, BlockPos pos) {
        if (config.isHeightEnabled()
                && (pos.getY() < config.getHeightMin() || pos.getY() > config.getHeightMax())) {
            return false;
        }
        if (config.isOnSurface() || config.isUnderGround()) {
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            if (config.isOnSurface() && Math.abs(pos.getY() - surface) > 3) {
                return false;
            }
            if (config.isUnderGround() && pos.getY() > surface - 4) {
                return false;
            }
        }
        if (config.isCanSeeSky() && !level.canSeeSky(pos)) {
            return false;
        }
        return true;
    }

    private static boolean lightAllowed(GiantMonsterSpawnConfig config, ServerLevel level, BlockPos pos) {
        if (!config.isLightEnabled()) {
            return true;
        }
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return block >= config.getBlockLightMin() && block <= config.getBlockLightMax()
                && sky >= config.getSkyLightMin() && sky <= config.getSkyLightMax();
    }

    private static boolean weatherAllowed(GiantMonsterSpawnConfig config, ServerLevel level) {
        return switch (config.getWeatherMode()) {
            case ANY -> true;
            case CLEAR -> !level.isRaining();
            case RAIN -> level.isRaining() && !level.isThundering();
            case THUNDER -> level.isThundering();
        };
    }

    private static boolean moonAllowed(GiantMonsterSpawnConfig config, ServerLevel level) {
        return !config.isMoonPhaseEnabled() || config.isMoonPhaseAllowed(level.getMoonPhase());
    }

    private static boolean terrainAllowed(GiantMonsterSpawnConfig config, ServerLevel level, BlockPos pos) {
        if (!config.isTerrainEnabled()) {
            return true;
        }
        double halfX = config.getClearanceX() / 2.0D;
        double halfZ = config.getClearanceZ() / 2.0D;
        AABB box = new AABB(
                pos.getX() + 0.5D - halfX, pos.getY(), pos.getZ() + 0.5D - halfZ,
                pos.getX() + 0.5D + halfX, pos.getY() + config.getClearanceY(), pos.getZ() + 0.5D + halfZ);
        return level.noCollision(box);
    }

    /** 玩家状态条件；不满足时返回原因，满足时返回 null。 */
    private static String playerStateFailure(GiantMonsterSpawnConfig config, ServerPlayer player) {
        if (config.isRequireCombatMode() && !CombatServerState.isCombatMode(player)) {
            return "玩家未开启战斗模式";
        }
        if (config.getMaxHealthRatio() < 1.0D
                && player.getHealth() / player.getMaxHealth() > config.getMaxHealthRatio()) {
            return "玩家血量比例过高";
        }
        if (config.getMinLevel() > 0 && player.experienceLevel < config.getMinLevel()) {
            return "玩家等级不足";
        }
        if (config.isNotInVillage() && nearVillage(player.serverLevel(), player.blockPosition())) {
            return "玩家位于村庄内";
        }
        return null;
    }

    private static boolean nearVillage(ServerLevel level, BlockPos pos) {
        return level.isCloseToVillage(pos, 2);
    }

    // ==================== 计数 ====================

    private static int countAlive(MinecraftServer server, EntityType<?> type) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() == type && entity.isAlive()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countNearPlayer(ServerLevel level, EntityType<?> type, ServerPlayer player, int radius) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getType() == type && entity.isAlive()
                    && entity.distanceToSqr(player) <= (double) radius * radius) {
                count++;
            }
        }
        return count;
    }

    private static boolean tooCloseToExisting(MinecraftServer server, EntityType<?> type, BlockPos pos,
                                              int minDistance) {
        double minSqr = (double) minDistance * minDistance;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() != type || !entity.isAlive()) {
                    continue;
                }
                mutable.set(entity.getX(), entity.getY(), entity.getZ());
                if (pos.distSqr(mutable) < minSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
