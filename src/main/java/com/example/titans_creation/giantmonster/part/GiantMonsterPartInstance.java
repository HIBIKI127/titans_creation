package com.example.titans_creation.giantmonster.part;

import net.minecraft.world.phys.Vec3;

/**
 * 一个部位碰撞箱的运行时实例：定义 + 当前时刻的 OBB（由动画姿态计算得到）。
 */
public final class GiantMonsterPartInstance {

    private final GiantMonsterPartDefinition definition;
    private OrientedBox box;

    public GiantMonsterPartInstance(GiantMonsterPartDefinition definition) {
        this.definition = definition;
    }

    public GiantMonsterPartDefinition getDefinition() {
        return definition;
    }

    /** 当前碰撞箱；尚未计算时为 null。 */
    public OrientedBox getBox() {
        return box;
    }

    void setBox(OrientedBox box) {
        this.box = box;
    }

    public boolean hasBox() {
        return box != null;
    }

    /** 该点是否位于本部位碰撞箱内。 */
    public boolean contains(Vec3 point) {
        return box != null && box.contains(point);
    }

    /** 距该点最近表面的外法线。 */
    public Vec3 faceNormal(Vec3 point) {
        return box == null ? Vec3.ZERO : box.faceNormal(point);
    }

    /** 该点所在表面相对水平面的倾角（度）。 */
    public double inclinationDegrees(Vec3 point) {
        return box == null ? 90.0D : box.inclinationDegrees(point);
    }

    @Override
    public String toString() {
        return definition.getId() + "@" + box;
    }
}
