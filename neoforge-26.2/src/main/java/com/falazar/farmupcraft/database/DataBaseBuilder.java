package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public class DataBaseBuilder<M, V> {
    private final Identifier databaseName;
    private DataSerializer<M> keySerializer;
    private DataSerializer<V> valueSerializer;
    private boolean enableExpiry;
    private long expiryDuration;
    private boolean shouldBackUp;
    private long backupTime;
    private int backupCount = 6;
    private long backupRemovalTime = 7L * 24 * 60 * 60 * 1000;
    private FileFormat fileFormat = FileFormat.NBT;
    private boolean autoSync;

    public DataBaseBuilder(Identifier databaseName) {
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
    }

    public DataBaseBuilder<M, V> setKeySerializer(DataSerializer<M> keySerializer) {
        this.keySerializer = Objects.requireNonNull(keySerializer, "keySerializer");
        return this;
    }

    public DataBaseBuilder<M, V> setValueSerializer(DataSerializer<V> valueSerializer) {
        this.valueSerializer = Objects.requireNonNull(valueSerializer, "valueSerializer");
        return this;
    }

    public DataBaseBuilder<M, V> enableExpiry(long expiryDurationMilliseconds) {
        return enableExpiry(true, expiryDurationMilliseconds);
    }

    public DataBaseBuilder<M, V> enableExpiry(boolean enabled, long expiryDurationMilliseconds) {
        validateExpiry(enabled, expiryDurationMilliseconds);
        enableExpiry = enabled;
        expiryDuration = expiryDurationMilliseconds;
        return this;
    }

    public DataBaseBuilder<M, V> setBackUpTime(long backupTimeSeconds) {
        validateBackupInterval(true, backupTimeSeconds);
        shouldBackUp = true;
        backupTime = backupTimeSeconds;
        return this;
    }

    public DataBaseBuilder<M, V> setBackupRemovalTime(long backupRemovalTime) {
        if (backupRemovalTime < 0) {
            throw new IllegalArgumentException("Backup retention duration must be non-negative.");
        }
        this.backupRemovalTime = backupRemovalTime;
        return this;
    }

    public DataBaseBuilder<M, V> setBackupCount(int backupCount) {
        if (backupCount <= 0) {
            throw new IllegalArgumentException("Backup count must be positive.");
        }
        this.backupCount = backupCount;
        return this;
    }

    public DataBaseBuilder<M, V> setBackUpFileFormat(FileFormat fileFormat) {
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        return this;
    }

    public DataBaseBuilder<M, V> autoSync() {
        autoSync = true;
        return this;
    }

    public DataBaseAccess<M, V> build() {
        if (keySerializer == null || valueSerializer == null) {
            throw new IllegalStateException("Key and value serializers must be set.");
        }
        return new DataBaseAccess<>(databaseName, keySerializer, valueSerializer, enableExpiry,
                expiryDuration, shouldBackUp, backupTime, backupCount, backupRemovalTime, fileFormat, autoSync);
    }

    static void validateExpiry(boolean enabled, long millis) {
        if (millis < 0 || (enabled && millis == 0)) {
            throw new IllegalArgumentException("Enabled expiry requires a positive duration; disabled expiry must be non-negative.");
        }
    }

    static void validateBackupInterval(boolean enabled, long seconds) {
        long maximumInterval = (Long.MAX_VALUE - System.currentTimeMillis()) / 1000;
        if (seconds < 0 || seconds > maximumInterval || (enabled && seconds == 0)) {
            throw new IllegalArgumentException("Backup interval must fit in milliseconds and be positive when enabled.");
        }
    }
}
