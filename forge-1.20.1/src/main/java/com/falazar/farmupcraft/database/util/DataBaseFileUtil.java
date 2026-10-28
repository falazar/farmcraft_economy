package com.falazar.farmupcraft.database.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class DataBaseFileUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Cache the unique identifier for the world
    private static String worldIdentifier = null;

    public static Path createWorldDirectory() {
        return createDirectoryForName(getUniqueWorldName());
    }

    public static Path getBackupDirectory(String name) {
        return createWorldBackUp(name);
    }

    public static Path createWorldBackUp(String name) {
        return createDirectoryForName("backup").resolve(getUniqueWorldName()).resolve(name);
    }

    public static Path createWorldExport(String name) {
        return createExportDirectory(getUniqueWorldName()).resolve(name);
    }

    public static Path createExportDirectory(String name) {
        Path path = getMainModDirectory().resolve("export").resolve(name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path createDirectoryForName(String name) {
        Path path = getMainModDirectory().resolve(name);
        createDirectoryIfNotExists(path);
        return path;
    }

    public static Path getMainModDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("databases");
    }

    public static void createDirectoryIfNotExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOGGER.info("Created directory: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("Error creating directory: {}", path.toAbsolutePath(), e);
        }
    }

    public static String getUniqueWorldName() {
        if (worldIdentifier == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel world = server.getLevel(ServerLevel.OVERWORLD);
                if (world != null) {
                    // Use the world name and append a unique identifier based on the seed
                    String levelName = server.getWorldData().getLevelName();
                    long seed = world.getSeed();
                    worldIdentifier = String.format("%s_%s", levelName, Long.toHexString(seed));
                } else {
                    worldIdentifier = "unknown_world";
                }
            } else {
                worldIdentifier = "unknown_world";
            }
        }
        return worldIdentifier;
    }
}
