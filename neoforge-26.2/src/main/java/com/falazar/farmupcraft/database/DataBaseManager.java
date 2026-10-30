package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.backup.BackupQueue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DataBaseManager {
    private static final Map<Identifier, DataBaseAccess<?, ?>> ACCESSES = new ConcurrentHashMap<>();
    private static final Set<Identifier> TO_SYNC = ConcurrentHashMap.newKeySet();
    private static MinecraftServer activeServer;

    private DataBaseManager() {}

    public static <M, V> void registerDataBaseAccess(Identifier name, DataBaseAccess<M, V> access) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(access, "access");
        if (!name.equals(access.getDatabaseName())) {
            throw new IllegalArgumentException("Registration name must match the database access name.");
        }
        DataBaseAccess<?, ?> previous = ACCESSES.putIfAbsent(name, access);
        if (previous != null && previous != access) {
            throw new IllegalStateException("Database already registered: " + name);
        }
        if (access.shouldSync()) {
            TO_SYNC.add(name);
        }
        if (access.isShouldBackup()) {
            BackupQueue.addToQueue(name);
        }
    }

    public static <M, V> void registerDataBaseAccess(Identifier name, DataSerializer<M> keySerializer,
                                                    DataSerializer<V> valueSerializer,
                                                    boolean enableExpiry, long expiryDuration) {
        registerDataBaseAccess(name, new DataBaseBuilder<M, V>(name).setKeySerializer(keySerializer)
                .setValueSerializer(valueSerializer).enableExpiry(enableExpiry, expiryDuration).build());
    }

    // Identifier-based registrations carry the type contract; the heterogeneous registry needs one cast.
    @SuppressWarnings("unchecked")
    public static <M, V> DataBaseAccess<M, V> getDataBaseAccess(Identifier name) {
        return (DataBaseAccess<M, V>) ACCESSES.get(Objects.requireNonNull(name, "name"));
    }

    public static List<String> getDataBasesList() {
        return ACCESSES.keySet().stream().map(Identifier::toString).sorted().toList();
    }

    public static List<DataBaseAccess<?, ?>> getDataBases() {
        return List.copyOf(ACCESSES.values());
    }

    public static Set<Identifier> getDataBasesToSync() {
        return Set.copyOf(TO_SYNC);
    }

    public static synchronized void start(MinecraftServer server) {
        BackupQueue.start(Objects.requireNonNull(server, "server"));
        MinecraftServer previous = activeServer;
        activeServer = server;
        if (previous != null && previous != server) {
            ACCESSES.values().forEach(access -> access.clearServerCaches(previous));
        }
    }

    public static void shutDownDataBases(ServerLevel level) {
        shutDownDataBases(Objects.requireNonNull(level, "level").getServer());
    }

    public static synchronized void shutDownDataBases(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        try {
            if (activeServer == server) {
                BackupQueue.shutdown();
            }
        } finally {
            if (activeServer == server) {
                activeServer = null;
            }
            ACCESSES.values().forEach(access -> access.clearServerCaches(server));
        }
    }

    public static void clearClientCaches() {
        ACCESSES.values().forEach(DataBaseAccess::clearClientCache);
    }

    public static void removeDataBaseAccess(ServerLevel level, Identifier name) {
        Objects.requireNonNull(level, "level");
        unregisterDataBaseAccess(name);
    }

    public static void unregisterDataBaseAccess(Identifier name) {
        Objects.requireNonNull(name, "name");
        BackupQueue.removeFromQueue(name);
        TO_SYNC.remove(name);
        DataBaseAccess<?, ?> removed = ACCESSES.remove(name);
        if (removed != null) {
            removed.clearCaches();
        }
    }

    public static synchronized void clear() {
        try {
            BackupQueue.shutdown();
        } finally {
            activeServer = null;
            ACCESSES.values().forEach(DataBaseAccess::clearCaches);
            ACCESSES.clear();
            TO_SYNC.clear();
        }
    }
}
