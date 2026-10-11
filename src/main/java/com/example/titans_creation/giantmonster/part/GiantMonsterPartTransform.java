package com.example.titans_creation.giantmonster.part;

import net.minecraft.world.phys.Vec3;

/**
 * 单个骨骼在某一时刻的动画变换（由动画系统或程序化动画计算得到）。
 *
 * <p>部位碰撞箱通过「骨骼名 + 本地偏移/旋转」绑定到骨骼上，
 * 因此骨骼一动，碰撞箱的位置与朝向就会随之改变——这就是「碰撞箱根据动画改变位置」的实现方式。
 *
 * @param offsetX 骨骼在世界（实体局部）坐标系中的位移 X
 * @param offsetY 位移 Y
 * @param offsetZ 位移 Z
 * @param yaw    偏航（度）
 * @param pitch  俯仰（度）
 * @param roll   翻滚（度）
 * @param scale  缩放（1.0 表示原始大小）
 */
public record GiantMonsterPartTransform(double offsetX, double offsetY, double offsetZ,
                                        double yaw, double pitch, double roll, double scale) {

    /** 单位变换（骨骼位于原点、无旋转、无缩放）。 */
    public static final GiantMonsterPartTransform IDENTITY =
            new GiantMonsterPartTransform(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D);

    public Vec3 offset() {
        return new Vec3(offsetX, offsetY, offsetZ);
    }

    public GiantMonsterPartTransform withScale(double value) {
        return new GiantMonsterPartTransform(offsetX, offsetY, offsetZ, yaw, pitch, roll, value);
    }

    @Override
    public String toString() {
        return "Transform[off=(" + offsetX + "," + offsetY + "," + offsetZ + "), rot=("
                + yaw + "," + pitch + "," + roll + "), scale=" + scale + "]";
    }
}
