package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.migration.LegacySavedDataPreflight;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Explicit, operator-only preflight; intentionally has no installation path. */
public final class LegacySavedDataCommand {
    private LegacySavedDataCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("farmupcraft-legacy")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Read-only Forge 1.20.1 SavedData preflight: /farmupcraft-legacy inspect \"COPY directory\". "
                                    + "Supply a COPY of the old overworld data directory, outside the active world. "
                                    + "Windows copy filenames: farmupcraft_NAME.dat (instead of farmupcraft:NAME.dat). "
                                    + "All nine databases and farmupcraft_biome_rules_manager.dat are required. "
                                    + LegacySavedDataPreflight.BLOCKED_REASON), false);
                    return 1;
                })
                .then(Commands.literal("inspect")
                        .then(Commands.argument("copyDirectory", StringArgumentType.greedyString())
                                .executes(context -> inspect(context, false))))
                .then(Commands.literal("apply")
                        .then(Commands.argument("copyDirectory", StringArgumentType.greedyString())
                                .executes(context -> inspect(context, true)))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context, boolean applyRequested) {
        var source = context.getSource();
        try {
            // A raw trailing path avoids Brigadier's backslash escaping for Windows paths.
            String directory = StringArgumentType.getString(context, "copyDirectory").strip();
            if (directory.startsWith("\"") && directory.endsWith("\"") && directory.length() >= 2) {
                directory = directory.substring(1, directory.length() - 1);
            }
            if (directory.isBlank()) throw new IOException("An explicit source COPY directory is required.");
            var report = LegacySavedDataPreflight.inspect(
                    Path.of(directory),
                    source.getServer().getWorldPath(LevelResource.ROOT), source.getServer().registryAccess());
            for (var file : report.files()) {
                source.sendSuccess(() -> Component.literal(file.id() + ": validated " + file.records()
                        + " records; COPY " + file.sourceFile() + "; proposed target " + file.destinationFile()), false);
            }
            source.sendSuccess(() -> Component.literal("Read-only inspection completed for all ten allowlisted files. "
                    + "Source and destination were not modified. This is not an import success."), false);
            source.sendFailure(Component.literal(report.blockedReason()));
            return applyRequested ? 0 : 1;
        } catch (IOException | InvalidPathException exception) {
            source.sendFailure(Component.literal("Legacy preflight failed; no import performed: " + exception.getMessage()));
            return 0;
        }
    }
}
