package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Optional;

public class HolderDataSerializer<T> implements DataBase.DataSerializer<Holder<T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HOLDER_KEY = "holder_key";
    private static final String DATABASE_NAME_TAG = "database_name";
    private final ResourceKey<? extends Registry<T>> registryKey;
    public HolderDataSerializer(ResourceKey<? extends Registry<T>> registryKey){
        this.registryKey = registryKey;
    }

    @Override
    public CompoundTag serialize(Holder<T> value) {
        CompoundTag holderTag = new CompoundTag();
        holderTag.putString(HOLDER_KEY, value.unwrapKey().orElseThrow().location().toString());
        return holderTag;
    }

    @Override
    public Holder<T> deserialize(CompoundTag tag, Level level) {
        String holderKey = tag.getString(HOLDER_KEY);
        String databaseName = tag.getString(DATABASE_NAME_TAG);
        ResourceKey<T> registryResourceKey = ResourceKey.create(registryKey, new ResourceLocation(holderKey));

        // Retrieve biome holder
        Optional<Holder<T>> optionalHolder = level.registryAccess().registry(registryKey)
                .flatMap(registry -> registry.getHolder(registryResourceKey));

        return optionalHolder.orElseGet(() -> {
            LOGGER.error("[{}] Failed to load holder for registry {} with key {}.",databaseName ,registryKey.registry(), holderKey);
            return null; // or throw a custom exception if appropriate
        });
    }
}
