package com.example.titans_creation.climb;

/**
 * 由提供攀爬能力的装备物品实现（例如攀爬手套、利爪之靴）。
 *
 * <p>玩家只要在装备栏（未装 Curios 时为本模组的 5 格装备栏；装了 Curios 时为 Curios 的槽位）中
 * 装备了实现本接口的物品，对应能力就会**自动生效**，无需按键开关。
 */
public interface ClimbGear {

    /** 该装备提供的能力。 */
    ClimbAbility climbAbility();
}
