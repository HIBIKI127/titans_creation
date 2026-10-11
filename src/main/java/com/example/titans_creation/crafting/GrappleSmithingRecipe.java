package com.example.titans_creation.crafting;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * 自定义锻造台配方：<b>不需要模板</b>。
 *
 * <p>原版的 {@code minecraft:smithing_transform} 强制要求模板槽有物品（其 template 字段必须是
 * 一个匹配物品的 Ingredient，无法匹配空槽），因此这里自建一个只校验
 * 「基础槽（左，槽位 1）+ 附加槽（右，槽位 2）」的锻造配方。
 *
 * <p>JSON 格式：{@code {"type": "titans_creation:grapple_smithing", "base": {...}, "addition": {...}, "result": {...}}}
 */
public class GrappleSmithingRecipe implements SmithingRecipe {

    private final ResourceLocation id;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public GrappleSmithingRecipe(ResourceLocation id, Ingredient base, Ingredient addition, ItemStack result) {
        this.id = id;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public boolean matches(Container container, net.minecraft.world.level.Level level) {
        return this.base.test(container.getItem(1)) && this.addition.test(container.getItem(2));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return this.result.copy();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return this.result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(Container container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.hasCraftingRemainingItem()) {
                remaining.set(i, stack.getCraftingRemainingItem());
            }
        }
        return remaining;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return this.addition.test(stack);
    }

    @Override
    public boolean isSpecial() {
        // 特殊配方：不出现在配方书中（避免配方书尝试展示三格输入）
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TitansRecipes.GRAPPLE_SMITHING.get();
    }

    /** 序列化器。 */
    public static class Serializer implements RecipeSerializer<GrappleSmithingRecipe> {

        @Override
        public GrappleSmithingRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient base = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "base"));
            Ingredient addition = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "addition"));
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            return new GrappleSmithingRecipe(id, base, addition, result);
        }

        @Override
        public GrappleSmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient base = Ingredient.fromNetwork(buffer);
            Ingredient addition = Ingredient.fromNetwork(buffer);
            ItemStack result = buffer.readItem();
            return new GrappleSmithingRecipe(id, base, addition, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, GrappleSmithingRecipe recipe) {
            recipe.base.toNetwork(buffer);
            recipe.addition.toNetwork(buffer);
            buffer.writeItem(recipe.result);
        }
    }
}
