package com.example.titans_creation.giantmonster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.giantmonster.part.GiantMonsterAnimator;
import com.example.titans_creation.giantmonster.part.GiantMonsterClimbConfig;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartDefinition;
import com.example.titans_creation.giantmonster.part.GiantMonsterPartType;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 巨型怪物注册表。
 *
 * <p>「巨型怪物」是模组特有的巨型生物类型：本类负责注册该类型的实体、
 * 对应的刷怪蛋、属性表，并把默认配置登记到 {@link GiantMonsterConfigManager}。
 *
 * <p><b>新增一只巨型怪物</b>只需在下面追加一行 {@link #registerMonster}：
 * 该怪物会自动获得
 * <ol>
 *   <li>实体类型 {@code titans_creation:<id>}（可被 {@code /summon titans_creation:<id>} 生成）；</li>
 *   <li>创造模式物品栏中的刷怪蛋 {@code <id>_spawn_egg}；</li>
 *   <li>属性表；</li>
 *   <li>配置文件 {@code config/titans_creation/monsters/<id>.json}（首次启动自动生成）。</li>
 * </ol>
 *
 * <p>巨型怪物使用 {@link MobCategory#MISC} 类别，不占用原版怪物刷怪配额，
 * 也不会被加入任何原版群系的刷怪表，因此不会影响原版生物的生成与行为。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GiantMonsterRegistry {

    /** 巨型怪物实体类型注册器。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TitansCreationMod.MODID);

    /** 刷怪蛋物品注册器（与主类的 ITEMS 分开，避免注册顺序耦合）。 */
    public static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TitansCreationMod.MODID);

    /** 实体类型 -> 属性表工厂，供 {@link EntityAttributeCreationEvent} 统一注册。 */
    private static final Map<RegistryObject<? extends EntityType<? extends LivingEntity>>,
            Supplier<AttributeSupplier.Builder>> ATTRIBUTE_FACTORIES = new LinkedHashMap<>();

    /** 已注册的刷怪蛋，供创造模式物品栏统一添加。 */
    private static final List<RegistryObject<Item>> SPAWN_EGG_ITEMS = new ArrayList<>();

    /** 怪物 id -> 实体类型，供生成机制按配置查找。 */
    private static final Map<String, RegistryObject<? extends EntityType<? extends Mob>>> MONSTERS =
            new LinkedHashMap<>();

    // ======================== 巨人（第一个测试用巨型怪物） ========================

    /**
     * 巨人（{@code titans_creation:giant_monster}）—— 第一个测试用巨型怪物。
     *
     * <p>外观直接复用<b>原版巨人资源</b>（{@code GiantZombieModel} + 僵尸贴图 + 6 倍缩放），
     * 碰撞箱与原版巨人一致为 3.6×10.8。
     */
    public static final RegistryObject<EntityType<GiantMonster>> GIANT_MONSTER = registerMonster(
            "giant_monster",
            GiantMonster::new,
            GiantMonster::createAttributes,
            createGiantDefinition(),
            3.0F, 12.0F,
            0x3B5B3B, 0x2E7D32);

    /**
     * 巨人（测试用）的默认配置。
     *
     * <p>数值面向「测试状态 / 生成 / 碰撞箱」三项机制：血量与伤害偏高便于观察战斗反馈，
     * 脱战动作间隔较短、生成条件较宽松，便于快速验证。
     */
    private static GiantMonsterDefinition createGiantDefinition() {
        // ---- 生成机制：测试用，较宽松 ----
        GiantMonsterSpawnConfig spawn = new GiantMonsterSpawnConfig();
        spawn.setEnabled(true);
        spawn.setCheckInterval(200);            // 每 10 秒检查一次
        spawn.setMaxPerWorld(2);
        spawn.setMaxPerPlayer(1);
        spawn.setCooldownTicks(2400);           // 冷却 2 分钟
        spawn.setMinDistanceToPlayer(24);
        spawn.setMaxDistanceToPlayer(80);
        spawn.setMinDistanceBetween(96);
        spawn.setTerrainEnabled(true);
        spawn.setClearanceX(5);
        spawn.setClearanceY(12);
        spawn.setClearanceZ(5);

        // ---- 仇恨机制：初始脱战、敌对、创造模式不仇恨 ----
        GiantMonsterCombatConfig combat = new GiantMonsterCombatConfig();
        combat.setInitialState(GiantMonsterState.OUT_OF_COMBAT);
        combat.setDisposition(GiantMonsterDisposition.HOSTILE);
        combat.setAggroInCreative(false);
        combat.setActionInterval(80);
        combat.setActionIdle(true);
        combat.setActionMove(true);
        combat.setActionRest(true);
        combat.setActionEnvironment(true);
        combat.setRestHealPerSecond(2.0D);
        combat.setEnvironmentTrample(true);
        combat.setLoseTargetTicks(200);
        combat.setDeathStayTicks(120);          // 死亡后停留 6 秒

        // ---- 攀爬与站立：45° 以内可站立，更陡只能攀爬 ----
        GiantMonsterClimbConfig climb = new GiantMonsterClimbConfig();
        climb.setEnabled(true);
        climb.setAllowStanding(true);
        climb.setAllowClimbing(true);
        climb.setMaxStandAngle(45.0D);
        climb.setContactDistance(1.6D);         // 巨人体型大，接触探测稍远
        climb.setGroundProbeDistance(0.8D);
        climb.setPushOutMargin(0.2D);
        climb.setClimbSpeed(0.16D);
        climb.setClimbOffset(0.5D);

        return new GiantMonsterDefinition("giant_monster", "巨人")
                // ===== 基础属性 =====
                .setMaxHealth(300.0D)
                .setAttackDamage(18.0D)
                .setMovementSpeed(0.28D)
                .setToughness(6.0D)
                .setArmor(8.0D)
                .setFollowRange(48.0D)
                .setStamina(150.0D)
                // ===== 弱点词条（伤害提高）=====
                .addWeakPoint(new GiantMonsterEntry(GiantMonsterEntries.SLASH, 1.5D, "head", true,
                        "斩击弱点：头部受到额外伤害（剑/斧）"))
                .addWeakPoint(new GiantMonsterEntry(GiantMonsterEntries.FIRE, 2.0D, "body", true,
                        "火焰弱点：火焰伤害大幅提高"))
                // ===== 抗性词条（伤害减少）=====
                .addResistance(new GiantMonsterEntry(GiantMonsterEntries.BLUNT, 0.6D, "body", true,
                        "打击抗性：钝击伤害降低"))
                .addResistance(new GiantMonsterEntry(GiantMonsterEntries.PROJECTILE, 0.8D, "body", true,
                        "弹射物抗性：远程伤害降低"))
                // ===== 部位模块（数值为原版巨人模型的精确几何，单位：格）=====
                // 原版巨人 = HumanoidModel + 6 倍缩放，模型顶点换算关系：
                //   世界 Y = 6 * (1.501 - 模型Y/16)，世界 X = -6 * 模型X/16，世界 Z = 6 * 模型Z/16
                // 头部：模型 cube x∈[-4,4] y∈[-8,0] z∈[-4,4]（单位 1/16 格）→ 世界 y 9.006~12.006
                .addPart(new GiantMonsterPartDefinition("head")
                        .bone(GiantMonsterAnimator.BONE_HEAD)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(3.0D, 3.0D, 3.0D)
                        .offset(0.0D, 10.506D, 0.0D)
                        .pivot(0.0D, 9.006D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("头部（模型 3×3×3，顶面 y=12.006）：顶面为水平面可站立，全身可攀爬"))
                // 头部外层帽体：模型 CubeDeformation 0.5 → 3.375³，纯装饰
                .addPart(new GiantMonsterPartDefinition("head_layer")
                        .bone(GiantMonsterAnimator.BONE_HEAD)
                        .type(GiantMonsterPartType.DECORATIVE)
                        .size(3.375D, 3.375D, 3.375D)
                        .offset(0.0D, 10.506D, 0.0D)
                        .pivot(0.0D, 9.006D, 0.0D)
                        .climbable(true)
                        .description("头部外层帽体（模型 3.375³）：无碰撞体积、不可交互（装饰）"))
                // 躯干：模型 cube x∈[-4,4] y∈[0,12] z∈[-2,2] → 世界 y 4.506~9.006
                .addPart(new GiantMonsterPartDefinition("body")
                        .bone(GiantMonsterAnimator.BONE_BODY)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(3.0D, 4.5D, 1.5D)
                        .offset(0.0D, 6.756D, 0.0D)
                        .pivot(0.0D, 9.006D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("躯干（模型 3×4.5×1.5，y 4.506~9.006）：竖直侧面需要攀爬"))
                // 双臂：模型臂 cube 4×12×4 单位，肩关节 y=2 单位 → 世界 (±1.875, 8.256, 0)
                .addPart(new GiantMonsterPartDefinition("arm_left")
                        .bone(GiantMonsterAnimator.BONE_ARM_LEFT)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(1.5D, 4.5D, 1.5D)
                        .offset(-2.25D, 6.756D, 0.0D)
                        .pivot(-1.875D, 8.256D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("左臂（模型 1.5×4.5×1.5，绕肩 (±1.875, 8.256) 摆动）：顶面可站立、可攀爬"))
                .addPart(new GiantMonsterPartDefinition("arm_right")
                        .bone(GiantMonsterAnimator.BONE_ARM_RIGHT)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(1.5D, 4.5D, 1.5D)
                        .offset(2.25D, 6.756D, 0.0D)
                        .pivot(1.875D, 8.256D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("右臂（模型 1.5×4.5×1.5，绕肩 (±1.875, 8.256) 摆动）：顶面可站立、可攀爬"))
                // 双腿：模型腿 cube 4×12×4 单位，髋关节 (±1.9, 12, 0) → 世界 (±0.7125, 4.506, 0)
                .addPart(new GiantMonsterPartDefinition("leg_left")
                        .bone(GiantMonsterAnimator.BONE_LEG_LEFT)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(1.5D, 4.5D, 1.5D)
                        .offset(-0.7125D, 2.256D, 0.0D)
                        .pivot(-0.7125D, 4.506D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("左腿（模型 1.5×4.5×1.5，绕髋 (±0.7125, 4.506) 摆动）：可攀爬"))
                .addPart(new GiantMonsterPartDefinition("leg_right")
                        .bone(GiantMonsterAnimator.BONE_LEG_RIGHT)
                        .type(GiantMonsterPartType.SOLID_INTERACTIVE)
                        .size(1.5D, 4.5D, 1.5D)
                        .offset(0.7125D, 2.256D, 0.0D)
                        .pivot(0.7125D, 4.506D, 0.0D)
                        .climbable(true)
                        .interactionMargin(0.1D)
                        .description("右腿（模型 1.5×4.5×1.5，绕髋 (±0.7125, 4.506) 摆动）：可攀爬"))
                // 弱点：紧贴模型背部表面（躯干 z=-0.75），无碰撞体积、可交互
                .addPart(new GiantMonsterPartDefinition("core")
                        .bone(GiantMonsterAnimator.BONE_BODY)
                        .type(GiantMonsterPartType.INTERACTIVE_NO_COLLISION)
                        .size(1.4D, 1.4D, 0.2D)
                        .offset(0.0D, 6.756D, -0.85D)
                        .pivot(0.0D, 9.006D, 0.0D)
                        .climbable(true)
                        .description("背部核心（弱点）：紧贴背部表面，无碰撞体积、可交互"))
                .setSpawn(spawn)
                .setCombat(combat)
                .setClimb(climb);
    }

    // ======================== 注册辅助 ========================

    /**
     * 注册一只巨型怪物：实体类型 + 刷怪蛋 + 属性表 + 默认配置。
     *
     * @param id              怪物 id（同时作为实体注册名与配置文件文件名）
     * @param factory         实体工厂（通常为 {@code GiantMonster::new} 或子类构造器）
     * @param attributes      属性表工厂
     * @param definition      默认配置（首次启动据此生成配置文件）
     * @param width           碰撞箱宽度（格）
     * @param height          碰撞箱高度（格）
     * @param eggBackground   刷怪蛋底色
     * @param eggHighlight    刷怪蛋斑点色
     */
    private static <T extends Mob> RegistryObject<EntityType<T>> registerMonster(
            String id,
            EntityType.EntityFactory<T> factory,
            Supplier<AttributeSupplier.Builder> attributes,
            GiantMonsterDefinition definition,
            float width,
            float height,
            int eggBackground,
            int eggHighlight) {

        RegistryObject<EntityType<T>> type = ENTITY_TYPES.register(id, () -> EntityType.Builder
                .of(factory, MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build(id));

        RegistryObject<Item> spawnEgg = SPAWN_EGGS.register(id + "_spawn_egg",
                () -> new ForgeSpawnEggItem(type, eggBackground, eggHighlight, new Item.Properties()));
        SPAWN_EGG_ITEMS.add(spawnEgg);

        ATTRIBUTE_FACTORIES.put(type, attributes);
        MONSTERS.put(id, type);
        GiantMonsterConfigManager.registerDefaults(id, definition);
        return type;
    }

    /** 已注册的全部刷怪蛋（创造模式物品栏使用）。 */
    public static List<RegistryObject<Item>> getSpawnEggs() {
        return List.copyOf(SPAWN_EGG_ITEMS);
    }

    /** 按怪物 id 获取实体类型；未注册时返回 null。 */
    public static EntityType<? extends Mob> getEntityType(String id) {
        RegistryObject<? extends EntityType<? extends Mob>> reference = MONSTERS.get(id);
        return reference == null ? null : reference.get();
    }

    /** 已注册的怪物 id 列表。 */
    public static List<String> monsterIds() {
        return List.copyOf(MONSTERS.keySet());
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        ATTRIBUTE_FACTORIES.forEach((type, factory) -> event.put(type.get(), factory.get().build()));
    }
}
