package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.OllamaService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/** Replies are delivered on the server thread; registering the command never contacts Ollama. */
public class AiCommand {
    private static final CustomLogger LOGGER = new CustomLogger(AiCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ai")
                .then(Commands.literal("test")
                        .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                .executes(context -> runTest(context.getSource(),
                                        StringArgumentType.getString(context, "prompt"))))));
    }

    private static int runTest(CommandSourceStack source, String prompt) {
        return runTest(source, prompt, OllamaService::chat, source.getServer()::execute);
    }

    static int runTest(CommandSourceStack source, String prompt,
                       Function<String, CompletableFuture<String>> chat, Consumer<Runnable> serverThread) {
        source.sendSuccess(() -> Component.literal("[AI] Thinking..."), false);
        CompletableFuture<String> request;
        try {
            request = chat.apply(prompt);
        } catch (RejectedExecutionException failure) {
            LOGGER.error("AiCommand request rejected", failure);
            source.sendFailure(Component.literal("[AI Error] " + failure.getMessage()));
            return 0;
        }
        request.whenComplete((response, failure) -> {
            try {
                serverThread.accept(() -> {
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                        LOGGER.error("AiCommand failed", cause);
                        source.sendFailure(Component.literal("[AI Error] " + cause.getMessage()));
                    } else {
                        source.sendSuccess(() -> Component.literal(response), false);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                LOGGER.error("AiCommand reply could not be delivered: server is stopping", rejected);
            }
        });
        return 1;
    }
}
