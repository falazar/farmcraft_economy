package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChoresFileTest {
    @TempDir
    Path game;

    @Test
    void missingFileIsSeededByteForByteFromTheBundledResource() throws IOException {
        Path config = game.resolve("config");
        assertTrue(ChoresFile.seedIfMissing(config, game));
        Path target = ChoresFile.getPath(config);
        assertEquals(config.resolve("farmupcraft").resolve("chores.md"), target);
        try (var source = ChoresFile.class.getResourceAsStream(ChoresFile.DEFAULT_RESOURCE)) {
            assertNotNull(source);
            assertArrayEquals(source.readAllBytes(), Files.readAllBytes(target));
        }
        assertTrue(Files.readAllLines(target, StandardCharsets.UTF_8).stream()
                .anyMatch(line -> !line.isBlank() && !line.stripLeading().startsWith("#")));
    }

    @Test
    void anExistingCustomFileIsNeverOverwritten() throws IOException {
        Path config = game.resolve("config");
        Path target = ChoresFile.getPath(config);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "- Custom task\n", StandardCharsets.UTF_8);
        assertFalse(ChoresFile.seedIfMissing(config, game));
        assertEquals("- Custom task\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void anExistingEmptyFileIsIntentionallyPreserved() throws IOException {
        Path config = game.resolve("config");
        Path target = ChoresFile.getPath(config);
        Files.createDirectories(target.getParent());
        Files.createFile(target);
        assertFalse(ChoresFile.seedIfMissing(config, game));
        assertEquals(0, Files.size(target));
    }

    @Test
    void anExistingLegacyFileIsCopiedWithoutBeingChanged() throws IOException {
        Path config = game.resolve("config");
        Path legacy = game.resolve("Rimfog").resolve("chores.md");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "- Legacy custom task\n", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(legacy);
        assertTrue(ChoresFile.seedIfMissing(config, game));
        assertArrayEquals(original, Files.readAllBytes(ChoresFile.getPath(config)));
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    @Test
    void theNewLocationWinsOverLegacyContent() throws IOException {
        Path config = game.resolve("config");
        Path target = ChoresFile.getPath(config);
        Path legacy = game.resolve("Rimfog").resolve("chores.md");
        Files.createDirectories(target.getParent());
        Files.createDirectories(legacy.getParent());
        Files.writeString(target, "- New task\n");
        Files.writeString(legacy, "- Old task\n");
        assertFalse(ChoresFile.seedIfMissing(config, game));
        assertEquals("- New task\n", Files.readString(target));
        assertEquals("- Old task\n", Files.readString(legacy));
    }

    @Test
    void subsequentStartupPreservesEditsToTheSeededFile() throws IOException {
        Path config = game.resolve("config");
        assertTrue(ChoresFile.seedIfMissing(config, game));
        Files.writeString(ChoresFile.getPath(config), "- Edited after startup\n");
        assertFalse(ChoresFile.seedIfMissing(config, game));
        assertEquals("- Edited after startup\n", Files.readString(ChoresFile.getPath(config)));
    }

    @Test
    void aDirectoryAtTheDestinationFailsExplicitly() throws IOException {
        Path config = game.resolve("config");
        Files.createDirectories(ChoresFile.getPath(config));
        assertThrows(IOException.class, () -> ChoresFile.seedIfMissing(config, game));
    }

    @Test
    void anInvalidConfigParentFailsWithoutChangingIt() throws IOException {
        Path config = game.resolve("config");
        Files.writeString(config, "Not a directory");
        assertThrows(IOException.class, () -> ChoresFile.seedIfMissing(config, game));
        assertEquals("Not a directory", Files.readString(config));
    }

    @Test
    void anUnreadableLegacyDirectoryDoesNotSilentlyFallBackToDefaults() throws IOException {
        Path config = game.resolve("config");
        Files.createDirectories(game.resolve("Rimfog").resolve("chores.md"));
        assertThrows(IOException.class, () -> ChoresFile.seedIfMissing(config, game));
        assertFalse(Files.exists(ChoresFile.getPath(config)));
    }

    @Test
    void concurrentStartupOnlyCreatesTheFileOnce() throws Exception {
        Path config = game.resolve("config");
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return ChoresFile.seedIfMissing(config, game);
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return ChoresFile.seedIfMissing(config, game);
            });
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            try (var source = ChoresFile.class.getResourceAsStream(ChoresFile.DEFAULT_RESOURCE)) {
                assertNotNull(source);
                assertArrayEquals(source.readAllBytes(), Files.readAllBytes(ChoresFile.getPath(config)));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
