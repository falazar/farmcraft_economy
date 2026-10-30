package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.backup.ChecksumUtil;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class DataBaseTest {
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
        Path path = Path.of("build", "test-fixtures", "database-core-" + UUID.randomUUID());
        Files.createDirectories(path);
        directories.add(path);
        return path;
    }

    private DataBase<String, Integer> database(MinecraftServer server) {
        return new DataBase<>("test", new StringDataSerializer(), new IntDataSerializer(),
                false, 0, false, server.registryAccess());
    }

    @Test
    void overwriteRemovalAndDuplicateValuesKeepReverseLookupAndReadsConsistent(MinecraftServer server) {
        try (var database = database(server)) {
            database.putData("a", 1);
            assertEquals(1, database.getData("a"));
            database.putData("a", 2);
            assertNull(database.getKeyByValue(1));
            assertEquals(2, database.getData("a"));
            database.putData("b", 2);
            database.removeDataBatch(List.of("b"));
            assertEquals("a", database.getKeyByValue(2));
            database.putDataBatch(Map.of("a", 3, "c", 4));
            assertNull(database.getKeyByValue(2));
            assertEquals(Map.of("a", 3, "c", 4), database.getDataBatch(List.of("a", "c", "missing")));
            Map<String, Integer> copy = database.getDataMap();
            copy.clear();
            assertEquals(2, database.getSize());
            database.clearDataBase();
            assertNull(database.getKeyByValue(3));
            assertNull(database.getData("a"));
        }
    }

    @Test
    void transactionsAndSavepointsRestoreAllDerivedStateAndDirtyFlag(MinecraftServer server) {
        try (var database = database(server)) {
            database.putData("original", 1);
            database.beginTransaction();
            database.putData("original", 2);
            database.putData("new", 3);
            database.createSavepoint();
            database.clearDataBase();
            database.setDirty(false);
            database.rollbackToSavepoint();
            assertTrue(database.isDirty());
            assertEquals(Map.of("original", 2, "new", 3), database.getDataMap());
            assertEquals("new", database.getKeyByValue(3));
            database.setDirty(false);
            database.rollbackTransaction();
            assertTrue(database.isDirty());
            assertEquals(Map.of("original", 1), database.getDataMap());
            assertNull(database.getKeyByValue(2));
            assertNull(database.getKeyByValue(3));
            database.beginTransaction();
            database.putData("committed", 4);
            database.commitTransaction();
            database.rollbackTransaction();
            assertEquals(4, database.getData("committed"));
        }
    }

    @Test
    void replacementRejectsEveryMalformedEntryBeforeTouchingLiveData(MinecraftServer server) {
        try (var database = database(server); var source = database(server)) {
            database.putData("good", 7);
            source.putData("replacement", 8);
            CompoundTag valid = source.save(new CompoundTag());
            CompoundTag invalid = valid.copy();
            ListTag list = (ListTag) invalid.get("map_entry");
            CompoundTag malformed = new CompoundTag();
            malformed.put("key", new CompoundTag());
            malformed.put("value", new CompoundTag());
            list.add(malformed);
            assertThrows(IllegalArgumentException.class, () -> database.load(invalid, server.registryAccess()));
            assertEquals(Map.of("good", 7), database.getDataMap());
            assertEquals("good", database.getKeyByValue(7));
            assertThrows(IllegalArgumentException.class,
                    () -> database.load(new CompoundTag(), server.registryAccess()));
            CompoundTag wrongName = valid.copy();
            wrongName.putString("database_name", "another");
            assertThrows(IllegalArgumentException.class,
                    () -> database.load(wrongName, server.registryAccess()));
            CompoundTag duplicate = valid.copy();
            ListTag duplicateList = (ListTag) duplicate.get("map_entry");
            duplicateList.add(duplicateList.getFirst().copy());
            assertThrows(IllegalArgumentException.class,
                    () -> database.load(duplicate, server.registryAccess()));
            database.load(valid, server.registryAccess());
            assertEquals(Map.of("replacement", 8), database.getDataMap());
            assertNull(database.getKeyByValue(7));
            assertEquals(8, database.getData("replacement"));
        }
    }

    @Test
    void batchesValidateAtomicallyAndChunkingHandlesEmptyAndHugeBatchSizes(MinecraftServer server) {
        try (var database = database(server)) {
            assertThrows(IllegalArgumentException.class, () -> database.saveChunked(0));
            assertThrows(IllegalArgumentException.class, () -> database.saveChunked(-1));
            assertThrows(IllegalArgumentException.class, () -> database.queryWithBatchProcessing(value -> true, 0));
            assertThrows(IllegalArgumentException.class, () -> database.queryWithPagination(-1, 1));
            assertThrows(IllegalArgumentException.class, () -> database.queryWithPagination(0, 0));
            assertEquals(1, database.saveChunked(3).size());
            assertTrue(((ListTag) database.saveChunked(3).getFirst().get("map_entry")).isEmpty());
            database.putData("good", 7);
            Map<String, Integer> invalid = new HashMap<>();
            invalid.put("new", 9);
            invalid.put("bad", null);
            assertThrows(IllegalArgumentException.class, () -> database.putDataBatch(invalid));
            assertEquals(Map.of("good", 7), database.getDataMap());
            database.putDataBatch(Map.of("a", 1, "b", 2));
            assertEquals(2, database.saveChunked(2).size());
            assertEquals(1, database.saveChunked(Integer.MAX_VALUE).size());
            assertTrue(database.getSerializedSize() > 0);
        }
    }

    @Test
    void expiryWorksImmediatelyOnReadAfterLoadAndDoesNotResurrectOnRollback(MinecraftServer server)
            throws InterruptedException {
        try (var source = database(server);
             var expiring = new DataBase<>("test", new StringDataSerializer(), new IntDataSerializer(),
                     true, 100, false, server.registryAccess())) {
            source.putData("loaded", 1);
            expiring.load(source.save(new CompoundTag()), server.registryAccess());
            assertEquals(1, expiring.getData("loaded"));
            expiring.beginTransaction();
            expiring.createSavepoint();
            expiring.putData("loaded", 2);
            Thread.sleep(150);
            assertNull(expiring.getData("loaded"));
            assertNull(expiring.getKeyByValue(2));
            expiring.rollbackToSavepoint();
            assertFalse(expiring.containsKey("loaded"));
            expiring.rollbackTransaction();
            assertEquals(0, expiring.getSize());
            expiring.getOrCreate("loaded", () -> 3);
            assertEquals(3, expiring.getData("loaded"));
            Thread.sleep(150);
            assertEquals(4, expiring.getOrCreate("loaded", () -> 4));
        }
    }

    @Test
    void queriesAndQueryBuilderUseDetachedSnapshotsAndExpectedBooleanSemantics(MinecraftServer server) {
        try (var database = database(server)) {
            database.putDataBatch(Map.of("one", 1, "two", 2, "three", 3));
            assertEquals(Map.of("two", 2, "three", 3), database.query(value -> value > 1));
            assertEquals(database.query(value -> value > 1), database.queryOptimized(value -> value > 1));
            assertEquals(Map.of("two", 2), database.queryByRange(key -> key.startsWith("t"), value -> value == 2));
            assertEquals(Map.of("three", 3), database.queryWithConditions(
                    List.of(value -> value > 1, value -> value % 2 == 1), true));
            assertEquals(Map.of("two", 2), database.queryByTextSearch("2", Object::toString));
            assertEquals(List.of(3, 2, 1), database.queryAndSort(Comparator.reverseOrder()));
            assertEquals(6, database.queryAndAggregate(value -> value, Integer::sum, 0));
            assertEquals(2, database.queryWithPagination(0, 2).size());
            assertTrue(database.queryWithPagination(3, 2).isEmpty());
            assertEquals(database.query(value -> value > 1), database.queryWithBatchProcessing(value -> value > 1, 1));
            assertEquals(Map.of("two", 2), new QueryBuilder<String, Integer>().or(value -> value == 2).execute(database));
            assertEquals(Map.of("one", 1, "three", 3), new QueryBuilder<String, Integer>()
                    .where(value -> value < 2).or(value -> value > 2).and(value -> value != 2).execute(database));
        }
    }

    @Test
    void asyncFailuresHaveAccurateCallbacksAndDoNotPoisonWorker(MinecraftServer server) throws Exception {
        var database = database(server);
        try {
            CompletableFuture<Boolean> failedPut = new CompletableFuture<>();
            database.putDataAsync(null, 1, failedPut::complete);
            assertFalse(failedPut.get(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> put = new CompletableFuture<>();
            database.putDataAsync("a", 1, put::complete);
            assertTrue(put.get(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> remove = new CompletableFuture<>();
            database.removeDataAsync("a", remove::complete);
            assertTrue(remove.get(5, TimeUnit.SECONDS));
            assertNull(database.getKeyByValue(1));
            CompletableFuture<Boolean> absent = new CompletableFuture<>();
            database.removeDataAsync("a", absent::complete);
            assertFalse(absent.get(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> batch = new CompletableFuture<>();
            database.putDataBatchAsync(Map.of("b", 2), batch::complete);
            assertTrue(batch.get(5, TimeUnit.SECONDS));
            CountDownLatch throwingCallback = new CountDownLatch(1);
            database.queryAsync(values -> {
                throwingCallback.countDown();
                throw new IllegalStateException("test callback failure");
            });
            assertTrue(throwingCallback.await(5, TimeUnit.SECONDS));
            CompletableFuture<List<Integer>> next = new CompletableFuture<>();
            database.queryAsync(next::complete);
            assertEquals(List.of(2), next.get(5, TimeUnit.SECONDS));
            database.shutdown();
            AtomicInteger calls = new AtomicInteger();
            database.putDataAsync("closed", 3, success -> {
                calls.incrementAndGet();
                assertFalse(success);
            });
            assertEquals(1, calls.get());
            CompletableFuture<List<Integer>> rejected = new CompletableFuture<>();
            database.queryAsync(rejected::complete);
            assertTrue(rejected.get(5, TimeUnit.SECONDS).isEmpty());
        } finally {
            database.shutdown();
        }
    }

    @Test
    void concurrentMutationsAndGetOrCreateAreSerialized(MinecraftServer server) throws Exception {
        try (var database = database(server)) {
            AtomicInteger created = new AtomicInteger();
            List<CompletableFuture<Void>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int id = worker;
                workers.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 50; i++) {
                        assertEquals(42, database.getOrCreate("shared", () -> {
                            created.incrementAndGet();
                            return 42;
                        }));
                        database.putData(id + "-" + i, id * 100 + i);
                    }
                }));
            }
            CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertEquals(1, created.get());
            assertEquals(201, database.getSize());
            database.getDataMap().forEach((key, value) ->
                    assertEquals(value, database.getData(database.getKeyByValue(value))));
        }
    }

    @Test
    void importIsAtomicChecksumSuccessIsTrueAndExportFailuresPropagate(MinecraftServer server) throws Exception {
        Path directory = directory();
        try (var source = database(server); var target = database(server)) {
            source.putData("imported", 12);
            for (FileFormat format : FileFormat.values()) {
                Path path = directory.resolve("snapshot" + format.getExtension());
                source.exportDataBase(path, format);
                ChecksumUtil.saveChecksum(path, ChecksumUtil.generateChecksum(path));
                target.putData("obsolete", 99);
                assertTrue(target.importWithCheckSumValidation(path, format, server.registryAccess()));
                assertEquals(Map.of("imported", 12), target.getDataMap());
                assertNull(target.getKeyByValue(99));
                assertTrue(target.isDirty());
                Files.writeString(Path.of(path + ".sha256"), "bad");
                assertFalse(target.importWithCheckSumValidation(path, format, server.registryAccess()));
                CompoundTag bad = source.save(new CompoundTag());
                bad.put("map_entry", new CompoundTag());
                format.getExportStrategy().export(bad, path);
                assertFalse(target.importDataBase(path, format, server.registryAccess()));
                assertEquals(Map.of("imported", 12), target.getDataMap());
            }
            Path blocked = directory.resolve("not-a-directory");
            Files.writeString(blocked, "file");
            assertThrows(UncheckedIOException.class,
                    () -> source.exportDataBase(blocked.resolve("snapshot"), FileFormat.NBT));
        }
    }
}
