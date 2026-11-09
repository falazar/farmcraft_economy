package com.falazar.farmupcraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NpcReplyRetry {
    public static final String BUSY_REPLY = "I'm a bit busy right now. Could we talk later?";
    public static final String TALKED_PLAYERS_KEY = "farmupcraft_talked_players";
    private static final java.util.List<String> GREETINGS = java.util.List.of(
            "Good to see you, %s!", "Hello there, %s!", "Welcome back, %s!",
            "Nice to see you again, %s!", "Hey there, %s!", "Ah, hello, %s!",
            "Always good to see you, %s!", "Hello again, %s!",
            "Glad you stopped by, %s!", "How have you been, %s?");

    private NpcReplyRetry() {}

    public static boolean countsAsConversation(String reply, Throwable error) {
        return error == null && reply != null && !reply.isBlank()
                && !reply.startsWith("[AI Error]") && !reply.startsWith("[NPC Error]")
                && !reply.equals(BUSY_REPLY);
    }

    public static String returningGreeting(String player, int choice) {
        return GREETINGS.get(choice).formatted(player);
    }

    public static String listeningMessage(String npc, String greeting, boolean familiar) {
        return "[NPC] " + (familiar ? npc + ": " + greeting
                : npc + " is listening.") + " Say something in chat within 2 minutes!";
    }

    public static String replyOrBusy(String reply, Throwable error, BiConsumer<String, Throwable> logFailure) {
        if (error != null || reply == null || reply.isBlank() || reply.startsWith("[AI Error]")) {
            logFailure.accept(reply, error);
            return BUSY_REPLY;
        }
        return reply;
    }

    public static CompletableFuture<String> request(Supplier<CompletableFuture<String>> model,
            Executor server, BooleanSupplier active, Function<String, String> validate,
            boolean retryInvalid, Runnable logRetry) {
        return attempt(model, server, active, validate, retryInvalid, logRetry);
    }

    private static CompletableFuture<String> attempt(Supplier<CompletableFuture<String>> model,
            Executor server, BooleanSupplier active, Function<String, String> validate,
            boolean retryInvalid, Runnable logRetry) {
        return model.get().thenComposeAsync(reply -> {
            if (!active.getAsBoolean()) return CompletableFuture.completedFuture(null);
            if (reply == null || reply.isBlank() || reply.startsWith("[AI Error]")) {
                return CompletableFuture.completedFuture(reply);
            }
            String safe = validate.apply(reply);
            if (retryInvalid && safe.startsWith("[NPC Error]")) {
                logRetry.run();
                return attempt(model, server, active, validate, false, logRetry);
            }
            return CompletableFuture.completedFuture(safe);
        }, server);
    }
}
