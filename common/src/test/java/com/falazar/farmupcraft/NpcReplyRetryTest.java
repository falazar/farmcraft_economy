package com.falazar.farmupcraft;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class NpcReplyRetryTest {
    private static final String INVALID = "[NPC Error] invalid";
    private static final Function<String, String> VALIDATE = reply -> reply.equals("bad") ? INVALID : reply;

    @Test void familiarityRequiresSuccessfulSpeechNotBusyOrFailedReplies() {
        assertTrue(NpcReplyRetry.countsAsConversation("Hello, friend!", null));
        for (String reply : new String[]{null, "", " ", "[AI Error] unavailable", INVALID, NpcReplyRetry.BUSY_REPLY})
            assertFalse(NpcReplyRetry.countsAsConversation(reply, null));
        assertFalse(NpcReplyRetry.countsAsConversation("Hello!", new IllegalStateException("failed")));
        assertEquals("[NPC] Ada is listening. Say something in chat within 2 minutes!",
                NpcReplyRetry.listeningMessage("Ada", "Robin", false));
        assertEquals("[NPC] Ada: Good to see you, Robin! Say something in chat within 2 minutes!",
                NpcReplyRetry.listeningMessage("Ada", NpcReplyRetry.returningGreeting("Robin", 0), true));
    }

    @Test void tenDistinctReturningGreetingsIncludeThePlayerName() {
        var greetings = new java.util.HashSet<String>();
        for (int choice = 0; choice < 10; choice++) {
            String greeting = NpcReplyRetry.returningGreeting("Robin", choice);
            assertTrue(greeting.contains("Robin"), greeting);
            assertTrue(greetings.add(greeting), greeting);
            assertTrue(NpcReplyRetry.listeningMessage("Ada", greeting, true).contains(greeting));
        }
        assertEquals(10, greetings.size());
        assertThrows(IndexOutOfBoundsException.class, () -> NpcReplyRetry.returningGreeting("Robin", 10));
    }

    @Test void ordinaryConversationAllowsNumbersBiomesAndMiningWordsUnchanged() {
        String reply = "One thing: I collect quartz and rocks near the forest, north of here at level 40.";
        assertEquals(reply, NpcReplyRetry.request(() -> CompletableFuture.completedFuture(reply),
                Runnable::run, () -> true, Function.identity(), false,
                () -> fail("ordinary conversation must not retry validation")).join());
    }

    @Test void missingRepliesAndServiceFailuresBecomeBusyRepliesAndAreLogged() {
        var logs = new AtomicInteger();
        for (String reply : new String[]{null, "", "  ", "[AI Error] unavailable"}) {
            assertEquals(NpcReplyRetry.BUSY_REPLY,
                    NpcReplyRetry.replyOrBusy(reply, null, (text, error) -> logs.incrementAndGet()));
        }
        var timeout = new java.util.concurrent.TimeoutException("timed out");
        assertEquals(NpcReplyRetry.BUSY_REPLY, NpcReplyRetry.replyOrBusy(null, timeout, (text, error) -> {
            assertSame(timeout, error);
            logs.incrementAndGet();
        }));
        assertEquals(5, logs.get());
        assertEquals("Hello!", NpcReplyRetry.replyOrBusy("Hello!", null,
                (text, error) -> fail("valid replies must not log a failure")));
        assertEquals(INVALID, NpcReplyRetry.replyOrBusy(INVALID, null,
                (text, error) -> fail("validation failures must not become busy replies")));
    }

    @Test void firstInvalidIsRetriedOnceAndSecondReplyIsValidated() {
        var responses = new ArrayDeque<>(java.util.List.of("bad", "good"));
        var calls = new AtomicInteger();
        var retries = new AtomicInteger();
        Supplier<CompletableFuture<String>> model = () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(responses.remove());
        };
        assertEquals("good", NpcReplyRetry.request(model, Runnable::run, () -> true, VALIDATE, true,
                retries::incrementAndGet).join());
        assertEquals(2, calls.get());
        assertEquals(1, retries.get());
    }

    @Test void secondInvalidIsReturnedWithoutThirdAttempt() {
        var calls = new AtomicInteger();
        var retries = new AtomicInteger();
        assertEquals(INVALID, NpcReplyRetry.request(() -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("bad");
        }, Runnable::run, () -> true, VALIDATE, true, retries::incrementAndGet).join());
        assertEquals(2, calls.get());
        assertEquals(1, retries.get());
    }

    @Test void validAndNonGemRepliesDoNotRetry() {
        var calls = new AtomicInteger();
        Supplier<CompletableFuture<String>> model = () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("good");
        };
        assertEquals("good", NpcReplyRetry.request(model, Runnable::run, () -> true, VALIDATE, true,
                () -> fail("unexpected retry")).join());
        assertEquals(INVALID, NpcReplyRetry.request(() -> CompletableFuture.completedFuture("bad"),
                Runnable::run, () -> true, VALIDATE, false, () -> fail("unexpected retry")).join());
        assertEquals(1, calls.get());
    }

    @Test void inactiveConversationsAndServiceErrorsAreNotRetried() {
        var calls = new AtomicInteger();
        Supplier<CompletableFuture<String>> model = () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("bad");
        };
        assertNull(NpcReplyRetry.request(model, Runnable::run, () -> false, VALIDATE, true,
                () -> fail("unexpected retry")).join());
        assertEquals(1, calls.get());
        assertEquals("[AI Error] unavailable", NpcReplyRetry.request(
                () -> CompletableFuture.completedFuture("[AI Error] unavailable"), Runnable::run,
                () -> true, VALIDATE, true, () -> fail("unexpected retry")).join());
        assertThrows(CompletionException.class, () -> NpcReplyRetry.request(
                () -> CompletableFuture.failedFuture(new IllegalStateException("unavailable")), Runnable::run,
                () -> true, VALIDATE, true, () -> fail("unexpected retry")).join());
    }

    @Test void conversationEndingBeforeRetryCompletesDiscardsReply() {
        var second = new CompletableFuture<String>();
        var calls = new AtomicInteger();
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        var result = NpcReplyRetry.request(() -> calls.getAndIncrement() == 0
                        ? CompletableFuture.completedFuture("bad") : second,
                Runnable::run, active::get, VALIDATE, true, () -> {});
        active.set(false);
        second.complete("good");
        assertNull(result.join());
        assertEquals(2, calls.get());
    }
}
