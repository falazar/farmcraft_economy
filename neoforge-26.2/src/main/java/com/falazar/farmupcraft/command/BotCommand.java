package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.LanBotLauncher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public final class BotCommand {
    private static final CustomLogger LOGGER = new CustomLogger(BotCommand.class.getSimpleName());

    private BotCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> spawnBot(ctx.getSource(), LanBotLauncher.DEFAULT_BOT_NAME))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopBot(ctx.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> stopBot(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> spawnBot(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))));
    }

    public static int spawnBot(CommandSourceStack source, String rawName) {
        MinecraftServer server = source.getServer();
        int port = server.getPort();
        if (port <= 0) {
            source.sendFailure(Component.literal(
                    "World is not Open to LAN (or has no listening port). Open to LAN first, then run /bot."));
            return 0;
        }
        try {
            String name = LanBotLauncher.validateBotName(rawName);
            if (server.getPlayerList().getPlayers().stream()
                    .anyMatch(p -> p.getName().getString().equalsIgnoreCase(name))) {
                source.sendFailure(Component.literal("Bot '" + name + "' is already online in the world."));
                return 0;
            }
            Process process = LanBotLauncher.startBot(name, port);
            LOGGER.info("Launched LAN bot '{}' on port {} (PID {})", name, port, process.pid());
            source.sendSuccess(() -> Component.literal("Summoning bot '" + name + "' on LAN port "
                    + port + " (PID " + process.pid() + ")...").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | IOException error) {
            LOGGER.warn("Failed to summon bot '{}': {}", rawName, error.getMessage());
            source.sendFailure(Component.literal("Could not summon bot: " + error.getMessage()));
            return 0;
        }
    }

    public static int stopBot(CommandSourceStack source, String rawName) {
        try {
            int stopped = LanBotLauncher.stopBot(rawName);
            if (stopped == 0) {
                source.sendFailure(Component.literal(rawName == null
                        ? "No managed bot processes are currently running."
                        : "Bot '" + rawName + "' is not currently running."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(rawName == null
                    ? "Stopped " + stopped + " bot process(es)."
                    : "Stopped bot '" + rawName + "'.").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } catch (IllegalArgumentException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }
}
