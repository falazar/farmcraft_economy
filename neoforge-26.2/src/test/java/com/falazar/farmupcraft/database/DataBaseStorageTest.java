package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.serializers.HolderDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class DataBaseStorageTest {
    private final List<Path> directories = new ArrayList<>();

    @AfterEach
    void cleanFixtures() throws IOException {
        for (Path directory : directories) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private Path directory() throws IOException {
        Path path = Path.of("build", "test-fixtures", "database-storage-" + UUID.randomUUID());
        Files.createDirectories(path);
        directories.add(path);
        return path;
    }

    private static SavedDataStorage storage(MinecraftServer server, Path directory) {
        return new SavedDataStorage(null, directory, DataFixers.getDataFixer(), server.registryAccess());
    }

    private static SavedDataType<DataBase<String, Holder<Biome>>> type(MinecraftServer server) {
        return DataBase.savedDataType("registry_database", new StringDataSerializer(),
                new HolderDataSerializer<>(Registries.BIOME), false, 0, false, server.registryAccess());
    }

    private static Holder<Biome> plains(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
    }

    private static CompoundTag legacyPayload() {
        CompoundTag payload = new CompoundTag();
        payload.putString("database_name", "registry_database");
        CompoundTag key = new CompoundTag();
        key.putString("string_value", "home");
        CompoundTag value = new CompoundTag();
        value.putString("holder_key", "minecraft:plains");
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.put("value", value);
        ListTag list = new ListTag();
        list.add(entry);
        payload.put("map_entry", list);
        return payload;
    }

    @Test
    void legacyPayloadUsesActualRegistriesAndRoundTripsExactly(MinecraftServer server) {
        var type = type(server);
        var codec = type.codecFactory().create(null);
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        try (var database = codec.parse(ops, legacyPayload()).getOrThrow()) {
            assertEquals(Map.of("home", plains(server)), database.getDataMap());
            assertEquals("home", database.getKeyByValue(plains(server)));
            assertEquals(legacyPayload(), codec.encodeStart(ops, database).getOrThrow());
            assertFalse(database.isDirty());
        }
        CompoundTag bad = legacyPayload();
        CompoundTag entry = (CompoundTag) ((ListTag) bad.get("map_entry")).getFirst();
        ((CompoundTag) entry.get("value")).putString("holder_key", "minecraft:not_a_real_biome");
        assertTrue(codec.parse(ops, bad).error().isPresent());
    }

    @Test
    void storageCodecUsesRegistryOpsRatherThanAnUnrelatedFallbackProvider(MinecraftServer server) {
        var type = DataBase.savedDataType("registry_database", new StringDataSerializer(),
                new HolderDataSerializer<>(Registries.BIOME), false, 0, false,
                HolderLookup.Provider.create(Stream.empty()));
        var codec = type.codecFactory().create(null);
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        try (var database = codec.parse(ops, legacyPayload()).getOrThrow()) {
            assertEquals(plains(server), database.getData("home"));
            assertEquals(legacyPayload(), codec.encodeStart(ops, database).getOrThrow());
        }
    }

    @Test
    void realSavedDataStorageWritesLegacyNbtAndReloadsRegistryValuesWithoutALevel(MinecraftServer server)
            throws IOException {
        assertNull(server.overworld());
        Path directory = directory();
        var type = type(server);
        try (var storage = storage(server, directory)) {
            try (var database = storage.computeIfAbsent(type)) {
                assertSame(database, storage.computeIfAbsent(type(server)));
                database.putData("home", plains(server));
                assertTrue(database.isDirty());
                storage.saveAndJoin();
                assertFalse(database.isDirty());
            }
        }
        Path file = type.id().withSuffix(".dat").resolveAgainst(directory);
        assertTrue(Files.isRegularFile(file));
        CompoundTag disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertEquals(legacyPayload(), disk.get("data"));
        assertTrue(disk.contains("DataVersion"));
        try (var reopened = storage(server, directory)) {
            try (var loaded = reopened.get(type)) {
                assertNotNull(loaded);
                assertFalse(loaded.isDirty());
                assertEquals(plains(server), loaded.getData("home"));
                assertEquals("home", loaded.getKeyByValue(plains(server)));
                loaded.removeDataBatch(List.of("home"));
                reopened.saveAndJoin();
            }
        }
        try (var reopened = storage(server, directory)) {
            try (var loaded = reopened.get(type)) {
                assertEquals(0, loaded.getSize());
                assertNull(loaded.getKeyByValue(plains(server)));
            }
        }
    }

    @Test
    void sameNamedDatabasesInIndependentStoresNeverShareState(MinecraftServer server) throws IOException {
        Path first = directory();
        Path second = directory();
        var type = type(server);
        try (var a = storage(server, first); var b = storage(server, second)) {
            try (var worldA = a.computeIfAbsent(type); var worldB = b.computeIfAbsent(type)) {
                assertNotSame(worldA, worldB);
                worldA.putData("home", plains(server));
                assertEquals(0, worldB.getSize());
                assertNull(worldB.getKeyByValue(plains(server)));
                worldB.putData("other", plains(server));
                worldA.clearDataBase();
                assertEquals(Map.of("other", plains(server)), worldB.getDataMap());
                a.saveAndJoin();
                b.saveAndJoin();
            }
        }
        try (var a = storage(server, first); var b = storage(server, second)) {
            try (var worldA = a.get(type); var worldB = b.get(type)) {
                assertEquals(0, worldA.getSize());
                assertEquals(Map.of("other", plains(server)), worldB.getDataMap());
            }
        }
    }
}
