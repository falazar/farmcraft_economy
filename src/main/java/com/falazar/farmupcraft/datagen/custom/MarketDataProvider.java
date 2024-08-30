package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.MarketData;
import com.google.common.collect.Sets;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


public abstract class MarketDataProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final String modid;
    public MarketDataProvider(PackOutput pOutput, String modid) {
        this.pathProvider = pOutput.createPathProvider(PackOutput.Target.DATA_PACK, new ResourceLocation(modid,"farmupcraft/market").getPath());
        this.modid = modid;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput pOutput) {
        Set<ResourceLocation> set = Sets.newHashSet();
        Set<ResourceLocation> taskSet = Sets.newHashSet();
        List<CompletableFuture<?>> list = new ArrayList<>();
        this.buildMarketData((marketData -> {
            if (!set.add(marketData.resourceLocation())) {
                throw new IllegalStateException("Duplicate Market Data " + marketData.resourceLocation());
            } else {

                MarketData.CODEC.encodeStart(JsonOps.INSTANCE, marketData.data)
                        .get()
                        .ifLeft(e -> list.add(DataProvider.saveStable(pOutput, e, this.pathProvider.json(marketData.resourceLocation()))))
                        .ifRight(partial -> LOGGER.error("Failed to create market data {}, due to {}", marketData.resourceLocation(), partial));
            }
        }));
        return CompletableFuture.allOf(list.toArray((p_253414_) -> {
            return new CompletableFuture[p_253414_];
        }));
    }

    public record MarketDataConsumer(ResourceLocation resourceLocation, MarketData data){}

    protected abstract void buildMarketData(Consumer<MarketDataConsumer> pWriter);

    @Override
    public String getName() {
        return modid + "Market Data";
    }
}
