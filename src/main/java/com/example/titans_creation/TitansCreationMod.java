package com.example.titans_creation;

import org.slf4j.Logger;

import com.example.titans_creation.client.GiantMonsterClientSetup;
import com.example.titans_creation.crafting.TitansRecipes;
import com.example.titans_creation.giantmonster.GiantMonsterConfigManager;
import com.example.titans_creation.giantmonster.GiantMonsterRegistry;
import com.example.titans_creation.item.TitansBlocks;
import com.example.titans_creation.item.TitansCreativeTabs;
import com.example.titans_creation.item.TitansItems;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TitansCreationMod.MODID)
public class TitansCreationMod {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "titans_creation";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "titans_creation" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "titans_creation" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "titans_creation" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "titans_creation:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "titans_creation:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> MYGO_RANA_BUFFETT = ITEMS.register("mygo_rana_buffett", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MYGO_RANA_GUITAR = ITEMS.register("mygo_rana_guitar", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MYGO_ANON_GLASSES = ITEMS.register("mygo_anon_glasses", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MYGO_ANON_GUITAR = ITEMS.register("mygo_anon_guitar", () -> new SwordItem(Tiers.NETHERITE, 100, -2.4f, new Item.Properties().fireResistant().setNoRepair().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MYGO_ANON_GUITARPACK = ITEMS.register("mygo_anon_guitarpack", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MUJICA_MUTSUMI_CUCUMBER = ITEMS.register("mujica_mutsumi_cucumber", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MUJICA_MUTSUMI_GUITAR = ITEMS.register("mujica_mutsumi_guitar", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // Creates a new food item with the id "titans_creation:example_id", nutrition 1 and saturation 2
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEat().nutrition(1).saturationMod(2f).build())));

    // Creates a creative tab with the id "titans_creation:example_tab" for the example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    public TitansCreationMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // 巨型怪物类型：实体与刷怪蛋
        GiantMonsterRegistry.ENTITY_TYPES.register(modEventBus);
        GiantMonsterRegistry.SPAWN_EGGS.register(modEventBus);

        // 分类物品：方块 / 物品 / 7 个创造模式物品栏栏位
        TitansBlocks.BLOCKS.register(modEventBus);
        TitansItems.ITEMS.register(modEventBus);
        TitansCreativeTabs.TABS.register(modEventBus);

        // 自定义配方序列化器（无需模板的锻造台升级）
        TitansRecipes.SERIALIZERS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 注册战斗模式网络通道（客户端战挥 -> 服务端 120° 扇形 AoE 伤害）
        CombatNetwork.register();

        // 客户端：注册「配置界面扩展点」，使模组列表中的 Config 按钮可用（否则一直是灰色）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GiantMonsterClientSetup.registerConfigScreen(context));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 读取巨型怪物配置；缺失的配置文件会在此自动生成（新增怪物无需手动建文件）
        event.enqueueWork(GiantMonsterConfigManager::loadAll);

        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock) {
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        }

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
        // 巨型怪物的刷怪蛋放入原版「刷怪蛋」物品栏
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            for (RegistryObject<Item> spawnEgg : GiantMonsterRegistry.getSpawnEggs()) {
                event.accept(spawnEgg);
            }
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
