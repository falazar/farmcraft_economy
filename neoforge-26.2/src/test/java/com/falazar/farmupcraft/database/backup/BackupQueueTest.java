package com.falazar.farmupcraft.database.backup;

import com.falazar.farmupcraft.database.fileformats.ExportFormatStrategy;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BackupQueueTest {
    private static final Identifier NAME = Identifier.parse("farmupcraft:players/coins");
    @TempDir
    Path directory;

    private Path managed(String basename, String extension, long modified) throws IOException {
        Path file = directory.resolve(basename + "_20260101000000_" + UUID.randomUUID() + extension);
        Files.writeString(file, "backup");
        Files.writeString(file.resolveSibling(file.getFileName() + ".sha256"), "checksum");
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
        return file;
    }

    @Test
    void retentionOnlyDeletesMatchingManagedFilesAndTheirSha256Sidecars() throws Exception {
        Path oldest = managed("coins", ".nbt", 100);
        Path middle = managed("coins", ".json", 200);
        Path newest = managed("coins", ".nbt", 300);
        Path unrelated = Files.writeString(directory.resolve("notes.txt"), "keep");
        Path unownedNbt = Files.writeString(directory.resolve("coins_manual.nbt"), "keep");
        Path otherDatabase = managed("other", ".nbt", 1);
        Path orphanChecksum = Files.writeString(directory.resolve("orphan.nbt.sha256"), "keep");
        Path managedLookingDirectory = Files.createDirectory(directory.resolve(
                "coins_20260101000000_" + UUID.randomUUID() + ".nbt"));
        Path directoryContents = Files.writeString(managedLookingDirectory.resolve("important.txt"), "keep");
        BackupQueue.cleanupOldBackups(directory, "coins", 2, Long.MAX_VALUE, 1000);
        assertFalse(Files.exists(oldest));
        assertFalse(Files.exists(oldest.resolveSibling(oldest.getFileName() + ".sha256")));
        for (Path keep : new Path[]{middle, newest, unrelated, unownedNbt, otherDatabase,
                orphanChecksum, managedLookingDirectory, directoryContents}) {
            assertTrue(Files.exists(keep), keep.toString());
        }
    }

    @Test
    void ageCleanupDoesNotCountAlreadyDeletedBackupsAgainstRetentionLimit() throws Exception {
        Path expired = managed("coins", ".nbt", 100);
        Path firstRecent = managed("coins", ".nbt", 900);
        Path secondRecent = managed("coins", ".json", 950);
        BackupQueue.cleanupOldBackups(directory, "coins", 2, 200, 1000);
        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(firstRecent));
        assertTrue(Files.exists(secondRecent));
        assertTrue(Files.exists(firstRecent.resolveSibling(firstRecent.getFileName() + ".sha256")));
    }

    @Test
    void retentionNeverDeletesDirectoriesMasqueradingAsChecksumFiles() throws Exception {
        Path backup = managed("coins", ".nbt", 100);
        Path sidecar = backup.resolveSibling(backup.getFileName() + ".sha256");
        Files.delete(sidecar);
        Files.createDirectory(sidecar);
        assertThrows(IOException.class, () -> BackupQueue.cleanupOldBackups(directory, "coins", 1, 0, 1000));
        assertTrue(Files.isRegularFile(backup));
        assertTrue(Files.isDirectory(sidecar));
    }

    @Test
    void successfulExportsHaveVerifiableChecksumsForBothFormats() throws Exception {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("value", "saved");
        try (var session = new BackupQueue.Session(directory)) {
            for (FileFormat format : FileFormat.values()) {
                Path file = session.submitSnapshot(NAME, snapshot, format, 6, Long.MAX_VALUE)
                        .get(10, TimeUnit.SECONDS);
                assertTrue(Files.isRegularFile(file));
                Path sidecar = file.resolveSibling(file.getFileName() + ".sha256");
                assertTrue(ChecksumUtil.verifyChecksum(file, sidecar));
                assertEquals(snapshot, format.getImportStrategy().importData(file));
            }
            session.checkCompleted();
        }
    }

    @Test
    void failedExportNeverGeneratesAChecksum() throws Exception {
        Path output = directory.resolve("failed.nbt");
        ExportFormatStrategy failingExporter = new ExportFormatStrategy() {
            @Override
            public void export(CompoundTag data, Path filePath) throws IOException {
                Files.writeString(filePath, "partial output");
                throw new IOException("Deliberate export failure");
            }

            @Override
            public String getExtension() {
                return ".nbt";
            }
        };
        assertThrows(IOException.class,
                () -> BackupQueue.exportWithChecksum(new CompoundTag(), output, failingExporter));
        assertFalse(Files.exists(output.resolveSibling(output.getFileName() + ".sha256")));
    }

    @Test
    void asynchronousIOErrorsReachTheFutureAndLifecycleFailureCheck() throws Exception {
        Path blocker = Files.writeString(directory.resolve("blocker"), "file");
        try (var session = new BackupQueue.Session(blocker)) {
            var future = session.submitSnapshot(NAME, new CompoundTag(), FileFormat.NBT, 1, 1000);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            IllegalStateException reported = assertThrows(IllegalStateException.class, session::checkCompleted);
            assertInstanceOf(IOException.class, reported.getCause());
            assertFalse(Files.exists(blocker.resolve("farmupcraft")));
        }
    }

    @Test
    void newServerRunGetsAFreshExecutorAndWorldRoot() throws Exception {
        Path databaseRoot = directory.resolve("databases");
        Path firstRoot = DataBaseFileUtil.backupRoot(databaseRoot, directory.resolve("save-one"));
        Path secondRoot = DataBaseFileUtil.backupRoot(databaseRoot, directory.resolve("save-two"));
        var first = new BackupQueue.Session(firstRoot);
        Path firstBackup;
        try (first) {
            firstBackup = first.submitSnapshot(NAME, new CompoundTag(), FileFormat.NBT, 1, Long.MAX_VALUE)
                    .get(10, TimeUnit.SECONDS);
        }
        assertThrows(RejectedExecutionException.class,
                () -> first.submitSnapshot(NAME, new CompoundTag(), FileFormat.NBT, 1, Long.MAX_VALUE));
        try (var second = new BackupQueue.Session(secondRoot)) {
            Path secondBackup = second.submitSnapshot(NAME, new CompoundTag(), FileFormat.NBT, 1, Long.MAX_VALUE)
                    .get(10, TimeUnit.SECONDS);
            assertTrue(firstBackup.startsWith(firstRoot.toAbsolutePath()));
            assertTrue(secondBackup.startsWith(secondRoot.toAbsolutePath()));
            assertNotEquals(firstBackup.getParent(), secondBackup.getParent());
            assertTrue(Files.exists(firstBackup));
        }
    }

    @Test
    void workerUsesADetachedSnapshot() throws Exception {
        CompoundTag source = new CompoundTag();
        source.putString("key", "before");
        CompoundTag expected = source.copy();
        try (var session = new BackupQueue.Session(directory)) {
            var result = session.submitSnapshot(NAME, source, FileFormat.NBT, 1, Long.MAX_VALUE);
            source.putString("key", "after");
            Path file = result.get(10, TimeUnit.SECONDS);
            assertEquals(expected, FileFormat.NBT.getImportStrategy().importData(file));
        }
    }

    @Test
    void pathsAndRetentionRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> BackupQueue.cleanupOldBackups(directory, "coins", 0, 100, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> BackupQueue.cleanupOldBackups(directory, "coins", 1, -1, 1000));
        assertThrows(IllegalArgumentException.class, () -> BackupQueue.createBackupFilePath(directory,
                Identifier.parse("farmupcraft:../escape"), FileFormat.NBT, Instant.now()));
    }
}
