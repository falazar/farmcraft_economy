package com.falazar.farmupcraft.database.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumUtilTest {
    @TempDir
    Path directory;

    @Test
    void generatesKnownSha256() throws Exception {
        Path file = directory.resolve("backup.json");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ChecksumUtil.generateChecksum(file));
    }

    @Test
    void savesAndVerifiesChecksumAndDetectsModifiedBackup() throws Exception {
        Path file = directory.resolve("backup.json");
        Files.writeString(file, "original", StandardCharsets.UTF_8);
        String checksum = ChecksumUtil.generateChecksum(file);

        ChecksumUtil.saveChecksum(file, checksum);
        Path checksumFile = directory.resolve("backup.json.sha256");
        assertEquals(checksum, Files.readString(checksumFile));
        assertTrue(ChecksumUtil.verifyChecksum(file, checksumFile));

        Files.writeString(file, "modified", StandardCharsets.UTF_8);
        assertFalse(ChecksumUtil.verifyChecksum(file, checksumFile));
    }

    @Test
    void missingBackupPropagatesAnError() {
        assertThrows(java.io.IOException.class,
                () -> ChecksumUtil.generateChecksum(directory.resolve("missing.json")));
    }
}
