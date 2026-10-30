package com.falazar.farmupcraft.migration;

import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.data.rules.crop.TagBasedRandomCropRule;
import com.falazar.farmupcraft.database.DataSerializer;
import com.falazar.farmupcraft.database.serializers.*;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only inspection of an operator-supplied COPY of the Forge 1.20.1 overworld data directory.
 * Forge used data/farmupcraft:NAME.dat, whereas 26.2 uses data/farmupcraft/NAME.dat.
 * On Windows, explicitly stage copied database files as farmupcraft_NAME.dat instead; both
 * spellings are allowlisted, but ambiguous copies are rejected. The biome manager's legacy
 * filename is farmupcraft_biome_rules_manager.dat on every platform.
 *
 * This is deliberately not a world upgrader or an installer. In particular it never calls
 * DataBaseAccess.get, SavedDataStorage.get/computeIfAbsent, or any live-state mutator.
 */
public final class LegacySavedDataPreflight {
    public static final String BLOCKED_REASON = "APPLY BLOCKED: no import performed. The live database caches "
            + "and biome manager cannot be atomically installed and rolled back together with all ten SavedData files. "
            + "Absent destination files do not prove that live caches are empty. An offline installer with cache/lifecycle "
            + "coordination is required; this preflight does not upgrade a Minecraft world.";
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final int LEGACY_DATA_VERSION = 3465;
    private static final List<Schema<?, ?>> DATABASES = List.of(
            schema("biome_rules_database", new VersionedDataSerializer<>(
                    new HolderDataSerializer<>(Registries.BIOME), new HolderDataSerializer<>(Registries.BIOME), 1),
                    BiomeRulesInstance.CODEC),
            schema("player_database", new UUIDDataSerializer(), PlayerData.CODEC),
            schema("chunk_data_database", new LongDataSerializer(), ChunkData.CODEC),
            schema("village_database", new UUIDDataSerializer(), VillageData.CODEC),
            schema("goods_data_database_new", new StringDataSerializer(), GoodsData.CODEC),
            schema("world_data_database", new IntDataSerializer(), WorldData.CODEC),
            schema("npc_database", new UUIDDataSerializer(), NpcData.CODEC),
            schema("game_structure_database", new LongDataSerializer(), GameStructureData.CODEC),
            schema("game_zone_database", new UUIDDataSerializer(), GameZone.CODEC));

    private LegacySavedDataPreflight() {}

    private record Schema<K, V>(String name, DataSerializer<K> key, DataSerializer<V> value) {
        String id() { return "farmupcraft:" + name; }
    }

    public record FileInspection(String id, Path sourceFile, Path destinationFile, int records) {}

    public record Report(Path sourceCopy, List<FileInspection> files) {
        public Report {
            files = List.copyOf(files);
        }

        public boolean canApply() { return false; }
        public String blockedReason() { return BLOCKED_REASON; }
    }

    private static <K, V> Schema<K, V> schema(String name, DataSerializer<K> key, Codec<V> codec) {
        return new Schema<>(name, key, new CodecDataSerializer<>(codec));
    }

    public static List<String> databaseNames() {
        return DATABASES.stream().map(Schema::name).toList();
    }

    /**
     * All ten files are required; an error never produces a partially validated report.
     * Existing destination files (even empty ones) are conservatively denied. This check is
     * on-disk only: no report certifies that a running world's unsaved state is empty.
     */
    public static Report inspect(Path sourceCopy, Path activeWorld, HolderLookup.Provider registries)
            throws IOException {
        Path world = activeWorld.toRealPath();
        Path copy = sourceCopy.toRealPath();
        if (!Files.isDirectory(world) || !Files.isDirectory(copy)) {
            throw new IOException("Source COPY and active world must be existing directories.");
        }
        if (copy.startsWith(world) || world.startsWith(copy)) {
            throw new IOException("Source COPY must be separate from, and not contain, the active world.");
        }
        Path destinationData = world.resolve("data");
        List<Path> sources = new ArrayList<>();
        List<Path> destinations = new ArrayList<>();
        sources.add(requireSource(copy, BiomeRulesManager.DATA_NAME + ".dat", null));
        destinations.add(destinationData.resolve("farmupcraft").resolve(BiomeRulesManager.DATA_NAME + ".dat"));
        for (Schema<?, ?> schema : DATABASES) {
            sources.add(requireSource(copy, schema.id() + ".dat", "farmupcraft_" + schema.name() + ".dat"));
            destinations.add(destinationData.resolve("farmupcraft").resolve(schema.name() + ".dat"));
        }
        for (Path destination : destinations) {
            rejectLinks(world, destination);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Destination already exists; overwrite/merge is forbidden: " + destination);
            }
        }
        long total = 0;
        List<FileInspection> files = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            Path file = sources.get(index);
            long size = Files.size(file);
            total += size;
            if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                throw new IOException("Source COPY exceeds preflight size limits: " + file);
            }
            try {
                CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.create(MAX_FILE_BYTES));
                requireFields(envelope, "data", "DataVersion");
                if (!(envelope.get("DataVersion") instanceof IntTag)
                        || envelope.getInt("DataVersion").orElseThrow() != LEGACY_DATA_VERSION) {
                    throw new IllegalArgumentException("Expected Forge 1.20.1 DataVersion " + LEGACY_DATA_VERSION);
                }
                CompoundTag payload = compound(envelope, "data");
                String id;
                int records;
                if (index == 0) {
                    id = "farmupcraft:" + BiomeRulesManager.DATA_NAME;
                    records = validateBiomeManager(payload, registries);
                } else {
                    Schema<?, ?> schema = DATABASES.get(index - 1);
                    id = schema.id();
                    records = validateDatabase(schema, payload, registries);
                }
                files.add(new FileInspection(id, file, destinations.get(index), records));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid legacy SavedData " + file.getFileName() + ": "
                        + exception.getMessage(), exception);
            } catch (IOException exception) {
                throw new IOException("Cannot read legacy SavedData " + file.getFileName() + ": "
                        + exception.getMessage(), exception);
            }
        }
        return new Report(copy, files);
    }

    private static Path requireSource(Path copy, String nativeName, String portableName) throws IOException {
        Path nativeFile = null;
        try {
            nativeFile = copy.resolve(nativeName);
        } catch (InvalidPathException exception) {
            if (portableName == null) throw new IOException("Unsupported legacy filename: " + nativeName, exception);
        }
        Path portableFile = portableName == null ? null : copy.resolve(portableName);
        boolean nativeExists = nativeFile != null && Files.exists(nativeFile, LinkOption.NOFOLLOW_LINKS);
        boolean portableExists = portableFile != null && Files.exists(portableFile, LinkOption.NOFOLLOW_LINKS);
        if (nativeExists && portableExists) {
            throw new IOException("Ambiguous COPY: both native and portable filenames exist for " + nativeName);
        }
        Path file = nativeExists ? nativeFile : portableExists ? portableFile : null;
        if (file == null) {
            throw new IOException("Missing required legacy SavedData: " + nativeName
                    + (portableName == null ? "" : " (portable COPY spelling: " + portableName + ")"));
        }
        rejectLinks(copy, file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().getParent().equals(copy)) {
            throw new IOException("Source must be a regular, non-linked file directly inside the COPY: " + file);
        }
        return file;
    }

    private static void rejectLinks(Path root, Path file) throws IOException {
        Path current = root;
        for (Path component : root.relativize(file)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in migration paths: " + current);
            }
            if (!current.equals(file) && Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Migration path parent is not a directory: " + current);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !current.toRealPath().startsWith(root)) {
                throw new IOException("Migration path escapes its directory: " + current);
            }
        }
    }

    private static <K, V> int validateDatabase(Schema<K, V> schema, CompoundTag payload,
                                               HolderLookup.Provider registries) {
        requireFields(payload, "database_name", "map_entry");
        if (!schema.id().equals(payload.getString("database_name").orElseThrow())) {
            throw new IllegalArgumentException("database_name must be " + schema.id());
        }
        ListTag entries = list(payload, "map_entry");
        Set<K> keys = new HashSet<>();
        for (Tag tag : entries) {
            if (!(tag instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid map entry");
            requireFields(entry, "key", "value");
            CompoundTag keyTag = compound(entry, "key");
            CompoundTag valueTag = compound(entry, "value");
            K key = schema.key().deserialize(keyTag, registries);
            V value = schema.value().deserialize(valueTag, registries);
            if (key == null || value == null || !keys.add(key)) {
                throw new IllegalArgumentException("Null or duplicate database key/value");
            }
            CompoundTag encodedKey = schema.key().serialize(key, registries);
            // Version zero/unwrapped holder keys were explicitly supported by the legacy serializer.
            if (schema.name().equals("biome_rules_database")) {
                validateLegacyHolderKey(keyTag, encodedKey);
            } else {
                requirePreserved(keyTag, encodedKey);
            }
            requirePreserved(valueTag, schema.value().serialize(value, registries));
            if (value instanceof BiomeRulesInstance instance) validateRules(instance, registries);
            if (value instanceof GoodsData goods) requireItem(goods.getItemId());
            if (value instanceof VillageData village) validateAnimalGrains(village);
        }
        return entries.size();
    }

    private static void validateLegacyHolderKey(CompoundTag original, CompoundTag encoded) {
        if (original.contains("version")) {
            if (!(original.get("version") instanceof IntTag)) throw new IllegalArgumentException("Invalid key version");
            int version = original.getInt("version").orElseThrow();
            if (version != 0 && version != 1) throw new IllegalArgumentException("Unsupported key version");
        }
        if (original.contains("versioned")) {
            if (!Set.of("version", "versioned").containsAll(original.keySet())) {
                throw new IllegalArgumentException("Unknown versioned key fields");
            }
            requirePreserved(compound(original, "versioned"), compound(encoded, "versioned"));
        } else {
            if (original.getInt("version").orElse(0) != 0) {
                throw new IllegalArgumentException("Missing versioned key payload");
            }
            CompoundTag unwrapped = original.copy();
            unwrapped.remove("version");
            requirePreserved(unwrapped, compound(encoded, "versioned"));
        }
    }

    private static int validateBiomeManager(CompoundTag payload, HolderLookup.Provider registries) {
        requireFields(payload, "biome_rules", "item_to_biome_list");
        BiomeRulesManager manager = BiomeRulesManager.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), payload).getOrThrow();
        Set<String> biomes = new HashSet<>();
        for (Tag tag : list(payload, "biome_rules")) {
            if (!(tag instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid biome entry");
            requireFields(entry, "biome", "rules");
            if (!biomes.add(entry.getString("biome").orElseThrow())) {
                throw new IllegalArgumentException("Duplicate biome rules");
            }
            CompoundTag rules = compound(entry, "rules");
            requireFields(rules, "rules_instance");
            CompoundTag instanceTag = compound(rules, "rules_instance");
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            BiomeRulesInstance instance = BiomeRulesInstance.CODEC.parse(ops, instanceTag).getOrThrow();
            requirePreserved(instanceTag, BiomeRulesInstance.CODEC.encodeStart(ops, instance).getOrThrow());
            validateRules(instance, registries);
        }
        Set<String> items = new HashSet<>();
        for (Tag tag : list(payload, "item_to_biome_list")) {
            if (!(tag instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid item entry");
            requireFields(entry, "item", "biomes");
            String item = entry.getString("item").orElseThrow();
            requireItem(item);
            if (!items.add(item)) throw new IllegalArgumentException("Duplicate item index");
            Set<String> itemBiomes = new HashSet<>();
            for (Tag biomeTag : list(entry, "biomes")) {
                if (!(biomeTag instanceof CompoundTag biome)) throw new IllegalArgumentException("Invalid item biome");
                requireFields(biome, "biome");
                if (!itemBiomes.add(biome.getString("biome").orElseThrow())) {
                    throw new IllegalArgumentException("Duplicate biome in item index");
                }
            }
        }
        if (manager.getBiomeKeys().size() != biomes.size() || manager.getItems().size() != items.size()) {
            throw new IllegalArgumentException("Biome manager decoding lost entries");
        }
        return biomes.size() + items.size();
    }

    private static void validateRules(BiomeRulesInstance instance, HolderLookup.Provider registries) {
        var rules = instance.getRulesData();
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        if (biomes.get(rules.getBiome()).isEmpty()
                || rules.getBiomeFilter().filter(tag -> biomes.get(tag).isEmpty()).isPresent()) {
            throw new IllegalArgumentException("Unresolved biome tag in rules_data");
        }
        if (rules.getCropRules() instanceof TagBasedRandomCropRule crops
                && (crops.getRandomCropCount() < 0
                || registries.lookupOrThrow(Registries.ITEM).get(crops.getCropTag()).isEmpty())) {
            throw new IllegalArgumentException("Unresolved crop item tag or negative random_crop_count");
        }
        CompoundTag encoded = (CompoundTag) BiomeRulesInstance.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), instance).getOrThrow();
        CompoundTag cropRules = compound(compound(encoded, "rules_data"), "crop_rules");
        if (cropRules.getInt("random_crop_count").orElse(0) < 0) {
            throw new IllegalArgumentException("Negative random_crop_count");
        }
    }

    private static void validateAnimalGrains(VillageData village) {
        String raw = village.getAnimalGrains();
        if (raw.isEmpty()) return;
        Set<String> animals = new HashSet<>();
        for (String part : raw.split("\\|", -1)) {
            String[] pair = part.split("=", -1);
            if (pair.length != 2 || !animals.add(pair[0])) {
                throw new IllegalArgumentException("Malformed/duplicate animal_grains entry");
            }
            if (!pair[1].isEmpty()) {
                for (String item : pair[1].split(",", -1)) requireItem(item);
            }
        }
    }

    private static void requireItem(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalArgumentException("Unresolved item: " + name);
        }
    }

    private static CompoundTag compound(CompoundTag parent, String key) {
        if (parent.get(key) instanceof CompoundTag tag) return tag;
        throw new IllegalArgumentException("Missing/invalid compound: " + key);
    }

    private static ListTag list(CompoundTag parent, String key) {
        if (parent.get(key) instanceof ListTag tag) return tag;
        throw new IllegalArgumentException("Missing/invalid list: " + key);
    }

    private static void requireFields(CompoundTag tag, String... fields) {
        if (!tag.keySet().equals(Set.of(fields))) {
            throw new IllegalArgumentException("Expected exactly these fields: " + String.join(", ", fields));
        }
    }

    private static void requirePreserved(Tag original, Tag encoded) {
        if (!original.equals(encoded)) {
            throw new IllegalArgumentException("Non-canonical or lossy schema; decoding/re-encoding changed the NBT. "
                    + "Unknown fields, invalid optional fields and silent defaults are not accepted.");
        }
    }
}
