package com.example.titans_creation.giantmonster.part;

import net.minecraft.world.phys.Vec3;

/**
 * 一次表面探测的结果。
 *
 * @param part               被命中的部位
 * @param point              命中点（世界坐标）
 * @param normal             命中面的外法线
 * @param inclinationDegrees 该面相对水平面的倾角（0=水平，90=垂直）
 * @param walkable           倾角是否足够小、允许玩家站立
 * @param climbable          该部位是否允许攀附
 */
public record GiantMonsterSurfaceHit(GiantMonsterPartInstance part, Vec3 point, Vec3 normal,
                                     double inclinationDegrees, boolean walkable, boolean climbable) {

    public String partId() {
        return part.getDefinition().getId();
    }

    /** 是否可以攀附（倾角过大无法站立时的替代移动方式）。 */
    public boolean canClimb() {
        return climbable;
    }

    @Override
    public String toString() {
        return "Surface[" + part.getDefinition().getId() + " point=" + point + " normal=" + normal
                + " inclination=" + String.format("%.1f", inclinationDegrees)
                + " walkable=" + walkable + "]";
    }
}
