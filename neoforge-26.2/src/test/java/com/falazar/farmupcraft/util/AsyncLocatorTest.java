package com.falazar.farmupcraft.util;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AsyncLocatorTest {
    @AfterEach
    void stopExecutor() {
        AsyncLocator.handleServerStoppingEvent();
    }

    @Test
    void locatingRequiresAnExplicitServerLifecycleAndCanRestart(MinecraftServer server) throws Exception {
        AsyncLocator.handleServerStoppingEvent();
        assertThrows(IllegalStateException.class, () -> AsyncLocator.submit(server, () -> "not started"));
        AsyncLocator.handleServerAboutToStartEvent();
        assertEquals("first", AsyncLocator.submit(server, () -> "first").completableFuture().get(5, TimeUnit.SECONDS));
        AsyncLocator.handleServerStoppingEvent();
        assertThrows(IllegalStateException.class, () -> AsyncLocator.submit(server, () -> "stopped"));
        AsyncLocator.handleServerAboutToStartEvent();
        assertEquals("restarted", AsyncLocator.submit(server, () -> "restarted").completableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void failuresAndMissingStructuresCompleteRatherThanHanging(MinecraftServer server) throws Exception {
        AsyncLocator.handleServerAboutToStartEvent();
        var expected = new IllegalArgumentException("structure search failed");
        var failed = AsyncLocator.submit(server, () -> { throw expected; });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> failed.completableFuture().get(5, TimeUnit.SECONDS));
        assertSame(expected, failure.getCause());
        assertNull(AsyncLocator.submit(server, () -> null).completableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void cancellationStopsBothFuturesAndShutdownCancelsOutstandingSearches(MinecraftServer server) throws Exception {
        AsyncLocator.handleServerAboutToStartEvent();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        var task = AsyncLocator.submit(server, () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return "unreachable";
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        AsyncLocator.handleServerStoppingEvent();
        assertTrue(task.completableFuture().isCancelled());
        assertTrue(task.taskFuture().isCancelled());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        task.cancel();
    }

    @Test
    void serverCallbacksUseTheOfficialFixtureExecutor(MinecraftServer server) throws Exception {
        AsyncLocator.handleServerAboutToStartEvent();
        CompletableFuture<String> observed = new CompletableFuture<>();
        var task = AsyncLocator.submit(server, () -> "village");
        assertSame(task, task.thenOnServerThread(value -> {
            if (server.isSameThread()) observed.complete(value);
            else observed.completeExceptionally(new AssertionError("Callback was not on server thread"));
        }));
        assertEquals("village", observed.get(5, TimeUnit.SECONDS));
    }
}
