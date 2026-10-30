package com.falazar.farmupcraft.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AsyncLocator {
    private static ExecutorService executor;
    private static volatile long lifecycle;
    private static final Set<LocateTask<?>> tasks = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger threadNumber = new AtomicInteger();

    private AsyncLocator() {}

    public static synchronized void handleServerAboutToStartEvent() {
        handleServerStoppingEvent();
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "farmupcraft-locator-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public static synchronized void handleServerStoppingEvent() {
        lifecycle++;
        tasks.forEach(LocateTask::cancel);
        tasks.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static LocateTask<BlockPos> locate(ServerLevel level, TagKey<Structure> structureTag,
                                              BlockPos pos, int searchRadius, boolean skipKnownStructures) {
        BlockPos origin = pos.immutable();
        return submit(level.getServer(),
                () -> level.findNearestMapStructure(structureTag, origin, searchRadius, skipKnownStructures));
    }

    public static LocateTask<Pair<BlockPos, Holder<Structure>>> locate(ServerLevel level,
            HolderSet<Structure> structureSet, BlockPos pos, int searchRadius, boolean skipKnownStructures) {
        BlockPos origin = pos.immutable();
        return submit(level.getServer(), () -> level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, structureSet, origin, searchRadius, skipKnownStructures));
    }

    static synchronized <T> LocateTask<T> submit(MinecraftServer server, Supplier<T> search) {
        if (executor == null) {
            throw new IllegalStateException("AsyncLocator must be started before locating structures");
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> worker = new FutureTask<>(() -> {
            try {
                result.complete(search.get());
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
                throw failure;
            }
            return null;
        });
        LocateTask<T> task = new LocateTask<>(server, result, worker);
        tasks.add(task);
        result.whenComplete((value, failure) -> tasks.remove(task));
        executor.execute(worker);
        return task;
    }

    public record LocateTask<T>(MinecraftServer server, CompletableFuture<T> completableFuture, Future<?> taskFuture) {
        /** Runs on the locating thread; use thenOnServerThread when accessing game state. */
        public LocateTask<T> then(Consumer<T> action) {
            completableFuture.thenAccept(action);
            return this;
        }

        public LocateTask<T> thenOnServerThread(Consumer<T> action) {
            long expectedLifecycle = lifecycle;
            completableFuture.thenAccept(value -> server.execute(() -> {
                if (expectedLifecycle == lifecycle && !completableFuture.isCancelled() && !server.isStopped()) {
                    action.accept(value);
                }
            }));
            return this;
        }

        public void cancel() {
            completableFuture.cancel(false);
            taskFuture.cancel(true);
        }
    }
}
