package com.example.titans_creation.giantmonster;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * 巨型怪物的「词条」配置条目。
 *
 * <p>弱点配置（{@code weak_points}）与抗性配置（{@code resistances}）使用同一种条目结构：
 * 每条配置声明一个<b>词条</b>与对应的伤害倍率。
 *
 * <ul>
 *   <li>弱点配置：词条匹配时<b>提高</b>伤害（{@code damage_multiplier} &gt; 1）；</li>
 *   <li>抗性配置：词条匹配时<b>减少</b>伤害（{@code damage_multiplier} &lt; 1）。</li>
 * </ul>
 *
 * <p>词条取自攻击本身：由伤害类型与攻击者手持武器共同推导（见 {@link GiantMonsterEntries#resolve}），
 * 例如用剑/斧攻击得到 {@code slash}，用镐攻击得到 {@code pierce}，火焰伤害得到 {@code fire}。
 * 也可以直接使用任意原版伤害类型 id（如 {@code arrow}）作为自定义词条。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code entry}              词条（如 slash / fire）</li>
 *   <li>{@code damage_multiplier}  伤害倍率</li>
 *   <li>{@code part}               预留给后续部位机制：限定生效部位（当前版本仅存储，不参与结算）</li>
 *   <li>{@code exposed}            预留给后续弱点暴露机制（当前版本仅存储，不参与结算）</li>
 *   <li>{@code description}        说明文本，仅用于阅读配置</li>
 * </ul>
 */
public final class GiantMonsterEntry {

    public static final String KEY_ENTRY = "entry";
    public static final String KEY_DAMAGE_MULTIPLIER = "damage_multiplier";
    public static final String KEY_PART = "part";
    public static final String KEY_EXPOSED = "exposed";
    public static final String KEY_DESCRIPTION = "description";
    /** 旧版本字段名（弱点曾使用 id），读取时作为 entry 的兜底。 */
    private static final String KEY_LEGACY_ID = "id";

    /** 未指定部位时的占位值。 */
    public static final String PART_ANY = "any";

    private final String entry;
    private final double damageMultiplier;
    private final String part;
    private final boolean exposed;
    private final String description;

    public GiantMonsterEntry(String entry, double damageMultiplier, String part, boolean exposed, String description) {
        this.entry = normalize(entry);
        this.damageMultiplier = damageMultiplier;
        this.part = part == null || part.isBlank() ? PART_ANY : part.trim();
        this.exposed = exposed;
        this.description = description == null ? "" : description;
    }

    /** 词条统一转为小写去空格，避免配置书写大小写不一致导致匹配失败。 */
    public static String normalize(String entry) {
        return entry == null || entry.isBlank() ? "unknown" : entry.trim().toLowerCase(Locale.ROOT);
    }

    public String getEntry() {
        return entry;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public String getPart() {
        return part;
    }

    public boolean isExposed() {
        return exposed;
    }

    public String getDescription() {
        return description;
    }

    /** 该条目是否为攻击的词条集合所命中。 */
    public boolean matches(Collection<String> attackEntries) {
        return attackEntries != null && attackEntries.contains(entry);
    }

    public GiantMonsterEntry withDamageMultiplier(double value) {
        return new GiantMonsterEntry(entry, value, part, exposed, description);
    }

    public GiantMonsterEntry copy() {
        return new GiantMonsterEntry(entry, damageMultiplier, part, exposed, description);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ENTRY, entry);
        json.addProperty(KEY_DAMAGE_MULTIPLIER, damageMultiplier);
        json.addProperty(KEY_PART, part);
        json.addProperty(KEY_EXPOSED, exposed);
        json.addProperty(KEY_DESCRIPTION, description);
        return json;
    }

    /**
     * 从配置 JSON 反序列化。
     * 若缺少 {@code entry}，则回退读取旧字段 {@code id}，保证旧配置文件仍可加载。
     */
    public static GiantMonsterEntry fromJson(JsonObject json) {
        String entry = GsonHelper.getAsString(json, KEY_ENTRY, null);
        if (entry == null) {
            entry = GsonHelper.getAsString(json, KEY_LEGACY_ID, "unknown");
        }
        return new GiantMonsterEntry(
                entry,
                GsonHelper.getAsDouble(json, KEY_DAMAGE_MULTIPLIER, 1.0D),
                GsonHelper.getAsString(json, KEY_PART, PART_ANY),
                GsonHelper.getAsBoolean(json, KEY_EXPOSED, true),
                GsonHelper.getAsString(json, KEY_DESCRIPTION, ""));
    }

    @Override
    public String toString() {
        return entry + " x" + damageMultiplier;
    }
}
