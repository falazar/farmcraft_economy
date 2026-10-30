package com.falazar.farmupcraft.datagen.custom;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MarketDataDataGenerator extends MarketDataProvider {
    private final CompletableFuture<HolderLookup.Provider> lookup;

    public MarketDataDataGenerator(PackOutput output, String modid, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, modid);
        this.lookup = lookup;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return lookup.thenCompose(registries -> super.run(output));
    }

    @Override
    protected void buildMarketData(Consumer<MarketDataConsumer> writer) {
        // The legacy market template never emitted entries; do not generate an empty market over curated data.
    }
}
