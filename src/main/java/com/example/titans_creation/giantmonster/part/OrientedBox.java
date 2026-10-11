package com.example.titans_creation.giantmonster.part;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 可旋转的包围盒（OBB）。
 *
 * <p>原版 {@link AABB} 不能旋转，而「部位碰撞箱需要跟随动画改变位置与朝向」，
 * 因此部位的碰撞体用 OBB 表示：中心点 + 三个半长 + 一个旋转（偏航/俯仰/翻滚，单位度）。
 *
 * <p>本类同时提供交互所需的基础几何运算：
 * 点包含判定、最近点、面法线、与水平面的倾角、把点推出盒体、射线求交。
 */
public final class OrientedBox {

    private final Vec3 center;
    private final double halfX;
    private final double halfY;
    private final double halfZ;
    /** 世界坐标系下的三个正交轴（已包含旋转）。 */
    private final Vec3 axisX;
    private final Vec3 axisY;
    private final Vec3 axisZ;
    /** 保守包围盒（用于快速排除）。 */
    private final AABB bounds;

    private OrientedBox(Vec3 center, double halfX, double halfY, double halfZ,
                        Vec3 axisX, Vec3 axisY, Vec3 axisZ) {
        this.center = center;
        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
        double ex = Math.abs(axisX.x) * halfX + Math.abs(axisY.x) * halfY + Math.abs(axisZ.x) * halfZ;
        double ey = Math.abs(axisX.y) * halfX + Math.abs(axisY.y) * halfY + Math.abs(axisZ.y) * halfZ;
        double ez = Math.abs(axisX.z) * halfX + Math.abs(axisY.z) * halfY + Math.abs(axisZ.z) * halfZ;
        this.bounds = new AABB(center.x - ex, center.y - ey, center.z - ez,
                center.x + ex, center.y + ey, center.z + ez);
    }

    /**
     * 构建一个 OBB。
     *
     * @param center 世界坐标中心
     * @param sizeX  本地 X 方向全长
     * @param sizeY  本地 Y 方向全长
     * @param sizeZ  本地 Z 方向全长
     * @param yawDeg 偏航（绕 Y 轴，度）
     * @param pitchDeg 俯仰（绕 X 轴，度）
     * @param rollDeg 翻滚（绕 Z 轴，度）
     */
    public static OrientedBox of(Vec3 center, double sizeX, double sizeY, double sizeZ,
                                 double yawDeg, double pitchDeg, double rollDeg) {
        double halfX = Math.max(1.0E-4D, sizeX * 0.5D);
        double halfY = Math.max(1.0E-4D, sizeY * 0.5D);
        double halfZ = Math.max(1.0E-4D, sizeZ * 0.5D);
        Vec3 axisX = rotate(new Vec3(1.0D, 0.0D, 0.0D), yawDeg, pitchDeg, rollDeg);
        Vec3 axisY = rotate(new Vec3(0.0D, 1.0D, 0.0D), yawDeg, pitchDeg, rollDeg);
        Vec3 axisZ = rotate(new Vec3(0.0D, 0.0D, 1.0D), yawDeg, pitchDeg, rollDeg);
        return new OrientedBox(center, halfX, halfY, halfZ, axisX, axisY, axisZ);
    }

    /** 旋转顺序：先翻滚（Z），再俯仰（X），最后偏航（Y）。公开以便部位系统复用同一套角度约定。 */
    public static Vec3 rotate(Vec3 vector, double yawDeg, double pitchDeg, double rollDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double roll = Math.toRadians(rollDeg);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        double x1 = vector.x * cosRoll - vector.y * sinRoll;
        double y1 = vector.x * sinRoll + vector.y * cosRoll;
        double z1 = vector.z;

        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        double x3 = x2 * cosYaw + z2 * sinYaw;
        double y3 = y2;
        double z3 = -x2 * sinYaw + z2 * cosYaw;
        return new Vec3(x3, y3, z3);
    }

    // ==================== 基本访问 ====================

    public Vec3 getCenter() {
        return center;
    }

    public Vec3 getAxisX() {
        return axisX;
    }

    public Vec3 getAxisY() {
        return axisY;
    }

    public Vec3 getAxisZ() {
        return axisZ;
    }

    public double getHalfX() {
        return halfX;
    }

    public double getHalfY() {
        return halfY;
    }

    public double getHalfZ() {
        return halfZ;
    }

    public double getMaxHalfExtent() {
        return Math.max(halfX, Math.max(halfY, halfZ));
    }

    /** 保守 AABB（包住旋转后的盒体）。 */
    public AABB toAABB() {
        return bounds;
    }

    // ==================== 坐标变换 ====================

    /** 世界坐标点 -> 盒体本地坐标（原点在中心，各轴单位为格）。 */
    public Vec3 toLocal(Vec3 world) {
        Vec3 delta = world.subtract(center);
        return new Vec3(delta.dot(axisX), delta.dot(axisY), delta.dot(axisZ));
    }

    /** 盒体本地坐标 -> 世界坐标。 */
    public Vec3 toWorld(double localX, double localY, double localZ) {
        return center
                .add(axisX.scale(localX))
                .add(axisY.scale(localY))
                .add(axisZ.scale(localZ));
    }

    /**
     * 把本地坐标点夹取到盒体范围内（各轴限制在 ±half 之内）。
     * 用于让攀附点沿着表面移动时不会跑出盒体。
     */
    public Vec3 clampLocal(Vec3 local) {
        return new Vec3(
                clamp(local.x, -halfX, halfX),
                clamp(local.y, -halfY, halfY),
                clamp(local.z, -halfZ, halfZ));
    }

    // ==================== 点判定 ====================

    /** 点是否位于盒体内部。 */
    public boolean contains(Vec3 world) {
        Vec3 local = toLocal(world);
        return Math.abs(local.x) <= halfX && Math.abs(local.y) <= halfY && Math.abs(local.z) <= halfZ;
    }

    /** 盒体表面（或内部）距离给定点最近的位置。 */
    public Vec3 closestPoint(Vec3 world) {
        Vec3 local = toLocal(world);
        double lx = clamp(local.x, -halfX, halfX);
        double ly = clamp(local.y, -halfY, halfY);
        double lz = clamp(local.z, -halfZ, halfZ);
        return toWorld(lx, ly, lz);
    }

    /**
     * 距离给定点最近的那个面的<b>外法线</b>。
     * 点在外部时返回最近面的法线；点在内部时返回穿透最浅面的法线。
     */
    public Vec3 faceNormal(Vec3 world) {
        Vec3 local = toLocal(world);
        double dx = halfX - Math.abs(local.x);
        double dy = halfY - Math.abs(local.y);
        double dz = halfZ - Math.abs(local.z);
        if (dx <= dy && dx <= dz) {
            return axisX.scale(Math.signum(local.x) >= 0.0D ? 1.0D : -1.0D);
        }
        if (dy <= dz) {
            return axisY.scale(Math.signum(local.y) >= 0.0D ? 1.0D : -1.0D);
        }
        return axisZ.scale(Math.signum(local.z) >= 0.0D ? 1.0D : -1.0D);
    }

    /**
     * 该面上的给定点相对水平面的<b>倾角</b>（度）。
     *
     * <p>0 度表示水平面（可站立，例如平躺的躯干顶部、肩部平台），
     * 90 度表示垂直面（无法站立，需要攀爬）。
     */
    public double inclinationDegrees(Vec3 world) {
        Vec3 normal = faceNormal(world).normalize();
        double dot = clamp(normal.y, -1.0D, 1.0D);
        // 只关心朝上的面：朝下的面（dot < 0）视为不可站立
        if (dot <= 0.0D) {
            return 90.0D;
        }
        return Math.toDegrees(Math.acos(dot));
    }

    /** 把点沿最近面的法线推出盒体，并留出 margin 的余量。 */
    public Vec3 pushOut(Vec3 world, double margin) {
        Vec3 local = toLocal(world);
        double dx = halfX - Math.abs(local.x);
        double dy = halfY - Math.abs(local.y);
        double dz = halfZ - Math.abs(local.z);
        double lx = local.x;
        double ly = local.y;
        double lz = local.z;
        if (dx <= dy && dx <= dz) {
            lx = Math.signum(local.x) >= 0.0D ? halfX + margin : -halfX - margin;
        } else if (dy <= dz) {
            ly = Math.signum(local.y) >= 0.0D ? halfY + margin : -halfY - margin;
        } else {
            lz = Math.signum(local.z) >= 0.0D ? halfZ + margin : -halfZ - margin;
        }
        return toWorld(lx, ly, lz);
    }

    // ==================== 射线求交 ====================

    /**
     * 射线与盒体求交（slab 法，在本地坐标系中进行，结果精确）。
     *
     * @param origin      起点（世界坐标）
     * @param direction   方向（需已归一化）
     * @param maxDistance 最大距离
     * @return 命中点（世界坐标）；未命中返回 null
     */
    public Vec3 rayIntersect(Vec3 origin, Vec3 direction, double maxDistance) {
        Vec3 delta = origin.subtract(center);
        double[] localOrigin = {delta.dot(axisX), delta.dot(axisY), delta.dot(axisZ)};
        double[] localDirection = {direction.dot(axisX), direction.dot(axisY), direction.dot(axisZ)};
        double[] half = {halfX, halfY, halfZ};

        double tMin = 0.0D;
        double tMax = maxDistance;
        for (int i = 0; i < 3; i++) {
            double d = localDirection[i];
            double o = localOrigin[i];
            if (Math.abs(d) < 1.0E-8D) {
                if (o < -half[i] || o > half[i]) {
                    return null;
                }
                continue;
            }
            double t1 = (-half[i] - o) / d;
            double t2 = (half[i] - o) / d;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        return origin.add(direction.scale(tMin));
    }

    /** 与给定 AABB 是否可能相交（用保守包围盒快速判断）。 */
    public boolean intersects(AABB other) {
        return bounds.intersects(other);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    @Override
    public String toString() {
        return "OrientedBox[center=" + center + ", half=(" + halfX + "," + halfY + "," + halfZ + ")]";
    }
}
