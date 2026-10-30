package com.falazar.farmupcraft.database.backup;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.fileformats.ExportFormatStrategy;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class BackupQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);
    private static final PriorityQueue<BackupTask> QUEUE = new PriorityQueue<>(
            Comparator.comparingLong(BackupTask::nextBackupTime));
    private static MinecraftServer activeServer;
    private static Session session;

    private BackupQueue() {}

    public static synchronized void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        if (activeServer == server) {
            return;
        }
        shutdown();
        session = new Session(DataBaseFileUtil.backupRoot(DataBaseFileUtil.getMainModDirectory(),
                server.getWorldPath(LevelResource.ROOT)));
        activeServer = server;
        DataBaseManager.getDataBases().stream().filter(DataBaseAccess::isShouldBackup)
                .forEach(access -> addToQueue(access.getDatabaseName()));
    }

    public static synchronized void addToQueue(Identifier name) {
        Objects.requireNonNull(name, "name");
        removeFromQueue(name);
        DataBaseAccess<?, ?> access = DataBaseManager.getDataBaseAccess(name);
        if (session != null && access != null && access.isShouldBackup()) {
            QUEUE.add(new BackupTask(name, nextTime(System.currentTimeMillis(), access.getBackupInterval())));
        }
    }

    public static synchronized void removeFromQueue(Identifier name) {
        Objects.requireNonNull(name, "name");
        QUEUE.removeIf(task -> task.databaseName().equals(name));
    }

    public static synchronized void processQueue(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        MinecraftServer server = level.getServer();
        requireServerThread(server);
        if (session == null) {
            return;
        }
        requireSession(server);
        session.checkCompleted();
        long now = System.currentTimeMillis();
        while (!QUEUE.isEmpty() && QUEUE.peek().nextBackupTime() <= now) {
            BackupTask task = QUEUE.remove();
            DataBaseAccess<?, ?> access = DataBaseManager.getDataBaseAccess(task.databaseName());
            if (access != null && access.isShouldBackup()) {
                performBackupAsync(server, access.get(level), access.getBackUpFileFormat(),
                        access.getBackupCount(), access.getBackupRemovalTime());
                QUEUE.add(new BackupTask(task.databaseName(), nextTime(now, access.getBackupInterval())));
            }
        }
    }

    public static Future<Path> performBackupAsync(DataBase<?, ?> database, FileFormat format,
                                                 int backupCount, long backupRemovalTime) {
        return performBackupAsync(DataBaseFileUtil.requireCurrentServer(), database, format, backupCount, backupRemovalTime);
    }

    public static synchronized Future<Path> performBackupAsync(MinecraftServer server, DataBase<?, ?> database,
                                                               FileFormat format, int backupCount,
                                                               long backupRemovalTime) {
        requireServerThread(Objects.requireNonNull(server, "server"));
        requireSession(server);
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(format, "format");
        validateRetention(backupCount, backupRemovalTime);
        Identifier name = Identifier.parse(database.getDatabaseName());
        // Serialization and registry access must stay on the owning server thread.
        CompoundTag snapshot = new CompoundTag();
        database.save(snapshot);
        return session.submitSnapshot(name, snapshot, format, backupCount, backupRemovalTime);
    }

    public static synchronized void shutdown() {
        Session previous = session;
        session = null;
        activeServer = null;
        QUEUE.clear();
        if (previous != null) {
            previous.close();
        }
    }

    private static void requireSession(MinecraftServer server) {
        if (session == null || activeServer != server) {
            throw new IllegalStateException("Backup queue has not been started for this server.");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Database backup snapshots must be made on the server thread.");
        }
    }

    private static long nextTime(long now, long seconds) {
        if (seconds <= 0 || seconds > (Long.MAX_VALUE - now) / 1000) {
            throw new IllegalArgumentException("Backup interval is outside the schedulable range.");
        }
        return now + seconds * 1000;
    }

    private static void validateRetention(int backupCount, long backupRemovalTime) {
        if (backupCount <= 0 || backupRemovalTime < 0) {
            throw new IllegalArgumentException("Backup count must be positive and retention duration non-negative.");
        }
    }

    static void exportWithChecksum(CompoundTag snapshot, Path file, ExportFormatStrategy exporter) throws Exception {
        Files.createDirectories(file.getParent());
        exporter.export(snapshot, file);
        ChecksumUtil.saveChecksum(file, ChecksumUtil.generateChecksum(file));
    }

    static Path createBackupFilePath(Path backupRoot, Identifier databaseName, FileFormat format, Instant time) {
        String path = databaseName.getPath();
        String basename = path.substring(path.lastIndexOf('/') + 1);
        String filename = basename + "_" + TIMESTAMP.format(time) + "_" + UUID.randomUUID() + format.getExtension();
        return DataBaseFileUtil.resolveDatabaseDirectory(backupRoot, databaseName).resolve(filename);
    }

    static void cleanupOldBackups(Path backupDirectory, String basename, int backupCount,
                                  long backupRemovalTime, long now) throws IOException {
        validateRetention(backupCount, backupRemovalTime);
        Pattern managed = Pattern.compile(Pattern.quote(basename)
                + "_\\d{14}_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:nbt|json)");
        List<BackupFile> files = new ArrayList<>();
        try (var entries = Files.list(backupDirectory)) {
            for (Path file : entries.toList()) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && managed.matcher(file.getFileName().toString()).matches()) {
                    files.add(new BackupFile(file, Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis()));
                }
            }
        }
        files.sort(Comparator.comparingLong(BackupFile::modified)
                .thenComparing(file -> file.path().getFileName().toString()));
        List<BackupFile> remaining = new ArrayList<>();
        for (BackupFile file : files) {
            if (now > file.modified() && now - file.modified() > backupRemovalTime) {
                deleteBackup(file.path());
            } else {
                remaining.add(file);
            }
        }
        for (int i = 0; i < remaining.size() - backupCount; i++) {
            deleteBackup(remaining.get(i).path());
        }
    }

    private static void deleteBackup(Path file) throws IOException {
        Path checksum = file.resolveSibling(file.getFileName() + ".sha256");
        // A directory or symlink masquerading as a sidecar is never ours to delete.
        if (Files.exists(checksum, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(checksum, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Backup checksum is not a regular file: " + checksum);
        }
        Files.deleteIfExists(file);
        Files.deleteIfExists(checksum);
    }

    private record BackupTask(Identifier databaseName, long nextBackupTime) {}
    private record BackupFile(Path path, long modified) {}

    /** A fresh worker and world-root belong to each server run; also usable without a Minecraft world in IO tests. */
    static final class Session implements AutoCloseable {
        private final Path backupRoot;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "farmupcraft-database-backup");
            thread.setDaemon(true);
            return thread;
        });
        private final List<Future<Path>> pending = new ArrayList<>();

        Session(Path backupRoot) {
            this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot");
        }

        synchronized Future<Path> submitSnapshot(Identifier name, CompoundTag snapshot, FileFormat format,
                                                  int backupCount, long backupRemovalTime) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(format, "format");
            validateRetention(backupCount, backupRemovalTime);
            Path file = createBackupFilePath(backupRoot, name, format, Instant.now());
            CompoundTag detached = snapshot.copy();
            Future<Path> result = executor.submit(() -> {
                exportWithChecksum(detached, file, format.getExportStrategy());
                String path = name.getPath();
                cleanupOldBackups(file.getParent(), path.substring(path.lastIndexOf('/') + 1),
                        backupCount, backupRemovalTime, System.currentTimeMillis());
                LOGGER.info("Backup completed successfully for database '{}' at {}", name, file);
                return file;
            });
            pending.add(result);
            return result;
        }

        synchronized void checkCompleted() {
            var iterator = pending.iterator();
            while (iterator.hasNext()) {
                Future<Path> future = iterator.next();
                if (future.isDone()) {
                    iterator.remove();
                    await(future);
                }
            }
        }

        @Override
        public synchronized void close() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    pending.forEach(future -> future.cancel(false));
                    throw new IllegalStateException("Database backup worker did not stop within 30 seconds.");
                }
                checkCompleted();
            } catch (InterruptedException failure) {
                executor.shutdownNow();
                pending.forEach(future -> future.cancel(false));
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping database backups.", failure);
            } finally {
                pending.clear();
            }
        }

        private static void await(Future<Path> future) {
            try {
                future.get();
            } catch (ExecutionException failure) {
                throw new IllegalStateException("Database backup failed.", failure.getCause());
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while checking database backups.", failure);
            }
        }
    }
}
