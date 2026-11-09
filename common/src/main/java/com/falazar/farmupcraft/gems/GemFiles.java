package com.falazar.farmupcraft.gems;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class GemFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ID = "[a-z0-9_.-]+:[a-z0-9_./-]+";
    public record Policy(int schema_version, List<String> allowed_dimensions, boolean exclude_cave_biomes,
                         List<String> cave_biome_tags, List<String> excluded_biomes, boolean exclude_rare_biomes,
                         List<String> rare_biome_tags, List<String> rare_biomes, List<String> rare_biome_exceptions) {
        public boolean eligible(GemRules.Biome biome) {
            return !excluded_biomes.contains(biome.id()) && !(exclude_cave_biomes && biome.cave())
                    && !(exclude_rare_biomes && (biome.rare() || rare_biomes.contains(biome.id()))
                        && !rare_biome_exceptions.contains(biome.id()));
        }
    }
    private GemFiles() {}
    public static Policy policy(Path path) throws IOException {
        if (!Files.exists(path)) {
            try (var stream = GemFiles.class.getResourceAsStream("/gem_biome_config.json")) {
                if (stream == null) throw new IOException("Missing default gem biome policy");
                Files.createDirectories(path.getParent());
                Files.copy(stream, path);
            }
        }
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (String key : List.of("exclude_cave_biomes", "exclude_rare_biomes"))
            if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isBoolean())
                throw new IllegalStateException("Gem policy requires boolean " + key);
        Policy policy = GSON.fromJson(json, Policy.class);
        if (policy == null || policy.schema_version != 1) throw new IllegalStateException("Invalid gem policy schema");
        for (List<String> ids : Arrays.asList(policy.allowed_dimensions, policy.cave_biome_tags,
                policy.excluded_biomes, policy.rare_biome_tags, policy.rare_biomes, policy.rare_biome_exceptions))
            if (ids == null || ids.stream().anyMatch(id -> id == null || !id.matches(ID)))
                throw new IllegalStateException("Invalid or missing gem policy biome IDs/tags");
        if (!policy.allowed_dimensions.equals(List.of("minecraft:overworld")))
            throw new IllegalStateException("Gem generation v1 supports minecraft:overworld only");
        return policy;
    }

    public static GemRules.Rules load(Path path, long seed, List<GemRules.Biome> biomes,
                                     List<String> overworld, int minY, int maxY, boolean existing) throws IOException {
        Set<String> installed = new HashSet<>(overworld);
        GemRules.Rules rules;
        if (Files.exists(path)) {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String key : List.of("generationVersion", "seed", "minY", "maxY", "attemptsMin",
                    "attemptsMax", "targetsMin", "targetsMax", "gems", "overworldBiomes", "referenceElevations"))
                if (!json.has(key)) throw new IllegalStateException("Missing gem rule field " + key);
            for (var gem : json.getAsJsonObject("gems").entrySet()) for (var entry : gem.getValue().getAsJsonArray()) {
                var band = entry.getAsJsonObject();
                for (String key : List.of("id", "centerY", "minY", "maxY"))
                    if (!band.has(key)) throw new IllegalStateException("Missing gem band field " + key);
            }
            rules = GSON.fromJson(json, GemRules.Rules.class);
        } else {
            if (existing) throw new IllegalStateException("Existing gem world is missing gem_biomes.json; restore backup");
            rules = GemRules.create(seed, biomes, overworld, minY, maxY);
            write(path, rules);
        }
        GemRules.validate(rules, seed, installed, minY, maxY);
        return rules;
    }

    public static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path pending = path.resolveSibling(path.getFileName() + ".pending");
        try {
            Files.writeString(pending, GSON.toJson(value) + "\n");
            try { Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(pending); }
    }

    public static void migrateProfiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject json;
                try { json = JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
                catch (RuntimeException e) {
                    System.getLogger("farmupcraft.gems").log(System.Logger.Level.WARNING,
                            "Skipping unreadable NPC profile during gem migration; generic fallback applies: " + file, e);
                    continue;
                }
                if (!json.has("uuid") || !json.has("name")) continue;
                if (json.has("gems_migration_version") && json.get("gems_migration_version").getAsInt() >= 1) continue;
                Path backup = directory.resolve("gem-migration-v1-backups").resolve(file.getFileName());
                Files.createDirectories(backup.getParent());
                if (!Files.exists(backup)) Files.copy(file, backup);
                json.addProperty("gems_migration_version", 1);
                json.addProperty("gem_knowledge_source", "world-seed-and-entity-uuid-v1");
                write(file, json);
            }
        }
    }
}
