package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.IslandScanManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ScanCommand {
    private ScanCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scan")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("islands")
                        .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> islands(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "chunkradius")))))
                .then(Commands.literal("point")
                        .executes(context -> point(context.getSource(), null, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> point(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "z"))))))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource()))));
    }

    private static int islands(CommandSourceStack source, int chunkRadius) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        int x = player.blockPosition().getX(), z = player.blockPosition().getZ();
        try {
            IslandScanManager.runScanAsync(source.getLevel(), x, z, chunkRadius * 16,
                    message -> feedback(source, message), player);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Starting island/lake scan (radius=" + chunkRadius
                + " chunks) around " + x + ", " + z + "..."), false);
        return 1;
    }

    private static int point(CommandSourceStack source, Integer x, Integer z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        int sx = x == null ? player.blockPosition().getX() : x;
        int sz = z == null ? player.blockPosition().getZ() : z;
        try {
            IslandScanManager.scanPointAsync(source.getLevel(), sx, sz, message -> feedback(source, message), player);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Scan] Checking point (" + sx + "," + sz + ")..."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer current ? current : null;
        IslandScanManager.clearCacheAsync(source.getLevel(), player, message -> feedback(source, message));
        return 1;
    }

    private static void feedback(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.literal("[Scan] ").append(message), false);
    }
}
