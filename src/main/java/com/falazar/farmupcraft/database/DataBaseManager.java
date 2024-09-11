package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.backup.BackupQueue;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class DataBaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, DataBaseAccess<?, ?>> databaseAccessMap = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> dataBasesToSync = new ConcurrentSkipListSet<>();

    // Prevent instantiation
    private DataBaseManager() {}



    public static <M, V> void registerDataBaseAccess(ResourceLocation databaseName, DataBaseAccess<M,V> dataBaseAccess) {
        databaseAccessMap.put(databaseName, dataBaseAccess);
        if(dataBaseAccess.isShouldBackup()){
            BackupQueue.addToQueue(databaseName);
        }
        if(dataBaseAccess.shouldSync()) {
            dataBasesToSync.add(databaseName);
        }
        LOGGER.info("Registered DataBaseAccess for database '{}'. Expiry enabled: {}, Duration: {} ms", databaseName, dataBaseAccess.isEnableExpiry(), dataBaseAccess.getExpiryDuration());
    }

    public static <M, V> void registerDataBaseAccess(ResourceLocation databaseName, DataBase.DataSerializer<M> keySerializer, DataBase.DataSerializer<V> valueSerializer, boolean enableExpiry, long expiryDuration) {
        DataBaseAccess<M, V> access = new DataBaseBuilder<M, V>(databaseName)
                .setKeySerializer(keySerializer)
                .setValueSerializer(valueSerializer)
                .enableExpiry(enableExpiry, expiryDuration)
                .build();
        
        databaseAccessMap.put(databaseName, access);
        LOGGER.info("Registered DataBaseAccess for database '{}'. Expiry enabled: {}, Duration: {} ms", databaseName, enableExpiry, expiryDuration);
    }

    @SuppressWarnings("unchecked")
    public static <M, V> DataBaseAccess<M, V> getDataBaseAccess(ResourceLocation name) {
        DataBaseAccess<M, V> access = (DataBaseAccess<M, V>) databaseAccessMap.get(name);
        if (access != null) {
            LOGGER.info("Retrieved DataBaseAccess for database '{}'", name);
        } else {
            LOGGER.warn("DataBaseAccess for database '{}' not found", name);
        }
        return access;
    }

    public static List<String> getDataBasesList() {
        return databaseAccessMap.keySet().stream().map(ResourceLocation::toString).toList();
    }

    public static List<DataBaseAccess<?, ?>> getDataBases() {
        return databaseAccessMap.values().stream().toList();
    }

    public static void shutDownDataBases(ServerLevel level) {
        LOGGER.info("Shutting down databases!");
        for(Map.Entry<ResourceLocation, DataBaseAccess<?, ?>> dataBaseAccessEntry : databaseAccessMap.entrySet()) {

            dataBaseAccessEntry.getValue().get(level).shutdown();
            LOGGER.info("Shutdown {}", dataBaseAccessEntry.getKey());
        }
        LOGGER.info("Shutdown all databases");
    }

    public static void removeDataBaseAccess(ServerLevel level, String name) {
        if (databaseAccessMap.containsKey(name)) {
            DataBaseAccess<?,?> dataBaseAccess = databaseAccessMap.get(name);
            dataBaseAccess.get(level).shutdown();
            databaseAccessMap.remove(name);
            LOGGER.info("Removed DataBaseAccess for database '{}'", name);
        } else {
            LOGGER.warn("DataBaseAccess for database '{}' not found", name);
        }
    }

    public static Set<ResourceLocation> getDataBasesToSync() {
        return dataBasesToSync;
    }
}
