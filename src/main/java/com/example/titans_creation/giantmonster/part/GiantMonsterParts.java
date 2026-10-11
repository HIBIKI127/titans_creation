package com.example.titans_creation.giantmonster.part;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.example.titans_creation.giantmonster.GiantMonster;

import net.minecraft.world.phys.Vec3;

/**
 * 一只怪物的全部部位碰撞箱。
 *
 * <p>每 tick 调用 {@link #applyPose(GiantMonster, GiantMonsterPose)}，
 * 用动画姿态重算每个部位的 OBB —— 这就是「碰撞箱根据动画改变位置」的核心。
 *
 * <p>同时提供交互查询：射线拾取可交互部位、表面探测（用于判断站立/攀爬）、
 * 以及判断某点是否落在「有碰撞体积」的部位内（用于把实体推出体积外）。
 */
public final class GiantMonsterParts {

    private final List<GiantMonsterPartInstance> parts = new ArrayList<>();
    private final Map<String, GiantMonsterPartInstance> byId = new LinkedHashMap<>();

    private GiantMonsterParts() {
    }

    public static GiantMonsterParts of(List<GiantMonsterPartDefinition> definitions) {
        GiantMonsterParts parts = new GiantMonsterParts();
        if (definitions != null) {
            for (GiantMonsterPartDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                GiantMonsterPartInstance instance = new GiantMonsterPartInstance(definition);
                parts.parts.add(instance);
                parts.byId.put(definition.getId(), instance);
            }
        }
        return parts;
    }

    public List<GiantMonsterPartInstance> all() {
        return parts;
    }

    public int size() {
        return parts.size();
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    @Nullable
    public GiantMonsterPartInstance byId(String id) {
        return id == null ? null : byId.get(id);
    }

    // ==================== 动画驱动 ====================

    /**
     * 用动画姿态更新所有部位碰撞箱。
     *
     * <p>部位世界中心 = 实体位置 + 按身体朝向旋转后的骨骼位置，其中骨骼位置 =
     * 枢轴 + 绕枢轴旋转（部位偏移 - 枢轴） + 骨骼位移。
     * 也就是说：手臂绕肩、腿绕髋旋转，因此骨骼一动碰撞箱就跟着移动。
     *
     * <p>部位朝向 = 身体朝向 + 部位本地旋转 + 骨骼旋转（角度相加的近似；正式接入模型骨骼矩阵后可直接替换）。
     */
    public void applyPose(GiantMonster monster, GiantMonsterPose pose) {
        Vec3 origin = monster.position();
        double bodyYaw = monster.yBodyRot;
        for (GiantMonsterPartInstance part : parts) {
            GiantMonsterPartDefinition definition = part.getDefinition();
            GiantMonsterPartTransform bone = pose.get(definition.getBone());
            double scale = bone.scale() <= 0.0D ? 1.0D : bone.scale();

            Vec3 pivot = new Vec3(definition.getPivotX(), definition.getPivotY(), definition.getPivotZ());
            Vec3 relative = new Vec3(definition.getOffsetX(), definition.getOffsetY(), definition.getOffsetZ())
                    .subtract(pivot);
            Vec3 posed = pivot
                    .add(OrientedBox.rotate(relative, bone.yaw(), bone.pitch(), bone.roll()))
                    .add(bone.offset());
            Vec3 worldCenter = origin.add(OrientedBox.rotate(posed, bodyYaw, 0.0D, 0.0D));

            part.setBox(OrientedBox.of(worldCenter,
                    definition.getSizeX() * scale,
                    definition.getSizeY() * scale,
                    definition.getSizeZ() * scale,
                    bodyYaw + definition.getYaw() + bone.yaw(),
                    definition.getPitch() + bone.pitch(),
                    definition.getRoll() + bone.roll()));
        }
    }

    // ==================== 查询 ====================

    /** 射线命中的原始结果。 */
    private record RawHit(GiantMonsterPartInstance part, Vec3 point) {
    }

    @Nullable
    private RawHit raycast(Vec3 origin, Vec3 direction, double maxDistance, boolean climbOnly) {
        GiantMonsterPartInstance bestPart = null;
        Vec3 bestPoint = null;
        double bestDistance = maxDistance;
        for (GiantMonsterPartInstance part : parts) {
            GiantMonsterPartDefinition definition = part.getDefinition();
            if (!definition.isInteractive()) {
                continue;
            }
            if (climbOnly && !definition.isClimbable()) {
                continue;
            }
            OrientedBox box = part.getBox();
            if (box == null) {
                continue;
            }
            Vec3 hit = box.rayIntersect(origin, direction, bestDistance);
            if (hit == null) {
                continue;
            }
            double distance = hit.distanceTo(origin);
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestPart = part;
                bestPoint = hit;
            }
        }
        return bestPart == null ? null : new RawHit(bestPart, bestPoint);
    }

    /** 拾取射线命中的最近可交互部位；未命中返回 null。 */
    @Nullable
    public GiantMonsterPartInstance pick(Vec3 origin, Vec3 direction, double maxDistance) {
        RawHit hit = raycast(origin, direction, maxDistance, false);
        return hit == null ? null : hit.part();
    }

    /** 拾取射线命中的最近可攀附部位；未命中返回 null。 */
    @Nullable
    public GiantMonsterPartInstance pickClimbable(Vec3 origin, Vec3 direction, double maxDistance) {
        RawHit hit = raycast(origin, direction, maxDistance, true);
        return hit == null ? null : hit.part();
    }

    /**
     * 沿给定方向探测可交互表面，并计算倾角与是否可站立。
     *
     * @param maxStandAngle 允许站立的最大倾角（度）
     */
    @Nullable
    public GiantMonsterSurfaceHit probe(Vec3 origin, Vec3 direction, double maxDistance, double maxStandAngle) {
        RawHit hit = raycast(origin, direction, maxDistance, false);
        if (hit == null) {
            return null;
        }
        OrientedBox box = hit.part().getBox();
        if (box == null) {
            return null;
        }
        Vec3 normal = box.faceNormal(hit.point());
        double inclination = box.inclinationDegrees(hit.point());
        boolean walkable = inclination <= maxStandAngle;
        return new GiantMonsterSurfaceHit(hit.part(), hit.point(), normal, inclination, walkable,
                hit.part().getDefinition().isClimbable());
    }

    /**
     * 在玩家周围（四个水平方向 + 上方）寻找最近的可攀附表面，
     * 用于判断玩家"正贴着"怪物的哪一面。
     */
    @Nullable
    public GiantMonsterSurfaceHit probeContact(Vec3 origin, double horizontalDistance, double verticalDistance,
                                               double maxStandAngle) {
        GiantMonsterSurfaceHit best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3[] directions = {
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
        };
        for (Vec3 direction : directions) {
            GiantMonsterSurfaceHit hit = probe(origin, direction, horizontalDistance, maxStandAngle);
            if (hit == null) {
                continue;
            }
            double distance = hit.point().distanceTo(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }
        GiantMonsterSurfaceHit above = probe(origin, new Vec3(0.0D, 1.0D, 0.0D), verticalDistance, maxStandAngle);
        if (above != null) {
            double distance = above.point().distanceTo(origin);
            if (distance < bestDistance) {
                best = above;
            }
        }
        return best;
    }

    /** 找出包含该点的「有碰撞体积」部位；没有则返回 null。 */
    @Nullable
    public GiantMonsterPartInstance solidContaining(Vec3 point) {
        for (GiantMonsterPartInstance part : parts) {
            if (!part.getDefinition().hasCollision()) {
                continue;
            }
            if (part.contains(point)) {
                return part;
            }
        }
        return null;
    }

    /** 该点是否落在任意有碰撞体积的部位内。 */
    public boolean isInsideSolid(Vec3 point) {
        return solidContaining(point) != null;
    }

    /** 统计各类型部位数量（便于调试与界面展示）。 */
    public int countOf(GiantMonsterPartType type) {
        int count = 0;
        for (GiantMonsterPartInstance part : parts) {
            if (part.getDefinition().getType() == type) {
                count++;
            }
        }
        return count;
    }
}
