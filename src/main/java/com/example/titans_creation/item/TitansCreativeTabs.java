package com.example.titans_creation.item;

import java.util.function.Consumer;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的创造模式物品栏栏位（7 个分类）。
 *
 * <p>分类顺序：功能方块 → 素材 → 战利品 → 武器 → 工具 → 图纸 → 饰品/装备。
 * 每个栏位的标题语言键为 {@code itemGroup.titans_creation.<id>}。
 */
public final class TitansCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TitansCreationMod.MODID);

    private TitansCreativeTabs() {
    }

    /** ① 功能方块。 */
    public static final RegistryObject<CreativeModeTab> FUNCTIONAL_BLOCKS = register(
            "functional_blocks", CreativeModeTabs.SPAWN_EGGS, TitansItems.TITAN_TOTEM,
            output -> output.accept(TitansItems.TITAN_TOTEM.get()));

    /** ② 素材。 */
    public static final RegistryObject<CreativeModeTab> MATERIALS = register(
            "materials", key("functional_blocks"), TitansItems.GIANT_TENDON,
            output -> {
                output.accept(TitansItems.GIANT_TENDON.get());
                output.accept(TitansItems.TITAN_ALLOY_INGOT.get());
                output.accept(TitansItems.WOODEN_GRAPPLE_HOOK.get());
                output.accept(TitansItems.IRON_GRAPPLE_HOOK.get());
                output.accept(TitansItems.DIAMOND_GRAPPLE_HOOK.get());
            });

    /** ③ 战利品。 */
    public static final RegistryObject<CreativeModeTab> LOOT = register(
            "loot", key("materials"), TitansItems.GIANT_CORE,
            output -> output.accept(TitansItems.GIANT_CORE.get()));

    /** ④ 武器。 */
    public static final RegistryObject<CreativeModeTab> WEAPONS = register(
            "weapons", key("loot"), TitansItems.GIANT_BONE_BLADE,
            output -> output.accept(TitansItems.GIANT_BONE_BLADE.get()));

    /** ⑤ 工具。 */
    public static final RegistryObject<CreativeModeTab> TOOLS = register(
            "tools", key("weapons"), TitansItems.CLIMBING_PICKAXE,
            output -> output.accept(TitansItems.CLIMBING_PICKAXE.get()));

    /** ⑥ 图纸。 */
    public static final RegistryObject<CreativeModeTab> BLUEPRINTS = register(
            "blueprints", key("tools"), TitansItems.GIANT_ANATOMY_BLUEPRINT,
            output -> output.accept(TitansItems.GIANT_ANATOMY_BLUEPRINT.get()));

    /** ⑦ 饰品 / 装备。 */
    public static final RegistryObject<CreativeModeTab> ACCESSORIES = register(
            "accessories", key("blueprints"), TitansItems.IRON_GLOVES,
            output -> {
                output.accept(TitansItems.LEATHER_GLOVES.get());
                output.accept(TitansItems.IRON_GLOVES.get());
                output.accept(TitansItems.GRIP_BOOTS.get());
                output.accept(TitansItems.CUSHION_BOOTS.get());
                output.accept(TitansItems.TRAVEL_BACKPACK.get());
                output.accept(TitansItems.CLIMBING_GLOVES.get());
                output.accept(TitansItems.CLAW_BOOTS.get());
                output.accept(TitansItems.GRAPPLE_CROSSBOW.get());
                output.accept(TitansItems.GRAPPLE_CROSSBOW_IRON.get());
                output.accept(TitansItems.GRAPPLE_CROSSBOW_DIAMOND.get());
            });

    private static RegistryObject<CreativeModeTab> register(String id,
                                                             ResourceKey<CreativeModeTab> placeAfter,
                                                             RegistryObject<Item> icon,
                                                             Consumer<CreativeModeTab.Output> items) {
        return TABS.register(id, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.titans_creation." + id))
                .withTabsBefore(placeAfter)
                .icon(() -> icon.get().getDefaultInstance())
                .displayItems((parameters, output) -> items.accept(output))
                .build());
    }

    private static ResourceKey<CreativeModeTab> key(String id) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, id));
    }
}
