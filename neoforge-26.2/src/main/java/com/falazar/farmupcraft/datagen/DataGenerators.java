package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.datagen.custom.BiomeRulesDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.CropBlockDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.CropItemDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.MarketDataDataGenerator;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class DataGenerators {
    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Server event) {
        event.createDatapackRegistryObjects(FUCDatapackBuiltinEntriesProvider.BUILDER);
        event.createProvider(BlockTagsGenerator::new);
        event.createProvider((output, lookup) -> new ItemTagsGenerator(
                output, lookup, event.getResourceManager(PackType.SERVER_DATA)));
        event.createProvider(BiomeTagsProvider::new);
        event.createProvider(output -> new CropBlockDataDataGenerator(output, FarmUpCraftNeoForge.MOD_ID));
        event.createProvider(output -> new CropItemDataDataGenerator(output, FarmUpCraftNeoForge.MOD_ID));
        event.createProvider(output -> new BiomeRulesDataDataGenerator(output, FarmUpCraftNeoForge.MOD_ID));
        event.createProvider((output, lookup) -> new MarketDataDataGenerator(output, FarmUpCraftNeoForge.MOD_ID, lookup));
    }
}
