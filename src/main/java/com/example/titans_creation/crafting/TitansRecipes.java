package com.example.titans_creation.crafting;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的配方序列化器。
 */
public final class TitansRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TitansCreationMod.MODID);

    /** 无需模板的锻造台升级配方（抓钩手弩 + 抓钩）。 */
    public static final RegistryObject<RecipeSerializer<GrappleSmithingRecipe>> GRAPPLE_SMITHING =
            SERIALIZERS.register("grapple_smithing", GrappleSmithingRecipe.Serializer::new);

    private TitansRecipes() {
    }
}
