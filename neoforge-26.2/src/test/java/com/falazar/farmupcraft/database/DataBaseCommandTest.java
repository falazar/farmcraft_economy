package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.HolderDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class DataBaseCommandTest {
    private final List<Path> directories = new ArrayList<>();

    @AfterEach
    void cleanFixtures() throws IOException {
        for (Path directory : directories) {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private Path directory() throws IOException {
        Path directory = Path.of("build", "test-fixtures", "database-command-" + UUID.randomUUID());
        Files.createDirectories(directory);
        directories.add(directory);
        return directory;
    }

    private static Holder<Biome> plains(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME)
                .get(Identifier.parse("minecraft:plains")).orElseThrow();
    }

    private static DataBase<String, Holder<Biome>> database(MinecraftServer server) {
        return new DataBase<>("command_registry_test", new StringDataSerializer(),
                new HolderDataSerializer<>(Registries.BIOME), false, 0, false, server.registryAccess());
    }

    private static void checksum(Path file) throws Exception {
        Files.writeString(Path.of(file + ".sha256"),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
    }

    @Test
    void completeCommandTreePermissionsSuggestionsAndUnknownDatabases(MinecraftServer server) throws Exception {
        assertNotNull(server.registryAccess());
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        DataBaseCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("database");
        assertEquals(Set.of("test", "clear", "load", "export", "restore", "query"),
                root.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet()));
        var guest = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var moderator = Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR);
        var feedback = new Feedback();
        var operator = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER).withSource(feedback);
        assertFalse(root.canUse(guest));
        assertFalse(root.canUse(moderator));
        assertTrue(root.canUse(operator));
        for (String command : List.of("database test missing:db", "database load missing:db",
                "database clear missing:db", "database export missing:db json", "database export missing:db nbt",
                "database restore missing:db backup_name", "database query missing:db",
                "database query missing:db \"some filter\"", "database query missing:db \"some filter\" 2")) {
            var parsed = dispatcher.parse(command, operator);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
            assertEquals(0, dispatcher.execute(command, operator), command);
            assertEquals("Database not found: missing:db", feedback.messages.getLast());
        }
        assertEquals(0, dispatcher.execute("database query missing:db filter NaN", operator));
        assertEquals("Page must be a number.", feedback.messages.getLast());
        var context = new CommandContext<CommandSourceStack>(operator, "", Map.of(), null,
                dispatcher.getRoot(), List.of(), com.mojang.brigadier.context.StringRange.at(0), null, null, false);
        assertEquals(List.of("json", "nbt"), DataBaseCommand.SUGGEST_FILE_FORMATS.getSuggestions(
                context, new SuggestionsBuilder("", 0)).get().getList().stream().map(s -> s.getText()).toList());
        assertEquals(0, DataBaseCommand.SUGGEST_TYPE.getSuggestions(
                context, new SuggestionsBuilder("unregistered_namespace:", 0)).get().getList().size());
    }

    @Test
    void registryAwareDiagnosticTestsPreserveLiveDatabase(MinecraftServer server) {
        try (var database = database(server)) {
            database.putData("home", plains(server));
            var before = database.save(new net.minecraft.nbt.CompoundTag());
            DataBaseCommand.runDiagnostics(database, new StringDataSerializer(),
                    new HolderDataSerializer<>(Registries.BIOME), server.registryAccess());
            assertEquals(before, database.save(new net.minecraft.nbt.CompoundTag()));
            assertEquals(plains(server), database.getData("home"));
        }
    }

    @Test
    void exportBothFormatsAndReportIoErrorsInsteadOfFalseSuccess(MinecraftServer server) throws Exception {
        Path directory = directory();
        var feedback = new Feedback();
        var source = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER).withSource(feedback);
        try (var database = database(server)) {
            database.putData("home", plains(server));
            for (FileFormat format : FileFormat.values()) {
                Path file = directory.resolve("export" + format.getExtension());
                assertEquals(1, DataBaseCommand.exportDataBase(source, database, file, format));
                assertTrue(Files.size(file) > 0);
                assertTrue(feedback.messages.getLast().contains("exported successfully"));
                try (var restored = database(server)) {
                    assertTrue(restored.importDataBase(file, format, server.registryAccess()));
                    assertEquals(Map.of("home", plains(server)), restored.getDataMap());
                }
            }
            Path blocked = directory.resolve("not-a-directory");
            Files.writeString(blocked, "fixture");
            assertEquals(0, DataBaseCommand.exportDataBase(source, database,
                    blocked.resolve("export.nbt"), FileFormat.NBT));
            assertTrue(feedback.messages.getLast().startsWith("Database export failed:"));
        }
    }

    @Test
    void restoreRequiresChecksumValidDataAndRegistryProvider(MinecraftServer server) throws Exception {
        Path directory = directory();
        Path file = directory.resolve("backup.nbt");
        var feedback = new Feedback();
        var source = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER).withSource(feedback);
        try (var database = database(server)) {
            database.putData("home", plains(server));
            assertEquals(0, DataBaseCommand.restoreBackup(source, database, file, server.registryAccess()));
            assertTrue(feedback.messages.getLast().startsWith("Backup not found:"));
            database.exportDataBase(file, FileFormat.NBT);
            assertEquals(0, DataBaseCommand.restoreBackup(source, database, file, server.registryAccess()));
            assertTrue(feedback.messages.getLast().startsWith("Backup checksum not found:"));
            Files.writeString(Path.of(file + ".sha256"), "invalid hash");
            assertEquals(0, DataBaseCommand.restoreBackup(source, database, file, server.registryAccess()));
            assertTrue(feedback.messages.getLast().contains("checksum mismatch"));
            assertEquals(Map.of("home", plains(server)), database.getDataMap());
            checksum(file);
            database.clearDataBase();
            assertEquals(1, DataBaseCommand.restoreBackup(source, database, file, server.registryAccess()));
            assertEquals(Map.of("home", plains(server)), database.getDataMap());
            assertTrue(feedback.messages.getLast().contains("successfully restored"));
            Files.writeString(file, "not compressed NBT");
            checksum(file);
            assertEquals(0, DataBaseCommand.restoreBackup(source, database, file, server.registryAccess()));
            assertTrue(feedback.messages.getLast().contains("invalid database data"));
            assertEquals(Map.of("home", plains(server)), database.getDataMap());
        }
    }

    @Test
    void backupNamesRejectTraversalAndQueryHandlesNegativeAndOverflowPages(MinecraftServer server) throws Exception {
        Path directory = directory();
        for (String name : List.of("", " ", ".", "..", "../other", "..\\other", "C:\\outside", "/absolute")) {
            assertThrows(IllegalArgumentException.class, () -> DataBaseCommand.backupFile(directory, name), name);
        }
        assertEquals(directory.resolve("backup_20261028.nbt"),
                DataBaseCommand.backupFile(directory, "backup_20261028"));
        var feedback = new Feedback();
        var source = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER).withSource(feedback);
        try (var database = new DataBase<>("query_test", new StringDataSerializer(), new StringDataSerializer(),
                false, 0, false, server.registryAccess())) {
            for (int i = 0; i < 12; i++) database.putData("key%02d".formatted(i), "value" + i);
            assertEquals(1, DataBaseCommand.queryData(source, database, null, 0));
            assertEquals("Showing entries 1–10 of 12", feedback.messages.getFirst());
            assertEquals("[key00] → value0", feedback.messages.get(1));
            feedback.messages.clear();
            assertEquals(1, DataBaseCommand.queryData(source, database, null, 1));
            assertEquals(List.of("Showing entries 11–12 of 12", "[key10] → value10", "[key11] → value11"),
                    feedback.messages);
            assertEquals(1, DataBaseCommand.queryData(source, database, "key00", 0));
            assertEquals("[key00] → value0", feedback.messages.getLast());
            assertEquals(0, DataBaseCommand.queryData(source, database, null, -1));
            assertEquals("Page must be non-negative.", feedback.messages.getLast());
            assertEquals(0, DataBaseCommand.queryData(source, database, null, Integer.MAX_VALUE));
            assertEquals("No results on page 2147483647", feedback.messages.getLast());
        }
    }

    private static class Feedback implements CommandSource {
        final List<String> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component component) { messages.add(component.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
