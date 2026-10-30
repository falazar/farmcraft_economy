package com.falazar.farmupcraft.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public class CropBlockData {
    public static final Codec<CropBlockData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.either(
                            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").codec(),
                            TagKey.codec(Registries.BLOCK).fieldOf("tag").codec()
                    ).fieldOf("crop").forGetter(data ->
                            data.block != null ? Either.left(data.block) : Either.right(data.blockTagKey)),
                    Codec.DOUBLE.fieldOf("growth_succes_rate").forGetter(CropBlockData::getGrowthSuccesRate),
                    Codec.BOOL.fieldOf("populate_blocks").forGetter(CropBlockData::getPopulateBlocks)
            ).apply(instance, CropBlockData::new));

    private final Block block;
    private final TagKey<Block> blockTagKey;
    private final double growthSuccesRate;
    private final boolean populateBlocks;

    public CropBlockData(Either<Block, TagKey<Block>> crop, double growthSuccesRate, boolean populateBlocks) {
        this.block = crop.left().orElse(null);
        this.blockTagKey = crop.right().orElse(null);
        this.growthSuccesRate = growthSuccesRate;
        this.populateBlocks = populateBlocks;
    }

    public CropBlockData(TagKey<Block> tag, double growthSuccesRate, boolean populateBlocks) {
        this(Either.right(tag), growthSuccesRate, populateBlocks);
    }

    public CropBlockData(Block block, TagKey<Biome> allowedBiomes, double growthSuccesRate, boolean populateBlocks) {
        this(Either.left(block), growthSuccesRate, populateBlocks);
    }

    public double getGrowthSuccesRate() {
        return growthSuccesRate;
    }

    public boolean getPopulateBlocks() {
        return populateBlocks;
    }

    public TagKey<Block> getBlocks() {
        return blockTagKey;
    }

    public Block getBlock() {
        return block;
    }
}
