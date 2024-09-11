package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.util.DataBaseFileUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataBaseCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final SuggestionProvider<CommandSourceStack> BACKUP_SUGGESTION_PROVIDER_NBT = createSuggestionProvider(".nbt");
    public static final SuggestionProvider<CommandSourceStack> IMPORT_SUGGESTION_PROVIDER_JSON = createSuggestionProvider(".json");
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FILE_FORMATS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(FileFormat.values())
                        .map(FileFormat::name)
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()), builder
        );
    };

    private static SuggestionProvider<CommandSourceStack> createSuggestionProvider(String extension) {
        return (ctx, builder) -> {
            ResourceLocation databaseName = ResourceLocationArgument.getId(ctx, "databaseName");
            Path backupDir = DataBaseFileUtil.getBackupDirectory("")
                    .resolve(databaseName.getNamespace())
                    .resolve(databaseName.getPath());

            try {
                List<String> backups = Files.list(backupDir)
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                        .filter(path -> path.getFileName().toString().endsWith(extension))
                        .map(path -> path.getFileName().toString().replace(extension, ""))
                        .collect(Collectors.toList());

                return SharedSuggestionProvider.suggest(backups, builder);
            } catch (IOException e) {
                // Log the exception and return an empty future
                LOGGER.error("Error retrieving backups for {}: {}", databaseName, e.getMessage(), e);
                return builder.buildFuture();
            }
        };
    }


    public static final SuggestionProvider<CommandSourceStack> SUGGEST_TYPE = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(DataBaseManager.getDataBasesList(), builder);
    };


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("database")
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Define the "clear" sub-command
        //LiteralArgumentBuilder<CommandSourceStack> clearBuilder = Commands.literal("clear")
        //        .then(Commands.argument("databaseName", StringArgumentType.string()).suggests(SUGGEST_TYPE)
        //                .executes(context -> clearDatabase(context, StringArgumentType.getString(context, "databaseName"))))
        //        .requires(s -> s.hasPermission(2));  // Adjust permission as needed
//
        //// Define the "add" sub-command
        //LiteralArgumentBuilder<CommandSourceStack> addBuilder = Commands.literal("add")
        //        .then(Commands.argument("databaseName", StringArgumentType.string()).suggests(SUGGEST_TYPE)
        //                .then(Commands.argument("key", StringArgumentType.string())
        //                        .then(Commands.argument("value", StringArgumentType.string())
        //                                .executes(context -> addData(context,
        //                                        StringArgumentType.getString(context, "databaseName"),
        //                                        StringArgumentType.getString(context, "key"),
        //                                        StringArgumentType.getString(context, "value")))))
        //                .requires(s -> s.hasPermission(2)));  // Adjust permission as needed
//
        //// Define the "query" sub-command
        //LiteralArgumentBuilder<CommandSourceStack> queryBuilder = Commands.literal("query")
        //        .then(Commands.argument("databaseName", StringArgumentType.string()).suggests(SUGGEST_TYPE)
        //                .then(Commands.argument("key", StringArgumentType.string())
        //                        .executes(context -> queryData(context,
        //                                StringArgumentType.getString(context, "databaseName"),
        //                                StringArgumentType.getString(context, "key")))))
        //        .requires(s -> s.hasPermission(2));  // Adjust permission as needed
//
        //// Define the "status" sub-command
        //LiteralArgumentBuilder<CommandSourceStack> statusBuilder = Commands.literal("status")
        //        .then(Commands.argument("databaseName", StringArgumentType.string()).suggests(SUGGEST_TYPE)
        //                .executes(context -> showStatus(context, StringArgumentType.getString(context, "databaseName"))))
        //        .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Define the "test" sub-command
        LiteralArgumentBuilder<CommandSourceStack> testBuilder = Commands.literal("test")
                .then(Commands.argument("databaseName", ResourceLocationArgument.id()).suggests(SUGGEST_TYPE)
                        .executes(context -> {
                            try {
                                return runTests(context, ResourceLocationArgument.getId(context, "databaseName"));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        LiteralArgumentBuilder<CommandSourceStack> clearBuilder = Commands.literal("clear")
                .then(Commands.argument("databaseName", ResourceLocationArgument.id()).suggests(SUGGEST_TYPE)
                        .executes(context -> {
                            try {
                                return clearDataBase(context, ResourceLocationArgument.getId(context, "databaseName"));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        LiteralArgumentBuilder<CommandSourceStack> loadBuilder = Commands.literal("load")
                .then(Commands.argument("databaseName", ResourceLocationArgument.id()).suggests(SUGGEST_TYPE)
                        .executes(context -> {
                            try {
                                return loadDataBase(context, ResourceLocationArgument.getId(context, "databaseName"));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        LiteralArgumentBuilder<CommandSourceStack> exportBuilder = Commands.literal("export")
                .then(Commands.argument("databaseName", ResourceLocationArgument.id()).suggests(SUGGEST_TYPE)
                        .then(Commands.argument("format", StringArgumentType.word()).suggests(SUGGEST_FILE_FORMATS)
                                .executes(context -> {
                                    try {
                                        ResourceLocation databaseName = ResourceLocationArgument.getId(context, "databaseName");
                                        String formatString = StringArgumentType.getString(context, "format");
                                        FileFormat format = FileFormat.fromExtension("." + formatString);
                                        return exportDataBase(context, databaseName, format);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    } catch (IllegalArgumentException e) {
                                        context.getSource().sendFailure(Component.literal("Invalid file format: " + e.getMessage()));
                                        return 1; // or appropriate error code
                                    }
                                }))
                        .requires(s -> s.hasPermission(2)));  // Adjust permission as needed
        // Adjust permission as needed

        // Define the "backups" sub-command
        LiteralArgumentBuilder<CommandSourceStack> restoreBuilder = Commands.literal("restore")
                .then(Commands.argument("databaseName", ResourceLocationArgument.id()).suggests(SUGGEST_TYPE)
                        .then(Commands.argument("backupName", StringArgumentType.string()).suggests(BACKUP_SUGGESTION_PROVIDER_NBT)
                                .executes(context -> restoreBackup(context,
                                        ResourceLocationArgument.getId(context, "databaseName"),
                                        StringArgumentType.getString(context, "backupName")))))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Add sub-commands to the "database" command
        builder.then(restoreBuilder);


        // Add sub-commands to the "database" command
        //builder.then(clearBuilder);
        //builder.then(addBuilder);
        //builder.then(queryBuilder);
        //builder.then(statusBuilder);
        builder.then(testBuilder);
        builder.then(loadBuilder);
        builder.then(clearBuilder);
        builder.then(exportBuilder);
        // Register the main "database" command with the dispatcher
        dispatcher.register(builder);
    }

    private static int restoreBackup(CommandContext<CommandSourceStack> context, ResourceLocation databaseName, String backupName) {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        Path backupDir = DataBaseFileUtil.getBackupDirectory("").resolve(databaseName.getNamespace()).resolve(databaseName.getPath());
        Path backupFile = backupDir.resolve(backupName + FileFormat.NBT.getExtension());

        if (!Files.exists(backupFile)) {
            source.sendFailure(Component.literal("Backup " + backupName + " not found for database " + databaseName));
            return 0;
        }

        DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName); // Assuming string key/value for simplicity
        DataBase<?, ?> database = dataBaseAccess.get(serverLevel);
        // Attempt to restore the database from the backup file
        try {

            // Path to the expected checksum file
            boolean success = database.importWithCheckSumValidation(backupFile, FileFormat.NBT, serverLevel);
            if (success) {
                source.sendSuccess(() -> Component.literal("Backup " + backupName + " successfully restored for database " + databaseName), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("Failed to restore backup " + backupName + " for database " + databaseName + " it might be corrupted or changed!"));
                return 0;
            }
        } catch (Exception e) {
            // Handle any exceptions that occur during the import process
            source.sendFailure(Component.literal("An error occurred while restoring backup " + backupName + " for database " + databaseName + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int exportDataBase(CommandContext<CommandSourceStack> context, ResourceLocation databaseName, FileFormat format) throws InterruptedException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        // Obtain the DataBase instance
        DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName);
        DataBase<?, ?> database = dataBaseAccess.get(serverLevel);

        if (database == null) {
            source.sendFailure(Component.literal("Database " + databaseName + " could not be found"));
            return 1;
        }

        String baseName = databaseName.getPath();
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uniqueSuffix = UUID.randomUUID().toString();
        String uniqueFileName = String.format("%s_%s_%s%s", baseName, timestamp, uniqueSuffix, format.getExtension());

        Path exportPath = DataBaseFileUtil.createWorldExport(databaseName.getNamespace()).resolve(uniqueFileName);

        database.exportDataBase(exportPath, format);
        source.sendSuccess(() -> Component.literal("Database " + databaseName + " exported successfully to " + exportPath.toAbsolutePath()), true);

        return 0;
    }



    private static int loadDataBase(CommandContext<CommandSourceStack> context, ResourceLocation databaseName) throws InterruptedException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        // Obtain the DataBase instance
        DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName); // Assuming string key/value for simplicity
        DataBase<?, ?> database = dataBaseAccess.get(serverLevel);


        if (database == null) {
            source.sendFailure(Component.literal("Database " + databaseName + " not found."));
            return 0;
        } else {
            source.sendSuccess(() -> Component.literal("Database " + databaseName + " successfully loaded!"), true);
        }

        // Run your custom test logic
        return 1;
    }

    private static int clearDataBase(CommandContext<CommandSourceStack> context, ResourceLocation databaseName) throws InterruptedException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        // Obtain the DataBase instance
        DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName); // Assuming string key/value for simplicity
        DataBase<?, ?> database = dataBaseAccess.get(serverLevel);
        database.clearDataBase();


        if (database == null) {
            source.sendFailure(Component.literal("Database " + databaseName + " not found."));
            return 0;
        } else {
            source.sendSuccess(() -> Component.literal("Database " + databaseName + " successfully cleared!"), true);
        }

        // Run your custom test logic
        return 1;
    }

    private static int runTests(CommandContext<CommandSourceStack> context, ResourceLocation databaseName) throws InterruptedException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        // Obtain the DataBase instance
        DataBaseAccess<String, String> dataBaseAccess = DataBaseManager.getDataBaseAccess(databaseName); // Assuming string key/value for simplicity
        DataBase<String, String> database = dataBaseAccess.get(serverLevel);
        DataBaseTest dataBaseTest = new DataBaseTest(database);
        dataBaseTest.runTests();


        if (database == null) {
            source.sendFailure(Component.literal("Database " + databaseName + " not found."));
            return 0;
        }

        // Run your custom test logic

        return 1;
    }


    //private static DataBase<String, String> getDataBase(ServerLevel level, String databaseName) {
    //    // Implement the logic to get the DataBase instance by its name
    //    // This is a placeholder implementation
    //    return DataBase.get(level, databaseName, new StringDataSerializer(), new StringDataSerializer(), false, 0);
    //}
//
    //// Simple DataSerializer for strings

}
