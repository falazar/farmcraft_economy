package com.falazar.farmupcraft.registry;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.data.rules.crop.CropRulesType;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FUCRegistries {
    public static final DeferredRegister<CropRulesType<?>> CROP_RULES_TYPE_SERIALIZER = DeferredRegister.create(
            Keys.CROP_RULES_SERIALIZER, FarmUpCraftNeoForge.MOD_ID);
    public static final Registry<CropRulesType<?>> CROP_RULES_TYPE_REGISTRY =
            CROP_RULES_TYPE_SERIALIZER.makeRegistry(builder -> builder.sync(false));
    public static final Supplier<Registry<CropRulesType<?>>> CROP_RULES_TYPE_SERIALIZER_SUPP =
            () -> CROP_RULES_TYPE_REGISTRY;

    public static final class Keys {
        public static final ResourceKey<Registry<CropRulesType<?>>> CROP_RULES_SERIALIZER = ResourceKey.createRegistryKey(
                Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "crop_rules_type"));
        public static final ResourceKey<Registry<CropRules>> CROP_RULES = ResourceKey.createRegistryKey(
                Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "crop_rules"));
        public static final ResourceKey<Registry<Coin>> COIN = ResourceKey.createRegistryKey(
                Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "coin"));
    }
}
