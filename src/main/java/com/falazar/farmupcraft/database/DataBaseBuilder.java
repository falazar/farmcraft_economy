package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import net.minecraft.resources.ResourceLocation;

import javax.management.timer.Timer;

public class DataBaseBuilder<M, V> {
    private DataBase.DataSerializer<M> keySerializer;
    private DataBase.DataSerializer<V> valueSerializer;
    private boolean enableExpiry = false;
    private long expiryDuration = 0; // in milliseconds
    private long backupTime = 0; // in seconds
    private boolean shouldBackUp = false;
    private final ResourceLocation databaseName;
    private int backupCount = 6;
    private long backupRemovalTime = Timer.ONE_WEEK;
    private FileFormat fileFormat = FileFormat.NBT; // Default to NBT
    private boolean autoSync = false;
    public DataBaseBuilder(ResourceLocation databaseName) {
        this.databaseName = databaseName;
    }

    public DataBaseBuilder<M, V> setKeySerializer(DataBase.DataSerializer<M> keySerializer) {
        this.keySerializer = keySerializer;
        return this;
    }

    public DataBaseBuilder<M, V> setValueSerializer(DataBase.DataSerializer<V> valueSerializer) {
        this.valueSerializer = valueSerializer;
        return this;
    }

    public DataBaseBuilder<M, V> enableExpiry(long expiryDurationMilliseconds) {
        return enableExpiry(true, expiryDurationMilliseconds);
    }

    public DataBaseBuilder<M, V> enableExpiry(boolean enableExpiry, long expiryDurationMilliseconds) {
        this.enableExpiry = enableExpiry;
        this.expiryDuration = expiryDurationMilliseconds;
        return this;
    }

    public DataBaseBuilder<M, V> setBackUpTime(long backupTimeSeconds) {
        this.shouldBackUp = true;
        this.backupTime = backupTimeSeconds;
        return this;
    }

    public DataBaseBuilder<M, V> setBackupRemovalTime(long backupRemovalTime) {
        this.backupRemovalTime = backupRemovalTime;
        return this;
    }

    public DataBaseBuilder<M, V> setBackupCount(int backupCount) {
        this.backupCount = backupCount;
        return this;
    }

    public DataBaseBuilder<M, V> setBackUpFileFormat(FileFormat fileFormat) {
        this.fileFormat = fileFormat;
        return this;
    }

    public DataBaseBuilder<M, V>  autoSync() {
        this.autoSync = true;
        return this;
    }

    public DataBaseAccess<M, V> build() {
        if (keySerializer == null || valueSerializer == null) {
            throw new IllegalStateException("Key and value serializers must be set.");
        }
        if (expiryDuration < 0) {
            throw new IllegalArgumentException("Expiry duration must be non-negative.");
        }
        return new DataBaseAccess<>(databaseName, keySerializer, valueSerializer, enableExpiry, expiryDuration, shouldBackUp, backupTime, backupCount, backupRemovalTime, fileFormat, autoSync);
    }
}

