package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.backup.ChecksumUtil;
import com.falazar.farmupcraft.database.fileformats.ExportFormatStrategy;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.fileformats.ImportFormatStrategy;
import com.falazar.farmupcraft.database.message.DataBaseEntryS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.*;
import java.util.stream.Collectors;

import static com.google.common.io.Files.getFileExtension;

public class DataBase<M, V> extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<M, V> data = new ConcurrentHashMap<>();
    private final Map<V, M> index = new ConcurrentHashMap<>(); // Index map
    private final Map<M, Long> expiryMap = new ConcurrentHashMap<>();
    private final Map<M, V> cache = new LinkedHashMap<M, V>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<M, V> eldest) {
            return size() > 100; // Keep cache size to 100 entries
        }
    };

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    private final DataSerializer<M> keySerializer;
    private final DataSerializer<V> valueSerializer;
    private final boolean enableExpiry;
    private final long expiryDuration; // in milliseconds

    private final Stack<Map<M, V>> transactionStack = new Stack<>();

    private final Stack<Map<M, V>> savepoints = new Stack<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private final boolean autoSync;


    private final String databaseName;

    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String MAP_ENTRY = "map_entry";
    private static final String DATABASE_NAME_TAG = "database_name";

    public static <M, V> DataBase<M, V> get(Level level, String key, DataSerializer<M> keySerializer, DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration, boolean autoSync) {
        // Make sure we are always in overworld storage!
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = ((ServerLevel) overworld).getDataStorage();
        return storage.computeIfAbsent(e -> new DataBase<>(e, key, overworld, keySerializer, valueSerializer, enableExpiry, expiryDuration, autoSync),
                () -> new DataBase<>(key, keySerializer, valueSerializer, enableExpiry, expiryDuration, autoSync), key);
    }


    public DataBase(String databaseName, DataSerializer<M> keySerializer, DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration, boolean autoSync) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.enableExpiry = enableExpiry;
        this.expiryDuration = expiryDuration;
        this.databaseName = databaseName;
        this.autoSync = autoSync;
        setExpiration();
        //LOGGER.info("Initialized DataBase {} with expiryEnabled={}, expiryDuration={}", databaseName, enableExpiry, expiryDuration);
    }

    public DataBase(CompoundTag nbt, String databaseName, ServerLevel level, DataSerializer<M> keySerializer, DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration, boolean autoSync) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.enableExpiry = enableExpiry;
        this.expiryDuration = expiryDuration;
        this.databaseName = databaseName;
        this.autoSync = autoSync;
        setExpiration();
        load(nbt, level);
        LOGGER.info("Initialized DataBase {} with expiryEnabled={}, expiryDuration={}", databaseName, enableExpiry, expiryDuration);
    }

    public void load(CompoundTag compoundTag, Level level) {
        load(compoundTag, level, false);
    }


    private void load(CompoundTag nbt, Level level, boolean setDirty) {
        Map<M, V> map = loadDataToMap(nbt, level);
        Map<V, M> newIndexMap = createIndexMap(map);
        data.putAll(map);
        index.putAll(newIndexMap);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[{}] Data loaded: {} entries", databaseName, data.size());
        }
        if (setDirty) {
            setDirty();
        }
    }


    private Map<M, V> loadDataToMap(CompoundTag nbt, Level level) {
        Map<M, V> newDataMap = new HashMap<>();
        ListTag listTag = nbt.getList(MAP_ENTRY, Tag.TAG_COMPOUND);
        for (Tag tag : listTag) {
            CompoundTag entryTag = (CompoundTag) tag;
            CompoundTag keyCompound = entryTag.getCompound(KEY);
            CompoundTag valueCompound = entryTag.getCompound(VALUE);

            M key = keySerializer.deserialize(keyCompound, level);
            V value = valueSerializer.deserialize(valueCompound, level);
            if (key == null || value == null) continue;

            newDataMap.put(key, value);
        }
        return newDataMap;
    }


    private Map<V, M> createIndexMap(Map<M, V> dataMap) {
        Map<V, M> newIndexMap = new HashMap<>();
        for (Map.Entry<M, V> entry : dataMap.entrySet()) {
            newIndexMap.put(entry.getValue(), entry.getKey());
        }
        return newIndexMap;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {

        ListTag listTag = new ListTag();
        tag.putString(DATABASE_NAME_TAG, databaseName);
        for (Map.Entry<M, V> entry : data.entrySet()) {
            CompoundTag compoundTag = new CompoundTag();
            CompoundTag key = keySerializer.serialize(entry.getKey());
            CompoundTag value = valueSerializer.serialize(entry.getValue());
            if (key == null || value == null) continue;

            compoundTag.put(KEY, key);
            compoundTag.put(VALUE, value);
            listTag.add(compoundTag);
        }
        tag.put(MAP_ENTRY, listTag);

        LOGGER.info("[{}] Data saved: {} entries", databaseName, listTag.size());
        return tag;

    }


    public void putData(M key, V value) {
        readWriteLock.writeLock().lock();
        try {
            if (key == null || value == null) {
                LOGGER.error("[{}] Null key or value encountered", databaseName);
                throw new IllegalArgumentException("Key or Value cannot be null");
            }

            // Perform the actual put operation
            data.put(key, value);
            index.put(value, key);

            if (enableExpiry) {
                expiryMap.put(key, System.currentTimeMillis() + expiryDuration);
            }

            setDirty(true);

            if (autoSync) {
                try {
                    CompoundTag keyTag = keySerializer.serialize(key);
                    CompoundTag valueTag = valueSerializer.serialize(value);
                    EDBMessages.sendToClients(new DataBaseEntryS2C<>(keyTag, valueTag, databaseName));
                } catch (Exception e) {
                    LOGGER.error("[{}] Failed to sync data to clients", databaseName, e);
                }
            }


            int logInterval = Math.max(100, data.size() / 10);
            if (data.size() % logInterval == 0) {
                LOGGER.info("[{}] Data size reached {}", databaseName, data.size());
            }

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }


    public V getData(M key) {
        readWriteLock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value == null) {
                value = data.get(key);
                if (value != null) cache.put(key, value);
            }
            return value;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public M getKeyByValue(V value) {
        readWriteLock.readLock().lock();
        try {
            return index.get(value);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> getDataMap() {
        readWriteLock.readLock().lock();
        try {
            return new HashMap<>(data);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public int getSize() {
        readWriteLock.readLock().lock();
        try {
            return data.size();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Set<M> getKeys() {
        readWriteLock.readLock().lock();
        try {
            return new HashSet<>(data.keySet());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Collection<V> getValues() {
        readWriteLock.readLock().lock();
        try {
            return new ArrayList<>(data.values());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> query(Predicate<V> predicate) {
        readWriteLock.readLock().lock();
        try {
            return data.entrySet().stream()
                    .filter(entry -> predicate.test(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> queryOptimized(Predicate<V> predicate) {
        readWriteLock.readLock().lock();
        try {
            return data.entrySet().parallelStream()
                    .filter(entry -> predicate.test(entry.getValue()))
                    .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
            readWriteLock.readLock().unlock();
        }
    }


    public List<V> queryWithPagination(int page, int pageSize) {
        readWriteLock.readLock().lock();
        try {
            return data.values().stream()
                    .skip((long) page * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> queryByRange(Predicate<M> keyPredicate, Predicate<V> valuePredicate) {
        readWriteLock.readLock().lock();
        try {
            return data.entrySet().stream()
                    .filter(entry -> keyPredicate.test(entry.getKey()) && valuePredicate.test(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> queryWithConditions(List<Predicate<V>> conditions, boolean matchAll) {
        readWriteLock.readLock().lock();
        try {
            return data.entrySet().stream()
                    .filter(entry -> {
                        if (matchAll) {
                            return conditions.stream().allMatch(cond -> cond.test(entry.getValue()));
                        } else {
                            return conditions.stream().anyMatch(cond -> cond.test(entry.getValue()));
                        }
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> queryByTextSearch(String searchText, Function<V, String> textExtractor) {
        readWriteLock.readLock().lock();
        try {
            return data.entrySet().stream()
                    .filter(entry -> {
                        String text = textExtractor.apply(entry.getValue());
                        return text != null && text.contains(searchText);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public List<V> queryAndSort(Comparator<V> comparator) {
        readWriteLock.readLock().lock();
        try {
            return data.values().stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public <R> R queryAndAggregate(Function<V, R> mapper, BinaryOperator<R> aggregator, R identity) {
        readWriteLock.readLock().lock();
        try {
            return data.values().stream()
                    .map(mapper)
                    .reduce(identity, aggregator);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public Map<M, V> queryWithBatchProcessing(Predicate<V> predicate, int batchSize) {
        readWriteLock.readLock().lock();
        try {
            Map<M, V> result = new HashMap<>();
            List<Map.Entry<M, V>> entries = new ArrayList<>(data.entrySet());

            for (int i = 0; i < entries.size(); i += batchSize) {
                int end = Math.min(entries.size(), i + batchSize);
                List<Map.Entry<M, V>> batch = entries.subList(i, end);
                for (Map.Entry<M, V> entry : batch) {
                    if (predicate.test(entry.getValue())) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            return result;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }


    public void beginTransaction() {
        readWriteLock.readLock().lock();
        try {
            transactionStack.push(new HashMap<>(data));
            LOGGER.info("[{}] Transaction started. Transaction stack size: {}", databaseName, transactionStack.size());

        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    // Commit a transaction
    public void commitTransaction() {
        readWriteLock.readLock().lock();
        try {
            transactionStack.clear(); // Clear transaction stack after committing
            LOGGER.info("[{}] Transaction committed. Transaction stack cleared.", databaseName);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    // Rollback a transaction
    public void rollbackTransaction() {
        readWriteLock.readLock().lock();
        try {
            if (!transactionStack.isEmpty()) {
                data.clear();
                data.putAll(transactionStack.pop()); // Restore previous state
                LOGGER.info("[{}] Transaction rolled back. Transaction stack size: {}", databaseName, transactionStack.size());

            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }


    public void createSavepoint() {
        readWriteLock.readLock().lock();
        try {
            savepoints.push(new HashMap<>(data));
            LOGGER.info("[{}] Savepoint created. Savepoints stack size: {}", databaseName, savepoints.size());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void rollbackToSavepoint() {
        readWriteLock.readLock().lock();
        try {
            if (!savepoints.isEmpty()) {
                data.clear();
                data.putAll(savepoints.pop());
                setDirty(true);
                LOGGER.info("[{}] Rolled back to savepoint. Savepoints stack size: {}", databaseName, savepoints.size());
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void queryAsync(Consumer<List<V>> callback) {
        executor.submit(() -> {
            List<V> results = new ArrayList<>();
            readWriteLock.readLock().lock();
            try {
                results.addAll(data.values());
            } catch (Exception e) {
                LOGGER.error("[{}] Error during asynchronous query", databaseName, e);
            } finally {
                readWriteLock.readLock().unlock();
            }
            callback.accept(results);
        });
    }

    public void putDataAsync(M key, V value, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            readWriteLock.writeLock().lock();
            try {
                putData(key, value);
                success = true;
            } catch (Exception e) {
                LOGGER.error("[{}] Error during asynchronous putData", databaseName, e);
            } finally {
                readWriteLock.writeLock().unlock();
            }
            callback.accept(success);
        });
    }

    public void removeDataAsync(M key, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            readWriteLock.writeLock().lock();
            try {
                success = data.remove(key) != null;
                if (success) {
                    index.remove(key);
                    cache.remove(key);
                    expiryMap.remove(key);
                }
                setDirty(true);
            } catch (Exception e) {
                LOGGER.error("[{}] Error during asynchronous removeData", databaseName, e);
            } finally {
                readWriteLock.writeLock().unlock();
            }
            callback.accept(success);
        });
    }

    public void putDataBatchAsync(Map<M, V> entries, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            readWriteLock.writeLock().lock();
            try {
                putDataBatch(entries);
                success = true;
            } catch (Exception e) {
                LOGGER.error("[{}] Error during asynchronous batch putData", databaseName, e);
            } finally {
                readWriteLock.writeLock().unlock();
            }
            callback.accept(success);
        });
    }


    public void putDataBatch(Map<M, V> entries) {
        readWriteLock.writeLock().lock();
        try {
            for (Map.Entry<M, V> entry : entries.entrySet()) {
                M key = entry.getKey();
                V value = entry.getValue();
                if (key == null || value == null) {
                    throw new IllegalArgumentException("Key or Value cannot be null");
                }

                data.put(key, value);
                index.put(value, key);

                if (enableExpiry) {
                    expiryMap.put(key, System.currentTimeMillis() + expiryDuration);
                }

                cache.put(key, value);
            }
            setDirty(true);
            LOGGER.info("[{}] Batch insert completed. Added {} entries", databaseName, entries.size());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public Map<M, V> getDataBatch(Collection<M> keys) {
        readWriteLock.readLock().lock();
        try {
            Map<M, V> result = new HashMap<>();
            for (M key : keys) {
                V value = cache.getOrDefault(key, data.get(key));
                if (value != null) {
                    result.put(key, value);
                    cache.put(key, value);
                }
            }
            return result;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void removeDataBatchAsync(Collection<M> keys, Consumer<Boolean> callback) {
        executor.submit(() -> {
            boolean success = false;
            readWriteLock.writeLock().lock();
            try {
                removeDataBatch(keys);
                success = true;
            } catch (Exception e) {
                LOGGER.error("[{}] Error during asynchronous batch removeData", databaseName, e);
            } finally {
                readWriteLock.writeLock().unlock();
            }
            callback.accept(success);
        });
    }


    public void removeDataBatch(Collection<M> keys) {
        readWriteLock.writeLock().lock();
        try {
            for (M key : keys) {
                V value = data.remove(key);
                if (value != null) {
                    index.remove(value);
                    cache.remove(key);
                    expiryMap.remove(key);
                }
            }
            setDirty(true);
            LOGGER.info("[{}] Batch remove completed. Removed {} entries", databaseName, keys.size());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private <R> R profile(Supplier<R> task, String taskName) {
        long startTime = System.nanoTime();
        try {
            return task.get();
        } finally {
            long duration = System.nanoTime() - startTime;
            LOGGER.info("[{}] {} took {} ms", databaseName, taskName, duration / 1_000_000);
        }
    }

    private void profile(Runnable task, String taskName) {
        long startTime = System.nanoTime();
        try {
            task.run();
        } finally {
            long duration = System.nanoTime() - startTime;
            LOGGER.info("[{}] {} took {} ms", databaseName, taskName, duration / 1_000_000);
        }
    }

    public void putDataProfiled(M key, V value) {
        profile(() -> putData(key, value), "putData");
    }

    public V getDataProfiled(M key) {
        return profile(() -> getData(key), "getData");
    }

    //public void exportDataBase(Path filePath) {
    //    exportDataBase(filePath, "");
    //}


    public void exportDataBase(Path filePath, FileFormat format) {
        readWriteLock.readLock().lock();
        try {
            // Ensure parent directory exists

            filePath = ensureFileExtension(filePath, format);
            Files.createDirectories(filePath.getParent());
            //Path path = filePath.resolve(name);

            CompoundTag tag = new CompoundTag();
            save(tag);

            ExportFormatStrategy strategy = format.getExportStrategy();
            strategy.export(tag, filePath);

            LOGGER.info("[{}] Database exported to {}", databaseName, filePath.toAbsolutePath());

        } catch (IOException e) {
            LOGGER.error("[{}] Failed to export database to {}", databaseName, filePath.toAbsolutePath(), e);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }


    //mainly used for backups now
    public boolean importWithCheckSumValidation(Path path, FileFormat format, ServerLevel level) throws Exception {
        Path checksumFile = Paths.get(path.toString() + ".sha256");
        if (Files.exists(path)) {
            boolean isValid = ChecksumUtil.verifyChecksum(path, checksumFile);

            if (!isValid) {
                LOGGER.error("[{}] Checksum verification failed for file {}", databaseName, path.toAbsolutePath());
                return false;
            } else {
                importDataBase(path, format, level);
            }
        } else {
            LOGGER.error("[{}] Checksum file does not exist for {}", databaseName, path.toAbsolutePath());
            return false;
        }
        return false;
    }


    public boolean importDataBase(Path filePath, ServerLevel level) {
        String extension = getFileExtension(filePath.toString());
        FileFormat format = FileFormat.fromExtension(extension);
        return importDataBase(filePath, format, level);
    }

    public boolean importDataBase(Path filePath, FileFormat format, ServerLevel level) {
        readWriteLock.readLock().lock();
        try {
            // Ensure the file exists

            filePath = ensureFileExtension(filePath, format);

            if (!Files.exists(filePath)) {
                LOGGER.error("[{}] The file does not exist at {}", databaseName, filePath.toAbsolutePath());
                return false;
            }


            // Get the appropriate import strategy based on file format
            ImportFormatStrategy strategy = format.getImportStrategy();

            // Import the data
            CompoundTag tag = strategy.importData(filePath);

            // Clear existing database data and load the new data
            clearDataBase(false);
            load(tag, level, true);

            LOGGER.info("[{}] Database imported from {}", databaseName, filePath.toAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to import database from {}", databaseName, filePath.toAbsolutePath(), e);
        } catch (Exception e) {
            LOGGER.error("[{}] An error occurred during import", databaseName, e);
        } finally {
            readWriteLock.readLock().unlock();
        }
        return false;
    }

    private Path ensureFileExtension(Path filePath, FileFormat format) {
        String extension = format.getExtension();
        if (!filePath.toString().endsWith(extension)) {
            return Paths.get(filePath.toString() + extension);
        }
        return filePath;
    }

    private void setExpiration() {
        if (enableExpiry) {
            scheduler.scheduleAtFixedRate(this::cleanUpExpiredData, 1, 1, TimeUnit.MINUTES);
        }
    }

    public void shutdown() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.MINUTES);
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            LOGGER.error("[{}] Shutdown interrupted", databaseName, e);
            Thread.currentThread().interrupt();
        }
    }

    public void cleanUpExpiredData() {
        if (!enableExpiry) return;

        long currentTime = System.currentTimeMillis();
        List<M> expiredKeys = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String formattedDate = sdf.format(currentTime);
        LOGGER.debug("[{}] Cleaning up expired data at {}", databaseName, formattedDate);

        readWriteLock.writeLock().lock();
        try {
            data.entrySet().removeIf(entry -> {
                M key = entry.getKey();
                boolean isExpired = currentTime > expiryMap.getOrDefault(key, 0L);

                if (isExpired) {
                    V value = entry.getValue();
                    index.remove(value);
                    expiryMap.remove(key);
                    cache.remove(key);
                    expiredKeys.add(key);
                    LOGGER.debug("[{}] Data with key={} expired and removed", databaseName, key);
                }

                return isExpired;
            });

            index.keySet().removeIf(value -> {
                M key = index.get(value);
                boolean notInData = !data.containsKey(key);

                if (notInData) {
                    LOGGER.debug("[{}] Removing key={} from index as it is no longer in data", databaseName, key);
                    return true;
                }
                return false;
            });

            cache.keySet().removeIf(key -> !data.containsKey(key));

            if (!expiredKeys.isEmpty()) {
                LOGGER.debug("[{}] Cleaned up {} expired data items", databaseName, expiredKeys.size());
            }
            setDirty();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void clearDataBase() {
        clearDataBase(true);
    }

    public void clearDataBase(boolean setDirty) {
        LOGGER.info("[{}] Clearing database!", databaseName);
        data.clear();
        index.clear();
        cache.clear();
        expiryMap.clear();
        if (setDirty) {
            setDirty();
        }
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public interface DataSerializer<T> {

        @Nullable
        CompoundTag serialize(T value);

        @Nullable
        T deserialize(CompoundTag tag, Level level);
    }
}