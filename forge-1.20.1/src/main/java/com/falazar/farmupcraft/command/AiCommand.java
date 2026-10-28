package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.OllamaService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Provides the /ai command for interacting with a locally running Ollama
 * instance.
 *
 * Usage:
 * /ai test <prompt> — Sends the prompt to Ollama and prints the response in
 * chat.
 *
 * The response is fetched asynchronously so the server thread is never blocked.
 * See OllamaService for setup instructions (Ollama must be running locally
 * first).
 */
public class AiCommand {

    private static final CustomLogger LOGGER = new CustomLogger(AiCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        pDispatcher.register(
                Commands.literal("ai")
                        .then(Commands.literal("test")
                                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String prompt = StringArgumentType.getString(context, "prompt");
                                            return runTest(context.getSource(), prompt);
                                        }))));
    }

    private static int runTest(CommandSourceStack source, String prompt) {
        MinecraftServer server = source.getServer();

        // Acknowledge immediately so the player knows the request was received.
        source.sendSuccess(() -> Component.literal("[AI] Thinking..."), false);

        OllamaService.chat(prompt)
                .thenAccept(response ->
                // Schedule the reply back on the main server thread (thread-safe).
                server.execute(() -> source.sendSuccess(() -> Component.literal(response), false)))
                .exceptionally(err -> {
                    server.execute(() -> source.sendFailure(Component.literal("[AI Error] " + err.getMessage())));
                    LOGGER.error("AiCommand failed: {}", err.getMessage());
                    return null;
                });

        return 1;
    }
}
