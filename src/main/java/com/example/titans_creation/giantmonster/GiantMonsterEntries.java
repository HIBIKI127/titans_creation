package com.example.titans_creation.giantmonster;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 巨型怪物的词条体系。
 *
 * <p>「词条」是攻击与怪物配置之间的匹配关键字：攻击会携带一组词条，
 * 怪物的弱点配置 / 抗性配置按词条声明伤害倍率（见 {@link GiantMonsterEntry}）。
 *
 * <h3>内置词条与来源</h3>
 * <table border="1">
 *   <tr><th>词条</th><th>含义</th><th>攻击如何获得</th></tr>
 *   <tr><td>slash</td><td>斩击</td><td>手持剑 / 斧攻击</td></tr>
 *   <tr><td>blunt</td><td>打击</td><td>空手、手持锄或其他普通物品攻击</td></tr>
 *   <tr><td>pierce</td><td>穿刺</td><td>手持镐 / 三叉戟攻击</td></tr>
 *   <tr><td>projectile</td><td>弹射物</td><td>远程武器（弓/弩）或弹射物伤害类型</td></tr>
 *   <tr><td>fire</td><td>火焰</td><td>火焰伤害类型，或武器带火焰附加附魔</td></tr>
 *   <tr><td>explosion</td><td>爆炸</td><td>爆炸伤害类型</td></tr>
 *   <tr><td>magic</td><td>魔法</td><td>魔法伤害类型（含药水/女巫抗性标签）</td></tr>
 *   <tr><td>lightning</td><td>雷电</td><td>闪电伤害类型</td></tr>
 *   <tr><td>frost</td><td>冰冻</td><td>冰冻伤害类型</td></tr>
 *   <tr><td>fall</td><td>摔落</td><td>摔落伤害类型</td></tr>
 *   <tr><td>drown</td><td>溺水</td><td>溺水伤害类型</td></tr>
 *   <tr><td>wither</td><td>凋零</td><td>凋零伤害类型（含凋零之首）</td></tr>
 * </table>
 *
 * <p>除内置词条外，攻击还会携带<b>伤害类型 id</b> 与<b>伤害消息 id</b>作为词条，
 * 因此配置里可以直接写任意原版伤害类型 id（例如 {@code arrow}、{@code trident}、{@code sonic_boom}），
 * 无需修改代码即可扩展词条。
 */
public final class GiantMonsterEntries {

    // ==================== 内置词条 ====================

    /** 斩击：剑 / 斧。 */
    public static final String SLASH = "slash";
    /** 打击：空手、锄或其他普通物品。 */
    public static final String BLUNT = "blunt";
    /** 穿刺：镐 / 三叉戟。 */
    public static final String PIERCE = "pierce";
    /** 弹射物：远程武器或弹射物伤害类型。 */
    public static final String PROJECTILE = "projectile";
    /** 火焰。 */
    public static final String FIRE = "fire";
    /** 爆炸。 */
    public static final String EXPLOSION = "explosion";
    /** 魔法。 */
    public static final String MAGIC = "magic";
    /** 雷电。 */
    public static final String LIGHTNING = "lightning";
    /** 冰冻。 */
    public static final String FROST = "frost";
    /** 摔落。 */
    public static final String FALL = "fall";
    /** 溺水。 */
    public static final String DROWN = "drown";
    /** 凋零。 */
    public static final String WITHER = "wither";

    /** 全部内置词条（顺序即展示顺序）。 */
    private static final List<String> BUILT_IN = List.of(
            SLASH, BLUNT, PIERCE, PROJECTILE, FIRE, EXPLOSION,
            MAGIC, LIGHTNING, FROST, FALL, DROWN, WITHER);

    private static final String TRANSLATION_PREFIX = "titans_creation.entry.";

    private GiantMonsterEntries() {
    }

    /** 全部内置词条。 */
    public static List<String> builtIn() {
        return BUILT_IN;
    }

    /** 判断是否为内置词条。 */
    public static boolean isBuiltIn(String entry) {
        return entry != null && BUILT_IN.contains(entry.trim().toLowerCase(Locale.ROOT));
    }

    /** 词条的显示名称；自定义词条（无翻译）直接显示其 id。 */
    public static Component displayName(String entry) {
        String normalized = GiantMonsterEntry.normalize(entry);
        return Component.translatableWithFallback(TRANSLATION_PREFIX + normalized, normalized);
    }

    /** 词条的翻译键。 */
    public static String translationKey(String entry) {
        return TRANSLATION_PREFIX + GiantMonsterEntry.normalize(entry);
    }

    // ==================== 攻击 -> 词条 ====================

    /**
     * 解析一次攻击携带的词条集合。
     *
     * @param source   伤害来源
     * @param attacker 攻击者实体（可为 null）
     * @param weapon   攻击者手持物品（可为 null，视为空手）
     * @return 该次攻击的词条集合（小写）
     */
    public static Set<String> resolve(@Nullable DamageSource source,
                                      @Nullable Entity attacker,
                                      @Nullable ItemStack weapon) {
        Set<String> entries = new LinkedHashSet<>();
        if (source == null) {
            return entries;
        }

        // ① 伤害类型 -> 词条
        if (source.is(DamageTypeTags.IS_FIRE)) {
            entries.add(FIRE);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            entries.add(EXPLOSION);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            entries.add(PROJECTILE);
        }
        if (source.is(DamageTypes.FALL)) {
            entries.add(FALL);
        }
        if (source.is(DamageTypes.DROWN)) {
            entries.add(DROWN);
        }
        if (source.is(DamageTypes.FREEZE)) {
            entries.add(FROST);
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            entries.add(LIGHTNING);
        }
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
            entries.add(WITHER);
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            entries.add(MAGIC);
        }

        // ② 近战攻击 -> 按手持武器判定词条
        //    仅当直接伤害来源就是攻击者本人时才算近战；远程/投掷伤害交给伤害类型判定，
        //    避免玩家射箭后切换武器导致词条误判。
        boolean directAttack = source.getDirectEntity() == attacker;
        if (directAttack) {
            if (weapon != null && !weapon.isEmpty()) {
                Item item = weapon.getItem();
                if (item instanceof BowItem || item instanceof CrossbowItem) {
                    entries.add(PROJECTILE);
                } else if (item instanceof SwordItem || item instanceof AxeItem) {
                    entries.add(SLASH);
                } else if (item instanceof PickaxeItem || item instanceof TridentItem) {
                    entries.add(PIERCE);
                } else {
                    entries.add(BLUNT);
                }
                if (weapon.getEnchantmentLevel(Enchantments.FIRE_ASPECT) > 0) {
                    entries.add(FIRE);
                }
            } else if (attacker instanceof LivingEntity) {
                // 空手攻击
                entries.add(BLUNT);
            }
        }

        // ③ 伤害类型 id 与伤害消息 id 作为开放词条，便于用任意伤害类型 id 扩展词条
        source.typeHolder().unwrapKey()
                .map(key -> key.location().getPath())
                .ifPresent(path -> entries.add(GiantMonsterEntry.normalize(path)));
        entries.add(GiantMonsterEntry.normalize(source.getMsgId()));

        return entries;
    }
}
