package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.example.titans_creation.giantmonster.part.GiantMonsterAnimator;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbConfig;
import com.example.titans_creation.giantmonster.part.GiantMonsterParts;

/**
 * 巨型怪物（模组特有的巨型生物类型）。
 *
 * <p>属性数值全部来自 {@link GiantMonsterDefinition}（配置文件），
 * 行为由 {@link GiantMonsterState} 状态机与 {@link GiantMonsterDisposition} 友好程度共同驱动。
 *
 * <h3>状态机</h3>
 * <ul>
 *   <li><b>NONE 无状态（测试用）</b>：不装配任何 AI 目标，不执行任何动作，保持默认姿态；
 *       玩家靠近、攻击等交互都不会改变其行动模式。</li>
 *   <li><b>OUT_OF_COMBAT 脱战</b>：按配置随机执行待机 / 移动 / 休息 / 对环境造成影响。</li>
 *   <li><b>IN_COMBAT 进战</b>：追击并攻击目标；长时间没有有效目标则回到脱战。</li>
 *   <li><b>DEATH 死亡</b>：血量为空后以死亡姿态停留在原地，{@code combat.death.stay_ticks} 后消失。</li>
 * </ul>
 *
 * <h3>友好程度</h3>
 * <ul>
 *   <li>友好：玩家进入侦测范围、攻击怪物都不进战；</li>
 *   <li>中立：进入范围不进战，被攻击才进战；</li>
 *   <li>敌对：玩家进入侦测范围立刻进战。</li>
 * </ul>
 * 创造模式玩家是否会被仇恨由 {@code combat.aggro_in_creative} 单独控制。
 */
public class GiantMonster extends Monster {

    private static final String NBT_STAMINA = "Stamina";
    private static final String NBT_STATE = "GiantMonsterState";
    private static final String NBT_ACTION = "GiantMonsterAction";
    private static final String NBT_ACTION_TICKS = "GiantMonsterActionTicks";

    /** 伤害倍率下限（防止配置失误导致几乎免疫）。 */
    public static final double MIN_DAMAGE_MULTIPLIER = 0.02D;
    /** 伤害倍率上限（防止配置失误导致被秒杀）。 */
    public static final double MAX_DAMAGE_MULTIPLIER = 20.0D;

    /** 同步到客户端的状态（供 HUD / 调试显示）。 */
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(GiantMonster.class, EntityDataSerializers.INT);

    /** 当前耐力（基础耐力上限由配置决定）。 */
    private double stamina;
    /** 基础耐力上限（来自配置）。 */
    private double maxStamina = 100.0D;

    /** 配置定义缓存（配置重载时由 {@link GiantMonsterConfigManager} 清除）。 */
    @Nullable
    private GiantMonsterDefinition definitionCache;
    /** 部位碰撞箱集合（按定义懒加载，配置变更时重建）。 */
    @Nullable
    private GiantMonsterParts partsCache;

    // ==================== 脱战行为运行时状态 ====================
    private GiantMonsterAction currentAction = GiantMonsterAction.IDLE;
    private int actionTicks;
    private int restHealTicks;
    /** 进战状态下失去有效目标的累计 tick。 */
    private int loseTargetTicks;

    public GiantMonster(EntityType<? extends GiantMonster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        // 实体构造时套用配置：所有生成方式（刷怪蛋 / 指令 / 生成器）都会经过这里
        GiantMonsterConfigManager.applyTo(this, true);
        this.stamina = this.maxStamina;
        // 初始状态模式来自配置（读档时会被 NBT 覆盖）
        GiantMonsterState initialState = this.combatConfig().getInitialState();
        this.entityData.set(DATA_STATE, initialState.ordinal());
        this.actionTicks = this.combatConfig().getActionInterval();
        this.applyGoalsForState();
    }

    /**
     * 注册用的基础属性。这里的数值只是「出厂默认值」，
     * 实体会在构造时用配置文件中的数值覆盖它们。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    /**
     * 目标集合完全由 {@link #applyGoalsForState()} 按状态装配，
     * 因此这里保持为空（该方法会在 {@link Mob} 构造期被调用，早于本类字段初始化）。
     */
    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, GiantMonsterState.OUT_OF_COMBAT.ordinal());
    }

    // ==================== 配置访问 ====================

    /** 当前生效的配置定义（未注册配置时返回 null）。 */
    @Nullable
    public GiantMonsterDefinition getDefinition() {
        if (this.definitionCache == null) {
            this.definitionCache = GiantMonsterConfigManager.get(this.getType());
        }
        return this.definitionCache;
    }

    /** 配置重载时清除定义缓存。 */
    public void invalidateDefinitionCache() {
        this.definitionCache = null;
        this.partsCache = null;
    }

    /** 部位碰撞箱集合（由配置中的 {@code parts} 定义构建，懒加载）。
     *  注意：方法名不能叫 getParts()，那会与 Forge 的 IForgeEntity#getParts()（多部件实体 API）冲突。 */
    public GiantMonsterParts getPartBoxes() {
        if (this.partsCache == null) {
            GiantMonsterDefinition definition = this.getDefinition();
            this.partsCache = GiantMonsterParts.of(definition == null ? List.of() : definition.getParts());
        }
        return this.partsCache;
    }

    /** 攀爬与站立配置。 */
    public GiantMonsterClimbConfig getClimbConfig() {
        GiantMonsterDefinition definition = this.getDefinition();
        return definition == null ? new GiantMonsterClimbConfig() : definition.getClimb();
    }

    /** 当前脱战动作（供动画计算骨骼姿态）。 */
    public GiantMonsterAction getCurrentAction() {
        return this.currentAction;
    }

    /** 仇恨机制配置（无配置时返回默认值）。 */
    public GiantMonsterCombatConfig combatConfig() {
        GiantMonsterDefinition definition = this.getDefinition();
        return definition == null ? new GiantMonsterCombatConfig() : definition.getCombat();
    }

    /** 该怪物的弱点配置（来自配置文件）：命中词条使伤害提高。 */
    public List<GiantMonsterEntry> getWeakPoints() {
        GiantMonsterDefinition definition = this.getDefinition();
        return definition == null ? Collections.emptyList() : definition.getWeakPoints();
    }

    /** 该怪物的抗性配置（来自配置文件）：命中词条使伤害减少。 */
    public List<GiantMonsterEntry> getResistances() {
        GiantMonsterDefinition definition = this.getDefinition();
        return definition == null ? Collections.emptyList() : definition.getResistances();
    }

    /** 按词条查找弱点配置，未找到返回 null。 */
    public GiantMonsterEntry getWeakPointEntry(String entry) {
        return findEntry(this.getWeakPoints(), entry);
    }

    /** 按词条查找抗性配置，未找到返回 null。 */
    public GiantMonsterEntry getResistanceEntry(String entry) {
        return findEntry(this.getResistances(), entry);
    }

    private static GiantMonsterEntry findEntry(List<GiantMonsterEntry> entries, String entry) {
        if (entry == null) {
            return null;
        }
        String normalized = GiantMonsterEntry.normalize(entry);
        for (GiantMonsterEntry candidate : entries) {
            if (candidate.getEntry().equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 计算某次攻击对本怪物的最终伤害倍率。
     *
     * <p>规则：弱点配置中命中的词条倍率与抗性配置中命中的词条倍率<b>相乘</b>，
     * 最终限制在 {@link #MIN_DAMAGE_MULTIPLIER} ~ {@link #MAX_DAMAGE_MULTIPLIER} 之间。
     */
    public double getDamageMultiplierFor(Collection<String> attackEntries) {
        if (attackEntries == null || attackEntries.isEmpty()) {
            return 1.0D;
        }
        GiantMonsterDefinition definition = this.getDefinition();
        if (definition == null) {
            return 1.0D;
        }
        double multiplier = 1.0D;
        for (GiantMonsterEntry weakPoint : definition.getWeakPoints()) {
            if (weakPoint.matches(attackEntries)) {
                multiplier *= weakPoint.getDamageMultiplier();
            }
        }
        for (GiantMonsterEntry resistance : definition.getResistances()) {
            if (resistance.matches(attackEntries)) {
                multiplier *= resistance.getDamageMultiplier();
            }
        }
        return Mth.clamp(multiplier, MIN_DAMAGE_MULTIPLIER, MAX_DAMAGE_MULTIPLIER);
    }

    // ==================== 状态机 ====================

    /** 当前状态模式（两端可用：客户端读取同步值）。 */
    public GiantMonsterState getState() {
        return GiantMonsterState.byOrdinal(this.entityData.get(DATA_STATE));
    }

    /** 切换状态模式（服务端权威；会同步到客户端并重新装配 AI 目标）。 */
    public void setState(GiantMonsterState state) {
        if (this.getState() == state) {
            return;
        }
        this.entityData.set(DATA_STATE, state.ordinal());
        this.onStateChanged(state);
    }

    private void onStateChanged(GiantMonsterState state) {
        GiantMonsterCombatConfig config = this.combatConfig();
        switch (state) {
            case NONE, DEATH -> {
                this.setTarget(null);
                this.currentAction = GiantMonsterAction.IDLE;
            }
            case OUT_OF_COMBAT -> {
                this.setTarget(null);
                this.currentAction = GiantMonsterAction.IDLE;
                this.actionTicks = config.getActionInterval();
                this.restHealTicks = 0;
                this.loseTargetTicks = 0;
            }
            case IN_COMBAT -> this.loseTargetTicks = 0;
        }
        this.applyGoalsForState();
    }

    /** 按当前状态装配 AI 目标：无状态与死亡状态不装配任何目标，因此完全不动作。 */
    private void applyGoalsForState() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);

        switch (this.getState()) {
            case IN_COMBAT -> {
                this.goalSelector.addGoal(0, new FloatGoal(this));
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
                this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
            }
            case OUT_OF_COMBAT -> {
                this.goalSelector.addGoal(0, new FloatGoal(this));
                if (this.currentAction == GiantMonsterAction.MOVE && this.combatConfig().isActionMove()) {
                    this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
                }
                this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
                this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
            }
            // NONE（测试用）与 DEATH：不添加任何目标
            default -> {
            }
        }
    }

    /** 进入战斗状态并锁定目标。 */
    public void enterCombat(@Nullable LivingEntity target) {
        GiantMonsterState state = this.getState();
        if (state == GiantMonsterState.DEATH || state == GiantMonsterState.NONE) {
            return;
        }
        this.setState(GiantMonsterState.IN_COMBAT);
        this.setTarget(target);
        this.loseTargetTicks = 0;
    }

    /** 退出战斗，回到脱战状态。 */
    public void exitCombat() {
        GiantMonsterState state = this.getState();
        if (state == GiantMonsterState.DEATH || state == GiantMonsterState.NONE) {
            return;
        }
        this.setState(GiantMonsterState.OUT_OF_COMBAT);
    }

    @Override
    public void tick() {
        super.tick();
        // 每 tick 用动画姿态重算部位碰撞箱：两端都需要（服务端用于碰撞/交互判定，客户端用于本地预测与调试渲染）
        this.getPartBoxes().applyPose(this, GiantMonsterAnimator.compute(this, 1.0F));
        if (this.level().isClientSide) {
            return;
        }
        switch (this.getState()) {
            case NONE -> this.tickNone();
            case OUT_OF_COMBAT -> this.tickOutOfCombat();
            case IN_COMBAT -> this.tickInCombat();
            case DEATH -> this.tickDeathState();
        }
    }

    /** 无状态：不动作、不反应（测试用）。 */
    private void tickNone() {
        if (this.getTarget() != null) {
            this.setTarget(null);
        }
    }

    /** 脱战：敌对则侦测玩家，否则随机执行配置的动作。 */
    private void tickOutOfCombat() {
        GiantMonsterCombatConfig config = this.combatConfig();

        if (config.getDisposition().aggroOnSight()) {
            Player player = this.findDetectablePlayer(config);
            if (player != null) {
                this.enterCombat(player);
                return;
            }
        }

        if (--this.actionTicks <= 0) {
            this.actionTicks = config.getActionInterval();
            GiantMonsterAction action = this.pickRandomAction(config);
            boolean changed = action != this.currentAction;
            this.currentAction = action;
            if (changed) {
                this.applyGoalsForState();
            }
            if (action == GiantMonsterAction.ENVIRONMENT) {
                this.performEnvironmentEffect(config);
            }
        }

        if (this.currentAction == GiantMonsterAction.REST && config.getRestHealPerSecond() > 0.0D) {
            if (++this.restHealTicks >= 20) {
                this.restHealTicks = 0;
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal((float) config.getRestHealPerSecond());
                }
            }
        } else {
            this.restHealTicks = 0;
        }
    }

    /** 进战：目标失效则寻找下一个，长时间找不到就脱战。 */
    private void tickInCombat() {
        GiantMonsterCombatConfig config = this.combatConfig();
        LivingEntity target = this.getTarget();

        if (this.isValidAggroTarget(config, target)) {
            this.loseTargetTicks = 0;
            return;
        }
        Player replacement = this.findDetectablePlayer(config);
        if (replacement != null) {
            this.setTarget(replacement);
            this.loseTargetTicks = 0;
            return;
        }
        if (++this.loseTargetTicks >= config.getLoseTargetTicks()) {
            this.exitCombat();
        }
    }

    /** 死亡：停在原地等待消失（消失时机由 {@link #tickDeath()} 负责）。 */
    private void tickDeathState() {
        if (this.getTarget() != null) {
            this.setTarget(null);
        }
        if (this.getDeltaMovement().lengthSqr() > 1.0E-4D) {
            this.setDeltaMovement(Vec3.ZERO);
            this.hurtMarked = true;
        }
    }

    /** 从已启用的动作中随机挑选一个。 */
    private GiantMonsterAction pickRandomAction(GiantMonsterCombatConfig config) {
        List<GiantMonsterAction> candidates = new ArrayList<>(4);
        if (config.isActionIdle()) {
            candidates.add(GiantMonsterAction.IDLE);
        }
        if (config.isActionMove()) {
            candidates.add(GiantMonsterAction.MOVE);
        }
        if (config.isActionRest()) {
            candidates.add(GiantMonsterAction.REST);
        }
        if (config.isActionEnvironment()) {
            candidates.add(GiantMonsterAction.ENVIRONMENT);
        }
        if (candidates.isEmpty()) {
            return GiantMonsterAction.IDLE;
        }
        return candidates.get(this.random.nextInt(candidates.size()));
    }

    /** 对环境造成影响：脚步震动 + 尘土粒子（可选踩踏脆弱方块）。 */
    private void performEnvironmentEffect(GiantMonsterCombatConfig config) {
        Level level = this.level();
        BlockPos pos = this.blockPosition();
        level.playSound(null, pos, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE,
                3.0F, 0.5F + this.random.nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 0.2D, this.getZ(),
                    24, this.getBbWidth() * 0.4D, 0.1D, this.getBbWidth() * 0.4D, 0.02D);
        }
        if (!config.isEnvironmentTrample()) {
            return;
        }
        int radius = Mth.clamp((int) (this.getBbWidth() / 2.0F), 1, 4);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (isTrampleable(state)) {
                        level.destroyBlock(cursor.immutable(), false);
                    }
                }
            }
        }
    }

    private static boolean isTrampleable(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS);
    }

    // ==================== 仇恨判定 ====================

    /** 该玩家是否可被本怪物仇恨（考虑创造模式开关与观察者模式）。 */
    public boolean canAggro(GiantMonsterCombatConfig config, @Nullable Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (player.isCreative() && !config.isAggroInCreative()) {
            return false;
        }
        return true;
    }

    /** 在基础侦测范围（follow_range 属性）内寻找最近的可仇恨玩家。 */
    @Nullable
    private Player findDetectablePlayer(GiantMonsterCombatConfig config) {
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (range <= 0.0D) {
            return null;
        }
        double bestDistance = range * range;
        Player best = null;
        for (Player player : this.level().players()) {
            if (!this.canAggro(config, player)) {
                continue;
            }
            double distance = this.distanceToSqr(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /** 进战状态下目标是否仍然有效（存活、未脱离过远、且仍允许仇恨）。 */
    private boolean isValidAggroTarget(GiantMonsterCombatConfig config, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && !this.canAggro(config, player)) {
            return false;
        }
        double range = Math.max(32.0D, this.getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5D);
        return this.distanceToSqr(target) <= range * range;
    }

    // ==================== 受伤 / 死亡 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || this.level().isClientSide) {
            return hurt;
        }
        GiantMonsterState state = this.getState();
        if (state == GiantMonsterState.NONE || state == GiantMonsterState.DEATH) {
            // 无状态模式：任何交互都不会改变其行动模式
            return hurt;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return hurt;
        }
        GiantMonsterCombatConfig config = this.combatConfig();
        if (!config.getDisposition().retaliatesWhenAttacked() || !this.canAggro(config, player)) {
            return hurt;
        }
        if (state == GiantMonsterState.OUT_OF_COMBAT) {
            this.enterCombat(player);
        } else if (state == GiantMonsterState.IN_COMBAT && this.getTarget() == null) {
            this.setTarget(player);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide) {
            this.setState(GiantMonsterState.DEATH);
        }
    }

    /**
     * 死亡计时：原版会在 20 tick 后移除实体，这里改为按配置的
     * {@code combat.death.stay_ticks} 停留后再消失。
     */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!this.level().isClientSide && this.deathTime >= this.combatConfig().getDeathStayTicks()) {
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ==================== 耐力 ====================

    /** 基础耐力上限（来自配置的 stamina 字段）。 */
    public double getMaxStamina() {
        return this.maxStamina;
    }

    /** 设置耐力上限；当前耐力超过新上限时会被收敛。 */
    public void setMaxStamina(double value) {
        this.maxStamina = Math.max(0.0D, value);
        if (this.stamina > this.maxStamina) {
            this.stamina = this.maxStamina;
        }
    }

    /** 当前耐力。 */
    public double getStamina() {
        return this.stamina;
    }

    public void setStamina(double value) {
        this.stamina = Mth.clamp(value, 0.0D, this.maxStamina);
    }

    /** 当前耐力比例（0~1）。 */
    public double getStaminaRatio() {
        return this.maxStamina <= 0.0D ? 0.0D : this.stamina / this.maxStamina;
    }

    /** 消耗耐力；耐力不足时不消耗并返回 false。 */
    public boolean consumeStamina(double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (this.stamina < amount) {
            return false;
        }
        this.stamina -= amount;
        return true;
    }

    /** 恢复耐力。 */
    public void restoreStamina(double amount) {
        if (amount > 0.0D) {
            this.setStamina(this.stamina + amount);
        }
    }

    // ==================== 巨体表现 ====================

    /** 巨型怪物不会被推挤（体积巨大，避免被玩家/生物推动）。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 巨型怪物不会因玩家远离而消失。 */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    /** 死亡姿态：与原版一致使用 DYING 姿态。 */
    @Override
    public Pose getPose() {
        return this.isDeadOrDying() ? Pose.DYING : super.getPose();
    }

    // ==================== 存档 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(NBT_STAMINA, this.stamina);
        tag.putString(NBT_STATE, this.getState().id());
        tag.putString(NBT_ACTION, this.currentAction.id());
        tag.putInt(NBT_ACTION_TICKS, this.actionTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // 先套用配置属性，再执行原版读取，避免存量血量被旧的血量上限截断
        GiantMonsterConfigManager.applyTo(this, false);
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_STAMINA)) {
            this.setStamina(tag.getDouble(NBT_STAMINA));
        } else {
            this.stamina = this.maxStamina;
        }
        GiantMonsterCombatConfig config = this.combatConfig();
        this.currentAction = GiantMonsterAction.parse(tag.getString(NBT_ACTION), GiantMonsterAction.IDLE);
        this.actionTicks = tag.contains(NBT_ACTION_TICKS)
                ? tag.getInt(NBT_ACTION_TICKS)
                : config.getActionInterval();
        GiantMonsterState state = GiantMonsterState.parse(tag.getString(NBT_STATE), config.getInitialState());
        // 直接写入同步数据并装配目标（此时不需要"变化"判定）
        this.entityData.set(DATA_STATE, state.ordinal());
        this.applyGoalsForState();
    }

    @Override
    public String toString() {
        return super.toString() + "[state=" + this.getState().id()
                + ", action=" + this.currentAction.id()
                + ", stamina=" + this.stamina + "/" + this.maxStamina + "]";
    }

    /** 便于外部判断类型（例如后续的部位/攀附机制）。 */
    public static boolean isGiantMonster(Entity entity) {
        return entity instanceof GiantMonster;
    }

    /** 便捷方法：判断某个实体类型是否为已注册的巨型怪物。 */
    public static boolean isGiantMonsterType(EntityType<?> type) {
        return GiantMonsterConfigManager.get(type) != null;
    }
}
