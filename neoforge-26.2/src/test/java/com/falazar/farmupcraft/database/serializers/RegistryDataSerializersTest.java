package com.falazar.farmupcraft.database.serializers;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RegistryDataSerializersTest {
    @Test
    void itemSerializerPreservesLegacyKeyForVanillaAndRealHarvestCraftItems(MinecraftServer server) {
        var serializer = new ItemDataSerializer();
        var registries = server.registryAccess();
        var items = registries.lookupOrThrow(Registries.ITEM);
        for (String id : List.of("minecraft:air", "minecraft:wheat_seeds", "pamhc2crops:cornitem")) {
            var item = items.get(Identifier.parse(id)).orElseThrow().value();
            var expected = new CompoundTag();
            expected.putString("item_key", id);
            assertEquals(expected, serializer.serialize(item, registries));
            assertSame(item, serializer.deserialize(expected, registries));
        }
    }

    @Test
    void itemSerializerRejectsMissingInvalidAndUnknownKeysInsteadOfReturningAir(MinecraftServer server) {
        var serializer = new ItemDataSerializer();
        var registries = server.registryAccess();
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(new CompoundTag(), registries));
        for (String id : List.of("INVALID ID", "farmupcraft:missing_item", "")) {
            var tag = new CompoundTag();
            tag.putString("item_key", id);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(tag, registries), id);
        }
        var wrongType = new CompoundTag();
        wrongType.putInt("item_key", 1);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(wrongType, registries));
        assertThrows(IllegalStateException.class, () -> serializer.serialize(Items.AIR, RegistryAccess.EMPTY));
    }

    @Test
    void itemSerializerDoesNotBypassTheSuppliedRegistryContext(MinecraftServer server) {
        var serializer = new ItemDataSerializer();
        var filteredItems = server.registryAccess().lookupOrThrow(Registries.ITEM)
                .filterElements(item -> item != Items.WHEAT_SEEDS);
        var restricted = HolderLookup.Provider.create(Stream.of(filteredItems));
        var tag = new CompoundTag();
        tag.putString("item_key", "minecraft:wheat_seeds");
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(Items.WHEAT_SEEDS, restricted));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(tag, restricted));
    }

    @Test
    void holderSerializerPreservesLegacyKeyAndUsesTheSuppliedBiomeRegistry(MinecraftServer server) {
        var serializer = new HolderDataSerializer<>(Registries.BIOME);
        var registries = server.registryAccess();
        for (String id : List.of("minecraft:plains", "biomesoplenty:bayou")) {
            var holder = registries.lookupOrThrow(Registries.BIOME).get(Identifier.parse(id)).orElseThrow();
            var expected = new CompoundTag();
            expected.putString("holder_key", id);
            assertEquals(expected, serializer.serialize(holder, registries));
            assertSame(holder, serializer.deserialize(expected, registries));
        }
    }

    @Test
    void holderSerializerRejectsUnregisteredValuesMissingRegistriesAndMalformedKeys(MinecraftServer server) {
        var serializer = new HolderDataSerializer<>(Registries.BIOME);
        var registries = server.registryAccess();
        var plains = registries.lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(Holder.direct(plains.value()), registries));
        assertThrows(IllegalStateException.class, () -> serializer.serialize(plains, RegistryAccess.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(new CompoundTag(), registries));
        for (String id : List.of("INVALID ID", "farmupcraft:missing_biome", "")) {
            var tag = new CompoundTag();
            tag.putString("holder_key", id);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(tag, registries), id);
        }
        var wrongType = new CompoundTag();
        wrongType.putInt("holder_key", 1);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(wrongType, registries));
    }

    @Test
    void holderSerializerDoesNotBypassTheSuppliedRegistryContext(MinecraftServer server) {
        var serializer = new HolderDataSerializer<>(Registries.BIOME);
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomes.get(Identifier.parse("minecraft:plains")).orElseThrow();
        var filteredBiomes = biomes.filterElements(biome -> biome != plains.value());
        var restricted = HolderLookup.Provider.create(Stream.of(filteredBiomes));
        var tag = new CompoundTag();
        tag.putString("holder_key", "minecraft:plains");
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(plains, restricted));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(tag, restricted));
    }

    @Test
    void codecSerializerResolvesDynamicBiomeReferencesInBothDirections(MinecraftServer server) {
        var serializer = new CodecDataSerializer<>(Biome.CODEC);
        var registries = server.registryAccess();
        var plains = registries.lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
        var expected = new CompoundTag();
        expected.put("codec_value", StringTag.valueOf("minecraft:plains"));
        assertEquals(expected, serializer.serialize(plains, registries));
        assertSame(plains, serializer.deserialize(expected, registries));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(expected, RegistryAccess.EMPTY));

        var listSerializer = new CodecDataSerializer<>(Biome.CODEC.listOf());
        var encoded = listSerializer.serialize(List.of(plains), registries);
        assertEquals(List.of(plains), listSerializer.deserialize(encoded, registries));
    }

    @Test
    void versionedHolderSerializerForwardsRegistryContextForCurrentAndLegacyPayloads(MinecraftServer server) {
        var holders = new HolderDataSerializer<>(Registries.BIOME);
        var serializer = new VersionedDataSerializer<>(holders, holders, 1);
        var registries = server.registryAccess();
        var plains = registries.lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
        var current = serializer.serialize(plains, registries);
        assertEquals(1, current.getInt("version").orElseThrow());
        assertSame(plains, serializer.deserialize(current, registries));
        var legacy = holders.serialize(plains, registries);
        assertSame(plains, serializer.deserialize(legacy, registries));
    }
}
