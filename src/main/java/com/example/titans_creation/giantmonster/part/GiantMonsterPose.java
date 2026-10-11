package com.example.titans_creation.giantmonster.part;

import java.util.Map;

/**
 * 某一时刻整只怪物的骨骼姿态集合：骨骼名 -> 动画变换。
 *
 * <p>部位碰撞箱通过 {@code bone} 字段从姿态中取出对应变换，
 * 因此每 tick 重新计算姿态即可让碰撞箱跟随动画移动/旋转。
 */
public final class GiantMonsterPose {

    private static final GiantMonsterPose EMPTY = new GiantMonsterPose(Map.of());

    private final Map<String, GiantMonsterPartTransform> bones;

    private GiantMonsterPose(Map<String, GiantMonsterPartTransform> bones) {
        this.bones = bones;
    }

    /** 空姿态：所有骨骼都使用单位变换（即「默认姿态」）。 */
    public static GiantMonsterPose empty() {
        return EMPTY;
    }

    public static GiantMonsterPose of(Map<String, GiantMonsterPartTransform> bones) {
        return bones == null || bones.isEmpty() ? EMPTY : new GiantMonsterPose(Map.copyOf(bones));
    }

    /** 取骨骼变换；未定义的骨骼返回单位变换。 */
    public GiantMonsterPartTransform get(String bone) {
        if (bone == null) {
            return GiantMonsterPartTransform.IDENTITY;
        }
        GiantMonsterPartTransform transform = bones.get(bone);
        return transform == null ? GiantMonsterPartTransform.IDENTITY : transform;
    }

    public boolean isEmpty() {
        return bones.isEmpty();
    }

    public int size() {
        return bones.size();
    }

    @Override
    public String toString() {
        return "GiantMonsterPose" + bones.keySet();
    }
}
