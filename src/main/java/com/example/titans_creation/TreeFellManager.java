package com.example.titans_creation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * 服务端伐树管理器。
 *
 * 当斧类工具在战斗模式下触发伐树时，由 {@link CombatNetwork#handleTreeFell} 调用。
 * 通过玩家视线射线检测目标原木方块，BFS 搜索整棵树的原木方块（6 方向连通），
 * 再收集原木附近的树叶方块，最后统一破坏。
 *
 * 兼容所有原版树种（橡树、白桦、云杉、丛林、金合欢、深色橡树、樱花、红树林、杜鹃等）：
 * - 原木检测使用 {@link net.minecraft.tags.BlockTags#LOGS} 标签 + 红树根
 * - 树叶检测使用 {@link net.minecraft.tags.BlockTags#LEAVES} 标签 + 杜鹃花/红树胎生苗
 */
public final class TreeFellManager {

    private TreeFellManager() {}

    /** BFS 搜索原木方块的最大数量（防止超大结构卡服）。 */
    private static final int MAX_LOGS = 256;
    /** 树叶收集的最大数量。 */
    private static final int MAX_LEAVES = 512;
    /** 树叶搜索半径（从每根原木出发搜索周围多少格内的树叶）。 */
    private static final int LEAF_SEARCH_RADIUS = 2;
    /** 射线检测最大距离。 */
    private static final double RAYCAST_DISTANCE = 6.0;

    /**
     * 执行伐树。
     *
     * @param player 服务端玩家
     * @param toolType 工具类型（必须 canFellTrees()）
     */
    public static void fellTree(ServerPlayer player, CombatToolType toolType) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 射线检测玩家瞄准的方块
        BlockPos targetPos = raycastTargetBlock(player);
        if (targetPos == null) {
            return;
        }

        BlockState targetState = serverLevel.getBlockState(targetPos);
        if (!isLog(targetState)) {
            return;
        }

        // BFS 搜索整棵树的原木
        Set<BlockPos> logs = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        findTreeBlocks(serverLevel, targetPos, logs, leaves);

        if (logs.isEmpty()) {
            return;
        }

        // 破坏所有方块
        breakBlocks(serverLevel, player, logs, leaves);

        // 消耗工具耐久（每破坏一个方块消耗 1 点耐久，上限 64）
        damageTool(player, logs.size() + leaves.size());
    }

    /**
     * 射线检测玩家瞄准的方块。返回方块坐标，若未命中则返回 null。
     */
    private static BlockPos raycastTargetBlock(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(RAYCAST_DISTANCE));
        BlockHitResult hit = player.level().clip(
                new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    /**
     * BFS 搜索整棵树的原木方块和树叶方块。
     *
     * 原木搜索：从起点开始，6 方向连通，只搜索原木方块（含红树根）。
     * 树叶搜索：对每根原木，搜索周围 LEAF_SEARCH_RADIUS 范围内的树叶方块。
     *
     * @param level 世界
     * @param start 起始原木坐标
     * @param logs 输出：所有原木坐标
     * @param leaves 输出：所有树叶坐标
     */
    private static void findTreeBlocks(ServerLevel level, BlockPos start,
                                        Set<BlockPos> logs, Set<BlockPos> leaves) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        logs.add(start);

        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos current = queue.poll();

            // 搜索 6 方向相邻的原木
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (logs.contains(neighbor)) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbor);
                if (isLog(state)) {
                    logs.add(neighbor);
                    if (logs.size() < MAX_LOGS) {
                        queue.add(neighbor);
                    }
                }
            }

            // 搜索周围树叶
            collectNearbyLeaves(level, current, logs, leaves);
        }
    }

    /**
     * 收集原木周围 LEAF_SEARCH_RADIUS 范围内的树叶方块。
     */
    private static void collectNearbyLeaves(ServerLevel level, BlockPos logPos,
                                             Set<BlockPos> logs, Set<BlockPos> leaves) {
        for (int dx = -LEAF_SEARCH_RADIUS; dx <= LEAF_SEARCH_RADIUS; dx++) {
            for (int dy = -LEAF_SEARCH_RADIUS; dy <= LEAF_SEARCH_RADIUS; dy++) {
                for (int dz = -LEAF_SEARCH_RADIUS; dz <= LEAF_SEARCH_RADIUS; dz++) {
                    if (leaves.size() >= MAX_LEAVES) {
                        return;
                    }
                    BlockPos pos = logPos.offset(dx, dy, dz);
                    if (logs.contains(pos) || leaves.contains(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (isLeaf(state)) {
                        leaves.add(pos);
                    }
                }
            }
        }
    }

    /**
     * 破坏所有原木和树叶方块，含掉落物、粒子、音效。
     */
    private static void breakBlocks(ServerLevel level, ServerPlayer player,
                                     Set<BlockPos> logs, Set<BlockPos> leaves) {
        ItemStack tool = player.getMainHandItem();

        // 先破坏原木（从上到下，避免悬浮树叶）
        for (BlockPos pos : logs) {
            breakBlock(level, player, pos, tool);
        }

        // 再破坏树叶
        for (BlockPos pos : leaves) {
            breakBlock(level, player, pos, tool);
        }
    }

    /**
     * 破坏单个方块：播放音效、粒子，并根据工具是否适合掉落物品。
     */
    private static void breakBlock(ServerLevel level, ServerPlayer player,
                                    BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        FluidState fluidState = level.getFluidState(pos);

        // 播放方块破坏音效
        level.playSound(null, pos, state.getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);

        // 在创造模式下直接移除方块
        if (player.isCreative()) {
            level.removeBlock(pos, false);
        } else {
            // 检查工具是否适合此方块（影响掉落）
            boolean canHarvest = tool.isCorrectToolForDrops(state);
            // 先生成掉落物和粒子
            Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, tool);
            level.destroyBlock(pos, false, player);
        }

        // 生成破坏粒子
        level.levelEvent(2001, pos, Block.getId(state));
    }

    /**
     * 消耗工具耐久。每破坏一个方块消耗 1 点耐久，上限 64 点。
     */
    private static void damageTool(ServerPlayer player, int blocksBroken) {
        if (player.isCreative()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }
        int damage = Math.min(blocksBroken, 64);
        tool.hurtAndBreak(damage, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
    }

    /**
     * 判断方块是否为原木（含红树根）。
     * 使用 BlockTags.LOGS 标签覆盖所有原版原木类型，
     * 另外加入红树根以支持红树林。
     */
    private static boolean isLog(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS);
    }

    /**
     * 判断方块是否为树叶（含杜鹃花、红树胎生苗）。
     * 使用 BlockTags.LEAVES 标签覆盖所有原版树叶类型，
     * 另外加入杜鹃花和红树胎生苗以支持杜鹃树和红树林。
     */
    private static boolean isLeaf(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES)
                || state.is(net.minecraft.world.level.block.Blocks.MANGROVE_PROPAGULE)
                || state.is(net.minecraft.world.level.block.Blocks.AZALEA)
                || state.is(net.minecraft.world.level.block.Blocks.FLOWERING_AZALEA)
                || state.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)
                || state.is(net.minecraft.world.level.block.Blocks.VINE);
    }
}
