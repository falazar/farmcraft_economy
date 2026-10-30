package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.*;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ModEventsTest {
    private static final UUID ID = UUID.fromString("12345678-1234-5678-9876-123456789abc");
    private static final Set<String> NAMES = Set.of("biome_rules_database", "player_database",
            "chunk_data_database", "village_database", "goods_data_database_new", "world_data_database",
            "npc_database", "game_structure_database", "game_zone_database");
    private final List<Path> directories = new ArrayList<>();

    @BeforeEach
    void registerOnlyLegacyAccesses() {
        DataBaseManager.clear();
        ModEvents.registerDataBases();
    }

    @AfterEach
    void restoreRegistrationsAndCleanStorage() throws IOException {
        DataBaseManager.clear();
        ModEvents.registerDataBases();
        for (Path directory : directories) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @Test
    void nineLegacyIdentitiesRetainSerializerTypesDefaultsAndSyncFlags(MinecraftServer server) throws Exception {
        assertEquals(NAMES, DataBaseManager.getDataBases().stream()
                .map(access -> access.getDatabaseName().getPath()).collect(Collectors.toSet()));
        assertEquals(9, DataBaseManager.getDataBasesList().size());
        Map<String, Class<?>> keyTypes = Map.of(
                "biome_rules_database", VersionedDataSerializer.class,
                "player_database", UUIDDataSerializer.class,
                "chunk_data_database", LongDataSerializer.class,
                "village_database", UUIDDataSerializer.class,
                "goods_data_database_new", StringDataSerializer.class,
                "world_data_database", IntDataSerializer.class,
                "npc_database", UUIDDataSerializer.class,
                "game_structure_database", LongDataSerializer.class,
                "game_zone_database", UUIDDataSerializer.class);
        for (var access : DataBaseManager.getDataBases()) {
            String name = access.getDatabaseName().getPath();
            assertEquals("farmupcraft", access.getDatabaseName().getNamespace());
            assertInstanceOf(keyTypes.get(name), access.getKeySerializer());
            assertInstanceOf(CodecDataSerializer.class, access.getValueSerializer());
            assertFalse(access.isEnableExpiry());
            assertEquals(0, access.getExpiryDuration());
            assertFalse(access.isShouldBackup());
            assertEquals(0, access.getBackupInterval());
            assertEquals(6, access.getBackupCount());
            assertEquals(604_800_000L, access.getBackupRemovalTime());
            assertEquals(FileFormat.NBT, access.getBackUpFileFormat());
            assertEquals(!Set.of("biome_rules_database", "world_data_database").contains(name), access.shouldSync());
        }
        Set<Identifier> syncIds = NAMES.stream()
                .filter(name -> !Set.of("biome_rules_database", "world_data_database").contains(name))
                .map(ModEventsTest::id).collect(Collectors.toSet());
        assertEquals(syncIds, DataBaseManager.getDataBasesToSync());

        int identities = 0;
        for (var field : ModEvents.class.getDeclaredFields()) {
            if (field.getType() != DataBaseAccess.class) continue;
            field.setAccessible(true);
            DataBaseAccess<?, ?> access = (DataBaseAccess<?, ?>) field.get(null);
            assertSame(access, DataBaseManager.getDataBaseAccess(access.getDatabaseName()));
            identities++;
        }
        assertEquals(9, identities);
        ModEvents.registerDataBases();
        assertEquals(9, DataBaseManager.getDataBases().size(), "Registration is idempotent");

        Holder<Biome> plains = biome(server);
        DataBaseAccess<Holder<Biome>, BiomeRulesInstance> rules = access("biome_rules_database");
        CompoundTag encodedKey = rules.getKeySerializer().serialize(plains, server.registryAccess());
        assertEquals(1, encodedKey.getInt("version").orElseThrow());
        assertEquals("minecraft:plains", encodedKey.getCompound("versioned").orElseThrow()
                .getString("holder_key").orElseThrow());
        assertSame(plains, rules.getKeySerializer().deserialize(encodedKey, server.registryAccess()));
    }

    @Test
    void allNineRegisteredConfigurationsPersistThroughRealStorageCloseAndReopen(MinecraftServer server)
            throws IOException {
        // The official fixture has registries, but no ServerLevels. Do not stub a world:
        // level routing, player-name/home-village resolution, and live startup loading need a GameTest.
        assertNull(server.overworld());
        Path directory = Path.of("build", "economy-database-test-storage", UUID.randomUUID().toString());
        Files.createDirectories(directory);
        directories.add(directory);
        Map<String, CompoundTag> saved = new HashMap<>();
        try (SavedDataStorage storage = storage(server, directory)) {
            save(storage, server, "biome_rules_database", biome(server), BiomeRulesInstance.EMPTY, saved);
            save(storage, server, "player_database", ID, new PlayerData(17, ID, null, 25), saved);
            save(storage, server, "chunk_data_database", 42L, new ChunkData("farm", 17, ID), saved);
            save(storage, server, "village_database", ID,
                    new VillageData(ID, "Home", new ChunkPos(3, -4), 2, List.of(new ChunkPos(3, -4)),
                            true, 50, "2026-10-28", "cow", "2026-10-27", "Founder", List.of(ID)), saved);
            save(storage, server, "goods_data_database_new", "minecraft:wheat",
                    new GoodsData("minecraft:wheat", 9, 12, "common", true, "food", "2026-10-28"), saved);
            save(storage, server, "world_data_database", 0, new WorldData(), saved);
            save(storage, server, "npc_database", ID, new NpcData(ID, "Test", ID, "Farmer"), saved);
            save(storage, server, "game_structure_database", 42L,
                    new GameStructureData(42L, "Barn", BlockPos.ZERO, "barn"), saved);
            save(storage, server, "game_zone_database", ID,
                    new GameZone(ID, "Forest", List.of(SectionPos.of(1, 2, 3)),
                            GameZone.ZoneType.FOREST, BlockPos.ZERO), saved);
            storage.saveAndJoin();
        }
        assertEquals(NAMES, saved.keySet());
        for (String name : NAMES) {
            Path file = id(name).withSuffix(".dat").resolveAgainst(directory);
            assertTrue(Files.isRegularFile(file), name);
            CompoundTag disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            assertTrue(disk.contains("DataVersion"));
            assertEquals(saved.get(name), disk.get("data"), name);
            assertEquals("farmupcraft:" + name, saved.get(name).getString("database_name").orElseThrow());
            assertEquals(Set.of("database_name", "map_entry"), saved.get(name).keySet());
            assertEquals(1, saved.get(name).getListOrEmpty("map_entry").size());
        }
        try (SavedDataStorage reopened = storage(server, directory)) {
            for (String name : NAMES) reload(reopened, server, name, saved.get(name));
        }
    }

    @Test
    void registryOnlyLifecycleNeverRequiresAnOverworldOrCreatesFakeLevels(MinecraftServer server) {
        assertNull(server.overworld());
        assertDoesNotThrow(() -> ModEvents.serverStarted(new ServerStartedEvent(server)));
        assertThrows(IllegalStateException.class, ModEvents::getPlayerDatabase);
        DataBaseAccess<UUID, PlayerData> players = access("player_database");
        assertThrows(NullPointerException.class, () -> ModEvents.getDatabase(players, null));
        assertDoesNotThrow(() -> ModEvents.serverStopping(new ServerStoppingEvent(server)));
        assertDoesNotThrow(() -> ModEvents.serverStopped(new ServerStoppedEvent(server)));
        assertEquals(9, DataBaseManager.getDataBases().size(), "Shutdown retains registration for server restarts");
        assertDoesNotThrow(() -> ModEvents.serverStarted(new ServerStartedEvent(server)));
        assertDoesNotThrow(() -> ModEvents.serverStopped(new ServerStoppedEvent(server)));
    }

    private static <M, V> void save(SavedDataStorage storage, MinecraftServer server, String name,
                                    M key, V value, Map<String, CompoundTag> saved) {
        DataBaseAccess<M, V> access = access(name);
        var type = DataBase.savedDataType(access.getDatabaseName().toString(), access.getKeySerializer(),
                access.getValueSerializer(), access.isEnableExpiry(), access.getExpiryDuration(),
                access.shouldSync(), server.registryAccess());
        try (DataBase<M, V> database = storage.computeIfAbsent(type)) {
            assertSame(database, storage.computeIfAbsent(type));
            database.putData(key, value);
            assertSame(value, database.getData(key));
            saved.put(name, database.save(new CompoundTag()));
        }
    }

    private static <M, V> void reload(SavedDataStorage storage, MinecraftServer server, String name,
                                     CompoundTag expected) {
        DataBaseAccess<M, V> access = access(name);
        var type = DataBase.savedDataType(access.getDatabaseName().toString(), access.getKeySerializer(),
                access.getValueSerializer(), access.isEnableExpiry(), access.getExpiryDuration(),
                access.shouldSync(), server.registryAccess());
        try (DataBase<M, V> database = storage.get(type)) {
            assertNotNull(database, name);
            assertEquals(1, database.getSize(), name);
            assertEquals(expected, database.save(new CompoundTag()), name);
        }
    }

    private static <M, V> DataBaseAccess<M, V> access(String name) {
        DataBaseAccess<M, V> access = DataBaseManager.getDataBaseAccess(id(name));
        assertNotNull(access, name);
        return access;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("farmupcraft", path);
    }

    private static Holder<Biome> biome(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains")));
    }

    private static SavedDataStorage storage(MinecraftServer server, Path directory) {
        return new SavedDataStorage(null, directory, DataFixers.getDataFixer(), server.registryAccess());
    }
}
