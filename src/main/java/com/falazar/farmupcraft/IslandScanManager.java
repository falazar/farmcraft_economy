package com.falazar.farmupcraft;

import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.util.CustomLogger;
import com.google.gson.*;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scans a radius around a player to identify islands and lakes.
 *
 * Algorithm summary:
 * 1. Sparse grid sampling (every 5 blocks, grid-aligned) finds water/land
 * seeds.
 * 2. Water flood-fill → classify: river (any IS_RIVER
 * block),
 * ocean (size > OCEAN_THRESHOLD), or lake.
 * 3. Land flood-fill → C-erosion: land within ±2 of water is "coastal".
 * Interior BFS checks if any non-coastal land escapes the body → peninsula →
 * skip.
 * 4. Results saved to findings.json; visited coords cached in scan_cache.json.
 */
public class IslandScanManager {
    public static final CustomLogger LOGGER = new CustomLogger(IslandScanManager.class.getSimpleName());
    private static int scanCount = 0;
    private static String lastSkipReason = "";
    private static Set<Long> lastSkipBody = null;
    /**
     * Persisted map from grid-seed coord to large-body name (ocean/river/mainland).
     */
    private static Map<Long, String> largeBodyNames = new HashMap<>();
    // --- Tuning constants ---
    private static final int GRID_STEP = 5;
    private static final int EROSION_DIST = 2; // C / S erosion radius (±2 blocks)
    private static final int OCEAN_THRESHOLD = 1000; // water bodies larger than this = ocean
    private static final int MIN_FEATURE_SIZE = 10; // ignore tiny features
    private static final int MAX_BODY_SIZE = 60_000; // bail early if body is mainland/ocean

    // --- XZ packing helpers ---
    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int px(long k) {
        return (int) (k >> 32);
    }

    private static int pz(long k) {
        return (int) (k & 0xFFFFFFFFL);
    }

    // --- File paths (run/ directory) ---
    private static Path cacheFile() {
        return FMLPaths.GAMEDIR.get().resolve("scan_cache.json");
    }

    private static Path findingsFile() {
        return FMLPaths.GAMEDIR.get().resolve("findings.json");
    }

    private static Path largeBodiesFile() {
        return FMLPaths.GAMEDIR.get().resolve("large_body_names.json");
    }

    private static final String[] WP_NAMES = {
            "Mist", "Storm", "Dawn", "Dusk", "Frost", "Ember", "Tide", "Gale",
            "Vale", "Cove", "Reef", "Fen", "Briar", "Shoal", "Bluff", "Mere"
    };
    private static final Random RNG = new Random();

    private static String randomWpName(int blockCount) {
        return WP_NAMES[RNG.nextInt(WP_NAMES.length)] + "-" + blockCount;
    }

    // --- 4-directional offsets ---
    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 1, -1 };

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Starts the scan asynchronously. Feedback messages are dispatched back to
     * the server thread so they are safe to send to a player.
     */
    public static void runScanAsync(ServerLevel level, int centerX, int centerZ, int radius,
            Consumer<Component> feedback, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        Thread t = new Thread(() -> {
            try {
                scanCount++;
                doScan(level, centerX, centerZ, radius, msg -> server.execute(() -> feedback.accept(msg)), player);
            } catch (Throwable e) {
                LOGGER.error("Island scan failed: " + e.getMessage(), e);
                server.execute(() -> feedback.accept(
                        Component.literal("Scan error: " + e.getClass().getSimpleName() + " — " + e.getMessage())));
            }
        }, "island-scanner");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Forces a chunk to load on the server thread and waits up to 5s. Returns true
     * if loaded.
     */
    private static boolean forceLoadChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (level.hasChunk(chunkX, chunkZ))
            return true;
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        level.getServer().execute(() -> {
            try {
                level.getChunk(chunkX, chunkZ); // triggers load
                future.complete(true);
            } catch (Exception e) {
                future.complete(false);
            }
        });
        try {
            return Boolean.TRUE.equals(future.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Core scan
    // =========================================================================

    private static void doScan(ServerLevel level, int cx, int cz, int radius,
            Consumer<Component> feedback, ServerPlayer player) throws IOException {
        long t0 = System.currentTimeMillis();
        String startTime = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(t0));
        largeBodyNames = loadLargeBodyNames();
        feedback.accept(
                Component.literal("Scan:" + scanCount + " started at " + startTime + " (radius=" + radius + ")"));

        Set<Long> visited = loadCache(); // grid seeds from previous scans
        Set<Long> newVisited = new HashSet<>(); // body blocks, in-memory only for this run
        Set<Long> processedSeeds = new HashSet<>(); // grid seeds processed this run (saved to cache)
        List<JsonObject> findings = new ArrayList<>();
        Set<String> cachedBodyNamesSeen = new java.util.LinkedHashSet<>(); // unique body names hit from cache

        // Grid-aligned scan bounds
        int x0 = floorGrid(cx - radius), x1 = ceilGrid(cx + radius);
        int z0 = floorGrid(cz - radius), z1 = ceilGrid(cz + radius);

        int seeds = 0;
        int skippedUnloaded = 0;
        int skippedCached = 0;
        int skippedBody = 0;
        int chunksLoaded = 0;
        int xStepCount = 0;
        int totalXSteps = (x1 - x0) / GRID_STEP + 1;
        Set<Long> seenChunks = new HashSet<>();
        for (int x = x0; x <= x1; x += GRID_STEP) {
            xStepCount++;
            String progressMsg = ":" + scanCount + " " + xStepCount + "/" + totalXSteps
                    + " " + x + " fnd=" + findings.size() + " sds=" + seeds
                    + (skippedCached > 0 ? " cache:" + skippedCached : "")
                    // + " body:" + skippedBody
                    // + " ff:" + newVisited.size()
                    + (skippedUnloaded > 0 ? " skpChnks:" + skippedUnloaded : "");
            LOGGER.info(progressMsg);
            feedback.accept(Component.literal(progressMsg));
            for (int z = z0; z <= z1; z += GRID_STEP) {
                if (!inRadius(x, z, cx, cz, radius))
                    continue;
                long key = pack(x, z);
                // Skip if this grid seed was already processed (cache or this run)
                if (visited.contains(key) || processedSeeds.contains(key)) {
                    skippedCached++;
                    String cbn = largeBodyNames.get(key);
                    if (cbn != null)
                        cachedBodyNamesSeen.add(cbn);
                    continue;
                }
                // Skip if inside a body already flood-filled this run
                if (newVisited.contains(key)) {
                    skippedBody++;
                    processedSeeds.add(key); // save to cache so next scan skips it too
                    String bname = largeBodyNames.get(key);
                    if (bname != null)
                        LOGGER.info("Body-skip at ({},{}) -> {}", px(key), pz(key), bname);
                    continue;
                }

                // Force-load chunk if needed (dispatches to server thread)
                int chunkX = x >> 4, chunkZ = z >> 4;
                if (!forceLoadChunk(level, chunkX, chunkZ)) {
                    skippedUnloaded++;
                    LOGGER.info("Chunk load failed/timed out at ({},{})", x, z);
                    continue;
                }
                long chunkKey = pack(chunkX, chunkZ);
                if (seenChunks.add(chunkKey))
                    chunksLoaded++;

                int surfY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfY < 0)
                    continue;
                BlockPos pos = new BlockPos(x, surfY, z);
                seeds++;
                if (seeds % 500 == 0) {
                    LOGGER.info("Scan:{} {} seeds scanned, {} found so far", scanCount, seeds, findings.size());
                }
                processedSeeds.add(key); // mark this grid seed as done

                boolean water = isWater(level, pos);
                boolean coastal = !water && isCoastal(level, x, surfY, z);
                LOGGER.info("SEED ({},{}) y={} -> {}", x, z, surfY,
                        water ? "WATER" : (coastal ? "COASTAL" : "LAND"));

                if (water) {
                    JsonObject result = processWaterSeed(level, x, z, surfY, cx, cz, radius, visited, newVisited);
                    if (result != null) {
                        findings.add(result);
                        feedback.accept(formatFeatureMessage(result));
                        sendScanWaypoints(level, player, result);
                    }
                } else {
                    JsonObject result = processLandSeed(level, x, z, surfY, cx, cz, radius, visited, newVisited);
                    if (result != null) {
                        findings.add(result);
                        feedback.accept(formatFeatureMessage(result));
                        sendScanWaypoints(level, player, result);
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        String endTime = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        long elapsedSec = elapsed / 1000;
        String elapsedStr = elapsedSec >= 60
                ? (elapsedSec / 60) + "m " + (elapsedSec % 60) + "s"
                : elapsedSec + "s";
        feedback.accept(Component.literal(
                "Scan:" + scanCount + " done " + endTime + " (took " + elapsedStr + ") — " + seeds + " scanned, "
                        + skippedCached + " cached, " + skippedBody + " body-skipped, "
                        + chunksLoaded + " chunks, " + findings.size() + " features found.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        if (!cachedBodyNamesSeen.isEmpty()) {
            feedback.accept(Component
                    .literal("Scan:" + scanCount + " cached bodies seen: " + String.join(", ", cachedBodyNamesSeen)));
        }

        visited.addAll(processedSeeds); // only persist grid seeds, not body blocks
        saveCache(visited);
        saveLargeBodyNames();
        appendFindings(findings);
        feedback.accept(Component.literal(
                "Scan:" + scanCount + " Saved findings.json (" + findings.size() + " new) and scan_cache.json ("
                        + visited.size() + " grid seeds)"));
    }

    // =========================================================================
    // Water body processing
    // =========================================================================

    /**
     * From a water seed, flood-fill all connected water.
     */
    private static JsonObject processWaterSeed(ServerLevel level, int sx, int sz, int sy,
            int cx, int cz, int radius,
            Set<Long> visited, Set<Long> newVisited) {
        return floodFillWaterBody(level, sx, sz, sy, cx, cz, radius, visited, newVisited);
    }

    /** Core water body BFS. */
    private static JsonObject floodFillWaterBody(ServerLevel level, int sx, int sz, int sy,
            int cx, int cz, int radius,
            Set<Long> visited, Set<Long> newVisited) {
        long seedKey = pack(sx, sz);
        if (newVisited.contains(seedKey) || visited.contains(seedKey)) {
            String bname = largeBodyNames.get(seedKey);
            lastSkipReason = bname != null
                    ? "Already cached (previously: " + bname + ")"
                    : "Already cached (previously scanned water body)";
            lastSkipBody = null;
            return null;
        }

        Set<Long> deepCoords = new HashSet<>();
        boolean isRiver = false;
        boolean tooBig = false;

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { sx, sy, sz });
        deepCoords.add(seedKey);

        outer: while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int x = cur[0], y = cur[1], z = cur[2];

            if (!tooBig) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBiome(pos).is(BiomeTags.IS_RIVER))
                    isRiver = true;
                if (deepCoords.size() > MAX_BODY_SIZE)
                    tooBig = true;
            }

            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                long nkey = pack(nx, nz);
                if (deepCoords.contains(nkey) || visited.contains(nkey) || newVisited.contains(nkey))
                    continue;
                if (!level.hasChunk(nx >> 4, nz >> 4))
                    continue;
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                if (ny < 0)
                    continue;
                BlockPos npos = new BlockPos(nx, ny, nz);
                if (!isWater(level, npos))
                    continue;
                if (tooBig) {
                    // Body is too big — just mark as visited so later seeds don't pick up orphaned
                    // pockets
                    newVisited.add(nkey);
                } else {
                    deepCoords.add(nkey);
                }
                queue.add(new int[] { nx, ny, nz });
            }
        }

        newVisited.addAll(deepCoords);

        if (isRiver) {
            int[] sc = countGridSeeds(deepCoords, visited);
            String bname = getOrCreateBodyName(deepCoords, deepCoords.size());
            LOGGER.info("Water body at ({},{}) skipped: RIVER '{}' ({} blocks, cached:{} checked:{})", sx, sz, bname,
                    deepCoords.size(), sc[0], sc[1]);
            lastSkipReason = String.format("River '%s'  %,d blocks  cached:%d seeds, checked:%d seeds", bname,
                    deepCoords.size(),
                    sc[0], sc[1]);
            lastSkipBody = new HashSet<>(deepCoords);
            return null; // river — skip
        }
        if (tooBig || deepCoords.size() > OCEAN_THRESHOLD) {
            int[] sc = countGridSeeds(deepCoords, visited);
            String bname = getOrCreateBodyName(deepCoords, deepCoords.size());
            LOGGER.info("Water body at ({},{}) skipped: OCEAN/TOO BIG '{}' ({} blocks, cached:{} checked:{})", sx, sz,
                    bname,
                    deepCoords.size(), sc[0], sc[1]);
            lastSkipReason = String.format("Ocean/too big '%s'  %,d+ blocks  cached:%d seeds, checked:%d seeds", bname,
                    deepCoords.size(), sc[0], sc[1]);
            lastSkipBody = new HashSet<>(deepCoords);
            return null; // ocean — skip
        }
        if (deepCoords.size() < MIN_FEATURE_SIZE) {
            LOGGER.info("Water body at ({},{}) skipped: TOO SMALL ({} blocks)", sx, sz, deepCoords.size());
            lastSkipReason = String.format("Too small  %,d blocks", deepCoords.size());
            lastSkipBody = null;
            return null; // too small
        }

        LOGGER.info("Water body at ({},{}) → LAKE ({} blocks)", sx, sz, deepCoords.size());
        return buildResult("lake", deepCoords);
    }

    // =========================================================================
    // Land body processing (island detection)
    // =========================================================================

    private static JsonObject processLandSeed(ServerLevel level, int sx, int sz, int sy,
            int cx, int cz, int radius,
            Set<Long> visited, Set<Long> newVisited) {
        long seedKey = pack(sx, sz);
        if (newVisited.contains(seedKey) || visited.contains(seedKey)) {
            String bname = largeBodyNames.get(seedKey);
            lastSkipReason = bname != null
                    ? "Already cached (previously: " + bname + ")"
                    : "Already cached (previously scanned land body)";
            lastSkipBody = null;
            return null;
        }

        // Step 1: flood-fill all connected land
        Set<Long> body = new HashSet<>();
        Map<Long, Integer> yMap = new HashMap<>();
        boolean tooBig = false;

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { sx, sy, sz });
        body.add(seedKey);
        yMap.put(seedKey, sy);

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int x = cur[0], y = cur[1], z = cur[2];

            if (body.size() > MAX_BODY_SIZE) {
                tooBig = true;
                break;
            }

            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                long nkey = pack(nx, nz);
                if (body.contains(nkey) || visited.contains(nkey) || newVisited.contains(nkey))
                    continue;
                if (!level.hasChunk(nx >> 4, nz >> 4))
                    continue;
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                if (ny < 0)
                    continue;
                BlockPos npos = new BlockPos(nx, ny, nz);
                if (isWater(level, npos))
                    continue; // water = boundary, don't cross
                body.add(nkey);
                yMap.put(nkey, ny);
                queue.add(new int[] { nx, ny, nz });
            }
        }

        newVisited.addAll(body);

        // Definitely mainland — not an island
        if (tooBig || body.size() < MIN_FEATURE_SIZE) {
            int[] sc = countGridSeeds(body, visited);
            String bname = tooBig ? getOrCreateBodyName(body, body.size()) : null;
            String label = tooBig ? "Mainland '" + bname + "'" : "Too small";
            LOGGER.info("Land body at ({},{}) skipped: {} ({} blocks, cached:{} checked:{})",
                    sx, sz, label, body.size(), sc[0], sc[1]);
            lastSkipReason = String.format("%s  %,d+ blocks  cached:%d seeds, checked:%d seeds",
                    label, body.size(), sc[0], sc[1]);
            lastSkipBody = tooBig ? new HashSet<>(body) : null;
            return null;
        }

        // Step 2: C-erosion — mark land within ±EROSION_DIST of water as coastal
        Set<Long> coastal = new HashSet<>();
        for (long key : body) {
            int x = px(key), z = pz(key);
            Integer y = yMap.get(key);
            if (y == null)
                continue;
            if (isCoastal(level, x, y, z))
                coastal.add(key);
        }

        Set<Long> interior = new HashSet<>(body);
        interior.removeAll(coastal);

        // Step 3a: Small island — entire body is coastal (no interior)
        if (interior.isEmpty()) {
            // Confirm: no non-body land neighbor on any perimeter block
            if (!hasExternalLandNeighbor(level, body, yMap)) {
                LOGGER.info("Land body at ({},{}) → SMALL ISLAND ({} blocks, all coastal)", sx, sz, body.size());
                return buildResult("island", body);
            }
            LOGGER.info("Land body at ({},{}) skipped: CONNECTS TO MAINLAND via coastal ({} blocks)", sx, sz,
                    body.size());
            lastSkipReason = String.format("Connects to mainland  %,d blocks", body.size());
            lastSkipBody = null;
            return null; // connects to mainland
        }

        // Step 3b: Interior exists — BFS through interior only.
        // If any neighbor outside body is non-water land → peninsula → skip.
        if (interiorConnectsToExternalLand(level, body, interior)) {
            LOGGER.info("Land body at ({},{}) skipped: PENINSULA ({} blocks, {} interior)", sx, sz, body.size(),
                    interior.size());
            lastSkipReason = String.format("Peninsula  %,d blocks  %,d interior", body.size(), interior.size());
            lastSkipBody = null;
            return null;
        }

        LOGGER.info("Land body at ({},{}) → ISLAND ({} blocks, {} interior)", sx, sz, body.size(), interior.size());
        return buildResult("island", body);
    }

    /**
     * Returns true if any interior block's BFS (through interior only) can reach
     * a non-body, non-water block — meaning this landmass is a peninsula.
     */
    private static boolean interiorConnectsToExternalLand(ServerLevel level,
            Set<Long> body, Set<Long> interior) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        Long first = interior.iterator().next();
        queue.add(first);
        seen.add(first);

        while (!queue.isEmpty()) {
            long key = queue.poll();
            int x = px(key), z = pz(key);
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                long nkey = pack(nx, nz);
                if (seen.contains(nkey))
                    continue;
                seen.add(nkey);

                if (!body.contains(nkey)) {
                    // Outside this body — check if it's land
                    if (!level.hasChunk(nx >> 4, nz >> 4))
                        continue;
                    int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                    if (ny < 0)
                        continue;
                    BlockPos npos = new BlockPos(nx, ny, nz);
                    if (!isWater(level, npos))
                        return true; // external land → peninsula
                } else if (interior.contains(nkey)) {
                    queue.add(nkey); // only expand through interior (non-coastal)
                }
                // coastal neighbor inside body: stop expanding (don't add to queue)
            }
        }
        return false;
    }

    /**
     * Returns true if any perimeter block of body has a 4-directional land neighbor
     * not in body.
     */
    private static boolean hasExternalLandNeighbor(ServerLevel level, Set<Long> body, Map<Long, Integer> yMap) {
        for (long key : body) {
            int x = px(key), z = pz(key);
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                long nkey = pack(nx, nz);
                if (body.contains(nkey))
                    continue;
                if (!level.hasChunk(nx >> 4, nz >> 4))
                    continue;
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                if (ny < 0)
                    continue;
                BlockPos npos = new BlockPos(nx, ny, nz);
                if (!isWater(level, npos))
                    return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Erosion helpers
    // =========================================================================

    /**
     * Land block is "coastal" (C) if it has a water neighbor within EROSION_DIST.
     */
    private static boolean isCoastal(ServerLevel level, int x, int y, int z) {
        for (int dx = -EROSION_DIST; dx <= EROSION_DIST; dx++) {
            for (int dz = -EROSION_DIST; dz <= EROSION_DIST; dz++) {
                if (dx == 0 && dz == 0)
                    continue;
                int nx = x + dx, nz = z + dz;
                if (!level.hasChunk(nx >> 4, nz >> 4))
                    continue;
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                if (ny < 0)
                    continue;
                if (isWater(level, new BlockPos(nx, ny, nz)))
                    return true;
            }
        }
        return false;
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    // =========================================================================
    // Result building
    // =========================================================================

    private static JsonObject buildResult(String type, Set<Long> coords) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        long sumX = 0, sumZ = 0;
        for (long key : coords) {
            int x = px(key), z = pz(key);
            sumX += x;
            sumZ += z;
            if (x < minX)
                minX = x;
            if (x > maxX)
                maxX = x;
            if (z < minZ)
                minZ = z;
            if (z > maxZ)
                maxZ = z;
        }
        int n = coords.size();
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        JsonObject center = new JsonObject();
        center.addProperty("x", (int) (sumX / n));
        center.addProperty("z", (int) (sumZ / n));
        obj.add("center", center);
        JsonObject bb = new JsonObject();
        bb.addProperty("min_x", minX);
        bb.addProperty("min_z", minZ);
        bb.addProperty("max_x", maxX);
        bb.addProperty("max_z", maxZ);
        obj.add("bounding_box", bb);
        obj.addProperty("block_count", n);
        obj.addProperty("partial", false);
        return obj;
    }

    private static Component formatFeatureMessage(JsonObject result) {
        String type = result.get("type").getAsString();
        JsonObject center = result.getAsJsonObject("center");
        JsonObject bb = result.getAsJsonObject("bounding_box");
        int cx = center.get("x").getAsInt();
        int cz = center.get("z").getAsInt();
        int blocks = result.get("block_count").getAsInt();
        int sizeX = bb.get("max_x").getAsInt() - bb.get("min_x").getAsInt();
        int sizeZ = bb.get("max_z").getAsInt() - bb.get("min_z").getAsInt();
        int chunkX = (int) Math.ceil(sizeX / 16.0);
        int chunkZ = (int) Math.ceil(sizeZ / 16.0);
        // Generate name once and store on result so sendScanWaypoints uses the same
        // name
        if (!result.has("name")) {
            result.addProperty("name", randomWpName(blocks));
        }
        String wpName = result.get("name").getAsString();
        LOGGER.info("Feature '{}' {} at ({},{})  {}x{} chunks  {} blocks", wpName, type, cx, cz, chunkX, chunkZ,
                blocks);
        Style style = type.equals("lake")
                ? Style.EMPTY.withColor(ChatFormatting.AQUA)
                : Style.EMPTY.withColor(TextColor.fromRgb(0xD2B48C)); // tan
        Style coordStyle = style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + cx + " ~ " + cz));
        return Component.empty()
                .append(Component.literal("Found " + type + " '").withStyle(style))
                .append(Component.literal(wpName).withStyle(style.withBold(true)))
                .append(Component.literal("' at ").withStyle(style))
                .append(Component.literal(cx + ", " + cz).withStyle(coordStyle))
                .append(Component.literal("  size: " + chunkX + "x" + chunkZ + " chnks  blcks: " + blocks)
                        .withStyle(style));
    }

    // =========================================================================
    // Point debug scan
    // =========================================================================

    public static void scanPointAsync(ServerLevel level, int px, int pz, Consumer<Component> feedback,
            ServerPlayer player) {
        MinecraftServer server = level.getServer();
        scanCount++;
        Thread t = new Thread(() -> {
            try {
                doScanPoint(level, px, pz, msg -> server.execute(() -> feedback.accept(msg)), player);
            } catch (Throwable e) {
                LOGGER.error("scanPoint failed: " + e.getMessage(), e);
                server.execute(() -> feedback.accept(
                        Component.literal("scanPoint error: " + e.getMessage())));
            }
        }, "island-scanner-point");
        t.setDaemon(true);
        t.start();
    }

    private static void doScanPoint(ServerLevel level, int sx, int sz, Consumer<Component> feedback,
            ServerPlayer player) {
        // Round to nearest grid point and check cache first
        int gx = Math.round((float) sx / GRID_STEP) * GRID_STEP;
        int gz = Math.round((float) sz / GRID_STEP) * GRID_STEP;
        Set<Long> visited = loadCache();
        largeBodyNames = loadLargeBodyNames();
        long gridKey = pack(gx, gz);
        if (visited.contains(gridKey)) {
            String bname = largeBodyNames.get(gridKey);
            String cached = bname != null ? "'" + bname + "'" : "a previously scanned body";
            feedback.accept(Component
                    .literal("Scan:" + scanCount + " Grid point (" + gx + "," + gz + ") cached — part of " + cached));
            feedback.accept(Component.literal("Scan:" + scanCount + " === done ==="));
            return;
        }
        feedback.accept(Component.literal(
                "Scan:" + scanCount + " === scanPoint (" + sx + "," + sz + ") grid=(" + gx + "," + gz + ") ==="));
        forceLoadChunk(level, sx >> 4, sz >> 4);
        int surfY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) - 1;
        if (surfY < 0) {
            feedback.accept(Component.literal("No surface block."));
            return;
        }

        BlockPos pos = new BlockPos(sx, surfY, sz);
        boolean water = isWater(level, pos);
        boolean coastal = !water && isCoastal(level, sx, surfY, sz);
        feedback.accept(Component.literal(
                "y=" + surfY + " block=" + level.getBlockState(pos).getBlock()
                        + "  " + (water ? "WATER" : (coastal ? "COASTAL" : "LAND"))));

        // Run the real flood-fill from this seed
        Set<Long> newVisited = new HashSet<>();
        JsonObject result;
        if (water) {
            result = processWaterSeed(level, sx, sz, surfY, sx, sz, Integer.MAX_VALUE, visited, newVisited);
        } else {
            result = processLandSeed(level, sx, sz, surfY, sx, sz, Integer.MAX_VALUE, visited, newVisited);
        }

        if (result == null) {
            feedback.accept(Component.literal("[Scan:" + scanCount + "] " + lastSkipReason));
            if (lastSkipBody != null && !lastSkipBody.isEmpty()) {
                sendCornerWaypoints(level, player, lastSkipBody, lastSkipReason);
            }
        } else {
            feedback.accept(formatFeatureMessage(result));
            sendScanWaypoints(level, player, result);
        }
        feedback.accept(Component.literal("Scan:" + scanCount + " === done ==="));
    }

    private static void sendCornerWaypoints(ServerLevel level, ServerPlayer player, Set<Long> body, String label) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : body) {
            int x = px(key), z = pz(key);
            if (x < minX)
                minX = x;
            if (x > maxX)
                maxX = x;
            if (z < minZ)
                minZ = z;
            if (z > maxZ)
                maxZ = z;
        }
        int color = (RNG.nextInt(256) << 16) | (RNG.nextInt(256) << 8) | RNG.nextInt(256);
        String groupName = "Large-" + scanCount;
        int[][] corners = { { minX, minZ }, { maxX, minZ }, { minX, maxZ }, { maxX, maxZ } };
        String[] wpLabels = { "NW", "NE", "SW", "SE" };
        List<ScanWaypointsPacket.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(new ScanWaypointsPacket.Entry(
                    groupName + "-" + wpLabels[i], corners[i][0], 64, corners[i][1], color));
        }
        level.getServer().execute(() -> EDBMessages.sendToPlayer(new ScanWaypointsPacket(entries), player));
    }

    // =========================================================================

    public static String clearCache(ServerLevel level, ServerPlayer player) {
        String cacheMsg = clearCache();
        level.getServer().execute(() -> EDBMessages.sendToPlayer(new ScanWaypointsPacket(), player));
        return cacheMsg + " Scan waypoints cleared.";
    }

    public static String clearCache() {
        try {
            Files.deleteIfExists(cacheFile());
            LOGGER.info("scan_cache.json deleted. findings.json is NOT cleared.");
            return "Cache cleared (scan_cache.json). findings.json is kept. Next scan will start fresh.";
        } catch (IOException e) {
            LOGGER.error("Failed to delete scan_cache.json: " + e.getMessage());
            return "Failed to clear cache — see server log.";
        }
    }

    private static void sendScanWaypoints(ServerLevel level, ServerPlayer player, JsonObject result) {
        JsonObject bb = result.getAsJsonObject("bounding_box");
        int minX = bb.get("min_x").getAsInt(), maxX = bb.get("max_x").getAsInt();
        int minZ = bb.get("min_z").getAsInt(), maxZ = bb.get("max_z").getAsInt();
        int color = (RNG.nextInt(256) << 16) | (RNG.nextInt(256) << 8) | RNG.nextInt(256);
        // Reuse the name already set by formatFeatureMessage, or generate one
        int blockCount = result.get("block_count").getAsInt();
        String groupName = result.has("name") ? result.get("name").getAsString() : randomWpName(blockCount);
        // Minecraft: -X=west +X=east -Z=north +Z=south
        // minX=west maxX=east minZ=north maxZ=south
        int[][] corners = { { minX, minZ }, { maxX, minZ }, { minX, maxZ }, { maxX, maxZ } };
        String[] labels = { "NW", "NE", "SW", "SE" };
        List<ScanWaypointsPacket.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(new ScanWaypointsPacket.Entry(
                    groupName + "-" + labels[i], corners[i][0], 64, corners[i][1], color));
        }
        level.getServer().execute(() -> EDBMessages.sendToPlayer(new ScanWaypointsPacket(entries), player));
    }

    private static Set<Long> loadCache() {
        Set<Long> set = new HashSet<>();
        Path path = cacheFile();
        if (!Files.exists(path))
            return set;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("visited");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                set.add(pack(o.get("x").getAsInt(), o.get("z").getAsInt()));
            }
            LOGGER.info("Loaded " + set.size() + " cached coords from scan_cache.json");
        } catch (Exception e) {
            LOGGER.error("Failed to load scan_cache.json: " + e.getMessage());
        }
        return set;
    }

    private static void saveCache(Set<Long> visited) throws IOException {
        JsonArray arr = new JsonArray();
        for (long key : visited) {
            JsonObject o = new JsonObject();
            o.addProperty("x", px(key));
            o.addProperty("z", pz(key));
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("visited", arr);
        Files.writeString(cacheFile(), new GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8);
    }

    private static void appendFindings(List<JsonObject> newResults) throws IOException {
        List<JsonObject> all = new ArrayList<>();
        Map<String, JsonObject> seen = new java.util.LinkedHashMap<>();
        Path path = findingsFile();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject obj = e.getAsJsonObject();
                    seen.put(findingDedupeKey(obj), obj);
                }
            } catch (Exception ignored) {
            }
        }
        for (JsonObject obj : newResults) {
            String key = findingDedupeKey(obj);
            if (seen.containsKey(key)) {
                // Merge name into existing entry if it has one
                if (obj.has("name") && !seen.get(key).has("name")) {
                    seen.get(key).addProperty("name", obj.get("name").getAsString());
                }
            } else {
                seen.put(key, obj);
            }
        }
        JsonArray arr = new JsonArray();
        seen.values().forEach(arr::add);
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(arr),
                StandardCharsets.UTF_8);
    }

    private static String findingDedupeKey(JsonObject obj) {
        String type = obj.get("type").getAsString();
        JsonObject center = obj.getAsJsonObject("center");
        return type + ":" + center.get("x").getAsInt() + ":" + center.get("z").getAsInt();
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    private static boolean inRadius(int x, int z, int cx, int cz, int radius) {
        long dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static int floorGrid(int v) {
        return (int) Math.floor((double) v / GRID_STEP) * GRID_STEP;
    }

    private static int ceilGrid(int v) {
        return (int) Math.ceil((double) v / GRID_STEP) * GRID_STEP;
    }

    /**
     * Returns the existing name for this large body (checked via grid seeds), or
     * generates and persists a new one.
     */
    private static String getOrCreateBodyName(Set<Long> coords, int blockCount) {
        for (long key : coords) {
            if (px(key) % GRID_STEP == 0 && pz(key) % GRID_STEP == 0) {
                String existing = largeBodyNames.get(key);
                if (existing != null)
                    return existing;
            }
        }
        String name = randomWpName(blockCount);
        for (long key : coords) {
            if (px(key) % GRID_STEP == 0 && pz(key) % GRID_STEP == 0)
                largeBodyNames.put(key, name);
        }
        return name;
    }

    private static Map<Long, String> loadLargeBodyNames() {
        Map<Long, String> map = new HashMap<>();
        Path path = largeBodiesFile();
        if (!Files.exists(path))
            return map;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String name = o.get("name").getAsString();
                for (JsonElement se : o.getAsJsonArray("seeds")) {
                    JsonObject s = se.getAsJsonObject();
                    map.put(pack(s.get("x").getAsInt(), s.get("z").getAsInt()), name);
                }
            }
            LOGGER.info("Loaded {} large body name entries from large_body_names.json", map.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load large_body_names.json: " + e.getMessage());
        }
        return map;
    }

    private static void saveLargeBodyNames() {
        if (largeBodyNames.isEmpty())
            return;
        // Group coords by name
        Map<String, List<long[]>> byName = new java.util.LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : largeBodyNames.entrySet()) {
            byName.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(new long[] { px(entry.getKey()), pz(entry.getKey()) });
        }
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, List<long[]>> entry : byName.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", entry.getKey());
            JsonArray seeds = new JsonArray();
            for (long[] xz : entry.getValue()) {
                JsonObject s = new JsonObject();
                s.addProperty("x", (int) xz[0]);
                s.addProperty("z", (int) xz[1]);
                seeds.add(s);
            }
            o.add("seeds", seeds);
            arr.add(o);
        }
        try {
            Files.writeString(largeBodiesFile(), new GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save large_body_names.json: " + e.getMessage());
        }
    }

    /**
     * Count grid-aligned seeds in coords split by [cached (in visited), checked
     * (not in visited)].
     */
    private static int[] countGridSeeds(Set<Long> coords, Set<Long> visited) {
        int cached = 0, checked = 0;
        for (long key : coords) {
            int x = px(key), z = pz(key);
            if (x % GRID_STEP == 0 && z % GRID_STEP == 0) {
                if (visited.contains(key))
                    cached++;
                else
                    checked++;
            }
        }
        return new int[] { cached, checked };
    }
}
