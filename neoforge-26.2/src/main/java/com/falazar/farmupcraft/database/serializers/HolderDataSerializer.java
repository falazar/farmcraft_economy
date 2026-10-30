package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

public class HolderDataSerializer<T> implements DataSerializer<Holder<T>> {
    private static final String HOLDER_KEY = "holder_key";
    private final ResourceKey<? extends Registry<T>> registryKey;

    public HolderDataSerializer(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = Objects.requireNonNull(registryKey);
    }

    @Override
    public CompoundTag serialize(Holder<T> value, HolderLookup.Provider registries) {
        var key = value.unwrapKey()
                .orElseThrow(() -> new IllegalArgumentException("Cannot serialize a direct, unregistered holder"));
        if (!key.isFor(registryKey)) {
            throw new IllegalArgumentException("Holder belongs to another registry: " + key);
        }
        var lookup = registries.lookupOrThrow(registryKey);
        if (!value.canSerializeIn(lookup) || lookup.get(key).isEmpty()) {
            throw new IllegalArgumentException("Holder does not belong to this registry context: " + key);
        }
        var tag = new CompoundTag();
        tag.putString(HOLDER_KEY, key.identifier().toString());
        return tag;
    }

    @Override
    public Holder<T> deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        String name = tag.getString(HOLDER_KEY)
                .orElseThrow(() -> new IllegalArgumentException("Missing or invalid '" + HOLDER_KEY + "'"));
        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            throw new IllegalArgumentException("Invalid holder identifier: " + name);
        }
        var key = ResourceKey.create(registryKey, id);
        return registries.lookupOrThrow(registryKey).get(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown holder: " + key));
    }
}
