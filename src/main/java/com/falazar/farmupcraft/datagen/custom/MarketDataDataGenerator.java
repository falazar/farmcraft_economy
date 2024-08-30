package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.data.MarketData;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class MarketDataDataGenerator extends MarketDataProvider {
    public MarketDataDataGenerator(PackOutput pOutput, String modid) {
        super(pOutput, modid);
    }

    @Override
    protected void buildMarketData(Consumer<MarketDataConsumer> pWriter) {
        List<GoodsData> data = new ArrayList<>();
        for(Item item : ForgeRegistries.ITEMS.getValues()) {
            if(item.getDescriptionId().contains("pamhc2crops") || item.getDescriptionId().contains("pamhc2foodcore") || item.getDescriptionId().contains("pamhc2foodextended")) {
                data.add(new GoodsData(item, 5));
            }
        }

        pWriter.accept(new MarketDataConsumer(prefix("market"), new MarketData(data)));
    }


}
