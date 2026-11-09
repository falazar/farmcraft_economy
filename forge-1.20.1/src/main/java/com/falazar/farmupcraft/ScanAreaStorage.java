package com.falazar.farmupcraft;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** World/dimension-scoped scan data. Ambiguous installation-wide files are never imported. */
final class ScanAreaStorage {
    private static final Set<MinecraftServer> WARNED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path file(ServerLevel level, String filename) {
        warnLegacy(level.getServer());
        var dimension = level.dimension().location();
        for (String part : (dimension.getNamespace() + "/" + dimension.getPath()).split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("Unsafe scan storage dimension: " + dimension);
        }
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("farmupcraft").resolve("scans")
                .resolve(dimension.getNamespace()).resolve(dimension.getPath()).resolve(filename);
    }

    private static synchronized void warnLegacy(MinecraftServer server) {
        if (!WARNED.add(server)) return;
        for (String name : List.of("findings.json", "scan_cache.json", "large_body_names.json")) {
            Path legacy = FMLPaths.GAMEDIR.get().resolve(name);
            if (Files.exists(legacy)) {
                IslandScanManager.LOGGER.warn("Legacy global scan file {} is ambiguous; left read-only and NOT "
                        + "assigned to this world or dimension. New scans use the save's farmupcraft/scans directory.", legacy);
            }
        }
    }

    static List<JsonObject> findings(ServerLevel level) throws IOException {
        Path path = file(level, "findings.json");
        if (!Files.exists(path)) return List.of();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<JsonObject> result = new ArrayList<>();
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                result.add(element.getAsJsonObject());
            }
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid scan findings: " + path + "; existing file preserved", e);
        }
    }

    static void write(Path path, JsonElement data) throws IOException {
        Files.createDirectories(path.getParent());
        Path pending = path.resolveSibling(path.getFileName() + ".pending");
        try {
            Files.writeString(pending, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
