package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.Rarity;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.BlockPosDataSerializer;
import com.falazar.farmupcraft.database.serializers.BoolDataSerializer;
import com.falazar.farmupcraft.database.serializers.ChunkPosDataSerializer;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.HolderDataSerializer;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.falazar.farmupcraft.database.serializers.ItemDataSerializer;
import com.falazar.farmupcraft.database.serializers.ListDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import com.falazar.farmupcraft.database.serializers.MapDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.falazar.farmupcraft.database.serializers.UUIDDataSerializer;
import com.falazar.farmupcraft.database.serializers.VersionedDataSerializer;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class DatabaseSerializationRoundTripTest {
    @TempDir
    Path directory;

    @Test
    void primitiveCoordinateAndUuidSerializersSurviveBothFileFormats(MinecraftServer server) throws IOException {
        var registries = server.registryAccess();
        roundTrip("true", new BoolDataSerializer(), true, registries);
        roundTrip("false", new BoolDataSerializer(), false, registries);
        roundTrip("string", new StringDataSerializer(), "name and description", registries);
        roundTrip("empty-string", new StringDataSerializer(), "", registries);
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 127, 128, 32768, Integer.MAX_VALUE}) {
            roundTrip("int-" + value, new IntDataSerializer(), value, registries);
        }
        for (long value : new long[]{Long.MIN_VALUE, -1, 0, 9007199254740993L, Long.MAX_VALUE}) {
            roundTrip("long-" + value, new LongDataSerializer(), value, registries);
        }
        for (UUID value : List.of(new UUID(0L, 0L), new UUID(-1L, -1L),
                new UUID(0x0000000100000002L, 0x0000000300000004L),
                UUID.fromString("12345678-9abc-def0-1234-56789abcdef0"))) {
            roundTrip("uuid-" + value, new UUIDDataSerializer(), value, registries);
        }
        roundTrip("block", new BlockPosDataSerializer(), new BlockPos(-30000000, -64, 30000000), registries);
        roundTrip("chunk", new ChunkPosDataSerializer(), new ChunkPos(-12345, 67890), registries);
    }

    @Test
    void nestedCollectionsVersionEnvelopesAndScalarCodecsSurviveBothFormats(MinecraftServer server) throws IOException {
        var registries = server.registryAccess();
        var coordinates = new ListDataSerializer<>(new BlockPosDataSerializer());
        var map = new MapDataSerializer<>(new StringDataSerializer(), coordinates);
        roundTrip("map", map, Map.of(
                "first", List.of(new BlockPos(1, -2, 3), new BlockPos(4, 5, 6)),
                "empty", List.of()), registries);
        roundTrip("empty-map", map, Map.of(), registries);
        roundTrip("empty-list", coordinates, List.of(), registries);
        roundTrip("integer-codec", new CodecDataSerializer<>(Codec.INT), 7, registries);
        roundTrip("integer-list-codec", new CodecDataSerializer<>(Codec.INT.listOf()), List.of(1, 2, 3), registries);
        var versioned = new VersionedDataSerializer<>(new LongDataSerializer(), new LongDataSerializer(), 1);
        roundTrip("versioned-long", versioned, Long.MAX_VALUE, registries);
    }

    @Test
    void realRegistryReferencesAndCurrencyCodecSurviveBothFormats(MinecraftServer server) throws IOException {
        var registries = server.registryAccess();
        roundTrip("item", new ItemDataSerializer(), Items.WHEAT_SEEDS, registries);
        var plains = registries.lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
        roundTrip("holder", new HolderDataSerializer<>(Registries.BIOME), plains, registries);
        roundTrip("biome-codec", new CodecDataSerializer<>(Biome.CODEC), plains, registries);
        var coin = new Coin(Identifier.parse("farmupcraft:bronze_coin"), "bronze_coin", 10, Rarity.COMMON);
        roundTrip("currency-codec", new CodecDataSerializer<>(CoinStack.CODEC), new CoinStack(coin, 7), registries);
    }

    private <T> void roundTrip(String name, DataSerializer<T> serializer, T value, HolderLookup.Provider registries)
            throws IOException {
        var expected = serializer.serialize(value, registries);
        for (FileFormat format : FileFormat.values()) {
            Path file = directory.resolve(name + format.getExtension());
            format.getExportStrategy().export(expected, file);
            var imported = format.getImportStrategy().importData(file);
            T decoded = serializer.deserialize(imported, registries);
            if (value instanceof Map<?, ?>) {
                assertEquals(value, decoded, name + " via " + format);
            } else {
                assertEquals(expected, serializer.serialize(decoded, registries), name + " via " + format);
            }
        }
    }
}
