package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.IslandScanManager;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class ScanCommand {
    public static final CustomLogger LOGGER = new CustomLogger(ScanCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("scan")
                .requires(s -> s.hasPermission(2));

        // /scan islands <radius>
        builder.then(Commands.literal("islands")
                .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1, 128))
                        .executes(c -> scanIslands(c.getSource(),
                                IntegerArgumentType.getInteger(c, "chunkradius")))));

        // /scan point [x z]
        builder.then(Commands.literal("point")
                .executes(c -> scanPoint(c.getSource(), Integer.MIN_VALUE, Integer.MIN_VALUE))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> scanPoint(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "z"))))));

        // /scan clear
        builder.then(Commands.literal("clear")
                .executes(c -> clearCache(c.getSource())));

        pDispatcher.register(builder);
    }

    private static int scanPoint(CommandSourceStack source, int x, int z) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        if (x == Integer.MIN_VALUE) {
            x = (int) serverPlayer.getX();
            z = (int) serverPlayer.getZ();
        }
        final int fx = x, fz = z;
        ServerLevel level = source.getLevel();
        source.sendSuccess(() -> Component.literal("[Scan] Checking point (" + fx + "," + fz + ")..."), false);
        IslandScanManager.scanPointAsync(level, fx, fz,
                comp -> source.sendSuccess(() -> Component.literal("[Scan] ").append(comp), false), serverPlayer);
        return 1;
    }

    private static int clearCache(CommandSourceStack source) {
        Entity entity = source.getEntity();
        ServerPlayer player = entity instanceof ServerPlayer sp ? sp : null;
        String msg = player != null
                ? IslandScanManager.clearCache(source.getLevel(), player)
                : IslandScanManager.clearCache();
        source.sendSuccess(() -> Component.literal("[Scan] " + msg), false);
        return 1;
    }

    private static int scanIslands(CommandSourceStack source, int radius) {
        Entity entity = source.getEntity();
        if (!(entity instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!source.getServer().getPlayerList().isOp(player.getGameProfile())
                && !player.isCreative()) {
            source.sendFailure(Component.literal("This command requires creative mode or admin (op) status."));
            return 0;
        }
        int cx = (int) player.getX();
        int cz = (int) player.getZ();
        ServerLevel serverLevel = source.getLevel();
        int radiusBlocks = radius * 16; // radius argument is in chunks

        source.sendSuccess(() -> Component.literal(
                "Starting island/lake scan (radius=" + radius + " chunks) around " + cx + ", " + cz + "..."), false);

        IslandScanManager.runScanAsync(serverLevel, cx, cz, radiusBlocks,
                comp -> source.sendSuccess(() -> Component.literal("[Scan] ").append(comp), false), serverPlayer);

        return 1;
    }
}
