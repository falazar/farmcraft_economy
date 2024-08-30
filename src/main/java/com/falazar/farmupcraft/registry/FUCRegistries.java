package com.falazar.farmupcraft.registry;

import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.data.rules.crop.CropRulesType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.function.Supplier;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class FUCRegistries {
    static { init(); }
    public static final RegistryBuilder<CropRulesType<?>> CROP_RULES_TYPE_BUILDER = makeRegistry(Keys.CROP_RULES_SERIALIZER);

    public static final DeferredRegister<CropRulesType<?>> CROP_RULES_TYPE_SERIALIZER = DeferredRegister.create(Keys.CROP_RULES_SERIALIZER, Keys.CROP_RULES_SERIALIZER.location().getNamespace());
    public static final Supplier<IForgeRegistry<CropRulesType<?>>> CROP_RULES_TYPE_SERIALIZER_SUPP = CROP_RULES_TYPE_SERIALIZER.makeRegistry(() -> CROP_RULES_TYPE_BUILDER);


    private static <T> RegistryBuilder<T> makeRegistry(ResourceKey<? extends Registry<T>> key)
    {
        return new RegistryBuilder<T>().setName(key.location()).setMaxID(Integer.MAX_VALUE - 1);
    }

    public static final class Keys {
        public static final ResourceKey<Registry<CropRulesType<?>>> CROP_RULES_SERIALIZER = key(prefix("crop_rules_type").toString());
        public static final ResourceKey<Registry<CropRules>> CROP_RULES = key(prefix("crop_rules").toString());

        private static <T> ResourceKey<Registry<T>> key(String name)
        {
            return ResourceKey.createRegistryKey(new ResourceLocation(name));
        }
        private static void init() {}

    }

    private static void init()
    {
        Keys.init();
    }
}
