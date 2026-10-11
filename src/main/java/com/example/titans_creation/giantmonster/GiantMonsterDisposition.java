package com.example.titans_creation.giantmonster;

import java.util.Locale;

/**
 * 巨型怪物对玩家的友好程度。主要影响<b>脱战状态</b>下的行为：
 *
 * <ul>
 *   <li>{@link #FRIENDLY} <b>友好</b>：玩家进入侦测范围、甚至攻击怪物，都<b>不会</b>使怪物进入战斗状态。</li>
 *   <li>{@link #NEUTRAL} <b>中立</b>：玩家进入侦测范围<b>不会</b>进战，但<b>攻击怪物</b>会使怪物进入战斗状态。</li>
 *   <li>{@link #HOSTILE} <b>敌对</b>：玩家一旦进入侦测范围，<b>立刻</b>进入战斗状态。</li>
 * </ul>
 *
 * <p>是否对创造模式玩家生效由 {@link GiantMonsterCombatConfig#isAggroInCreative()} 单独控制。
 */
public enum GiantMonsterDisposition {

    /** 友好：不会被玩家激怒。 */
    FRIENDLY,
    /** 中立：被打才还手。 */
    NEUTRAL,
    /** 敌对：看到玩家就进战。 */
    HOSTILE;

    public static GiantMonsterDisposition parse(String name, GiantMonsterDisposition fallback) {
        if (name == null) {
            return fallback;
        }
        for (GiantMonsterDisposition disposition : values()) {
            if (disposition.name().equalsIgnoreCase(name.trim())) {
                return disposition;
            }
        }
        return fallback;
    }

    public GiantMonsterDisposition next() {
        GiantMonsterDisposition[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** 是否会在被玩家攻击时进入战斗。 */
    public boolean retaliatesWhenAttacked() {
        return this != FRIENDLY;
    }

    /** 是否会在玩家进入侦测范围时主动进入战斗。 */
    public boolean aggroOnSight() {
        return this == HOSTILE;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
