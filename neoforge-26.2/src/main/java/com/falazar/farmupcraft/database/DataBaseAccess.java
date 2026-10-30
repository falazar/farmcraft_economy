package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class DataBaseAccess<M, V> {
    private final Identifier databaseName;
    private final DataSerializer<M> keySerializer;
    private final DataSerializer<V> valueSerializer;
    private final boolean enableExpiry;
    private final long expiryDuration;
    private final boolean shouldBackup;
    private final long backupInterval;
    private final int backupCount;
    private final long backupRemovalTime;
    private final FileFormat fileFormat;
    private final boolean autoSync;
    private final Map<Level, DataBase<M, V>> serverCaches = new IdentityHashMap<>();
    private final ClientCache<Level, DataBase<M, V>> clientCache;

    public DataBaseAccess(Identifier databaseName, DataSerializer<M> keySerializer,
                          DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration,
                          boolean shouldBackUp, long backupInterval, int backupCount,
                          long backupRemovalTime, FileFormat fileFormat, boolean autoSync) {
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
        this.keySerializer = Objects.requireNonNull(keySerializer, "keySerializer");
        this.valueSerializer = Objects.requireNonNull(valueSerializer, "valueSerializer");
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        DataBaseBuilder.validateExpiry(enableExpiry, expiryDuration);
        DataBaseBuilder.validateBackupInterval(shouldBackUp, backupInterval);
        if (backupCount <= 0 || backupRemovalTime < 0) {
            throw new IllegalArgumentException("Backup count must be positive and retention duration non-negative.");
        }
        this.enableExpiry = enableExpiry;
        this.expiryDuration = expiryDuration;
        this.shouldBackup = shouldBackUp;
        this.backupInterval = backupInterval;
        this.backupCount = backupCount;
        this.backupRemovalTime = backupRemovalTime;
        this.autoSync = autoSync;
        clientCache = new ClientCache<>(level -> new DataBase<>(databaseName.toString(),
                keySerializer, valueSerializer, enableExpiry, expiryDuration, false, level.registryAccess()),
                DataBase::shutdown);
    }

    public synchronized DataBase<M, V> get(Level level) {
        Objects.requireNonNull(level, "level");
        if (level.isClientSide()) {
            return clientCache.get(level);
        }
        return serverCaches.computeIfAbsent(level, current -> DataBase.get(current, databaseName.toString(),
                keySerializer, valueSerializer, enableExpiry, expiryDuration, autoSync));
    }

    /** Called on client disconnect as well as automatically when a different client level is used. */
    public synchronized void clearClientCache() {
        clientCache.clear();
    }

    public synchronized void clearServerCaches(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        var iterator = serverCaches.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().getServer() == server) {
                entry.getValue().shutdown();
                iterator.remove();
            }
        }
    }

    public synchronized void clearCaches() {
        clearClientCache();
        serverCaches.values().forEach(DataBase::shutdown);
        serverCaches.clear();
    }

    public long getExpiryDuration() { return expiryDuration; }
    public Identifier getDatabaseName() { return databaseName; }
    public boolean isEnableExpiry() { return enableExpiry; }
    public long getBackupInterval() { return backupInterval; }
    public boolean isShouldBackup() { return shouldBackup; }
    public int getBackupCount() { return backupCount; }
    public long getBackupRemovalTime() { return backupRemovalTime; }
    public FileFormat getBackUpFileFormat() { return fileFormat; }
    public DataSerializer<M> getKeySerializer() { return keySerializer; }
    public DataSerializer<V> getValueSerializer() { return valueSerializer; }
    public boolean shouldSync() { return autoSync; }

    static final class ClientCache<L, T> {
        private final Function<L, T> factory;
        private final Consumer<T> disposer;
        private WeakReference<L> level = new WeakReference<>(null);
        private T value;

        ClientCache(Function<L, T> factory, Consumer<T> disposer) {
            this.factory = Objects.requireNonNull(factory, "factory");
            this.disposer = Objects.requireNonNull(disposer, "disposer");
        }

        T get(L currentLevel) {
            Objects.requireNonNull(currentLevel, "level");
            if (level.get() != currentLevel || value == null) {
                clear();
                value = Objects.requireNonNull(factory.apply(currentLevel), "client database");
                level = new WeakReference<>(currentLevel);
            }
            return value;
        }

        void clear() {
            T previous = value;
            value = null;
            level.clear();
            if (previous != null) {
                disposer.accept(previous);
            }
        }
    }
}
