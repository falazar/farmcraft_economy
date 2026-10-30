package com.falazar.farmupcraft;

import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.util.CustomLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Grid-seeded lake/island detection. Only immutable surface snapshots leave the server thread.
 * Files retain the legacy JSON schemas, under the current save's farmupcraft/scans/namespace/dimension.
 * Call cancelWorld on level unload and shutdown on server stopping; the next server starts fresh.
 */
public final class IslandScanManager {
    public static final CustomLogger LOGGER = new CustomLogger(IslandScanManager.class.getSimpleName());
    static final int GRID_STEP = 5;
    static final int EROSION_DIST = 2;
    static final int OCEAN_THRESHOLD = 1_000;
    static final int MIN_FEATURE_SIZE = 10;
    static final int MAX_BODY_SIZE = 60_000;
    static final int MAX_RADIUS = 128 * 16;
    private static final int MAX_SNAPSHOTS = 1_024;
    private static final int MAX_JOB_SAMPLES = 32_000_000;
    private static final int MAX_TERRAIN_QUERIES = 128_000_000;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private static final String[] WP_NAMES = {
            "Mist", "Storm", "Dawn", "Dusk", "Frost", "Ember", "Tide", "Gale",
            "Vale", "Cove", "Reef", "Fen", "Briar", "Shoal", "Bluff", "Mere"
    };
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, Service> SERVICES = new IdentityHashMap<>();

    private IslandScanManager() {
    }

    private static final class Service {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "farmupcraft-island-scanner");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        final Map<ServerLevel, WorldWork> worlds = new IdentityHashMap<>();
        int nextId;
    }

    private static final class WorldWork {
        final List<Future<?>> tasks = new ArrayList<>();
        volatile boolean cancelled;
    }

    private static synchronized Service service(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, ignored -> new Service());
    }

    /** Must be called on the server thread, before unloading a level. Does not stop other dimensions. */
    public static synchronized void cancelWorld(ServerLevel level) {
        requireServerThread(level);
        Service service = SERVICES.get(level.getServer());
        if (service != null) {
            WorldWork work = service.worlds.remove(level);
            if (work != null) {
                work.cancelled = true;
                work.tasks.forEach(task -> task.cancel(true));
                service.executor.purge();
            }
        }
    }

    /** Must be called on ServerStoppingEvent, while queued server tasks can still be discarded. */
    public static synchronized void shutdown(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Scan shutdown requires the server thread");
        }
        Service service = SERVICES.remove(server);
        if (service != null) {
            service.worlds.values().forEach(work -> {
                work.cancelled = true;
                work.tasks.forEach(task -> task.cancel(true));
            });
            service.worlds.clear();
            service.executor.shutdownNow();
        }
    }

    public static void runScanAsync(ServerLevel level, int centerX, int centerZ, int radius,
                                    Consumer<Component> feedback, ServerPlayer player) {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Scan radius must be 1.." + MAX_RADIUS + " blocks");
        }
        validatePosition(centerX, centerZ);
        submit(level, feedback, player, job -> job.scan(centerX, centerZ, radius));
    }

    public static void scanPointAsync(ServerLevel level, int x, int z, Consumer<Component> feedback,
                                      ServerPlayer player) {
        validatePosition(x, z);
        submit(level, feedback, player, job -> job.point(x, z));
    }

    /** Cancel scans before queueing deletion, so an older job cannot recreate the cleared cache. */
    public static void clearCacheAsync(ServerLevel level, ServerPlayer player, Consumer<Component> feedback) {
        requireServerThread(level);
        cancelWorld(level);
        submit(level, feedback, player, job -> {
            Files.deleteIfExists(job.directory.resolve("scan_cache.json"));
            job.deliver(() -> {
                feedback.accept(Component.literal(
                        "Cache cleared (scan_cache.json). findings.json is kept. Next scan will start fresh."
                                + (player == null ? "" : " Scan waypoints cleared.")));
                if (player != null) {
                    job.send(new ScanWaypointsPacket());
                }
            });
        });
    }

    @FunctionalInterface
    private interface Operation {
        void run(Job job) throws IOException;
    }

    private static void submit(ServerLevel level, Consumer<Component> feedback, ServerPlayer player,
                               Operation operation) {
        requireServerThread(level);
        MinecraftServer server = level.getServer();
        Service service = service(server);
        WorldWork work;
        int id;
        synchronized (IslandScanManager.class) {
            work = service.worlds.computeIfAbsent(level, ignored -> new WorldWork());
            work.tasks.removeIf(Future::isDone);
            id = ++service.nextId;
        }
        var dimension = level.dimension().identifier();
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("farmupcraft").resolve("scans")
                .resolve(dimension.getNamespace()).resolve(dimension.getPath());
        Job job = new Job(level, directory, work, id, feedback, player == null ? null : player.getUUID());
        try {
            synchronized (IslandScanManager.class) {
                work.tasks.add(service.executor.submit(() -> {
                    try {
                        job.check();
                        Files.createDirectories(directory);
                        operation.run(job);
                    } catch (CancellationException ignored) {
                        // Unloading, clearing, or stopping deliberately invalidated this generation.
                    } catch (IOException | RuntimeException exception) {
                        LOGGER.error("Island scan " + id + " failed", exception);
                        job.feedback(Component.literal("Scan error: " + exception.getClass().getSimpleName()
                                + " — " + exception.getMessage()).withStyle(ChatFormatting.RED));
                    }
                }));
            }
        } catch (RejectedExecutionException exception) {
            feedback.accept(Component.literal("Scan queue is full (one active, four queued). Try again later.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Scan requests and lifecycle hooks require the server thread");
        }
    }

    static void validatePosition(int x, int z) {
        if (Math.abs((long) x) > 29_999_984L - MAX_RADIUS
                || Math.abs((long) z) > 29_999_984L - MAX_RADIUS) {
            throw new IllegalArgumentException("Scan position is too close to the world coordinate limit");
        }
    }

    record Surface(int y, boolean water, boolean river, String block) {
    }

    @FunctionalInterface
    interface Terrain {
        /** Null means unknown/unloaded, never a water boundary. */
        Surface at(int x, int z);
    }

    record Detection(JsonObject result, String reason, Set<Long> skippedBody) {
    }

    private static final class Job implements Terrain {
        final ServerLevel level;
        final MinecraftServer server;
        final Path directory;
        final WorldWork work;
        final int id;
        final Consumer<Component> feedback;
        final UUID playerId;
        final Map<Long, Surface[]> snapshots = new LinkedHashMap<>(32, .75f, true);
        final Set<Long> visited = new HashSet<>();
        final Set<Long> newVisited = new HashSet<>();
        final Set<Long> cacheable = new HashSet<>();
        final Map<Long, String> largeNames = new LinkedHashMap<>();
        int samples;
        int queries;
        int scanX;
        int scanZ;
        int scanRadius;
        private static final Surface[] MISSING = new Surface[0];

        Job(ServerLevel level, Path directory, WorldWork work, int id,
            Consumer<Component> feedback, UUID playerId) {
            this.level = level;
            this.server = level.getServer();
            this.directory = directory;
            this.work = work;
            this.id = id;
            this.feedback = feedback;
            this.playerId = playerId;
        }

        void check() {
            if (work.cancelled || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Scan cancelled");
            }
        }

        void deliver(Runnable action) {
            if (!work.cancelled) {
                server.execute(() -> {
                    if (!work.cancelled) {
                        action.run();
                    }
                });
            }
        }

        void feedback(Component message) {
            deliver(() -> feedback.accept(message));
        }

        void send(ScanWaypointsPacket packet) {
            // This method is only invoked inside deliver, on the server thread.
            if (playerId != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && player.level() == level) {
                    EDBMessages.sendToPlayer(packet, player);
                }
            }
        }

        <T> T onServer(Supplier<T> action) {
            check();
            CompletableFuture<T> result = new CompletableFuture<>();
            server.execute(() -> {
                if (work.cancelled || result.isDone()) {
                    result.cancel(false);
                    return;
                }
                try {
                    result.complete(action.get());
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
            try {
                return result.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                result.cancel(false);
                throw new CancellationException("Scan interrupted");
            } catch (TimeoutException exception) {
                result.cancel(false);
                throw new IllegalStateException("Server did not supply a scan snapshot within 30 seconds", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Could not capture scan terrain", exception.getCause());
            }
        }

        Surface[] chunk(int chunkX, int chunkZ, boolean load) {
            check();
            long key = ChunkPos.pack(chunkX, chunkZ);
            Surface[] cached = snapshots.get(key);
            if (cached != null) {
                if (cached != MISSING || !load) {
                    return cached == MISSING ? null : cached;
                }
                snapshots.remove(key);
            }
            if (++samples > MAX_JOB_SAMPLES / 256) {
                throw new IllegalStateException("Scan snapshot budget exhausted; use a smaller radius");
            }
            Surface[] snapshot = onServer(() -> {
                if (!load && !level.hasChunk(chunkX, chunkZ)) {
                    return null;
                }
                level.getChunk(chunkX, chunkZ);
                Surface[] columns = new Surface[256];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                        if (y >= level.getMinY()) {
                            BlockPos pos = new BlockPos(wx, y, wz);
                            columns[x * 16 + z] = new Surface(y, level.getFluidState(pos).is(FluidTags.WATER),
                                    level.getBiome(pos).is(BiomeTags.IS_RIVER),
                                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                        }
                    }
                }
                return columns;
            });
            // Explicit seed loads replace a remembered missing chunk.
            snapshots.put(key, snapshot == null ? MISSING : snapshot);
            if (snapshots.size() > MAX_SNAPSHOTS) {
                snapshots.remove(snapshots.keySet().iterator().next());
            }
            return snapshot;
        }

        @Override
        public Surface at(int x, int z) {
            check();
            if (++queries > MAX_TERRAIN_QUERIES) {
                throw new IllegalStateException("Scan work budget exhausted; use a smaller radius");
            }
            Surface[] columns = chunk(x >> 4, z >> 4, false);
            return columns == null ? null : columns[(x & 15) * 16 + (z & 15)];
        }

        void load() throws IOException {
            Path cache = directory.resolve("scan_cache.json");
            if (Files.exists(cache)) {
                for (JsonElement element : read(cache).getAsJsonObject().getAsJsonArray("visited")) {
                    visited.add(readCoordinate(element.getAsJsonObject()));
                }
            }
            Path names = directory.resolve("large_body_names.json");
            if (Files.exists(names)) {
                for (JsonElement element : read(names).getAsJsonArray()) {
                    JsonObject entry = element.getAsJsonObject();
                    for (JsonElement seed : entry.getAsJsonArray("seeds")) {
                        largeNames.put(readCoordinate(seed.getAsJsonObject()), entry.get("name").getAsString());
                    }
                }
            }
        }

        Detection detect(int x, int z) {
            check();
            Set<Long> bodyVisited = new HashSet<>();
            Detection detection = detectBody(this, x, z, visited, bodyVisited);
            boolean complete = detection.result() != null || detection.skippedBody() != null
                    || detection.reason().startsWith("Too small");
            // Only later grid seeds need retention; whole bodies are capped and discarded per fill.
            for (long key : bodyVisited) {
                if (px(key) % GRID_STEP == 0 && pz(key) % GRID_STEP == 0
                        && inRadius(px(key), pz(key), scanX, scanZ, scanRadius)) {
                    newVisited.add(key);
                    if (complete) {
                        cacheable.add(key);
                    }
                }
            }
            if (detection.skippedBody() != null) {
                String name = detection.skippedBody().stream().map(largeNames::get)
                        .filter(java.util.Objects::nonNull).findFirst()
                        .orElseGet(() -> randomName(detection.skippedBody().size()));
                for (long key : detection.skippedBody()) {
                    if (px(key) % GRID_STEP == 0 && pz(key) % GRID_STEP == 0) {
                        largeNames.put(key, name);
                    }
                }
                return new Detection(null, detection.reason() + " '" + name + "'", detection.skippedBody());
            }
            return detection;
        }

        void found(JsonObject result) {
            Component message = formatFeatureMessage(result);
            ScanWaypointsPacket packet = waypoints(result, result.get("name").getAsString());
            deliver(() -> {
                feedback.accept(message);
                send(packet);
            });
        }

        void scan(int cx, int cz, int radius) throws IOException {
            scanX = cx;
            scanZ = cz;
            scanRadius = radius;
            long start = System.nanoTime();
            load();
            feedback(Component.literal("Scan:" + id + " started at " + time() + " (radius=" + radius + ")"));
            List<JsonObject> findings = new ArrayList<>();
            Set<Long> processed = new HashSet<>();
            Set<Long> loaded = new HashSet<>();
            Set<String> cachedNames = new LinkedHashSet<>();
            int seeds = 0, cached = 0, skipped = 0, incomplete = 0, row = 0;
            int x0 = floorGrid(cx - radius), x1 = ceilGrid(cx + radius);
            int z0 = floorGrid(cz - radius), z1 = ceilGrid(cz + radius);
            for (int x = x0; x <= x1; x += GRID_STEP) {
                check();
                feedback(Component.literal(":" + id + " " + (++row) + "/" + ((x1 - x0) / GRID_STEP + 1)
                        + " " + x + " fnd=" + findings.size() + " sds=" + seeds + " cache:" + cached));
                for (int z = z0; z <= z1; z += GRID_STEP) {
                    check();
                    if (!inRadius(x, z, cx, cz, radius)) {
                        continue;
                    }
                    long key = pack(x, z);
                    if (visited.contains(key)) {
                        cached++;
                        if (largeNames.containsKey(key)) {
                            cachedNames.add(largeNames.get(key));
                        }
                        continue;
                    }
                    ChunkPos chunk = ChunkPos.containing(new BlockPos(x, 0, z));
                    chunk(chunk.x(), chunk.z(), true);
                    loaded.add(ChunkPos.pack(chunk.x(), chunk.z()));
                    if (newVisited.contains(key)) {
                        if (cacheable.contains(key)) {
                            processed.add(key);
                        }
                        skipped++;
                        continue;
                    }
                    if (at(x, z) == null) {
                        continue;
                    }
                    seeds++;
                    Detection detection = detect(x, z);
                    if (detection.reason().startsWith("Incomplete")) {
                        incomplete++;
                    }
                    if (cacheable.contains(key)) {
                        processed.add(key);
                    }
                    if (detection.result() != null) {
                        findings.add(detection.result());
                        found(detection.result());
                    }
                }
            }
            check();
            // Validate/merge existing findings before replacing any cache file.
            JsonArray merged = mergeFindings(Files.exists(directory.resolve("findings.json"))
                    ? read(directory.resolve("findings.json")).getAsJsonArray() : new JsonArray(), findings);
            visited.addAll(processed);
            write("findings.json", merged);
            JsonArray coordinates = new JsonArray();
            visited.forEach(key -> coordinates.add(coordinate(key)));
            JsonObject cache = new JsonObject();
            cache.add("visited", coordinates);
            write("scan_cache.json", cache);
            JsonArray names = new JsonArray();
            Map<String, JsonArray> grouped = new LinkedHashMap<>();
            largeNames.forEach((key, name) -> grouped.computeIfAbsent(name, ignored -> new JsonArray()).add(coordinate(key)));
            grouped.forEach((name, coords) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", name);
                entry.add("seeds", coords);
                names.add(entry);
            });
            write("large_body_names.json", names);
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            feedback(Component.literal("Scan:" + id + " done " + time() + " (took " + seconds + "s) — "
                    + seeds + " scanned, " + cached + " cached, " + skipped + " body-skipped, "
                    + loaded.size() + " chunks, " + findings.size() + " features found.").withStyle(ChatFormatting.GREEN));
            if (incomplete > 0) {
                feedback(Component.literal(incomplete + " incomplete bodies deferred at unloaded/no-surface boundaries."
                        + " Their grid seeds were not cached; rerun after loading surrounding chunks.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (!cachedNames.isEmpty()) {
                feedback(Component.literal("Scan:" + id + " cached bodies seen: " + String.join(", ", cachedNames)));
            }
            feedback(Component.literal("Scan:" + id + " Saved findings.json (" + findings.size()
                    + " new) and scan_cache.json (" + visited.size() + " grid seeds)"));
        }

        void point(int x, int z) throws IOException {
            load();
            int gx = Math.round((float) x / GRID_STEP) * GRID_STEP;
            int gz = Math.round((float) z / GRID_STEP) * GRID_STEP;
            long key = pack(gx, gz);
            if (visited.contains(key)) {
                feedback(Component.literal("Scan:" + id + " Grid point (" + gx + "," + gz + ") cached — part of "
                        + largeNames.getOrDefault(key, "a previously scanned body")));
            } else {
                feedback(Component.literal("Scan:" + id + " === scanPoint (" + x + "," + z
                        + ") grid=(" + gx + "," + gz + ") ==="));
                chunk(x >> 4, z >> 4, true);
                Surface surface = at(x, z);
                if (surface == null) {
                    feedback(Component.literal("No surface block."));
                } else {
                    feedback(Component.literal("y=" + surface.y() + " block=" + surface.block() + "  "
                            + (surface.water() ? "WATER" : isCoastal(this, x, z) ? "COASTAL" : "LAND")));
                    Detection detection = detect(x, z);
                    if (detection.result() != null) {
                        found(detection.result());
                    } else {
                        feedback(Component.literal("[Scan:" + id + "] " + detection.reason()));
                        if (detection.skippedBody() != null) {
                            ScanWaypointsPacket packet = waypoints(buildResult("large", detection.skippedBody()), "Large-" + id);
                            deliver(() -> send(packet));
                        }
                    }
                }
            }
            feedback(Component.literal("Scan:" + id + " === done ==="));
        }

        void write(String name, JsonElement value) throws IOException {
            check();
            Path target = directory.resolve(name), pending = directory.resolve(name + ".pending");
            try {
                Files.writeString(pending, JSON.toJson(value), StandardCharsets.UTF_8);
                check();
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(pending);
            }
        }
    }

    /**
     * The fill is capped at MAX_BODY_SIZE, unlike the old ocean walk which could run forever.
     * Unknown perimeter columns never produce a complete feature or enter the persistent seed cache.
     */
    static Detection detectBody(Terrain terrain, int sx, int sz, Set<Long> visited, Set<Long> newVisited) {
        long seed = pack(sx, sz);
        if (visited.contains(seed) || newVisited.contains(seed)) {
            return new Detection(null, "Already cached (previously scanned body)", null);
        }
        Surface first = terrain.at(sx, sz);
        if (first == null) {
            return new Detection(null, "Unloaded/no surface", null);
        }
        Set<Long> body = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        body.add(seed);
        queue.add(seed);
        boolean river = false, unknown = false, tooBig = false;
        while (!queue.isEmpty() && !tooBig) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException();
            }
            long key = queue.removeFirst();
            Surface current = terrain.at(px(key), pz(key));
            river |= current.river();
            for (int direction = 0; direction < 4; direction++) {
                int nx = px(key) + DX[direction], nz = pz(key) + DZ[direction];
                long neighbor = pack(nx, nz);
                if (body.contains(neighbor)) {
                    continue;
                }
                Surface next = terrain.at(nx, nz);
                if (next == null) {
                    unknown = true;
                } else if (next.water() == first.water()) {
                    // A cached grid coordinate is not a physical boundary of a water/land body.
                    body.add(neighbor);
                    queue.addLast(neighbor);
                    if (body.size() > MAX_BODY_SIZE) {
                        tooBig = true;
                        break;
                    }
                }
            }
        }
        newVisited.addAll(body);
        if (river && first.water()) {
            return new Detection(null, "River — " + body.size() + " blocks", body);
        }
        if (tooBig || first.water() && body.size() > OCEAN_THRESHOLD) {
            return new Detection(null, (first.water() ? "Ocean/too big" : "Mainland")
                    + " — " + body.size() + (tooBig ? "+" : "") + " blocks", body);
        }
        if (unknown) {
            return new Detection(null, "Incomplete body: unloaded/no-surface boundary", null);
        }
        if (body.size() < MIN_FEATURE_SIZE) {
            return new Detection(null, "Too small — " + body.size() + " blocks", null);
        }
        if (first.water()) {
            return new Detection(buildResult("lake", body), "", null);
        }
        Set<Long> interior = new HashSet<>(body);
        interior.removeIf(key -> isCoastal(terrain, px(key), pz(key)));
        // Check every interior component, not only the first one.
        Set<Long> perimeterChecks = interior.isEmpty() ? body : interior;
        for (long key : perimeterChecks) {
            for (int direction = 0; direction < 4; direction++) {
                long neighbor = pack(px(key) + DX[direction], pz(key) + DZ[direction]);
                if (!body.contains(neighbor)) {
                    Surface next = terrain.at(px(neighbor), pz(neighbor));
                    if (next == null || !next.water()) {
                        return new Detection(null, "Peninsula/connects to mainland — " + body.size() + " blocks", null);
                    }
                }
            }
        }
        return new Detection(buildResult("island", body), "", null);
    }

    static boolean isCoastal(Terrain terrain, int x, int z) {
        for (int dx = -EROSION_DIST; dx <= EROSION_DIST; dx++) {
            for (int dz = -EROSION_DIST; dz <= EROSION_DIST; dz++) {
                if (dx != 0 || dz != 0) {
                    Surface surface = terrain.at(x + dx, z + dz);
                    if (surface != null && surface.water()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static JsonObject buildResult(String type, Set<Long> coords) {
        if (coords.isEmpty()) {
            throw new IllegalArgumentException("Cannot describe an empty feature");
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        long sumX = 0, sumZ = 0;
        for (long key : coords) {
            int x = px(key), z = pz(key);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            sumX += x;
            sumZ += z;
        }
        JsonObject result = new JsonObject(), center = new JsonObject(), box = new JsonObject();
        result.addProperty("type", type);
        center.addProperty("x", (int) (sumX / coords.size()));
        center.addProperty("z", (int) (sumZ / coords.size()));
        result.add("center", center);
        box.addProperty("min_x", minX);
        box.addProperty("min_z", minZ);
        box.addProperty("max_x", maxX);
        box.addProperty("max_z", maxZ);
        result.add("bounding_box", box);
        result.addProperty("block_count", coords.size());
        result.addProperty("partial", false);
        return result;
    }

    static Component formatFeatureMessage(JsonObject result) {
        String type = result.get("type").getAsString();
        int x = result.getAsJsonObject("center").get("x").getAsInt();
        int z = result.getAsJsonObject("center").get("z").getAsInt();
        int count = result.get("block_count").getAsInt();
        JsonObject box = result.getAsJsonObject("bounding_box");
        int chunksX = (int) Math.ceil((box.get("max_x").getAsInt() - box.get("min_x").getAsInt()) / 16.0);
        int chunksZ = (int) Math.ceil((box.get("max_z").getAsInt() - box.get("min_z").getAsInt()) / 16.0);
        if (!result.has("name")) {
            result.addProperty("name", randomName(count));
        }
        Style style = type.equals("lake") ? Style.EMPTY.withColor(ChatFormatting.AQUA)
                : Style.EMPTY.withColor(TextColor.fromRgb(0xD2B48C));
        return Component.empty()
                .append(Component.literal("Found " + type + " '").withStyle(style))
                .append(Component.literal(result.get("name").getAsString()).withStyle(style.withBold(true)))
                .append(Component.literal("' at ").withStyle(style))
                .append(Component.literal(x + ", " + z).withStyle(style.withClickEvent(
                        new ClickEvent.RunCommand("/tp @s " + x + " ~ " + z))))
                .append(Component.literal("  size: " + chunksX + "x" + chunksZ + " chnks  blcks: " + count)
                        .withStyle(style));
    }

    static ScanWaypointsPacket waypoints(JsonObject result, String name) {
        JsonObject box = result.getAsJsonObject("bounding_box");
        int minX = box.get("min_x").getAsInt(), maxX = box.get("max_x").getAsInt();
        int minZ = box.get("min_z").getAsInt(), maxZ = box.get("max_z").getAsInt();
        int color = ThreadLocalRandom.current().nextInt(0x1000000);
        return new ScanWaypointsPacket(List.of(
                new ScanWaypointsPacket.Entry(name + "-NW", minX, 64, minZ, color),
                new ScanWaypointsPacket.Entry(name + "-NE", maxX, 64, minZ, color),
                new ScanWaypointsPacket.Entry(name + "-SW", minX, 64, maxZ, color),
                new ScanWaypointsPacket.Entry(name + "-SE", maxX, 64, maxZ, color)));
    }

    static JsonArray mergeFindings(JsonArray existing, List<JsonObject> additions) {
        Map<String, JsonObject> seen = new LinkedHashMap<>();
        for (JsonElement element : existing) {
            JsonObject entry = element.getAsJsonObject().deepCopy();
            seen.put(findingKey(entry), entry);
        }
        for (JsonObject entry : additions) {
            JsonObject previous = seen.putIfAbsent(findingKey(entry), entry.deepCopy());
            if (previous != null && !previous.has("name") && entry.has("name")) {
                previous.addProperty("name", entry.get("name").getAsString());
            }
        }
        JsonArray result = new JsonArray();
        seen.values().forEach(result::add);
        return result;
    }

    private static String findingKey(JsonObject result) {
        JsonObject center = result.getAsJsonObject("center");
        return result.get("type").getAsString() + ":" + center.get("x").getAsInt() + ":" + center.get("z").getAsInt();
    }

    private static JsonElement read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static JsonObject coordinate(long key) {
        JsonObject result = new JsonObject();
        result.addProperty("x", px(key));
        result.addProperty("z", pz(key));
        return result;
    }

    private static long readCoordinate(JsonObject coordinate) {
        return pack(coordinate.get("x").getAsInt(), coordinate.get("z").getAsInt());
    }

    private static String randomName(int size) {
        return WP_NAMES[ThreadLocalRandom.current().nextInt(WP_NAMES.length)] + "-" + size;
    }

    private static String time() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int px(long key) {
        return (int) (key >> 32);
    }

    static int pz(long key) {
        return (int) key;
    }

    static boolean inRadius(int x, int z, int cx, int cz, int radius) {
        long dx = (long) x - cx, dz = (long) z - cz;
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius
                && dx * dx + dz * dz <= (long) radius * radius;
    }

    static int floorGrid(int value) {
        return Math.floorDiv(value, GRID_STEP) * GRID_STEP;
    }

    static int ceilGrid(int value) {
        return -Math.floorDiv(-value, GRID_STEP) * GRID_STEP;
    }
}
