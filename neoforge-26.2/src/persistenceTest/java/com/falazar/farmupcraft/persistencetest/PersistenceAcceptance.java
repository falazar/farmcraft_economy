package com.falazar.farmupcraft.persistencetest;

import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.*;
import com.falazar.farmupcraft.database.backup.ChecksumUtil;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Dev-source-set only. Two independent JVMs must use phases "seed" then "verify".
 * No mock storage, direct backup submission, scheduler clock changes, or player connection is used.
 */
@EventBusSubscriber(modid = "farmupcraft")
public final class PersistenceAcceptance {
    public static final String MARKER = "farmupcraft persistence acceptance v1";
    public static final String ENABLE_PROPERTY = "farmupcraft.persistence.enabled";
    private static final UUID PLAYER = UUID.fromString("26020000-0000-0000-0000-000000000001");
    private static final UUID VILLAGE = UUID.fromString("26020000-0000-0000-0000-000000000002");
    private static final UUID NPC = UUID.fromString("26020000-0000-0000-0000-000000000003");
    private static final UUID ZONE = UUID.fromString("26020000-0000-0000-0000-000000000004");
    private static final ChunkPos CHUNK = new ChunkPos(23, -17);
    private static final BlockPos CENTER = new BlockPos(376, 72, -264);
    private static final long STRUCTURE = CENTER.asLong();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Set<Path>> PREVIOUS_BACKUPS = new TreeMap<>();
    private static final Map<String, DataBase<?, ?>> DATABASES = new TreeMap<>();
    private static String phase;
    private static Path root;
    private static Path evidence;
    private static long deadline;
    private static boolean active;
    private static volatile Path freshSeedRoot;

    private PersistenceAcceptance() {}

    @SubscribeEvent
    public static void preflight(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        if (!System.getProperty("farmupcraft.persistence.phase", "").equals("seed")) return;
        try {
            Path checkedRoot = requireDisposableRoot();
            require(!Files.exists(checkedRoot.resolve("world").resolve("level.dat"))
                            && !Files.exists(checkedRoot.resolve("world").resolve("level.dat_old")),
                    "seed refuses an existing world at common-setup preflight");
            freshSeedRoot = checkedRoot;
        } catch (Exception failure) {
            throw new IllegalStateException("Persistence seed preflight failed before world initialization", failure);
        }
    }

    private static Path requireDisposableRoot() throws Exception {
        Path checkedRoot = Path.of(System.getProperty("farmupcraft.persistence.root")).toAbsolutePath().normalize();
        require(checkedRoot.getFileName().toString().equals("persistence-acceptance")
                        && checkedRoot.getParent().getFileName().toString().equals("build")
                        && checkedRoot.getParent().getParent().getFileName().toString().equals("neoforge-26.2"),
                "only neoforge-26.2/build/persistence-acceptance is permitted");
        require(checkedRoot.toRealPath().equals(FMLPaths.GAMEDIR.get().toRealPath()),
                "game directory must be the disposable root");
        for (Path path = checkedRoot; path != null; path = path.getParent()) {
            require(!Files.isSymbolicLink(path), "disposable root cannot traverse symlinks");
        }
        require(!Files.isSymbolicLink(checkedRoot.resolve("world")), "world must not be a symlink");
        require(Files.readString(checkedRoot.resolve("ACCEPTANCE_DISPOSABLE")).strip().equals(MARKER),
                "missing disposable opt-in marker");
        return checkedRoot;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void configure(ServerAboutToStartEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        String requested = System.getProperty("farmupcraft.persistence.phase", "");
        try {
            require(requested.equals("seed") || requested.equals("verify"), "phase must be seed or verify");
            phase = requested;
            root = requireDisposableRoot();
            require(event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .equals(root.resolve("world")), "configured world must be exactly disposable root/world");
            evidence = root.resolve("evidence");
            Files.createDirectories(evidence);
            require(!Files.exists(evidence.resolve(phase + "-complete.json")), "phase already completed; use a fresh disposable root");
            if (phase.equals("seed")) {
                // 26.2 writes level.dat before ServerAboutToStartEvent; check the earlier attestation instead.
                require(root.equals(freshSeedRoot), "seed requires a fresh-world common-setup preflight");
            } else {
                JsonObject seed = JsonParser.parseString(Files.readString(evidence.resolve("seed-complete.json")))
                        .getAsJsonObject();
                require(seed.get("pid").getAsLong() != ProcessHandle.current().pid(),
                        "verify requires a separate JVM/server lifecycle");
                require(seed.get("jvmStartedAt").getAsLong()
                        != ManagementFactory.getRuntimeMXBean().getStartTime(), "verify requires a new JVM");
                require(Files.isRegularFile(root.resolve("world").resolve("level.dat")), "missing saved world");
            }
            List<DataBaseAccess<?, ?>> accesses = DataBaseManager.getDataBases();
            require(accesses.size() == 9, "expected exactly nine production databases");
            for (DataBaseAccess<?, ?> access : accesses) {
                require(!access.isShouldBackup(), "production backup defaults unexpectedly changed");
                enableDisposableBackups(access);
            }
        } catch (Exception failure) {
            fail(event.getServer(), failure);
        }
    }

    private static <K, V> void enableDisposableBackups(DataBaseAccess<K, V> original) {
        DataBaseBuilder<K, V> builder = new DataBaseBuilder<K, V>(original.getDatabaseName())
                .setKeySerializer(original.getKeySerializer()).setValueSerializer(original.getValueSerializer())
                .enableExpiry(original.isEnableExpiry(), original.getExpiryDuration())
                .setBackUpTime(1).setBackupCount(2).setBackupRemovalTime(60_000)
                .setBackUpFileFormat(FileFormat.JSON);
        if (original.shouldSync()) builder.autoSync();
        DataBaseManager.unregisterDataBaseAccess(original.getDatabaseName());
        DataBaseManager.registerDataBaseAccess(original.getDatabaseName(), builder.build());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == null) return;
        try {
            MinecraftServer server = event.getServer();
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(root.resolve("world").toRealPath()),
                    "world must be exactly the disposable root/world");
            require(server.getPlayerList().getPlayerCount() == 0, "acceptance world must not have connected players");
            for (DataBaseAccess<?, ?> access : DataBaseManager.getDataBases()) {
                String name = access.getDatabaseName().toString();
                DATABASES.put(name, access.get(server.overworld()));
                PREVIOUS_BACKUPS.put(name, backups(server, access.getDatabaseName()));
            }
            if (phase.equals("seed")) seed(server);
            assertFixtures(server);
            for (var entry : DATABASES.entrySet()) {
                Path expected = expectedPath(entry.getKey());
                String actual = snapshot(entry.getValue());
                if (phase.equals("seed")) {
                    require(!Files.exists(expected), "refusing to overwrite seed evidence");
                    Files.writeString(expected, actual, StandardCharsets.UTF_8);
                } else {
                    require(Files.readString(expected).equals(actual),
                            "populated restart snapshot mismatch: " + entry.getKey());
                }
            }
            deadline = System.nanoTime() + 45_000_000_000L;
            active = true;
        } catch (Exception failure) {
            fail(event.getServer(), failure);
        }
    }

    private static void seed(MinecraftServer server) {
        DATABASES.values().forEach(DataBase::clearDataBase);
        var level = server.overworld();
        ModEvents.getVillageDatabase(level).putData(VILLAGE, new VillageData(VILLAGE, "Acceptance Village", CHUNK,
                3, List.of(CHUNK), true, 941, "2026-01-02", "cow", "2026-01-01",
                "Acceptance Founder", List.of(PLAYER), "cow=minecraft:wheat", List.of()));
        ModEvents.getPlayerDatabase(level).putData(PLAYER, new PlayerData(71, PLAYER, VILLAGE, 137, true, "green"));
        ChunkData chunk = new ChunkData("farm", 71, VILLAGE, PLAYER, List.of("Acceptance Visitor"), 72);
        chunk.setPlotLevel(2);
        ModEvents.getChunkDataDatabase(level).putData(CHUNK.pack(), chunk);
        ModEvents.getNpcDatabase(level).putData(NPC, new NpcData(NPC, "Acceptance Farmer", VILLAGE,
                "Persistent fixture", "calm", "Acceptance Village", false, "nonhostile", ""));
        ModEvents.getGoodsDataDatabase(level).putData("minecraft:wheat",
                new GoodsData("minecraft:wheat", 17, 29, "common", true, "daily", "2026-01-02"));
        ModEvents.getWorldDataDatabase(level).putData(0, new WorldData(314, "2026-01-02", false, true, false, false));
        ModEvents.getGameStructureDatabase(level).putData(STRUCTURE,
                new GameStructureData(STRUCTURE, "Acceptance Barn", CENTER, "barn", true, true,
                        CENTER.offset(-3, -1, -3), CENTER.offset(3, 3, 3), List.of(CHUNK.pack()),
                        CENTER, true, 1, List.of(CENTER)));
        ModEvents.getGameZoneDatabase(level).putData(ZONE, new GameZone(ZONE, "Acceptance Farm Zone",
                List.of(SectionPos.of(CHUNK, 4)), GameZone.ZoneType.COUNTRY, CENTER));
        ModEvents.getBiomeRulesDatabase().putData(level.registryAccess().getOrThrow(Biomes.PLAINS),
                new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.WHEAT, Items.CARROT)));
    }

    private static void assertFixtures(MinecraftServer server) {
        var level = server.overworld();
        DATABASES.forEach((name, database) -> require(database.getSize() == 1, "expected one entry in " + name));
        PlayerData player = Objects.requireNonNull(ModEvents.getPlayerDatabase(level).getData(PLAYER));
        VillageData village = Objects.requireNonNull(ModEvents.getVillageDatabase(level).getData(VILLAGE));
        ChunkData chunk = Objects.requireNonNull(ModEvents.getChunkDataDatabase(level).getData(CHUNK.pack()));
        NpcData npc = Objects.requireNonNull(ModEvents.getNpcDatabase(level).getData(NPC));
        require(player.getCoins() == 137 && player.getId() == 71 && player.getPlayerUUID().equals(PLAYER)
                && player.getHomeVillageUUID().equals(VILLAGE), "player key/balance/home reference");
        require(village.getUUID().equals(VILLAGE) && village.getCoins() == 941 && village.getLevel() == 3
                && village.getMemberUUIDs().equals(List.of(PLAYER))
                && village.getClaimedChunks().equals(List.of(CHUNK)), "village members/claims/balance");
        require(chunk.getOwnerUUID().equals(PLAYER) && chunk.getVillageId().equals(VILLAGE)
                && chunk.getPlayerId() == player.getId() && chunk.getPlotLevel() == 2, "chunk ownership references");
        require(npc.getUUID().equals(NPC) && npc.getHomeVillageUUID().equals(VILLAGE)
                && npc.getVillageName().equals(village.getName()), "NPC home reference");
        require(ModEvents.getGoodsDataDatabase(level).getData("minecraft:wheat").getCost() == 17
                && ModEvents.getGoodsDataDatabase(level).getData("minecraft:wheat").getAmountSold() == 29,
                "market values");
        require(ModEvents.getWorldData(level).getUpkeepTick() == 314, "world upkeep counter");
        require(ModEvents.getGameStructureDatabase(level).getData(STRUCTURE).getChunkPositions().equals(List.of(CHUNK.pack()))
                && ModEvents.getGameZoneDatabase(level).getData(ZONE).getChunkPositions().equals(List.of(CHUNK)),
                "structure and zone claimed-chunk references");
        require(ModEvents.getBiomeRulesDatabase().getData(level.registryAccess().getOrThrow(Biomes.PLAINS))
                .getCrops(level).equals(List.of(Items.WHEAT, Items.CARROT)), "persisted biome crops");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !active) return;
        try {
            MinecraftServer server = event.getServer();
            require(System.nanoTime() < deadline, "timed out waiting for real scheduled backup files/checksums");
            Map<String, Path> completed = new TreeMap<>();
            for (String name : DATABASES.keySet()) {
                Set<Path> candidates = backups(server, Identifier.parse(name));
                candidates.removeAll(PREVIOUS_BACKUPS.get(name));
                Path found = null;
                for (Path candidate : candidates.stream().sorted().toList()) {
                    Path checksum = Path.of(candidate + ".sha256");
                    if (Files.isRegularFile(checksum) && ChecksumUtil.verifyChecksum(candidate, checksum)) {
                        String content = canonical(JsonParser.parseString(Files.readString(candidate))).toString();
                        require(content.equals(Files.readString(expectedPath(name))), "scheduled backup content: " + name);
                        found = candidate;
                        break;
                    }
                }
                if (found == null) return;
                completed.put(name, found);
            }
            JsonObject report = new JsonObject();
            report.addProperty("status", "PASS");
            report.addProperty("phase", phase);
            report.addProperty("pid", ProcessHandle.current().pid());
            report.addProperty("jvmStartedAt", ManagementFactory.getRuntimeMXBean().getStartTime());
            report.addProperty("observedAt", Instant.now().toString());
            report.addProperty("world", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
            report.addProperty("backupIntervalSeconds", 1);
            report.addProperty("backupCount", 2);
            JsonObject tables = new JsonObject();
            for (var entry : completed.entrySet()) {
                DataBase<?, ?> database = DATABASES.get(entry.getKey());
                if (phase.equals("verify")) {
                    database.clearDataBase();
                    require(database.getSize() == 0, "restore precondition must be empty");
                    require(database.importWithCheckSumValidation(entry.getValue(), FileFormat.JSON, server.overworld()),
                            "actual checksum-validated restore failed: " + entry.getKey());
                    require(snapshot(database).equals(Files.readString(expectedPath(entry.getKey()))),
                            "restored populated payload differs: " + entry.getKey());
                }
                JsonObject table = new JsonObject();
                table.addProperty("entries", database.getSize());
                table.addProperty("backup", entry.getValue().toString());
                table.addProperty("backupSha256", ChecksumUtil.generateChecksum(entry.getValue()));
                table.addProperty("expectedCanonicalPayload", expectedPath(entry.getKey()).toString());
                table.addProperty("expectedCanonicalSha256", ChecksumUtil.generateChecksum(expectedPath(entry.getKey())));
                table.addProperty("restored", phase.equals("verify"));
                tables.add(entry.getKey(), table);
            }
            assertFixtures(server);
            report.add("databases", tables);
            require(server.saveEverything(true, true, true), "real world save failed");
            JsonObject exports = new JsonObject();
            for (var entry : Map.of("players", "player_database", "villages", "village_database",
                    "chunks", "chunk_data_database").entrySet()) {
                Path exported = root.resolve("farmupcraft-data").resolve(entry.getKey() + ".json");
                require(Files.isRegularFile(exported), "world-save export missing: " + exported);
                require(canonical(JsonParser.parseString(Files.readString(exported))).toString()
                                .equals(Files.readString(expectedPath("farmupcraft:" + entry.getValue()))),
                        "populated world-save export differs: " + exported);
                exports.addProperty(exported.toString(), ChecksumUtil.generateChecksum(exported));
            }
            report.add("worldSaveExportsSha256", exports);
            Files.writeString(evidence.resolve(phase + "-complete.json"), JSON.toJson(report), StandardCharsets.UTF_8);
            System.out.println("PERSISTENCE_ACCEPTANCE_" + phase.toUpperCase(Locale.ROOT) + "_PASS");
            active = false;
            server.halt(false);
        } catch (Exception failure) {
            fail(event.getServer(), failure);
        }
    }

    private static Set<Path> backups(MinecraftServer server, Identifier name) throws Exception {
        Path directory = DataBaseFileUtil.getBackupDirectory(server, name);
        require(directory.toAbsolutePath().normalize().startsWith(root), "backup escaped disposable root");
        if (!Files.isDirectory(directory)) return new HashSet<>();
        try (var files = Files.list(directory)) {
            return new HashSet<>(files.filter(path -> path.getFileName().toString().endsWith(".json")).toList());
        }
    }

    private static Path expectedPath(String name) {
        return evidence.resolve("expected-" + name.replace(':', '-') + ".json");
    }

    private static String snapshot(DataBase<?, ?> database) {
        return canonical(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, database.save(new CompoundTag()))).toString();
    }

    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            element.getAsJsonObject().keySet().stream().sorted()
                    .forEach(key -> sorted.add(key, canonical(element.getAsJsonObject().get(key))));
            return sorted;
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(item -> array.add(canonical(item)));
            return array;
        }
        return element;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private static void fail(MinecraftServer server, Exception failure) {
        active = false;
        System.err.println("PERSISTENCE_ACCEPTANCE_FAILED: " + failure);
        failure.printStackTrace();
        if (evidence != null) {
            try {
                Files.writeString(evidence.resolve((phase == null ? "unknown" : phase) + "-FAILED.txt"),
                        failure.toString(), StandardCharsets.UTF_8);
            } catch (Exception writeFailure) {
                failure.addSuppressed(writeFailure);
            }
        }
        server.halt(false);
        throw new IllegalStateException("Persistence acceptance failed", failure);
    }
}
