package com.falazar.farmupcraft.database.util;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class DataBaseFileUtil {
    private DataBaseFileUtil() {}

    public static MinecraftServer requireCurrentServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("A running server is required for database world paths.");
        }
        return server;
    }

    public static Path getMainModDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("databases");
    }

    /** World identity uses the save path, not a display name/seed shared by different saves. */
    public static String worldIdentifier(Path worldDirectory) {
        Path absolute = Objects.requireNonNull(worldDirectory, "worldDirectory").toAbsolutePath().normalize();
        return UUID.nameUUIDFromBytes(absolute.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String getUniqueWorldName(MinecraftServer server) {
        return worldIdentifier(Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT));
    }

    public static String getUniqueWorldName() {
        return getUniqueWorldName(requireCurrentServer());
    }

    public static Path worldDirectory(Path databaseRoot, Path worldDirectory) {
        return Objects.requireNonNull(databaseRoot, "databaseRoot").resolve(worldIdentifier(worldDirectory));
    }

    public static Path backupRoot(Path databaseRoot, Path worldDirectory) {
        return Objects.requireNonNull(databaseRoot, "databaseRoot").resolve("backup")
                .resolve(worldIdentifier(worldDirectory));
    }

    public static Path backupDirectory(Path databaseRoot, Path worldDirectory, Identifier databaseName) {
        return resolveDatabaseDirectory(backupRoot(databaseRoot, worldDirectory), databaseName);
    }

    public static Path resolveDatabaseDirectory(Path worldRoot, Identifier databaseName) {
        Objects.requireNonNull(databaseName, "databaseName");
        return resolveContained(resolveContained(worldRoot,
                databaseName.getNamespace()), databaseName.getPath());
    }

    public static Path getBackupDirectory(MinecraftServer server, Identifier databaseName) {
        return backupDirectory(getMainModDirectory(),
                Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT), databaseName);
    }

    public static Path createWorldDirectory(MinecraftServer server) {
        Path path = getMainModDirectory().resolve(getUniqueWorldName(server));
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path createWorldDirectory() {
        return createWorldDirectory(requireCurrentServer());
    }

    public static Path getBackupDirectory(String name) {
        return createWorldBackUp(name);
    }

    public static Path createWorldBackUp(MinecraftServer server, String name) {
        Path path = resolveContained(getMainModDirectory().resolve("backup").resolve(getUniqueWorldName(server)), name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path createWorldBackUp(String name) {
        return createWorldBackUp(requireCurrentServer(), name);
    }

    public static Path createWorldExport(MinecraftServer server, String name) {
        Path path = resolveContained(getMainModDirectory().resolve("export").resolve(getUniqueWorldName(server)), name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path createWorldExport(String name) {
        return createWorldExport(requireCurrentServer(), name);
    }

    public static Path createExportDirectory(String name) {
        Path path = resolveContained(getMainModDirectory().resolve("export"), name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path createDirectoryForName(String name) {
        Path path = resolveContained(getMainModDirectory(), name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static void createDirectoryIfNotExists(Path path) {
        try {
            Files.createDirectories(Objects.requireNonNull(path, "path"));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot create database directory: " + path, failure);
        }
    }

    private static Path resolveContained(Path parent, String name) {
        Objects.requireNonNull(name, "name");
        Path root = parent.toAbsolutePath().normalize();
        Path child = root.resolve(name).normalize();
        if (name.isBlank() || !child.startsWith(root) || child.equals(root)) {
            throw new IllegalArgumentException("Invalid database directory name: " + name);
        }
        return child;
    }
}
