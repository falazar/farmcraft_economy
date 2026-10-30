package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FUCDatapackBuiltinEntriesProvider extends DatapackBuiltinEntriesProvider {

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(FUCRegistries.Keys.COIN, CoinRegistry::bootstrap)
    ;

    public FUCDatapackBuiltinEntriesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider, BUILDER, Set.of(FarmUpCraft.MODID));
    }
}
