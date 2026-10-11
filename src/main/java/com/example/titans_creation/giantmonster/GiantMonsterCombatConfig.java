package com.example.titans_creation.giantmonster;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * 巨型怪物的仇恨机制配置（配置文件中的 {@code combat} 段）。
 *
 * <p>包含三部分：
 * <ol>
 *   <li><b>基础</b>：初始状态模式（无状态 / 脱战）、友好程度、创造模式玩家是否会被仇恨；</li>
 *   <li><b>脱战行为</b>：随机动作的间隔、启用哪些动作（待机 / 移动 / 休息 / 对环境造成影响）、
 *       休息回血速度、环境影响是否踩踏脆弱方块；</li>
 *   <li><b>战斗与死亡</b>：失去目标多久后脱战、死亡后停留多久消失。</li>
 * </ol>
 */
public final class GiantMonsterCombatConfig {

    // ==================== 字段默认值 ====================
    /** 初始状态模式（仅允许 NONE / OUT_OF_COMBAT）。 */
    private GiantMonsterState initialState = GiantMonsterState.OUT_OF_COMBAT;
    /** 友好程度。 */
    private GiantMonsterDisposition disposition = GiantMonsterDisposition.HOSTILE;
    /** 创造模式玩家是否会被仇恨。 */
    private boolean aggroInCreative = false;

    /** 脱战动作的切换间隔（tick），每个间隔随机挑选一个动作。 */
    private int actionInterval = 100;
    private boolean actionIdle = true;
    private boolean actionMove = true;
    private boolean actionRest = true;
    private boolean actionEnvironment = true;
    /** 休息时每秒恢复的血量。 */
    private double restHealPerSecond = 1.0D;
    /** 环境影响是否踩踏脆弱方块（花草 / 作物 / 树叶）。 */
    private boolean environmentTrample = true;

    /** 失去有效目标多久（tick）后脱战。 */
    private int loseTargetTicks = 200;
    /** 死亡后停留多久（tick）消失。 */
    private int deathStayTicks = 100;

    // ==================== 读取 ====================

    public GiantMonsterState getInitialState() {
        return initialState;
    }

    public GiantMonsterDisposition getDisposition() {
        return disposition;
    }

    public boolean isAggroInCreative() {
        return aggroInCreative;
    }

    public int getActionInterval() {
        return actionInterval;
    }

    public boolean isActionIdle() {
        return actionIdle;
    }

    public boolean isActionMove() {
        return actionMove;
    }

    public boolean isActionRest() {
        return actionRest;
    }

    public boolean isActionEnvironment() {
        return actionEnvironment;
    }

    public double getRestHealPerSecond() {
        return restHealPerSecond;
    }

    public boolean isEnvironmentTrample() {
        return environmentTrample;
    }

    public int getLoseTargetTicks() {
        return loseTargetTicks;
    }

    public int getDeathStayTicks() {
        return deathStayTicks;
    }

    /** 是否至少启用了一个脱战动作。 */
    public boolean hasAnyAction() {
        return actionIdle || actionMove || actionRest || actionEnvironment;
    }

    // ==================== 修改 ====================

    public void setInitialState(GiantMonsterState value) {
        this.initialState = value == GiantMonsterState.IN_COMBAT || value == GiantMonsterState.DEATH
                ? GiantMonsterState.OUT_OF_COMBAT
                : value;
    }

    public void setDisposition(GiantMonsterDisposition value) {
        this.disposition = value;
    }

    public void setAggroInCreative(boolean value) {
        this.aggroInCreative = value;
    }

    public void setActionInterval(int value) {
        this.actionInterval = value;
    }

    public void setActionIdle(boolean value) {
        this.actionIdle = value;
    }

    public void setActionMove(boolean value) {
        this.actionMove = value;
    }

    public void setActionRest(boolean value) {
        this.actionRest = value;
    }

    public void setActionEnvironment(boolean value) {
        this.actionEnvironment = value;
    }

    public void setRestHealPerSecond(double value) {
        this.restHealPerSecond = value;
    }

    public void setEnvironmentTrample(boolean value) {
        this.environmentTrample = value;
    }

    public void setLoseTargetTicks(int value) {
        this.loseTargetTicks = value;
    }

    public void setDeathStayTicks(int value) {
        this.deathStayTicks = value;
    }

    /** 数值钳制，避免配置失误导致异常行为。 */
    public GiantMonsterCombatConfig sanitize() {
        this.actionInterval = Mth.clamp(this.actionInterval, 10, 24000);
        this.restHealPerSecond = Mth.clamp(this.restHealPerSecond, 0.0D, 1000.0D);
        this.loseTargetTicks = Mth.clamp(this.loseTargetTicks, 20, 24000);
        this.deathStayTicks = Mth.clamp(this.deathStayTicks, 20, 24000);
        if (!hasAnyAction()) {
            this.actionIdle = true;
        }
        return this;
    }

    public GiantMonsterCombatConfig copy() {
        GiantMonsterCombatConfig copy = new GiantMonsterCombatConfig();
        copy.initialState = initialState;
        copy.disposition = disposition;
        copy.aggroInCreative = aggroInCreative;
        copy.actionInterval = actionInterval;
        copy.actionIdle = actionIdle;
        copy.actionMove = actionMove;
        copy.actionRest = actionRest;
        copy.actionEnvironment = actionEnvironment;
        copy.restHealPerSecond = restHealPerSecond;
        copy.environmentTrample = environmentTrample;
        copy.loseTargetTicks = loseTargetTicks;
        copy.deathStayTicks = deathStayTicks;
        return copy;
    }

    // ==================== 序列化 ====================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("initial_state", initialState.id());
        json.addProperty("disposition", disposition.id());
        json.addProperty("aggro_in_creative", aggroInCreative);

        JsonObject outOfCombat = new JsonObject();
        outOfCombat.addProperty("action_interval", actionInterval);
        outOfCombat.addProperty("action_idle", actionIdle);
        outOfCombat.addProperty("action_move", actionMove);
        outOfCombat.addProperty("action_rest", actionRest);
        outOfCombat.addProperty("action_environment", actionEnvironment);
        outOfCombat.addProperty("rest_heal_per_second", restHealPerSecond);
        outOfCombat.addProperty("environment_trample", environmentTrample);
        json.add("out_of_combat", outOfCombat);

        JsonObject combat = new JsonObject();
        combat.addProperty("lose_target_ticks", loseTargetTicks);
        json.add("combat", combat);

        JsonObject death = new JsonObject();
        death.addProperty("stay_ticks", deathStayTicks);
        json.add("death", death);
        return json;
    }

    public static GiantMonsterCombatConfig fromJson(JsonObject json) {
        GiantMonsterCombatConfig config = new GiantMonsterCombatConfig();
        if (json == null) {
            return config;
        }
        config.initialState = GiantMonsterState.parse(
                GsonHelper.getAsString(json, "initial_state", null), config.initialState);
        config.disposition = GiantMonsterDisposition.parse(
                GsonHelper.getAsString(json, "disposition", null), config.disposition);
        config.aggroInCreative = GsonHelper.getAsBoolean(json, "aggro_in_creative", config.aggroInCreative);

        if (json.has("out_of_combat") && json.get("out_of_combat").isJsonObject()) {
            JsonObject outOfCombat = json.getAsJsonObject("out_of_combat");
            config.actionInterval = GsonHelper.getAsInt(outOfCombat, "action_interval", config.actionInterval);
            config.actionIdle = GsonHelper.getAsBoolean(outOfCombat, "action_idle", config.actionIdle);
            config.actionMove = GsonHelper.getAsBoolean(outOfCombat, "action_move", config.actionMove);
            config.actionRest = GsonHelper.getAsBoolean(outOfCombat, "action_rest", config.actionRest);
            config.actionEnvironment = GsonHelper.getAsBoolean(outOfCombat, "action_environment",
                    config.actionEnvironment);
            config.restHealPerSecond = GsonHelper.getAsDouble(outOfCombat, "rest_heal_per_second",
                    config.restHealPerSecond);
            config.environmentTrample = GsonHelper.getAsBoolean(outOfCombat, "environment_trample",
                    config.environmentTrample);
        }
        if (json.has("combat") && json.get("combat").isJsonObject()) {
            JsonObject combat = json.getAsJsonObject("combat");
            config.loseTargetTicks = GsonHelper.getAsInt(combat, "lose_target_ticks", config.loseTargetTicks);
        }
        if (json.has("death") && json.get("death").isJsonObject()) {
            JsonObject death = json.getAsJsonObject("death");
            config.deathStayTicks = GsonHelper.getAsInt(death, "stay_ticks", config.deathStayTicks);
        }
        return config.sanitize();
    }

    @Override
    public String toString() {
        return "GiantMonsterCombatConfig[state=" + initialState.id()
                + " disposition=" + disposition.id()
                + " aggroInCreative=" + aggroInCreative
                + " actionInterval=" + actionInterval + "]";
    }
}
