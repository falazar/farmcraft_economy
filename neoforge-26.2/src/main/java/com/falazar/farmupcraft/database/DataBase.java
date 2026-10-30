package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.backup.ChecksumUtil;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.message.DataBaseEntryS2C;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An overworld-owned database whose codec retains the legacy compound/list payload.
 * Detached instances must be supplied the registries of the world whose values they contain.
 * Snapshots are structural copies; mutable application values are not deep copied.
 */
public class DataBase<M, V> extends SavedData implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<M, V> data = new HashMap<>();
    private final Map<V, M> index = new HashMap<>();
    private final Map<M, Long> expiryMap = new HashMap<>();
    // Reads can expire entries, so they also take the exclusive, reentrant lock.
    private final ReentrantLock lock = new ReentrantLock();
    private final DataSerializer<M> keySerializer;
    private final DataSerializer<V> valueSerializer;
    private final boolean enableExpiry;
    private final long expiryDuration;
    private final boolean autoSync;
    private final String databaseName;
    private HolderLookup.Provider registries;
    private final Deque<Snapshot<M, V>> transactionStack = new ArrayDeque<>();
    private final Deque<Snapshot<M, V>> savepoints = new ArrayDeque<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    private record Snapshot<M, V>(Map<M, V> data, Map<M, Long> expiry) {}
    private record AsyncTask(Runnable operation, Runnable rejection) implements Runnable {
        @Override
        public void run() { operation.run(); }
    }

    public DataBase(String name, DataSerializer<M> key, DataSerializer<V> value,
                    boolean expiry, long expiryMillis, boolean autoSync, HolderLookup.Provider registries) {
        this.databaseName = Objects.requireNonNull(name, "name");
        this.keySerializer = Objects.requireNonNull(key, "key serializer");
        this.valueSerializer = Objects.requireNonNull(value, "value serializer");
        this.registries = Objects.requireNonNull(registries, "registries");
        if (expiry && expiryMillis <= 0) {
            throw new IllegalArgumentException("Expiry duration must be positive");
        }
        this.enableExpiry = expiry;
        this.expiryDuration = expiryMillis;
        this.autoSync = autoSync;
        this.executor = Executors.newSingleThreadExecutor(task -> daemon(task, "database-worker-" + name));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "database-expiry-" + name));
        if (expiry) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    cleanUpExpiredData();
                } catch (RuntimeException exception) {
                    LOGGER.error("[{}] Expiration cleanup failed", databaseName, exception);
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    /**
     * Compatibility constructor for registry-independent serializers. World-aware callers
     * must use the overload accepting a registry provider.
     */
    public DataBase(String name, DataSerializer<M> key, DataSerializer<V> value,
                    boolean expiry, long expiryMillis, boolean autoSync) {
        this(name, key, value, expiry, expiryMillis, autoSync, HolderLookup.Provider.create(Stream.empty()));
    }

    public DataBase(CompoundTag nbt, String name, ServerLevel level, DataSerializer<M> key,
                    DataSerializer<V> value, boolean expiry, long expiryMillis, boolean autoSync) {
        this(name, key, value, expiry, expiryMillis, autoSync, level.registryAccess());
        try {
            load(nbt, level);
        } catch (RuntimeException exception) {
            shutdown();
            throw exception;
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    public static <M, V> DataBase<M, V> get(Level level, String name, DataSerializer<M> key,
                                           DataSerializer<V> value, boolean expiry, long duration, boolean autoSync) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Persistent databases require a server level");
        }
        ServerLevel overworld = serverLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                savedDataType(name, key, value, expiry, duration, autoSync, overworld.registryAccess()));
    }

    /**
     * Also usable with independent SavedDataStorage instances (including stores without a level).
     * There is no global database/type cache: each storage owns its own decoded instance.
     */
    public static <M, V> SavedDataType<DataBase<M, V>> savedDataType(
            String name, DataSerializer<M> key, DataSerializer<V> value, boolean expiry,
            long duration, boolean autoSync, HolderLookup.Provider registries) {
        Supplier<DataBase<M, V>> constructor =
                () -> new DataBase<>(name, key, value, expiry, duration, autoSync, registries);
        Codec<DataBase<M, V>> codec = new Codec<>() {
            @Override
            public <T> DataResult<Pair<DataBase<M, V>, T>> decode(DynamicOps<T> ops, T input) {
                HolderLookup.Provider provider = registryContext(ops, registries);
                return CompoundTag.CODEC.decode(ops, input).flatMap(pair -> {
                    DataBase<M, V> database = new DataBase<>(name, key, value, expiry, duration, autoSync, provider);
                    try {
                        database.load(pair.getFirst(), provider);
                        return DataResult.success(Pair.of(database, pair.getSecond()));
                    } catch (RuntimeException exception) {
                        database.shutdown();
                        return DataResult.error(() -> "Invalid database " + name + ": " + exception.getMessage());
                    }
                });
            }

            @Override
            public <T> DataResult<T> encode(DataBase<M, V> database, DynamicOps<T> ops, T prefix) {
                try {
                    CompoundTag tag = database.locked(() -> {
                        database.expire();
                        return database.encode(database.data.entrySet(), registryContext(ops, registries));
                    });
                    return CompoundTag.CODEC.encode(tag, ops, prefix);
                } catch (RuntimeException exception) {
                    return DataResult.error(() -> "Cannot encode database " + name + ": " + exception.getMessage());
                }
            }
        };
        Identifier id = name.contains(":") ? Identifier.parse(name)
                : Identifier.fromNamespaceAndPath("farmupcraft", name);
        return new SavedDataType<>(id, constructor, codec);
    }

    private static HolderLookup.Provider registryContext(DynamicOps<?> ops, HolderLookup.Provider fallback) {
        if (ops instanceof RegistryOps<?> registryOps
                && registryOps.lookupProvider instanceof RegistryOps.HolderLookupAdapter adapter) {
            return adapter.lookupProvider;
        }
        return fallback;
    }

    public void load(CompoundTag tag, Level level) {
        load(tag, Objects.requireNonNull(level, "level").registryAccess());
    }

    /** Decodes completely before replacing live state; ingestion itself does not rebroadcast. */
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        Objects.requireNonNull(provider, "registries");
        Map<M, V> decoded = decode(tag, provider);
        locked(() -> {
            data.clear();
            data.putAll(decoded);
            registries = provider;
            expiryMap.clear();
            if (enableExpiry) {
                long deadline = deadline();
                data.keySet().forEach(key -> expiryMap.put(key, deadline));
            }
            rebuildIndex();
            transactionStack.clear();
            savepoints.clear();
            return null;
        });
    }

    private Map<M, V> decode(CompoundTag tag, HolderLookup.Provider provider) {
        String name = tag.getString("database_name").orElseThrow(
                () -> new IllegalArgumentException("Missing database_name"));
        if (!databaseName.equals(name)) {
            throw new IllegalArgumentException("Database name mismatch: " + name);
        }
        if (!(tag.get("map_entry") instanceof ListTag entries)) {
            throw new IllegalArgumentException("Missing map_entry list");
        }
        Map<M, V> decoded = new HashMap<>();
        for (Tag item : entries) {
            if (!(item instanceof CompoundTag entry)
                    || !(entry.get("key") instanceof CompoundTag keyTag)
                    || !(entry.get("value") instanceof CompoundTag valueTag)) {
                throw new IllegalArgumentException("Invalid database entry");
            }
            M key = keySerializer.deserialize(keyTag, provider);
            V value = valueSerializer.deserialize(valueTag, provider);
            requireEntry(key, value);
            if (decoded.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate database key");
            }
        }
        return decoded;
    }

    /** Manual legacy payload export, not an obsolete SavedData override. */
    public CompoundTag save(CompoundTag tag) {
        return locked(() -> {
            expire();
            CompoundTag encoded = encode(data.entrySet());
            tag.putString("database_name", databaseName);
            tag.put("map_entry", Objects.requireNonNull(encoded.get("map_entry")));
            return tag;
        });
    }

    private CompoundTag encode(Collection<Map.Entry<M, V>> entries) {
        return encode(entries, registries);
    }

    private CompoundTag encode(Collection<Map.Entry<M, V>> entries, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("database_name", databaseName);
        ListTag list = new ListTag();
        for (Map.Entry<M, V> entry : entries) {
            CompoundTag key = keySerializer.serialize(entry.getKey(), provider);
            CompoundTag value = valueSerializer.serialize(entry.getValue(), provider);
            if (key == null || value == null) {
                throw new IllegalArgumentException("Serializer returned null for database entry");
            }
            CompoundTag item = new CompoundTag();
            item.put("key", key);
            item.put("value", value);
            list.add(item);
        }
        tag.put("map_entry", list);
        return tag;
    }

    private <T> T locked(Supplier<T> operation) {
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private static void requireEntry(Object key, Object value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or value cannot be null");
        }
    }

    private long deadline() {
        long now = System.currentTimeMillis();
        return expiryDuration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + expiryDuration;
    }

    private void rebuildIndex() {
        index.clear();
        data.forEach((key, value) -> index.put(value, key));
    }

    private void fullSync() {
        if (autoSync) {
            try {
                EDBMessages.sendToClients(new DataBaseFullS2C(encode(data.entrySet()), databaseName));
            } catch (RuntimeException exception) {
                LOGGER.error("[{}] Full synchronization failed", databaseName, exception);
            }
        }
    }

    private void changed() {
        setDirty();
        fullSync();
    }

    private void expire() {
        if (!enableExpiry) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean removed = expiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                data.remove(entry.getKey());
                return true;
            }
            return false;
        });
        if (removed) {
            rebuildIndex();
            changed();
        }
    }

    public void putData(M key, V value) {
        requireEntry(key, value);
        locked(() -> {
            V old = data.put(key, value);
            if (old != null && !old.equals(value) && Objects.equals(index.get(old), key)) {
                index.remove(old);
                data.forEach((otherKey, otherValue) -> {
                    if (old.equals(otherValue)) {
                        index.put(old, otherKey);
                    }
                });
            }
            index.put(value, key);
            if (enableExpiry) {
                expiryMap.put(key, deadline());
            }
            setDirty();
            if (autoSync) {
                try {
                    EDBMessages.sendToClients(new DataBaseEntryS2C(
                            Objects.requireNonNull(keySerializer.serialize(key, registries)),
                            Objects.requireNonNull(valueSerializer.serialize(value, registries)), databaseName));
                } catch (RuntimeException exception) {
                    LOGGER.error("[{}] Entry synchronization failed", databaseName, exception);
                }
            }
            return null;
        });
    }

    public V getData(M key) {
        if (key == null) {
            return null;
        }
        return locked(() -> {
            Long expiry = expiryMap.get(key);
            if (enableExpiry && expiry != null && expiry <= System.currentTimeMillis()) {
                data.remove(key);
                expiryMap.remove(key);
                rebuildIndex();
                changed();
            }
            return data.get(key);
        });
    }

    public boolean containsKey(M key) {
        return getData(key) != null;
    }

    public V getOrDefault(M key, V defaultValue) {
        V value = getData(key);
        return value == null ? defaultValue : value;
    }

    public V getOrCreate(M key, Supplier<V> creator) {
        Objects.requireNonNull(key, "key");
        return locked(() -> {
            V value = getData(key);
            if (value == null) {
                value = creator.get();
                putData(key, value);
            }
            return value;
        });
    }

    public M getKeyByValue(V value) {
        return locked(() -> {
            expire();
            return index.get(value);
        });
    }

    public Map<M, V> getDataMap() {
        return locked(() -> {
            expire();
            return new HashMap<>(data);
        });
    }

    public int getSize() { return locked(() -> { expire(); return data.size(); }); }
    public Set<M> getKeys() { return locked(() -> { expire(); return new HashSet<>(data.keySet()); }); }
    public Collection<V> getValues() { return locked(() -> { expire(); return new ArrayList<>(data.values()); }); }

    public Map<M, V> query(Predicate<V> predicate) {
        return getDataMap().entrySet().stream().filter(entry -> predicate.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<M, V> queryOptimized(Predicate<V> predicate) {
        return getDataMap().entrySet().parallelStream().filter(entry -> predicate.test(entry.getValue()))
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<V> queryWithPagination(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Page must be nonnegative and page size positive");
        }
        return getValues().stream().skip((long) page * pageSize).limit(pageSize).collect(Collectors.toList());
    }

    public Map<M, V> queryByRange(Predicate<M> keys, Predicate<V> values) {
        return getDataMap().entrySet().stream()
                .filter(entry -> keys.test(entry.getKey()) && values.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<M, V> queryWithConditions(List<Predicate<V>> conditions, boolean matchAll) {
        return query(value -> matchAll ? conditions.stream().allMatch(condition -> condition.test(value))
                : conditions.stream().anyMatch(condition -> condition.test(value)));
    }

    public Map<M, V> queryByTextSearch(String text, Function<V, String> extractor) {
        return query(value -> {
            String extracted = extractor.apply(value);
            return extracted != null && extracted.contains(text);
        });
    }

    public List<V> queryAndSort(Comparator<V> comparator) {
        return getValues().stream().sorted(comparator).collect(Collectors.toList());
    }

    public <R> R queryAndAggregate(Function<V, R> mapper, BinaryOperator<R> aggregator, R identity) {
        return getValues().stream().map(mapper).reduce(identity, aggregator);
    }

    public Map<M, V> queryWithBatchProcessing(Predicate<V> predicate, int batchSize) {
        positiveBatch(batchSize);
        return query(predicate);
    }

    private static void positiveBatch(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
    }

    private Snapshot<M, V> snapshot() {
        expire();
        return new Snapshot<>(new HashMap<>(data), new HashMap<>(expiryMap));
    }

    public void beginTransaction() {
        locked(() -> { transactionStack.push(snapshot()); return null; });
    }

    public void commitTransaction() {
        locked(() -> { transactionStack.clear(); return null; });
    }

    private void restore(Deque<Snapshot<M, V>> snapshots) {
        locked(() -> {
            if (!snapshots.isEmpty()) {
                Snapshot<M, V> snapshot = snapshots.pop();
                data.clear();
                data.putAll(snapshot.data());
                expiryMap.clear();
                expiryMap.putAll(snapshot.expiry());
                // Preserve original deadlines, not a fresh lease for rolled-back entries.
                if (enableExpiry) {
                    long now = System.currentTimeMillis();
                    expiryMap.entrySet().removeIf(entry -> {
                        if (entry.getValue() <= now) {
                            data.remove(entry.getKey());
                            return true;
                        }
                        return false;
                    });
                }
                rebuildIndex();
                changed();
            }
            return null;
        });
    }

    public void rollbackTransaction() { restore(transactionStack); }
    public void createSavepoint() { locked(() -> { savepoints.push(snapshot()); return null; }); }
    public void rollbackToSavepoint() { restore(savepoints); }

    private void deliver(Consumer<Boolean> callback, boolean success) {
        try {
            callback.accept(success);
        } catch (RuntimeException exception) {
            LOGGER.error("[{}] Asynchronous callback failed", databaseName, exception);
        }
    }

    private void booleanAsync(Supplier<Boolean> operation, Consumer<Boolean> callback) {
        Objects.requireNonNull(callback, "callback");
        try {
            executor.execute(new AsyncTask(() -> {
                boolean success = false;
                try {
                    success = operation.get();
                } catch (RuntimeException exception) {
                    LOGGER.error("[{}] Asynchronous operation failed", databaseName, exception);
                }
                deliver(callback, success);
            }, () -> deliver(callback, false)));
        } catch (RejectedExecutionException exception) {
            LOGGER.error("[{}] Database executor is shut down", databaseName, exception);
            deliver(callback, false);
        }
    }

    public void queryAsync(Consumer<List<V>> callback) {
        Objects.requireNonNull(callback, "callback");
        // A failed/rejected query is logged and delivered as an empty result, never silently dropped.
        Runnable task = () -> {
            List<V> results;
            try {
                results = new ArrayList<>(getValues());
            } catch (RuntimeException exception) {
                LOGGER.error("[{}] Asynchronous query failed", databaseName, exception);
                results = List.of();
            }
            try {
                callback.accept(results);
            } catch (RuntimeException exception) {
                LOGGER.error("[{}] Asynchronous query callback failed", databaseName, exception);
            }
        };
        try {
            executor.execute(new AsyncTask(task, () -> deliverQuery(callback, List.of())));
        } catch (RejectedExecutionException exception) {
            LOGGER.error("[{}] Database executor is shut down", databaseName, exception);
            deliverQuery(callback, List.of());
        }
    }

    private void deliverQuery(Consumer<List<V>> callback, List<V> results) {
        try {
            callback.accept(results);
        } catch (RuntimeException exception) {
            LOGGER.error("[{}] Asynchronous query callback failed", databaseName, exception);
        }
    }

    public void putDataAsync(M key, V value, Consumer<Boolean> callback) {
        booleanAsync(() -> { putData(key, value); return true; }, callback);
    }

    public void removeDataAsync(M key, Consumer<Boolean> callback) {
        booleanAsync(() -> locked(() -> {
            expire();
            boolean present = data.containsKey(key);
            removeDataBatch(java.util.Collections.singleton(key));
            return present;
        }), callback);
    }

    public void putDataBatchAsync(Map<M, V> entries, Consumer<Boolean> callback) {
        Map<M, V> copy = new HashMap<>(entries);
        booleanAsync(() -> { putDataBatch(copy); return true; }, callback);
    }

    public void removeDataBatchAsync(Collection<M> keys, Consumer<Boolean> callback) {
        List<M> copy = new ArrayList<>(keys);
        booleanAsync(() -> { removeDataBatch(copy); return true; }, callback);
    }

    public void putDataBatch(Map<M, V> entries) {
        Map<M, V> copy = new HashMap<>(entries);
        copy.forEach(DataBase::requireEntry);
        locked(() -> {
            expire();
            data.putAll(copy);
            if (enableExpiry) {
                long deadline = deadline();
                copy.keySet().forEach(key -> expiryMap.put(key, deadline));
            }
            rebuildIndex();
            changed();
            return null;
        });
    }

    public Map<M, V> getDataBatch(Collection<M> keys) {
        Map<M, V> snapshot = getDataMap();
        Map<M, V> result = new HashMap<>();
        for (M key : keys) {
            if (snapshot.containsKey(key)) {
                result.put(key, snapshot.get(key));
            }
        }
        return result;
    }

    public void removeDataBatch(Collection<M> keys) {
        List<M> copy = new ArrayList<>(keys);
        locked(() -> {
            expire();
            boolean removed = false;
            for (M key : copy) {
                removed |= data.remove(key) != null;
                expiryMap.remove(key);
            }
            if (removed) {
                rebuildIndex();
                changed();
            }
            return null;
        });
    }

    private <R> R profile(Supplier<R> task, String name) {
        long start = System.nanoTime();
        try {
            return task.get();
        } finally {
            LOGGER.info("[{}] {} took {} ms", databaseName, name, (System.nanoTime() - start) / 1_000_000);
        }
    }

    public void putDataProfiled(M key, V value) {
        profile(() -> { putData(key, value); return null; }, "putData");
    }

    public V getDataProfiled(M key) { return profile(() -> getData(key), "getData"); }

    public int getSerializedSize() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(save(new CompoundTag()), output);
            return output.size();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to measure database " + databaseName, exception);
        }
    }

    public List<CompoundTag> saveChunked(int entriesPerChunk) {
        positiveBatch(entriesPerChunk);
        return locked(() -> {
            expire();
            List<Map.Entry<M, V>> entries = new ArrayList<>(data.entrySet());
            List<CompoundTag> chunks = new ArrayList<>();
            for (int start = 0; start < entries.size();) {
                int end = (int) Math.min((long) entries.size(), (long) start + entriesPerChunk);
                chunks.add(encode(entries.subList(start, end)));
                start = end;
            }
            if (chunks.isEmpty()) {
                chunks.add(encode(List.of()));
            }
            return chunks;
        });
    }

    public void exportDataBase(Path path, FileFormat format) {
        Path target = ensureFileExtension(path, format);
        CompoundTag snapshot = save(new CompoundTag());
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            format.getExportStrategy().export(snapshot, target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to export database " + databaseName + " to " + target, exception);
        }
    }

    public boolean importWithCheckSumValidation(Path path, FileFormat format, ServerLevel level) throws Exception {
        return importWithCheckSumValidation(path, format, level.registryAccess());
    }

    public boolean importWithCheckSumValidation(Path path, FileFormat format, HolderLookup.Provider provider)
            throws Exception {
        Path target = ensureFileExtension(path, format);
        Path checksum = Path.of(target + ".sha256");
        return Files.isRegularFile(target) && Files.isRegularFile(checksum)
                && ChecksumUtil.verifyChecksum(target, checksum) && importDataBase(target, format, provider);
    }

    public boolean importDataBase(Path path, ServerLevel level) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return importDataBase(path, FileFormat.fromExtension(dot < 0 ? "" : name.substring(dot)), level);
    }

    public boolean importDataBase(Path path, FileFormat format, ServerLevel level) {
        return importDataBase(path, format, level.registryAccess());
    }

    public boolean importDataBase(Path path, FileFormat format, HolderLookup.Provider provider) {
        Path target = ensureFileExtension(path, format);
        try {
            CompoundTag tag = format.getImportStrategy().importData(target);
            // Keep replacement and synchronization ordered with concurrent mutations.
            locked(() -> {
                load(tag, provider);
                changed();
                return null;
            });
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[{}] Failed to import database from {}", databaseName, target, exception);
            return false;
        }
    }

    private static Path ensureFileExtension(Path path, FileFormat format) {
        return path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(format.getExtension())
                ? path : Path.of(path + format.getExtension());
    }

    public void cleanUpExpiredData() { locked(() -> { expire(); return null; }); }
    public void clearDataBase() { clearDataBase(true); }

    public void clearDataBase(boolean dirty) {
        locked(() -> {
            data.clear();
            index.clear();
            expiryMap.clear();
            if (dirty) {
                setDirty();
            }
            fullSync();
            return null;
        });
    }

    public String getDatabaseName() { return databaseName; }

    private void cancelPendingOperations() {
        for (Runnable pending : executor.shutdownNow()) {
            if (pending instanceof AsyncTask task) {
                task.rejection().run();
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
        executor.shutdown();
        // Callbacks are allowed to close their own database without waiting on themselves.
        if (Thread.currentThread().getName().equals("database-worker-" + databaseName)) {
            return;
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                cancelPendingOperations();
                LOGGER.warn("[{}] Forced database executor shutdown", databaseName);
            }
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            cancelPendingOperations();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() { shutdown(); }
}
