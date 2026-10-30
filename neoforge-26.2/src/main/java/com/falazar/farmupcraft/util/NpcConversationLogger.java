package com.falazar.farmupcraft.util;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Appends UTF-8 NPC transcripts to logs/&lt;player&gt;/&lt;yy-MM-dd&gt;/NPC.txt. */
public final class NpcConversationLogger {
    private static final CustomLogger LOGGER = new CustomLogger(NpcConversationLogger.class.getSimpleName());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private NpcConversationLogger() {}

    public static void log(String playerName, String speaker, String message) {
        try {
            append(FMLPaths.GAMEDIR.get().resolve("logs"), LocalDate.now(), LocalTime.now(),
                    playerName, speaker, message);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Cannot write NPC conversation for {}: {}", playerName, e.getMessage(), e);
        }
    }

    static synchronized void append(Path logs, LocalDate date, LocalTime time,
                                     String playerName, String speaker, String message) throws IOException {
        if (!playerName.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Invalid Minecraft player name");
        Path dir = logs.resolve(playerName).resolve(date.format(DATE));
        Files.createDirectories(dir);
        // A reply may contain newlines; keep each transcript entry on one timestamped line.
        String line = "[" + time.format(TIME) + "] " + singleLine(speaker) + ": " + singleLine(message) + "\n";
        Files.writeString(dir.resolve("NPC.txt"), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
