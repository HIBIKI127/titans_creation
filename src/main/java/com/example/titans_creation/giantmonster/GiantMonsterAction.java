package com.example.titans_creation.giantmonster;

import java.util.Locale;

/**
 * 脱战状态下怪物可随机执行的动作。
 *
 * <p>每个动作持续 {@code combat.out_of_combat.action_interval} 个 tick，
 * 到期后从已启用的动作中随机挑选下一个。
 */
public enum GiantMonsterAction {

    /** 待机：原地保持站姿。 */
    IDLE,
    /** 移动：随机漫步。 */
    MOVE,
    /** 休息：原地停留并缓慢恢复血量。 */
    REST,
    /** 对环境造成影响：产生脚步震动与尘土，并可踩踏脆弱方块。 */
    ENVIRONMENT;

    public static GiantMonsterAction parse(String name, GiantMonsterAction fallback) {
        if (name == null) {
            return fallback;
        }
        for (GiantMonsterAction action : values()) {
            if (action.name().equalsIgnoreCase(name.trim())) {
                return action;
            }
        }
        return fallback;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
