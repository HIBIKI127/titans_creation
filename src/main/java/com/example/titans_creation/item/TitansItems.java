package com.example.titans_creation.item;

import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.climb.ClimbAbility;
import com.example.titans_creation.climb.ClimbGearItem;
import com.example.titans_creation.grapple.GrappleCrossbowItem;
import com.example.titans_creation.item.accessory.AccessoryItemBase;
import com.example.titans_creation.item.accessory.AccessoryType;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品注册表（新的分类物品，旧的示例物品仍在主类中）。
 *
 * <p>物品按创造模式物品栏分类：功能方块 / 素材 / 战利品 / 武器 / 工具 / 图纸 / 饰品·装备。
 */
public final class TitansItems {

    /** 物品注册器。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TitansCreationMod.MODID);

    private TitansItems() {
    }

    // ======================== 功能方块 ========================

    /** 泰坦图腾：功能方块，用于在世界上标记/引导泰坦相关玩法。 */
    public static final RegistryObject<Item> TITAN_TOTEM = ITEMS.register("titan_totem",
            () -> new BlockItem(TitansBlocks.TITAN_TOTEM.get(), new Item.Properties()));

    // ======================== 素材 ========================

    /** 巨人肌腱：攀爬类物品的基础材料。 */
    public static final RegistryObject<Item> GIANT_TENDON = simple("giant_tendon", Rarity.COMMON);
    /** 泰坦合金锭：高级装备材料。 */
    public static final RegistryObject<Item> TITAN_ALLOY_INGOT = simple("titan_alloy_ingot", Rarity.COMMON);

    // ======================== 战利品 ========================

    /** 巨人核心：击败巨人后掉落的核心战利品。 */
    public static final RegistryObject<Item> GIANT_CORE = ITEMS.register("giant_core",
            () -> new Item(new Item.Properties().stacksTo(16).fireResistant().rarity(Rarity.RARE)));

    // ======================== 武器 ========================

    /** 巨人骨刃：以巨人骨骼打造的巨剑。 */
    public static final RegistryObject<Item> GIANT_BONE_BLADE = ITEMS.register("giant_bone_blade",
            () -> new SwordItem(Tiers.IRON, 4, -2.4F, new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ======================== 工具 ========================

    /** 攀爬镐：兼顾挖掘与攀爬的工具。 */
    public static final RegistryObject<Item> CLIMBING_PICKAXE = ITEMS.register("climbing_pickaxe",
            () -> new PickaxeItem(Tiers.IRON, 2, -2.8F, new Item.Properties()));

    // ======================== 图纸 ========================

    /** 巨人解剖图纸：记录巨人弱点与部位的手稿，用于解锁后续配方。 */
    public static final RegistryObject<Item> GIANT_ANATOMY_BLUEPRINT = simple("giant_anatomy_blueprint", Rarity.UNCOMMON);

    // ======================== 饰品 / 装备 ========================

    /** 皮革手套（手套槽）。 */
    public static final RegistryObject<Item> LEATHER_GLOVES = glove("leather_gloves", Rarity.COMMON);
    /** 铁质手套（手套槽）。 */
    public static final RegistryObject<Item> IRON_GLOVES = glove("iron_gloves", Rarity.COMMON);
    /** 防滑靴（鞋子槽）。 */
    public static final RegistryObject<Item> GRIP_BOOTS = boot("grip_boots", Rarity.COMMON);
    /** 缓冲靴（鞋子槽）。 */
    public static final RegistryObject<Item> CUSHION_BOOTS = boot("cushion_boots", Rarity.COMMON);
    /** 旅行背包（背部槽）。 */
    public static final RegistryObject<Item> TRAVEL_BACKPACK = back("travel_backpack", Rarity.UNCOMMON);

    /** 攀爬手套（手套槽）：紧贴方块或怪物、并按住朝其方向的移动键时向上攀爬，松开则缓慢下落。 */
    public static final RegistryObject<Item> CLIMBING_GLOVES = ITEMS.register("climbing_gloves",
            () -> new ClimbGearItem(AccessoryType.GLOVES, ClimbAbility.CLIMB, Rarity.UNCOMMON));

    /** 利爪之靴（鞋子槽）：紧贴方块或怪物时保持高度不下落（有滞留时长与冷却）。 */
    public static final RegistryObject<Item> CLAW_BOOTS = ITEMS.register("claw_boots",
            () -> new ClimbGearItem(AccessoryType.SHOES, ClimbAbility.GRIP, Rarity.UNCOMMON));

    /** 抓钩手弩（手套槽）：手持右键或按手套功能键发射抓钩，把玩家拉向命中点。 */
    public static final RegistryObject<Item> GRAPPLE_CROSSBOW = ITEMS.register("grapple_crossbow",
            () -> new GrappleCrossbowItem(0, Rarity.UNCOMMON));

    /** 抓钩手弩·铁质（铁质抓钩升级，拉动速度 +15%）。 */
    public static final RegistryObject<Item> GRAPPLE_CROSSBOW_IRON = ITEMS.register("grapple_crossbow_iron",
            () -> new GrappleCrossbowItem(1, Rarity.RARE));

    /** 抓钩手弩·钻石（钻石抓钩升级，拉动速度再 +15%）。 */
    public static final RegistryObject<Item> GRAPPLE_CROSSBOW_DIAMOND = ITEMS.register("grapple_crossbow_diamond",
            () -> new GrappleCrossbowItem(2, Rarity.EPIC));

    // ======================== 素材（抓钩零件） ========================

    /** 木制抓钩。 */
    public static final RegistryObject<Item> WOODEN_GRAPPLE_HOOK = simple("wooden_grapple_hook", Rarity.COMMON);
    /** 铁质抓钩。 */
    public static final RegistryObject<Item> IRON_GRAPPLE_HOOK = simple("iron_grapple_hook", Rarity.COMMON);
    /** 钻石抓钩。 */
    public static final RegistryObject<Item> DIAMOND_GRAPPLE_HOOK = simple("diamond_grapple_hook", Rarity.UNCOMMON);

    // ======================== 辅助 ========================

    private static RegistryObject<Item> simple(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    private static RegistryObject<Item> glove(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new AccessoryItemBase(AccessoryType.GLOVES, rarity));
    }

    private static RegistryObject<Item> boot(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new AccessoryItemBase(AccessoryType.SHOES, rarity));
    }

    private static RegistryObject<Item> back(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new AccessoryItemBase(AccessoryType.BACK, rarity));
    }
}
