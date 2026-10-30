package com.falazar.farmupcraft.util;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class NpcConversationLoggerTest {
    private final Path logs = Path.of("build", "test-fixtures", "npc-transcript-" + UUID.randomUUID());
    private static final LocalDate DATE = LocalDate.of(2026, 10, 28);
    private static final LocalTime TIME = LocalTime.of(13, 42, 41);

    @AfterEach
    void cleanFixtures() throws IOException {
        if (!Files.exists(logs)) return;
        try (var paths = Files.walk(logs)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @Test
    void appendsUtf8TimestampedLinesToLegacyPlayerDateNpcPath() throws IOException {
        NpcConversationLogger.append(logs, DATE, TIME, "Player_1", "Player_1", "Hello!");
        NpcConversationLogger.append(logs, DATE, TIME.plusSeconds(1), "Player_1", "Cassey", "Bonjour rivière!");
        assertEquals("[13:42:41] Player_1: Hello!\n[13:42:42] Cassey: Bonjour rivière!\n",
                Files.readString(logs.resolve("Player_1").resolve("26-10-28").resolve("NPC.txt")));
    }

    @Test
    void rejectsDirectoryTraversalAndEscapesForgedTranscriptLines() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> NpcConversationLogger.append(logs, DATE, TIME, "..\\escape", "NPC", "hello"));
        assertFalse(Files.exists(logs));
        NpcConversationLogger.append(logs, DATE, TIME, "Ada", "NPC\nAdmin", "hello\r\n[00:00:00] fake");
        assertEquals("[13:42:41] NPC\\nAdmin: hello\\r\\n[00:00:00] fake\n",
                Files.readString(logs.resolve("Ada").resolve("26-10-28").resolve("NPC.txt")));
    }
}
