package com.falazar.farmupcraft.database.util;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataBaseFileUtilTest {
    @TempDir
    Path directory;

    @Test
    void sameNamedSavesRemainIsolatedAcrossWorldSwitches() {
        Path root = directory.resolve("databases");
        Path first = directory.resolve("instance-one").resolve("world");
        Path second = directory.resolve("instance-two").resolve("world");
        Identifier name = Identifier.parse("farmupcraft:players/coins");
        Path firstBackup = DataBaseFileUtil.backupDirectory(root, first, name);
        Path secondBackup = DataBaseFileUtil.backupDirectory(root, second, name);
        assertNotEquals(firstBackup, secondBackup);
        assertEquals(firstBackup, DataBaseFileUtil.backupDirectory(root, first, name));
        assertTrue(firstBackup.startsWith(DataBaseFileUtil.backupRoot(root, first)));
        assertTrue(secondBackup.startsWith(DataBaseFileUtil.backupRoot(root, second)));
        assertTrue(firstBackup.endsWith(Path.of("farmupcraft", "players", "coins")));
        assertEquals(DataBaseFileUtil.worldIdentifier(first),
                DataBaseFileUtil.worldIdentifier(first.resolve("nested").resolve("..")));
        assertNotEquals(DataBaseFileUtil.worldDirectory(root, first), DataBaseFileUtil.worldDirectory(root, second));
    }

    @Test
    void createsDirectoriesOrSurfacesIOFailure() throws Exception {
        Path nested = directory.resolve("one").resolve("two");
        DataBaseFileUtil.createDirectoryIfNotExists(nested);
        assertTrue(Files.isDirectory(nested));
        Path file = Files.writeString(directory.resolve("blocker"), "not a directory");
        assertThrows(UncheckedIOException.class, () -> DataBaseFileUtil.createDirectoryIfNotExists(file.resolve("child")));
    }

    @Test
    void worldIdentityRequiresExplicitContextAndRejectsTraversal() {
        assertThrows(NullPointerException.class, () -> DataBaseFileUtil.worldIdentifier(null));
        assertThrows(NullPointerException.class, () -> DataBaseFileUtil.getUniqueWorldName(null));
        assertThrows(IllegalArgumentException.class, () -> DataBaseFileUtil.backupDirectory(directory,
                directory.resolve("world"), Identifier.parse("farmupcraft:../outside")));
    }
}
