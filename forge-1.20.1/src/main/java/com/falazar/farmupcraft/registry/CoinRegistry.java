package com.falazar.farmupcraft.registry;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.Rarity;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class CoinRegistry {

    public static final ResourceKey<Coin> BRONZE_COIN = ResourceKey.create(FUCRegistries.Keys.COIN, prefix("bronze_coin"));

    public static void bootstrap(BootstapContext<Coin> context) {

        context.register(BRONZE_COIN, new Coin(prefix("bronze_coin"), "bronze_coin", 10, Rarity.COMMON));

    }


    public static Coin getCoin(ResourceKey<Coin> coin) {
        ServerLevel serverLevel = ServerLifecycleHooks.getCurrentServer().overworld();
        Registry<Coin> coinRegistry = serverLevel.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
        return coinRegistry.get(coin);
    }

}
