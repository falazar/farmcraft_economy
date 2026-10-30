package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.BiomeRulesData;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class BiomeRulesDataDataProvider extends CodecDataProvider<BiomeRulesData> {
    public BiomeRulesDataDataProvider(PackOutput output, String modid) {
        super(output, modid, "biome_rules", "Biome Rules Data", BiomeRulesData.CODEC);
    }

    public record BiomeRulesDataConsumer(Identifier resourceLocation, BiomeRulesData data) {}

    @Override
    protected final void buildEntries(BiConsumer<Identifier, BiomeRulesData> writer) {
        buildBiomeRuleData(entry -> writer.accept(entry.resourceLocation(), entry.data()));
    }

    protected abstract void buildBiomeRuleData(Consumer<BiomeRulesDataConsumer> writer);
}
