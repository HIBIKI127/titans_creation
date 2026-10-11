package com.example.titans_creation.giantmonster;

import java.util.Set;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 巨型怪物的伤害结算：把「词条」配置应用到实际伤害上。
 *
 * <p>规则（与配置文件中的两项属性对应）：
 * <ul>
 *   <li><b>弱点配置 weak_points</b>：命中的词条使伤害<b>提高</b>（倍率 &gt; 1）；</li>
 *   <li><b>抗性配置 resistances</b>：命中的词条使伤害<b>减少</b>（倍率 &lt; 1）。</li>
 * </ul>
 * 若一次攻击同时命中多个词条，则各倍率<b>相乘</b>；最终倍率会被限制在
 * {@link GiantMonster#MIN_DAMAGE_MULTIPLIER} ~ {@link GiantMonster#MAX_DAMAGE_MULTIPLIER} 之间，
 * 避免配置失误造成无敌或秒杀。
 *
 * <p>挂在 {@link LivingHurtEvent} 上（盔甲减伤之前），因此对玩家攻击、原版生物攻击、
 * 环境伤害以及本模组自身的战斗系统造成的伤害一律有效。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID)
public final class GiantMonsterDamageEvents {

    private GiantMonsterDamageEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof GiantMonster monster)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        ItemStack weapon = attacker instanceof LivingEntity living
                ? living.getMainHandItem()
                : ItemStack.EMPTY;

        Set<String> entries = GiantMonsterEntries.resolve(source, attacker, weapon);
        double multiplier = monster.getDamageMultiplierFor(entries);
        if (multiplier != 1.0D) {
            event.setAmount((float) Math.max(0.0D, event.getAmount() * multiplier));
        }
    }
}
