package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.MarketData;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class MarketDataProvider extends CodecDataProvider<MarketData> {
    public MarketDataProvider(PackOutput output, String modid) {
        super(output, modid, "market", "Market Data", MarketData.CODEC);
    }

    public record MarketDataConsumer(Identifier resourceLocation, MarketData data) {}

    @Override
    protected final void buildEntries(BiConsumer<Identifier, MarketData> writer) {
        buildMarketData(entry -> writer.accept(entry.resourceLocation(), entry.data()));
    }

    protected abstract void buildMarketData(Consumer<MarketDataConsumer> writer);
}
