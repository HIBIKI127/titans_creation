package com.example.titans_creation.item;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的方块注册表（功能方块分类）。
 */
public final class TitansBlocks {

    /** 方块注册器。 */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TitansCreationMod.MODID);

    private TitansBlocks() {
    }

    /** 泰坦图腾：巨石材质的功能方块。 */
    public static final RegistryObject<Block> TITAN_TOTEM = BLOCKS.register("titan_totem",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
}
