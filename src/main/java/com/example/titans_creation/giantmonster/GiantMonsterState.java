package com.example.titans_creation.giantmonster;

import java.util.Locale;

/**
 * 巨型怪物的状态模式。
 *
 * <p>状态会根据实际反馈自动流转（见 {@link GiantMonster}）：
 * <ul>
 *   <li>{@link #NONE} <b>无状态（测试用）</b>：不执行任何动作、保持默认姿态；
 *       受到攻击、玩家靠近等交互都<b>不会</b>改变其行动模式（仅在死亡时进入 {@link #DEATH}）。</li>
 *   <li>{@link #OUT_OF_COMBAT} <b>脱战</b>：随机执行配置的动作（待机 / 移动 / 休息 / 对环境造成影响）。</li>
 *   <li>{@link #IN_COMBAT} <b>进战</b>：执行战斗动作、追击目标；长时间无有效目标则回到脱战。</li>
 *   <li>{@link #DEATH} <b>死亡</b>：血量为空，以死亡姿态停留在原地，一段时间后消失。</li>
 * </ul>
 */
public enum GiantMonsterState {

    /** 无状态：测试用，不进行任何动作。 */
    NONE,
    /** 脱战：非战斗状态，执行脱战动作。 */
    OUT_OF_COMBAT,
    /** 进战：战斗状态。 */
    IN_COMBAT,
    /** 死亡：死亡姿态，等待消失。 */
    DEATH;

    public static GiantMonsterState parse(String name, GiantMonsterState fallback) {
        if (name == null) {
            return fallback;
        }
        for (GiantMonsterState state : values()) {
            if (state.name().equalsIgnoreCase(name.trim())) {
                return state;
            }
        }
        return fallback;
    }

    public static GiantMonsterState byOrdinal(int ordinal) {
        GiantMonsterState[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OUT_OF_COMBAT;
    }

    /** 可用于"初始状态"的候选（无状态 / 脱战）之间循环。 */
    public GiantMonsterState nextSpawnState() {
        return this == NONE ? OUT_OF_COMBAT : NONE;
    }

    /** 是否允许执行动作（无状态与死亡都不动作）。 */
    public boolean isActive() {
        return this == OUT_OF_COMBAT || this == IN_COMBAT;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
