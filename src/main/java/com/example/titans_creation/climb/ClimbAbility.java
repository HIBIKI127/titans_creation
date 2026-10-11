package com.example.titans_creation.climb;

/**
 * 攀爬装备提供的能力。
 */
public enum ClimbAbility {

    /** 攀爬：紧贴方块或怪物、并按住朝该方向的移动键时向上攀爬（攀爬手套）。 */
    CLIMB("climb"),
    /** 滞留：紧贴方块或怪物时保持高度不下落（利爪之靴）。 */
    GRIP("grip");

    private final String id;

    ClimbAbility(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** 语言键，例如 {@code titans_creation.climb.ability.climb}。 */
    public String getTranslationKey() {
        return "titans_creation.climb.ability." + id;
    }
}
