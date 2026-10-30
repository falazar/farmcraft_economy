package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FUCDatapackBuiltinEntriesProvider extends DatapackBuiltinEntriesProvider {
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(FUCRegistries.Keys.COIN, CoinRegistry::bootstrap);

    public FUCDatapackBuiltinEntriesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, BUILDER, Set.of(FarmUpCraftNeoForge.MOD_ID));
    }
}
