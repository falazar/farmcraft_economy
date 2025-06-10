package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.CurrencyCost;
import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.data.MarketData;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class MarketDataDataGenerator extends MarketDataProvider {

    private final CompletableFuture<HolderLookup.Provider> lookup;
    public MarketDataDataGenerator(PackOutput pOutput, String modid, CompletableFuture<HolderLookup.Provider> lookup) {
        super(pOutput, modid);
        this.lookup = lookup;
    }

    @Override
    protected void buildMarketData(Consumer<MarketDataConsumer> pWriter) {
        List<GoodsData> data = new ArrayList<>();

        HolderLookup.RegistryLookup<Coin> coinRegistry = lookup.getNow(null).lookupOrThrow(FUCRegistries.Keys.COIN);

        Coin bronzeCoin =  coinRegistry.get(CoinRegistry.BRONZE_COIN).get().get();
        
//        for(Item item : ForgeRegistries.ITEMS.getValues()) {
//            if(item.getDescriptionId().contains("pamhc2crops") || item.getDescriptionId().contains("pamhc2foodcore") || item.getDescriptionId().contains("pamhc2foodextended")) {
//                data.add(new GoodsData(item, new CurrencyCost(bronzeCoin, 10)));
//            }
//        }
//
//        pWriter.accept(new MarketDataConsumer(prefix("market"), new MarketData(data)));
    }


}
