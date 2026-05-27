package com.falazar.farmupcraft.util;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Appends NPC conversation lines to:
 * logs/<playerName>/<YY-MM-DD>/NPC.txt
 *
 * Each line is timestamped:
 * [HH:mm:ss] <PlayerName>: hello there
 * [HH:mm:ss] Cassey: Well hello to you too!
 */
public class NpcConversationLogger {

    private static final CustomLogger LOGGER = new CustomLogger(NpcConversationLogger.class.getSimpleName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private NpcConversationLogger() {
    }

    /**
     * Appends one line to the player's NPC conversation log.
     *
     * @param playerName the Minecraft player name (used as folder name)
     * @param speaker    who said it (player name or NPC name)
     * @param message    what was said
     */
    public static void log(String playerName, String speaker, String message) {
        try {
            Path logFile = resolveLogFile(playerName);
            String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + speaker + ": " + message + "\n";
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to write NPC conversation log for {}: {}", playerName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private static Path resolveLogFile(String playerName) throws IOException {
        String today = LocalDate.now().format(DATE_FMT);
        Path dir = FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve(playerName)
                .resolve(today);

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        return dir.resolve("NPC.txt");
    }
}
