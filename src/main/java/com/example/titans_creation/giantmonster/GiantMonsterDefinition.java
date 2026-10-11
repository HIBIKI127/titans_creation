package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.example.titans_creation.giantmonster.part.GiantMonsterClimbConfig;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartDefinition;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * 巨型怪物的可配置定义（一只巨型怪物 = 一份定义）。
 *
 * 持久化位置：{@code config/titans_creation/monsters/<怪物id>.json}
 * 修改方式：游戏内菜单（{@code /giantconfig}）或直接编辑该 JSON 文件。
 *
 * <h3>可配置项</h3>
 * <ul>
 *   <li>{@code max_health}      总血量</li>
 *   <li>{@code attack_damage}   基础攻击力</li>
 *   <li>{@code movement_speed}  基础移动速度</li>
 *   <li>{@code toughness}       基础韧性（映射到原版盔甲韧性 ARMOR_TOUGHNESS）</li>
 *   <li>{@code armor}           基础防御力（映射到原版盔甲值 ARMOR）</li>
 *   <li>{@code follow_range}    基础侦测范围（映射到原版跟随范围 FOLLOW_RANGE）</li>
 *   <li>{@code stamina}         基础耐力（模组自定义属性，供后续玩法机制使用）</li>
 *   <li>{@code weak_points}     <b>弱点配置</b>：词条 -&gt; 伤害<b>提高</b>（倍率 &gt; 1）</li>
 *   <li>{@code resistances}     <b>抗性配置</b>：词条 -&gt; 伤害<b>减少</b>（倍率 &lt; 1）</li>
 * </ul>
 *
 * <p>词条的定义与来源见 {@link GiantMonsterEntries}；条目结构见 {@link GiantMonsterEntry}。
 */
public final class GiantMonsterDefinition {

    // ==================== 配置文件键名 ====================
    public static final String KEY_ID = "id";
    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_MAX_HEALTH = "max_health";
    public static final String KEY_ATTACK_DAMAGE = "attack_damage";
    public static final String KEY_MOVEMENT_SPEED = "movement_speed";
    public static final String KEY_TOUGHNESS = "toughness";
    public static final String KEY_ARMOR = "armor";
    public static final String KEY_FOLLOW_RANGE = "follow_range";
    public static final String KEY_STAMINA = "stamina";
    public static final String KEY_WEAK_POINTS = "weak_points";
    public static final String KEY_RESISTANCES = "resistances";
    /** 生成机制配置（10 类生成条件）。 */
    public static final String KEY_SPAWN = "spawn";
    /** 仇恨机制配置（状态模式 / 友好程度 / 脱战行为）。 */
    public static final String KEY_COMBAT = "combat";
    /** 部位模块配置（碰撞箱 / 交互 / 攀附）。 */
    public static final String KEY_PARTS = "parts";
    /** 攀爬与站立配置。 */
    public static final String KEY_CLIMB = "climb";
    public static final String KEY_CONFIG_VERSION = "config_version";
    public static final String KEY_COMMENT = "_comment";

    /** 弱点倍率允许范围（只放大）。 */
    public static final double WEAK_POINT_MIN_MULTIPLIER = 1.0D;
    public static final double WEAK_POINT_MAX_MULTIPLIER = 100.0D;
    /** 抗性倍率允许范围（只削减）。 */
    public static final double RESISTANCE_MIN_MULTIPLIER = 0.0D;
    public static final double RESISTANCE_MAX_MULTIPLIER = 1.0D;

    /** 生成配置文件时写入的说明文字，方便直接编辑文件的人理解各字段。 */
    public static final String FILE_COMMENT =
            "巨型怪物配置文件。可直接编辑本文件，也可在游戏内使用 /giantconfig 菜单修改；"
            + "服务端可用 /giantmonster reload 让已生成的怪物立即套用改动。"
            + " 属性字段：max_health=总血量, attack_damage=基础攻击力, movement_speed=基础移动速度, "
            + "toughness=基础韧性(盔甲韧性), armor=基础防御力(盔甲值), follow_range=基础侦测范围, "
            + "stamina=基础耐力(供后续机制使用)。"
            + " 词条字段：weak_points 中的词条命中时伤害提高(damage_multiplier 应 >1)；"
            + "resistances 中的词条命中时伤害减少(damage_multiplier 应 <1)。"
            + " 可用内置词条：slash(斩击/剑斧) blunt(打击/空手或锄) pierce(穿刺/镐或三叉戟) projectile(弹射物) "
            + "fire(火焰) explosion(爆炸) magic(魔法) lightning(雷电) frost(冰冻) fall(摔落) drown(溺水) wither(凋零)；"
            + "也可直接填写原版伤害类型 id（如 arrow）作为自定义词条。";

    private final String id;
    private String displayName;

    /** 总血量。 */
    private double maxHealth = 200.0D;
    /** 基础攻击力。 */
    private double attackDamage = 12.0D;
    /** 基础移动速度。 */
    private double movementSpeed = 0.25D;
    /** 基础韧性（原版盔甲韧性）。 */
    private double toughness = 4.0D;
    /** 基础防御力（原版盔甲值）。 */
    private double armor = 6.0D;
    /** 基础侦测范围（原版跟随范围）。 */
    private double followRange = 40.0D;
    /** 基础耐力（模组自定义属性）。 */
    private double stamina = 100.0D;

    /** 弱点配置：命中词条 -> 伤害提高。 */
    private final List<GiantMonsterEntry> weakPoints = new ArrayList<>();
    /** 抗性配置：命中词条 -> 伤害减少。 */
    private final List<GiantMonsterEntry> resistances = new ArrayList<>();
    /** 生成机制配置。 */
    private GiantMonsterSpawnConfig spawn = new GiantMonsterSpawnConfig();
    /** 仇恨机制配置。 */
    private GiantMonsterCombatConfig combat = new GiantMonsterCombatConfig();
    /** 部位模块定义（碰撞箱随动画移动）。 */
    private final List<GiantMonsterPartDefinition> parts = new ArrayList<>();
    /** 攀爬与站立配置。 */
    private GiantMonsterClimbConfig climb = new GiantMonsterClimbConfig();

    /**
     * 配置结构版本。
     *
     * <p>当默认值的<b>结构</b>（尤其是部位模块几何、攀爬参数等）发生不兼容变化时递增；
     * 读取到版本更低的旧文件时会自动用新的默认结构覆盖对应字段并回写文件，
     * 因此升级模组后无需手动「重置默认」。
     */
    public static final int CONFIG_VERSION = 2;

    private int configVersion = CONFIG_VERSION;

    public GiantMonsterDefinition(String id, String displayName) {
        this.id = id;
        this.displayName = displayName == null ? id : displayName;
    }

    // ==================== 读取 ====================

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 总血量。 */
    public double getMaxHealth() {
        return maxHealth;
    }

    /** 基础攻击力。 */
    public double getAttackDamage() {
        return attackDamage;
    }

    /** 基础移动速度。 */
    public double getMovementSpeed() {
        return movementSpeed;
    }

    /** 基础韧性。 */
    public double getToughness() {
        return toughness;
    }

    /** 基础防御力。 */
    public double getArmor() {
        return armor;
    }

    /** 基础侦测范围。 */
    public double getFollowRange() {
        return followRange;
    }

    /** 基础耐力（上限）。 */
    public double getStamina() {
        return stamina;
    }

    /** 弱点配置（返回可变列表，便于游戏内菜单编辑）。 */
    public List<GiantMonsterEntry> getWeakPoints() {
        return weakPoints;
    }

    /** 抗性配置（返回可变列表，便于游戏内菜单编辑）。 */
    public List<GiantMonsterEntry> getResistances() {
        return resistances;
    }

    /** 生成机制配置（返回可变对象，便于游戏内菜单编辑）。 */
    public GiantMonsterSpawnConfig getSpawn() {
        return spawn;
    }

    /** 用给定配置替换生成机制配置（保存副本）。 */
    public GiantMonsterDefinition setSpawn(GiantMonsterSpawnConfig value) {
        this.spawn = value == null ? new GiantMonsterSpawnConfig() : value.copy();
        return this;
    }

    /** 仇恨机制配置（返回可变对象，便于游戏内菜单编辑）。 */
    public GiantMonsterCombatConfig getCombat() {
        return combat;
    }

    /** 用给定配置替换仇恨机制配置（保存副本）。 */
    public GiantMonsterDefinition setCombat(GiantMonsterCombatConfig value) {
        this.combat = value == null ? new GiantMonsterCombatConfig() : value.copy();
        return this;
    }

    /** 部位模块定义（返回可变列表，便于菜单编辑）。 */
    public List<GiantMonsterPartDefinition> getParts() {
        return parts;
    }

    /** 追加一个部位模块。 */
    public GiantMonsterDefinition addPart(GiantMonsterPartDefinition part) {
        if (part != null) {
            this.parts.add(part);
        }
        return this;
    }

    /** 替换全部部位模块（保存副本）。 */
    public GiantMonsterDefinition setParts(List<GiantMonsterPartDefinition> value) {
        this.parts.clear();
        if (value != null) {
            for (GiantMonsterPartDefinition part : value) {
                if (part != null) {
                    this.parts.add(part.copy());
                }
            }
        }
        return this;
    }

    /** 攀爬与站立配置。 */
    public GiantMonsterClimbConfig getClimb() {
        return climb;
    }

    public GiantMonsterDefinition setClimb(GiantMonsterClimbConfig value) {
        this.climb = value == null ? new GiantMonsterClimbConfig() : value.copy();
        return this;
    }

    /** 配置结构版本（用于旧配置文件自动迁移）。 */
    public int getConfigVersion() {
        return configVersion;
    }

    public GiantMonsterDefinition setConfigVersion(int value) {
        this.configVersion = value;
        return this;
    }

    // ==================== 修改（链式，便于注册时书写） ====================

    public GiantMonsterDefinition setDisplayName(String value) {
        this.displayName = value == null ? id : value;
        return this;
    }

    public GiantMonsterDefinition setMaxHealth(double value) {
        this.maxHealth = value;
        return this;
    }

    public GiantMonsterDefinition setAttackDamage(double value) {
        this.attackDamage = value;
        return this;
    }

    public GiantMonsterDefinition setMovementSpeed(double value) {
        this.movementSpeed = value;
        return this;
    }

    public GiantMonsterDefinition setToughness(double value) {
        this.toughness = value;
        return this;
    }

    public GiantMonsterDefinition setArmor(double value) {
        this.armor = value;
        return this;
    }

    public GiantMonsterDefinition setFollowRange(double value) {
        this.followRange = value;
        return this;
    }

    public GiantMonsterDefinition setStamina(double value) {
        this.stamina = value;
        return this;
    }

    /** 追加一条弱点词条（命中时伤害提高）。 */
    public GiantMonsterDefinition addWeakPoint(GiantMonsterEntry entry) {
        if (entry != null) {
            this.weakPoints.add(entry);
        }
        return this;
    }

    /** 追加一条抗性词条（命中时伤害减少）。 */
    public GiantMonsterDefinition addResistance(GiantMonsterEntry entry) {
        if (entry != null) {
            this.resistances.add(entry);
        }
        return this;
    }

    public GiantMonsterDefinition setWeakPoints(List<GiantMonsterEntry> entries) {
        copyInto(entries, this.weakPoints);
        return this;
    }

    public GiantMonsterDefinition setResistances(List<GiantMonsterEntry> entries) {
        copyInto(entries, this.resistances);
        return this;
    }

    private static void copyInto(List<GiantMonsterEntry> source, List<GiantMonsterEntry> target) {
        target.clear();
        if (source != null) {
            for (GiantMonsterEntry entry : source) {
                if (entry != null) {
                    target.add(entry.copy());
                }
            }
        }
    }

    /**
     * 把数值限制在合理区间：
     * 属性值避免极端数值，弱点倍率强制 ≥1（只提高），抗性倍率强制 ≤1（只减少）。
     */
    public GiantMonsterDefinition sanitize() {
        this.maxHealth = Mth.clamp(this.maxHealth, 1.0D, 1_000_000.0D);
        this.attackDamage = Mth.clamp(this.attackDamage, 0.0D, 100_000.0D);
        this.movementSpeed = Mth.clamp(this.movementSpeed, 0.0D, 10.0D);
        this.toughness = Mth.clamp(this.toughness, 0.0D, 1_000.0D);
        this.armor = Mth.clamp(this.armor, 0.0D, 1_000.0D);
        this.followRange = Mth.clamp(this.followRange, 0.0D, 512.0D);
        this.stamina = Mth.clamp(this.stamina, 0.0D, 1_000_000.0D);
        clampMultipliers(this.weakPoints, WEAK_POINT_MIN_MULTIPLIER, WEAK_POINT_MAX_MULTIPLIER);
        clampMultipliers(this.resistances, RESISTANCE_MIN_MULTIPLIER, RESISTANCE_MAX_MULTIPLIER);
        this.spawn.sanitize();
        this.combat.sanitize();
        this.climb.sanitize();
        for (GiantMonsterPartDefinition part : this.parts) {
            part.sanitize();
        }
        return this;
    }

    private static void clampMultipliers(List<GiantMonsterEntry> entries, double min, double max) {
        for (int i = 0; i < entries.size(); i++) {
            GiantMonsterEntry entry = entries.get(i);
            double clamped = Mth.clamp(entry.getDamageMultiplier(), min, max);
            if (clamped != entry.getDamageMultiplier()) {
                entries.set(i, entry.withDamageMultiplier(clamped));
            }
        }
    }

    public GiantMonsterDefinition copy() {
        GiantMonsterDefinition copy = new GiantMonsterDefinition(id, displayName);
        copy.configVersion = configVersion;
        copy.maxHealth = maxHealth;
        copy.attackDamage = attackDamage;
        copy.movementSpeed = movementSpeed;
        copy.toughness = toughness;
        copy.armor = armor;
        copy.followRange = followRange;
        copy.stamina = stamina;
        copy.setWeakPoints(weakPoints);
        copy.setResistances(resistances);
        copy.spawn = spawn.copy();
        copy.combat = combat.copy();
        copy.climb = climb.copy();
        copy.setParts(parts);
        return copy;
    }

    // ==================== 序列化 ====================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_COMMENT, FILE_COMMENT);
        json.addProperty(KEY_CONFIG_VERSION, configVersion);
        json.addProperty(KEY_ID, id);
        json.addProperty(KEY_DISPLAY_NAME, displayName);
        json.addProperty(KEY_MAX_HEALTH, maxHealth);
        json.addProperty(KEY_ATTACK_DAMAGE, attackDamage);
        json.addProperty(KEY_MOVEMENT_SPEED, movementSpeed);
        json.addProperty(KEY_TOUGHNESS, toughness);
        json.addProperty(KEY_ARMOR, armor);
        json.addProperty(KEY_FOLLOW_RANGE, followRange);
        json.addProperty(KEY_STAMINA, stamina);
        json.add(KEY_WEAK_POINTS, entriesToJson(weakPoints));
        json.add(KEY_RESISTANCES, entriesToJson(resistances));
        json.add(KEY_SPAWN, spawn.toJson());
        json.add(KEY_COMBAT, combat.toJson());
        JsonArray partArray = new JsonArray();
        for (GiantMonsterPartDefinition part : parts) {
            partArray.add(part.toJson());
        }
        json.add(KEY_PARTS, partArray);
        json.add(KEY_CLIMB, climb.toJson());
        return json;
    }

    private static JsonArray entriesToJson(List<GiantMonsterEntry> entries) {
        JsonArray array = new JsonArray();
        for (GiantMonsterEntry entry : entries) {
            array.add(entry.toJson());
        }
        return array;
    }

    /**
     * 从配置文件 JSON 反序列化。缺失字段一律使用默认值，保证旧配置文件仍可加载。
     *
     * @param id 怪物 id（来自文件名，优先于文件内字段）
     */
    public static GiantMonsterDefinition fromJson(String id, JsonObject json) {
        GiantMonsterDefinition definition = new GiantMonsterDefinition(id,
                GsonHelper.getAsString(json, KEY_DISPLAY_NAME, id));
        // 旧文件没有该字段：视为版本 1，交由配置管理器迁移
        definition.configVersion = GsonHelper.getAsInt(json, KEY_CONFIG_VERSION, 1);
        definition.maxHealth = GsonHelper.getAsDouble(json, KEY_MAX_HEALTH, definition.maxHealth);
        definition.attackDamage = GsonHelper.getAsDouble(json, KEY_ATTACK_DAMAGE, definition.attackDamage);
        definition.movementSpeed = GsonHelper.getAsDouble(json, KEY_MOVEMENT_SPEED, definition.movementSpeed);
        definition.toughness = GsonHelper.getAsDouble(json, KEY_TOUGHNESS, definition.toughness);
        definition.armor = GsonHelper.getAsDouble(json, KEY_ARMOR, definition.armor);
        definition.followRange = GsonHelper.getAsDouble(json, KEY_FOLLOW_RANGE, definition.followRange);
        definition.stamina = GsonHelper.getAsDouble(json, KEY_STAMINA, definition.stamina);

        readEntries(json, KEY_WEAK_POINTS, definition.weakPoints);
        readEntries(json, KEY_RESISTANCES, definition.resistances);
        if (json.has(KEY_SPAWN) && json.get(KEY_SPAWN).isJsonObject()) {
            definition.spawn = GiantMonsterSpawnConfig.fromJson(json.getAsJsonObject(KEY_SPAWN));
        }
        if (json.has(KEY_COMBAT) && json.get(KEY_COMBAT).isJsonObject()) {
            definition.combat = GiantMonsterCombatConfig.fromJson(json.getAsJsonObject(KEY_COMBAT));
        }
        if (json.has(KEY_PARTS) && json.get(KEY_PARTS).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(KEY_PARTS)) {
                if (element != null && element.isJsonObject()) {
                    definition.parts.add(GiantMonsterPartDefinition.fromJson(element.getAsJsonObject()));
                }
            }
        }
        if (json.has(KEY_CLIMB) && json.get(KEY_CLIMB).isJsonObject()) {
            definition.climb = GiantMonsterClimbConfig.fromJson(json.getAsJsonObject(KEY_CLIMB));
        }
        return definition.sanitize();
    }

    private static void readEntries(JsonObject json, String key, List<GiantMonsterEntry> target) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element != null && element.isJsonObject()) {
                target.add(GiantMonsterEntry.fromJson(element.getAsJsonObject()));
            }
        }
    }

    @Override
    public String toString() {
        return "GiantMonsterDefinition[" + id + " hp=" + maxHealth + " atk=" + attackDamage
                + " spd=" + movementSpeed + " tough=" + toughness + " armor=" + armor
                + " detect=" + followRange + " stamina=" + stamina
                + " weakPoints=" + weakPoints.size() + " resistances=" + resistances.size() + "]";
    }
}
