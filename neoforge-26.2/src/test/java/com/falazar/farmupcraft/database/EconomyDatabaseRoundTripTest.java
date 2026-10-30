package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.GameZone;
import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.HolderDataSerializer;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.falazar.farmupcraft.database.serializers.UUIDDataSerializer;
import com.falazar.farmupcraft.database.serializers.VersionedDataSerializer;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class EconomyDatabaseRoundTripTest {
    @TempDir
    Path directory;

    @Test
    void allNineDatabaseSchemasSurviveTheCoreAndBothFileFormats(MinecraftServer server) throws IOException {
        UUID id = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        var registries = server.registryAccess();
        var biomeKeys = new HolderDataSerializer<>(Registries.BIOME);
        var plains = registries.lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
        verify("biome_rules_database", new VersionedDataSerializer<>(biomeKeys, biomeKeys, 1),
                new CodecDataSerializer<>(BiomeRulesInstance.CODEC), plains,
                new BiomeRulesInstance(BiomeRulesData.EMPTY), registries);
        verify("player_database", new UUIDDataSerializer(), new CodecDataSerializer<>(PlayerData.CODEC),
                id, new PlayerData(7, id, null, 123, true, "blue"), registries);
        verify("chunk_data_database", new LongDataSerializer(), new CodecDataSerializer<>(ChunkData.CODEC),
                Long.MIN_VALUE, new ChunkData("farm", 7, id, id), registries);
        var village = VillageData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"uuid":"12345678-9abc-def0-1234-56789abcdef0","name":"Test Village",
                 "position":{"chunk_long":146028888047},"level":2,
                 "claimed_chunks":[{"chunk_long":146028888047}],"bought":true}
                """)).getOrThrow();
        verify("village_database", new UUIDDataSerializer(), new CodecDataSerializer<>(VillageData.CODEC),
                id, village, registries);
        verify("goods_data_database_new", new StringDataSerializer(), new CodecDataSerializer<>(GoodsData.CODEC),
                "minecraft:wheat", new GoodsData("minecraft:wheat", 17, 3, "common", true, "food", "2026-10-28"),
                registries);
        verify("world_data_database", new IntDataSerializer(), new CodecDataSerializer<>(WorldData.CODEC),
                0, new WorldData(), registries);
        verify("npc_database", new UUIDDataSerializer(), new CodecDataSerializer<>(NpcData.CODEC),
                id, new NpcData(id, "Ada", id, "Farmer"), registries);
        verify("game_structure_database", new LongDataSerializer(), new CodecDataSerializer<>(GameStructureData.CODEC),
                Long.MAX_VALUE, new GameStructureData(Long.MAX_VALUE, "Tower", new BlockPos(-17, -64, 33), "tower"),
                registries);
        verify("game_zone_database", new UUIDDataSerializer(), new CodecDataSerializer<>(GameZone.CODEC),
                id, new GameZone(id, "Cave", List.of(SectionPos.of(-1, -4, 2)), GameZone.ZoneType.CAVE,
                        new BlockPos(-17, -64, 33)), registries);
    }

    private <M, V> void verify(String name, DataSerializer<M> keys, DataSerializer<V> values, M key, V value,
                               HolderLookup.Provider registries) throws IOException {
        String databaseName = "farmupcraft:" + name;
        var original = new DataBase<>(databaseName, keys, values, false, 0, false, registries);
        try {
            original.putData(key, value);
            var expected = original.save(new CompoundTag());
            for (FileFormat format : FileFormat.values()) {
                var restored = new DataBase<>(databaseName, keys, values, false, 0, false, registries);
                try {
                    Path path = directory.resolve(name + format.getExtension());
                    format.getExportStrategy().export(expected, path);
                    restored.load(format.getImportStrategy().importData(path), registries);
                    assertEquals(1, restored.getSize(), databaseName + " via " + format);
                    assertEquals(expected, restored.save(new CompoundTag()), databaseName + " via " + format);
                } finally {
                    restored.shutdown();
                }
            }
        } finally {
            original.shutdown();
        }
    }
}
