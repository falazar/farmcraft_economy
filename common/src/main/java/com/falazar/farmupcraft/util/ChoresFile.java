package com.falazar.farmupcraft.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChoresFile {
    static final String DEFAULT_RESOURCE = "/farmupcraft/defaults/chores.md";

    private ChoresFile() {}

    public static Path getPath(Path configDirectory) {
        return configDirectory.resolve("farmupcraft").resolve("chores.md");
    }

    public static boolean seedIfMissing(Path configDirectory, Path gameDirectory) throws IOException {
        Path target = getPath(configDirectory);
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) {
                throw new IOException("Chores path is not a regular file: " + target);
            }
            return false;
        }
        Files.createDirectories(target.getParent());
        Path legacy = gameDirectory.resolve("Rimfog").resolve("chores.md");
        boolean hasLegacy = Files.exists(legacy);
        if (hasLegacy && !Files.isRegularFile(legacy)) {
            throw new IOException("Legacy chores path is not a regular file: " + legacy);
        }
        try (InputStream source = hasLegacy
                ? Files.newInputStream(legacy) : ChoresFile.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (source == null) {
                throw new IOException("Missing bundled chores resource: " + DEFAULT_RESOURCE);
            }
            try {
                Files.copy(source, target);
                return true;
            } catch (FileAlreadyExistsException race) {
                if (!Files.isRegularFile(target)) {
                    throw race;
                }
                return false;
            }
        }
    }
}
