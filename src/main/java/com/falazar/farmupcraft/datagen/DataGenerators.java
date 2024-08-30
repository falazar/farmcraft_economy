package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.datagen.custom.CropBlockDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.CropItemDataDataGenerator;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        BlockTagsGenerator blockTagGenerator = generator.addProvider(event.includeServer(),
                new BlockTagsGenerator(packOutput, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(), new ItemTagsGenerator(packOutput, lookupProvider, blockTagGenerator.contentsGetter(), existingFileHelper));
        generator.addProvider(event.includeServer(), new CropBlockDataDataGenerator(packOutput, FarmUpCraft.MODID));
        generator.addProvider(event.includeServer(), new CropItemDataDataGenerator(packOutput, FarmUpCraft.MODID));


    }

}
