package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ItemDataSerializer implements DataSerializer<Item> {
    private static final String ITEM_KEY_TAG = "item_key";

    @Override
    public CompoundTag serialize(Item value, HolderLookup.Provider registries) {
        var key = BuiltInRegistries.ITEM.getResourceKey(value)
                .orElseThrow(() -> new IllegalArgumentException("Cannot serialize an unregistered item"));
        registries.lookupOrThrow(Registries.ITEM).get(key)
                .filter(holder -> holder.value() == value)
                .orElseThrow(() -> new IllegalArgumentException("Item is not registered in this context: " + key));
        var tag = new CompoundTag();
        tag.putString(ITEM_KEY_TAG, key.identifier().toString());
        return tag;
    }

    @Override
    public Item deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        String name = tag.getString(ITEM_KEY_TAG)
                .orElseThrow(() -> new IllegalArgumentException("Missing or invalid '" + ITEM_KEY_TAG + "'"));
        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            throw new IllegalArgumentException("Invalid item identifier: " + name);
        }
        var key = ResourceKey.create(Registries.ITEM, id);
        return registries.lookupOrThrow(Registries.ITEM).get(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id)).value();
    }
}
