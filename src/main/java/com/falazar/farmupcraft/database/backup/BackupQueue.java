package com.falazar.farmupcraft.database.backup;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BackupQueue {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final PriorityQueue<BackupTask> queue = new PriorityQueue<>();

    public static void addToQueue(ResourceLocation databaseName) {
        DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName);
        if (dataBaseAccess != null) {
            long currentTime = System.currentTimeMillis() / 1000;
            queue.add(new BackupTask(databaseName, currentTime + dataBaseAccess.getBackupInterval()));
            LOGGER.info("Added {} to backup queue", databaseName);
        }
    }

    public static void processQueue(ServerLevel level) {
        long currentTime = System.currentTimeMillis() / 1000;
        while (!queue.isEmpty() && queue.peek().nextBackupTime <= currentTime) {
            BackupTask task = queue.poll();
            DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(task.databaseName);
            DataBase<?,?> dataBase = dataBaseAccess.get(level);
            performBackupAsync(dataBase, dataBaseAccess.getBackUpFileFormat(), dataBaseAccess.getBackupCount(), dataBaseAccess.getBackupRemovalTime());
            // Re-add to queue with updated backup time
            if (dataBaseAccess != null) {
                long backupTime = currentTime + dataBaseAccess.getBackupInterval();
                task.nextBackupTime = backupTime;

                // Convert backup time to milliseconds for display
                String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(backupTime * 1000));
                queue.add(task);
                LOGGER.info("Next backup for {} at: {}", dataBase.getDatabaseName(), formattedDate);
            }
        }
    }


    public static void performBackupAsync(DataBase<?, ?> dataBase, FileFormat format, int backupCount, long backupRemovalTime) {
        new Thread(() -> {
            try {
                if (dataBase == null) {
                    LOGGER.error("Database is null, cannot perform backup.");
                    return;
                }

                String databaseName = dataBase.getDatabaseName();
                LOGGER.info("Starting backup for database: {}", databaseName);

                // Create the backup file path based on the provided format
                Path backupFile = createBackupFilePath(dataBase, format);

                // Perform the backup in the specified format
                dataBase.exportDataBase(backupFile, format);

                // Generate and save checksum
                generateAndSaveChecksum(backupFile);

                LOGGER.info("Backup completed successfully for database: {}", databaseName);

                // Cleanup old backups
                cleanupOldBackups(backupFile.getParent(), backupCount, backupRemovalTime);
            } catch (Exception e) {
                LOGGER.error("An error occurred during backup", e);
            }
        }).start();
    }

    private static Path createBackupFilePath(DataBase<?, ?> dataBase, FileFormat format) {
        String path = dataBase.getDatabaseName().split(":")[0];
        String name = dataBase.getDatabaseName().split(":")[1];
        if (name == null || name.isEmpty()) {
            name = "database_export";
        }

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uniqueSuffix = UUID.randomUUID().toString();
        String uniqueName = String.format("%s_%s_%s%s", name, timestamp, uniqueSuffix, format.getExtension());

        return DataBaseFileUtil.createWorldBackUp(path).resolve(name).resolve(uniqueName);
    }

    private static void generateAndSaveChecksum(Path file) throws Exception {
        String checksum = ChecksumUtil.generateChecksum(file);
        ChecksumUtil.saveChecksum(file, checksum);
    }



    private static void cleanupOldBackups(Path backupDir, int backupCount, long backupRemovalTime) {
        File[] files = backupDir.toFile().listFiles();
        if (files == null) {
            return;
        }

        List<File> fileList = getSortedFileList(files);
        long currentTime = System.currentTimeMillis();

        removeOldBackups(fileList, currentTime, backupRemovalTime);
        ensureBackupLimit(fileList, backupCount);
    }

    private static List<File> getSortedFileList(File[] files) {
        return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified))
                .collect(Collectors.toList());
    }

    private static void removeOldBackups(List<File> fileList, long currentTime, long backupRemovalTime) {
        for (File file : fileList) {
            if (file.isFile() && !file.getName().endsWith(".sha") && isOldBackup(file, currentTime, backupRemovalTime)) {
                deleteFileAndSha(file);
            }
        }
    }

    private static void ensureBackupLimit(List<File> fileList, int backupCount) {
        List<File> nonShaFiles = fileList.stream()
                .filter(file -> !file.getName().endsWith(".sha256"))
                .toList();

        if (nonShaFiles.size() > backupCount) {
            int filesToDelete = nonShaFiles.size() - backupCount;
            for (int i = 0; i < filesToDelete; i++) {
                deleteFileAndSha(nonShaFiles.get(i));
            }
        }
    }

    private static void deleteFileAndSha(File file) {
        try {
            if (Files.deleteIfExists(file.toPath())) {
                LOGGER.info("Deleted file: " + file.getName());
                deleteShaFile(file);
            } else {
                LOGGER.warn("File already deleted or does not exist: " + file.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete file '" + file.getName() + "'. Error: " + e.getMessage());
        }
    }

    private static void deleteShaFile(File file) {
        File shaFile = new File(file.getPath() + ".sha256");
        try {
            if (shaFile.exists() && Files.deleteIfExists(shaFile.toPath())) {
                LOGGER.info("Deleted corresponding SHA file: " + shaFile.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete SHA file '" + shaFile.getName() + "'. Error: " + e.getMessage());
        }
    }

    private static boolean isOldBackup(File file, long currentTime, long backupRemovalTime) {
        return (currentTime - file.lastModified()) > backupRemovalTime;
    }


    private static class BackupTask implements Comparable<BackupTask> {
        private final ResourceLocation databaseName;
        private long nextBackupTime;

        public BackupTask(ResourceLocation databaseName, long nextBackupTime) {
            this.databaseName = databaseName;
            this.nextBackupTime = nextBackupTime;
        }

        @Override
        public int compareTo(BackupTask other) {
            return Long.compare(this.nextBackupTime, other.nextBackupTime);
        }
    }
}
