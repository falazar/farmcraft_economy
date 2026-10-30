package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DataBaseCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final SuggestionProvider<CommandSourceStack> BACKUP_SUGGESTION_PROVIDER_NBT =
            createSuggestionProvider(".nbt");
    public static final SuggestionProvider<CommandSourceStack> IMPORT_SUGGESTION_PROVIDER_JSON =
            createSuggestionProvider(".json");
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FILE_FORMATS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(FileFormat.values())
                    .map(format -> format.name().toLowerCase(Locale.ROOT)), builder);
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_TYPE = (ctx, builder) ->
            SharedSuggestionProvider.suggest(DataBaseManager.getDataBasesList(), builder);

    private static SuggestionProvider<CommandSourceStack> createSuggestionProvider(String extension) {
        return (ctx, builder) -> {
            Identifier name = IdentifierArgument.getId(ctx, "databaseName");
            Path directory = DataBaseFileUtil.getBackupDirectory(ctx.getSource().getServer(), name);
            if (!Files.isDirectory(directory)) return builder.buildFuture();
            try (var files = Files.list(directory)) {
                List<String> names = files.filter(Files::isRegularFile)
                        .map(file -> file.getFileName().toString())
                        .filter(file -> file.endsWith(extension))
                        .map(file -> file.substring(0, file.length() - extension.length()))
                        .sorted().toList();
                return SharedSuggestionProvider.suggest(names, builder);
            } catch (IOException failure) {
                LOGGER.error("Cannot list database backups in {}", directory, failure);
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
        };
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("database")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        for (String action : List.of("test", "clear", "load")) {
            root.then(Commands.literal(action)
                    .then(Commands.argument("databaseName", IdentifierArgument.id()).suggests(SUGGEST_TYPE)
                            .executes(ctx -> accessDataBase(ctx, action))));
        }
        root.then(Commands.literal("export")
                .then(Commands.argument("databaseName", IdentifierArgument.id()).suggests(SUGGEST_TYPE)
                        .then(Commands.argument("format", StringArgumentType.word()).suggests(SUGGEST_FILE_FORMATS)
                                .executes(DataBaseCommand::exportDataBase))));
        root.then(Commands.literal("restore")
                .then(Commands.argument("databaseName", IdentifierArgument.id()).suggests(SUGGEST_TYPE)
                        .then(Commands.argument("backupName", StringArgumentType.string())
                                .suggests(BACKUP_SUGGESTION_PROVIDER_NBT)
                                .executes(DataBaseCommand::restoreBackup))));
        root.then(Commands.literal("query")
                .then(Commands.argument("databaseName", IdentifierArgument.id()).suggests(SUGGEST_TYPE)
                        .executes(ctx -> queryData(ctx, null, 0))
                        .then(Commands.argument("filter", StringArgumentType.string())
                                .executes(ctx -> queryData(ctx, StringArgumentType.getString(ctx, "filter"), 0))
                                .then(Commands.argument("page", StringArgumentType.word())
                                        .executes(ctx -> {
                                            int page;
                                            try {
                                                page = Integer.parseInt(StringArgumentType.getString(ctx, "page"));
                                            } catch (NumberFormatException failure) {
                                                ctx.getSource().sendFailure(Component.literal("Page must be a number."));
                                                return 0;
                                            }
                                            return queryData(ctx, StringArgumentType.getString(ctx, "filter"), page);
                                        })))));
        dispatcher.register(root);
    }

    private static Identifier name(CommandContext<CommandSourceStack> context) {
        return IdentifierArgument.getId(context, "databaseName");
    }

    private static DataBase<?, ?> database(CommandContext<CommandSourceStack> context) {
        DataBaseAccess<?, ?> access = DataBaseManager.getDataBaseAccess(name(context));
        if (access == null) {
            context.getSource().sendFailure(Component.literal("Database not found: " + name(context)));
            return null;
        }
        return access.get(context.getSource().getLevel());
    }

    private static int accessDataBase(CommandContext<CommandSourceStack> context, String action) {
        DataBase<?, ?> database = database(context);
        if (database == null) return 0;
        if ("test".equals(action)) {
            return runTests(context.getSource(), DataBaseManager.getDataBaseAccess(name(context)));
        }
        if ("clear".equals(action)) database.clearDataBase();
        context.getSource().sendSuccess(() -> Component.literal("Database " + name(context)
                + ("clear".equals(action) ? " successfully cleared!" : " successfully loaded!")), true);
        return 1;
    }

    private static <M, V> int runTests(CommandSourceStack source, DataBaseAccess<M, V> access) {
        DataBase<M, V> database = access.get(source.getLevel());
        try {
            runDiagnostics(database, access.getKeySerializer(), access.getValueSerializer(),
                    source.getLevel().registryAccess());
            source.sendSuccess(() -> Component.literal("Database " + access.getDatabaseName()
                    + " tests passed (snapshot, serialization, transactions, null-key validation)."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return failure(source, "Database tests failed", failure);
        }
    }

    /**
     * Exercise the registered serializers with real typed data, never insert String test keys
     * into live heterogeneous databases or sleep on the server thread.
     */
    static <M, V> void runDiagnostics(DataBase<M, V> database, DataSerializer<M> keys,
                                     DataSerializer<V> values, HolderLookup.Provider registries) {
        CompoundTag snapshot = database.save(new CompoundTag());
        try (DataBase<M, V> copy = new DataBase<>(database.getDatabaseName(), keys, values,
                false, 0, false, registries)) {
            copy.load(snapshot, registries);
            if (copy.getSize() != database.getSize()) {
                throw new IllegalStateException("Serialized database size does not match.");
            }
            if (!snapshot.equals(copy.save(new CompoundTag()))) {
                throw new IllegalStateException("Database serializers do not round-trip.");
            }
            copy.beginTransaction();
            copy.clearDataBase();
            copy.rollbackTransaction();
            if (!snapshot.equals(copy.save(new CompoundTag()))) {
                throw new IllegalStateException("Transaction rollback did not restore data.");
            }
            try {
                copy.putData(null, null);
            } catch (NullPointerException | IllegalArgumentException expected) {
                return;
            }
            throw new IllegalStateException("Database accepted a null key.");
        }
    }

    private static int exportDataBase(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DataBase<?, ?> database = database(context);
        if (database == null) return 0;
        FileFormat format;
        try {
            format = FileFormat.fromExtension("." + StringArgumentType.getString(context, "format"));
        } catch (IllegalArgumentException failure) {
            source.sendFailure(Component.literal("Invalid file format: " + failure.getMessage()));
            return 0;
        }
        try {
            Identifier name = name(context);
            String base = name.getPath().replace('/', '_');
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path path = DataBaseFileUtil.createWorldExport(source.getServer(), name.getNamespace())
                    .resolve(base + "_" + timestamp + "_" + UUID.randomUUID() + format.getExtension());
            return exportDataBase(source, database, path, format);
        } catch (UncheckedIOException | IllegalArgumentException failure) {
            return failure(source, "Database export failed", failure);
        }
    }

    static int exportDataBase(CommandSourceStack source, DataBase<?, ?> database, Path path, FileFormat format) {
        try {
            database.exportDataBase(path, format);
            source.sendSuccess(() -> Component.literal("Database " + database.getDatabaseName()
                    + " exported successfully to " + path.toAbsolutePath()), true);
            return 1;
        } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException failure) {
            return failure(source, "Database export failed", failure);
        }
    }

    private static int restoreBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DataBase<?, ?> database = database(context);
        if (database == null) return 0;
        String backup = StringArgumentType.getString(context, "backupName");
        try {
            Path directory = DataBaseFileUtil.getBackupDirectory(source.getServer(), name(context));
            Path file = backupFile(directory, backup);
            return restoreBackup(source, database, file, source.getLevel().registryAccess());
        } catch (IllegalArgumentException failure) {
            return failure(source, "Invalid backup name", failure);
        }
    }

    static Path backupFile(Path directory, String backup) {
        // Backups are file names, not arbitrary paths; quoted Brigadier arguments allow separators.
        if (backup.isBlank() || backup.equals(".") || backup.equals("..")
                || backup.indexOf('/') >= 0 || backup.indexOf('\\') >= 0 || backup.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Expected a backup file name without path separators.");
        }
        return directory.resolve(backup + FileFormat.NBT.getExtension());
    }

    static int restoreBackup(CommandSourceStack source, DataBase<?, ?> database, Path file,
                             HolderLookup.Provider registries) {
        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("Backup not found: " + file.getFileName()));
            return 0;
        }
        Path checksum = Path.of(file + ".sha256");
        if (!Files.isRegularFile(checksum)) {
            source.sendFailure(Component.literal("Backup checksum not found: " + checksum.getFileName()));
            return 0;
        }
        try {
            if (!checksumMatches(file, checksum)) {
                source.sendFailure(Component.literal("Failed to restore backup: checksum mismatch (corrupted or changed)."));
                return 0;
            }
            if (!database.importDataBase(file, FileFormat.NBT, registries)) {
                source.sendFailure(Component.literal("Failed to restore backup: invalid database data. See server log."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Backup " + file.getFileName()
                    + " successfully restored for database " + database.getDatabaseName()), true);
            return 1;
        } catch (IOException failure) {
            return failure(source, "Database restore failed", failure);
        }
    }

    private static boolean checksumMatches(Path file, Path checksum) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM is missing SHA-256 support.", impossible);
        }
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return Files.readString(checksum).equals(HexFormat.of().formatHex(digest.digest()));
    }

    private static int queryData(CommandContext<CommandSourceStack> context, String filter, int page) {
        DataBase<?, ?> database = database(context);
        if (database == null) return 0;
        return queryData(context.getSource(), database, filter, page);
    }

    static int queryData(CommandSourceStack source, DataBase<?, ?> database, String filter, int page) {
        if (page < 0) {
            source.sendFailure(Component.literal("Page must be non-negative."));
            return 0;
        }
        List<? extends Map.Entry<?, ?>> entries = database.getDataMap().entrySet().stream()
                .filter(entry -> filter == null || String.valueOf(entry.getKey()).contains(filter)
                        || String.valueOf(entry.getValue()).contains(filter))
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey()))).toList();
        long start = (long) page * 10;
        int end = (int) Math.min(start + 10, entries.size());
        if (start >= entries.size()) {
            source.sendFailure(Component.literal("No results on page " + page));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Showing entries " + (start + 1) + "–" + end + " of " + entries.size()), false);
        for (int i = (int) start; i < end; i++) {
            var entry = entries.get(i);
            source.sendSuccess(() -> Component.literal("[" + entry.getKey() + "] → " + entry.getValue()), false);
        }
        return 1;
    }

    private static int failure(CommandSourceStack source, String operation, Throwable failure) {
        LOGGER.error(operation, failure);
        source.sendFailure(Component.literal(operation + ": " + failure.getMessage()));
        return 0;
    }
}
