package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.util.ClientUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;

public class DataBaseAccess<M, V> {

    private final DataBase.DataSerializer<M> keySerializer;
    private final DataBase.DataSerializer<V> valueSerializer;
    private final boolean enableExpiry;
    private final boolean shouldBackup;
    private final long expiryDuration; // in milliseconds
    private final ResourceLocation databaseName;
    private final long backupInterval; // in seconds
    private final int backupCount;
    private final long backupRemovalTime;
    private final FileFormat fileFormat; // Added FileFormat field

    private WeakReference<DataBase<M, V>> clientCache = new WeakReference<>(null);
    private static WeakReference<ClientLevel> levelCache = new WeakReference<>(null);
    private final boolean autoSync;

    public DataBaseAccess(ResourceLocation databaseName, DataBase.DataSerializer<M> keySerializer, DataBase.DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration, boolean shouldBackUp, long backupInterval, int backupCount, long backupRemovalTime, FileFormat fileFormat, boolean autoSync) {
        this.databaseName = databaseName;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.enableExpiry = enableExpiry;
        this.expiryDuration = expiryDuration;
        this.backupInterval = backupInterval;
        this.shouldBackup = shouldBackUp;
        this.backupCount = backupCount;
        this.backupRemovalTime = backupRemovalTime;
        this.fileFormat = fileFormat; // Initialize FileFormat field
        this.autoSync = autoSync;
    }

    public DataBase<M, V> get(Level level) {
        if (level.isClientSide()) {
            if (levelCache.get() != ClientUtils.getClientLevel()) {
                levelCache = new WeakReference<>(ClientUtils.getClientLevel());
                clientCache = new WeakReference<>(new DataBase<>(databaseName.toString(), keySerializer, valueSerializer, enableExpiry, expiryDuration, false));
            }
            return clientCache.get();
        }
        return DataBase.get(level, databaseName.toString(), keySerializer, valueSerializer, enableExpiry, expiryDuration,autoSync);
    }

    public long getExpiryDuration() {
        return expiryDuration;
    }

    public ResourceLocation getDatabaseName() {
        return databaseName;
    }

    public boolean isEnableExpiry() {
        return enableExpiry;
    }

    public long getBackupInterval() {
        return backupInterval;
    }

    public boolean isShouldBackup() {
        return shouldBackup;
    }

    public int getBackupCount() {
        return backupCount;
    }

    public long getBackupRemovalTime() {
        return backupRemovalTime;
    }

    public FileFormat getBackUpFileFormat() {
        return fileFormat;
    }

    public DataBase.DataSerializer<M> getKeySerializer() {
        return keySerializer;
    }

    public DataBase.DataSerializer<V> getValueSerializer() {
        return valueSerializer;
    }

    public boolean shouldSync() {
        return autoSync;
    }
}

