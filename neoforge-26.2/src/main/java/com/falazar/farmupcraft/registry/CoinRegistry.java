package com.falazar.farmupcraft.registry;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.Rarity;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

public class CoinRegistry {
    public static final ResourceKey<Coin> BRONZE_COIN = ResourceKey.create(FUCRegistries.Keys.COIN,
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "bronze_coin"));

    public static void bootstrap(BootstrapContext<Coin> context) {
        context.register(BRONZE_COIN, new Coin(
                Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "bronze_coin"),
                "bronze_coin", 10, Rarity.COMMON));
    }

    public static @Nullable Coin getCoin(ResourceKey<Coin> coin) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Cannot look up a coin without a running server");
        }
        ServerLevel serverLevel = server.overworld();
        if (serverLevel == null) {
            throw new IllegalStateException("Cannot look up a coin before the server overworld is available");
        }
        Registry<Coin> coinRegistry = serverLevel.registryAccess().lookupOrThrow(FUCRegistries.Keys.COIN);
        return coinRegistry.getValue(coin);
    }
}
