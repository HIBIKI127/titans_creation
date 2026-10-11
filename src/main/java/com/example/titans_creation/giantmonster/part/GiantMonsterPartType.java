package com.example.titans_creation.giantmonster.part;

import java.util.Locale;

/**
 * 部位模块类型。
 *
 * <p>按「是否有碰撞体积」与「是否可交互」两个维度组合，共三种：
 * <ul>
 *   <li>{@link #DECORATIVE} <b>无碰撞体积、不可交互</b>：纯装饰/挂点，不参与碰撞、命中与攀附判定。</li>
 *   <li>{@link #INTERACTIVE_NO_COLLISION} <b>无碰撞体积、可交互</b>：
 *       不阻挡移动，但可以被命中、可以被攀附（例如胸口弱点、悬挂点）。</li>
 *   <li>{@link #SOLID_INTERACTIVE} <b>有碰撞体积、可交互</b>：
 *       阻挡移动（会把实体推出体积外），同时可以被命中与攀附（例如躯干、四肢）。</li>
 * </ul>
 */
public enum GiantMonsterPartType {

    /** 无碰撞体积、不可交互。 */
    DECORATIVE(false, false),
    /** 无碰撞体积、可交互。 */
    INTERACTIVE_NO_COLLISION(false, true),
    /** 有碰撞体积、可交互。 */
    SOLID_INTERACTIVE(true, true);

    private final boolean collision;
    private final boolean interactive;

    GiantMonsterPartType(boolean collision, boolean interactive) {
        this.collision = collision;
        this.interactive = interactive;
    }

    /** 是否具有碰撞体积（会阻挡并推出实体）。 */
    public boolean hasCollision() {
        return collision;
    }

    /** 是否可交互（可被命中、可被攀附）。 */
    public boolean isInteractive() {
        return interactive;
    }

    public static GiantMonsterPartType parse(String name, GiantMonsterPartType fallback) {
        if (name == null) {
            return fallback;
        }
        for (GiantMonsterPartType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return fallback;
    }

    public GiantMonsterPartType next() {
        GiantMonsterPartType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
