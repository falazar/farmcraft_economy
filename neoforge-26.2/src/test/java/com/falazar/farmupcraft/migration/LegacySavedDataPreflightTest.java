package com.falazar.farmupcraft.migration;

import com.falazar.farmupcraft.command.LegacySavedDataCommand;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class LegacySavedDataPreflightTest {
    private static final String UUID_TEXT = "01234567-89ab-cdef-fedc-ba9876543210";
    private static final String UUID_KEY = "{uuid:[I;19088743,-1985229329,-19088744,1985229328]}";
    private static final String INSTANCE = """
            {rules_data:{biome:"minecraft:is_overworld",
             crop_rules:{type:"farmupcraft:item_list_crop_rule",
                         allowed_crops:["minecraft:wheat"],random_crop_count:15}},
             random_crops:["minecraft:wheat"]}
            """;
    private final List<Path> directories = new ArrayList<>();

    @AfterEach
    void cleanFixtures() throws IOException {
        for (Path directory : directories) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @Test
    void actualLegacyCompressedEnvelopeForAllNineDatabasesAndBiomeManagerIsReadOnly(MinecraftServer server)
            throws Exception {
        Fixture fixture = fixture();
        Map<String, String> before = snapshot(fixture.root());
        var report = inspect(server, fixture);
        assertEquals(10, report.files().size());
        assertTrue(report.files().stream().allMatch(file -> file.records() > 0));
        assertEquals(9, LegacySavedDataPreflight.databaseNames().size());
        assertFalse(report.canApply());
        assertTrue(report.blockedReason().contains("APPLY BLOCKED"));
        assertTrue(report.blockedReason().contains("live caches"));
        assertEquals(before, snapshot(fixture.root()));
        assertFalse(Files.exists(fixture.world().resolve("data")));
        assertEquals(fixture.world().toRealPath().resolve("data").resolve("farmupcraft")
                        .resolve("goods_data_database_new.dat"),
                report.files().stream().filter(file -> file.id().equals("farmupcraft:goods_data_database_new"))
                        .findFirst().orElseThrow().destinationFile());
        CompoundTag envelope = NbtIo.readCompressed(source(fixture, "player_database"), NbtAccounter.unlimitedHeap());
        assertEquals(3465, envelope.getInt("DataVersion").orElseThrow());
        assertEquals("farmupcraft:player_database", compound(envelope, "data")
                .getString("database_name").orElseThrow());
    }

    @Test
    void nativeColonFilenamesAreAcceptedWhereTheFilesystemSupportsThem(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path nativeFile;
        try {
            nativeFile = fixture.copy().resolve("farmupcraft:player_database.dat");
        } catch (InvalidPathException exception) {
            assumeTrue(false, "Native Forge colon filenames cannot be represented on this filesystem");
            return;
        }
        Files.move(source(fixture, "player_database"), nativeFile);
        var report = inspect(server, fixture);
        Path realNativeFile = nativeFile.toRealPath();
        assertTrue(report.files().stream().anyMatch(file -> file.sourceFile().equals(realNativeFile)));
        Files.copy(nativeFile, source(fixture, "player_database"));
        assertDeniedWithoutMutation(server, fixture, "Ambiguous");
    }

    @Test
    void missingCopyDirectoryAndIncompleteCopyFailInsteadOfInventingEmptyDatabases(MinecraftServer server)
            throws Exception {
        Fixture fixture = fixture();
        assertThrows(IOException.class, () -> LegacySavedDataPreflight.inspect(
                fixture.root().resolve("missing"), fixture.world(), server.registryAccess()));
        Files.delete(source(fixture, "npc_database"));
        assertDeniedWithoutMutation(server, fixture, "Missing required");
    }

    @Test
    void activeWorldAndItsDescendantsAndAncestorsAreRejectedBeforeAnyMutation(MinecraftServer server)
            throws Exception {
        Fixture fixture = fixture();
        Path nested = Files.createDirectories(fixture.world().resolve("copied-data"));
        Map<String, String> before = snapshot(fixture.root());
        for (Path unsafe : List.of(fixture.world(), nested, fixture.root(), fixture.world().resolve("."))) {
            IOException exception = assertThrows(IOException.class, () -> LegacySavedDataPreflight.inspect(
                    unsafe, fixture.world(), server.registryAccess()));
            assertTrue(exception.getMessage().contains("separate"), exception.getMessage());
        }
        assertEquals(before, snapshot(fixture.root()));
    }

    @Test
    void sourceAliasIntoActiveWorldIsRejectedAfterRealPathResolution(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path alias = fixture.root().resolve("alias");
        createLinkOrSkip(alias, fixture.world());
        assertThrows(IOException.class, () -> LegacySavedDataPreflight.inspect(
                alias, fixture.world(), server.registryAccess()));
        assertTrue(Files.isDirectory(fixture.world()));
    }

    @Test
    void symbolicSourceFilesAreRejected(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path source = source(fixture, "npc_database");
        Path target = fixture.root().resolve("npc-outside-copy.dat");
        Files.move(source, target);
        createLinkOrSkip(source, target);
        assertThrows(IOException.class, () -> inspect(server, fixture));
        assertTrue(Files.isRegularFile(target));
    }

    @Test
    void populatedDestinationIsRejectedAndItsBytesRetained(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path destination = fixture.world().resolve("data").resolve("farmupcraft").resolve("player_database.dat");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "populated destination sentinel");
        assertDeniedWithoutMutation(server, fixture, "overwrite/merge is forbidden");
        assertEquals("populated destination sentinel", Files.readString(destination));
    }

    @Test
    void malformedCompressedNbtAndPayloadOnlyExportsAreNotSavedData(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path source = source(fixture, "game_zone_database");
        Files.writeString(source, "not compressed NBT");
        assertDeniedWithoutMutation(server, fixture, "Cannot read legacy SavedData");
        NbtIo.writeCompressed(payload("game_zone_database"), source);
        assertDeniedWithoutMutation(server, fixture, "Expected exactly");
    }

    @Test
    void incorrectDataVersionMissingFieldsAndMismatchedDatabaseIdAreDenied(MinecraftServer server) throws Exception {
        for (String invalid : List.of("version", "missing", "name", "extra")) {
            Fixture fixture = fixture();
            Path file = source(fixture, "npc_database");
            CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            switch (invalid) {
                case "version" -> envelope.putInt("DataVersion", 999999);
                case "missing" -> compound(envelope, "data").remove("map_entry");
                case "name" -> compound(envelope, "data").putString("database_name", "minecraft:map");
                case "extra" -> envelope.putString("unknown", "not a legacy envelope field");
                default -> throw new AssertionError(invalid);
            }
            NbtIo.writeCompressed(envelope, file);
            assertDeniedWithoutMutation(server, fixture, "Invalid legacy SavedData");
        }
    }

    @Test
    void duplicateKeysAndUnknownOrMalformedOptionalFieldsCannotSilentlyDefault(MinecraftServer server)
            throws Exception {
        for (String invalid : List.of("duplicate", "unknown", "optional", "negative")) {
            Fixture fixture = fixture();
            Path file = source(fixture, "player_database");
            CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ListTag rows = (ListTag) compound(envelope, "data").get("map_entry");
            CompoundTag row = (CompoundTag) rows.getFirst();
            CompoundTag value = compound(compound(row, "value"), "codec_value");
            switch (invalid) {
                case "duplicate" -> rows.add(row.copy());
                case "unknown" -> value.putString("unported_field", "must not be discarded");
                case "optional" -> value.putString("coins", "must not turn into zero");
                case "negative" -> value.putInt("coins", -10);
                default -> throw new AssertionError(invalid);
            }
            NbtIo.writeCompressed(envelope, file);
            assertDeniedWithoutMutation(server, fixture, "Invalid legacy SavedData");
        }
    }

    @Test
    void unresolvedBiomeItemAndTagReferencesFailWithoutAirOrEmptyFallback(MinecraftServer server) throws Exception {
        for (String invalid : List.of("biome", "item", "tag", "goods")) {
            Fixture fixture = fixture();
            Path file = invalid.equals("goods") ? source(fixture, "goods_data_database_new")
                    : fixture.copy().resolve(BiomeRulesManager.DATA_NAME + ".dat");
            CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag data = compound(envelope, "data");
            if (invalid.equals("goods")) {
                CompoundTag row = (CompoundTag) ((ListTag) data.get("map_entry")).getFirst();
                compound(compound(row, "value"), "codec_value").putString("itemId", "missing:item");
            } else {
                CompoundTag row = (CompoundTag) ((ListTag) data.get("biome_rules")).getFirst();
                if (invalid.equals("biome")) row.putString("biome", "missing:biome");
                CompoundTag instance = compound(compound(row, "rules"), "rules_instance");
                if (invalid.equals("tag")) compound(instance, "rules_data").putString("biome", "missing:tag");
                if (invalid.equals("item")) {
                    ListTag items = new ListTag();
                    items.add(StringTag.valueOf("missing:item"));
                    instance.put("random_crops", items);
                }
            }
            NbtIo.writeCompressed(envelope, file);
            assertDeniedWithoutMutation(server, fixture, "Invalid legacy SavedData");
        }
    }

    @Test
    void duplicateBiomeManagerEntriesAreNotSilentlyCollapsed(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path file = fixture.copy().resolve(BiomeRulesManager.DATA_NAME + ".dat");
        CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        ListTag biomes = (ListTag) compound(envelope, "data").get("biome_rules");
        biomes.add(biomes.getFirst().copy());
        NbtIo.writeCompressed(envelope, file);
        assertDeniedWithoutMutation(server, fixture, "Duplicate biome");
    }

    @Test
    void unrelatedFilesAreNeverScannedOrImported(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.copy().resolve("level.dat"), "not NBT and must not be opened");
        Files.writeString(fixture.copy().resolve("farmupcraft_unknown_database.dat"), "not allowlisted");
        Map<String, String> before = snapshot(fixture.root());
        assertEquals(10, inspect(server, fixture).files().size());
        assertEquals(before, snapshot(fixture.root()));
    }

    @Test
    void legacyUnwrappedVersionZeroBiomeKeysAreValidatedExplicitly(MinecraftServer server) throws Exception {
        Fixture fixture = fixture();
        Path file = source(fixture, "biome_rules_database");
        CompoundTag envelope = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        CompoundTag row = (CompoundTag) ((ListTag) compound(envelope, "data").get("map_entry")).getFirst();
        row.put("key", compound(compound(row, "key"), "versioned").copy());
        NbtIo.writeCompressed(envelope, file);
        Map<String, String> before = snapshot(fixture.root());
        assertEquals(10, inspect(server, fixture).files().size());
        assertEquals(before, snapshot(fixture.root()));
    }

    @Test
    void commandIsExplicitAndOperatorOnlyAndApplyHasNoInstallerBranch(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        LegacySavedDataCommand.register(dispatcher);
        var command = dispatcher.getRoot().getChild("farmupcraft-legacy");
        assertNotNull(command);
        assertFalse(command.canUse(Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS)));
        assertFalse(command.canUse(Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR)));
        assertTrue(command.canUse(Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)));
        assertTrue(command.canUse(Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS)));
        assertNotNull(command.getChild("inspect").getChild("copyDirectory").getCommand());
        assertNotNull(command.getChild("apply").getChild("copyDirectory").getCommand());
        assertEquals(2, command.getChildren().size());
        String path = "C:\\operator copy\\legacy data";
        String input = "farmupcraft-legacy inspect " + path;
        var parsed = dispatcher.parse(input, Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER));
        assertTrue(parsed.getExceptions().isEmpty());
        assertEquals(path, parsed.getContext().build(input).getArgument("copyDirectory", String.class));
        assertNotNull(server.getCommands().getDispatcher().getRoot().getChild("farmupcraft-legacy"));
    }

    private record Fixture(Path root, Path copy, Path world) {}

    private Fixture fixture() throws Exception {
        Path root = Path.of("build", "legacy-preflight-fixtures", UUID.randomUUID().toString()).toAbsolutePath();
        directories.add(root);
        Path copy = Files.createDirectories(root.resolve("operator-copy"));
        Path world = Files.createDirectories(root.resolve("active-world"));
        writeEnvelope(copy.resolve(BiomeRulesManager.DATA_NAME + ".dat"), TagParser.parseCompoundFully("""
                {biome_rules:[{biome:"minecraft:plains",rules:{rules_instance:%s}}],
                 item_to_biome_list:[{item:"minecraft:wheat",biomes:[{biome:"minecraft:plains"}]}]}
                """.formatted(INSTANCE)));
        Fixture fixture = new Fixture(root, copy, world);
        for (String name : LegacySavedDataPreflight.databaseNames()) writeEnvelope(source(fixture, name), payload(name));
        return fixture;
    }

    private static CompoundTag payload(String name) throws Exception {
        String key = switch (name) {
            case "biome_rules_database" -> "{version:1,versioned:{holder_key:\"minecraft:plains\"}}";
            case "chunk_data_database", "game_structure_database" -> "{long_val:123L}";
            case "goods_data_database_new" -> "{string_value:\"minecraft:wheat\"}";
            case "world_data_database" -> "{int_value:0}";
            default -> UUID_KEY;
        };
        String value = switch (name) {
            case "biome_rules_database" -> INSTANCE;
            case "player_database" -> "{id:1,player_uuid:\"" + UUID_TEXT + "\",coins:27}";
            case "chunk_data_database" -> "{type:\"farm\",player_id:1,village_id:\"" + UUID_TEXT + "\"}";
            case "village_database" -> "{uuid:\"" + UUID_TEXT
                    + "\",name:\"Legacy\",position:{chunk_long:8589934593L},level:1,claimed_chunks:[],bought:1b}";
            case "goods_data_database_new" -> """
                    {itemId:"minecraft:wheat",cost:10,amountSold:3,rarity:"common",active:1b,
                     marketType:"farm",dateAddedToMarket:"2026-01-01"}
                    """;
            case "world_data_database" -> "{upkeep_tick:12}";
            case "npc_database" -> "{id:\"" + UUID_TEXT + "\",name:\"Legacy NPC\"}";
            case "game_structure_database" -> "{id:123L,name:\"Legacy\",center_pos:{x:1,y:64,z:2},type:\"ruin\"}";
            case "game_zone_database" -> """
                    {uuid:[I;19088743,-1985229329,-19088744,1985229328],name:"Legacy",
                     sections:[L;123L],type:"ISLAND",center:[I;1,64,2]}
                    """;
            default -> throw new AssertionError(name);
        };
        return TagParser.parseCompoundFully("{database_name:\"farmupcraft:" + name
                + "\",map_entry:[{key:" + key + ",value:{codec_value:" + value + "}}]}");
    }

    private static Path source(Fixture fixture, String name) {
        return fixture.copy().resolve("farmupcraft_" + name + ".dat");
    }

    private static void writeEnvelope(Path file, CompoundTag payload) throws IOException {
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("DataVersion", 3465);
        envelope.put("data", payload);
        NbtIo.writeCompressed(envelope, file);
    }

    private static CompoundTag compound(CompoundTag parent, String key) {
        return (CompoundTag) parent.get(key);
    }

    private static LegacySavedDataPreflight.Report inspect(MinecraftServer server, Fixture fixture) throws IOException {
        return LegacySavedDataPreflight.inspect(fixture.copy(), fixture.world(), server.registryAccess());
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.sorted().toList()) {
                String key = root.relativize(file).toString();
                result.put(key, Files.isDirectory(file) ? "<directory>"
                        : Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private static void assertDeniedWithoutMutation(MinecraftServer server, Fixture fixture, String message)
            throws IOException {
        Map<String, String> before = snapshot(fixture.root());
        IOException exception = assertThrows(IOException.class, () -> inspect(server, fixture));
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
        assertEquals(before, snapshot(fixture.root()));
    }

    private static void createLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic links unavailable: " + exception.getMessage());
        }
    }
}
