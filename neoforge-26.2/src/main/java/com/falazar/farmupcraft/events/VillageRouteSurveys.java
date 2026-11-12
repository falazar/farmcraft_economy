package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.util.RouteSurvey;
import com.falazar.farmupcraft.util.RouteSurveyJson;
import com.falazar.farmupcraft.util.RouteSurveyWorld;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class VillageRouteSurveys {
    public static final Codec<RouteSurvey.Saved> CODEC = Codec.STRING.listOf().xmap(RouteSurveyJson::decodeChunks,
            RouteSurveyJson::encodeChunks);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static MinecraftServer activeServer;
    private static ExecutorService worker;
    private static RouteSurvey job;
    private static String jobKey;
    private static int checkTicks;
    private static final Set<String> forcedRequests = new LinkedHashSet<>();

    private VillageRouteSurveys() {
    }

    public static String key(UUID village, RouteSurvey.Kind kind) {
        return village + "/" + kind;
    }

    public static boolean request(ServerLevel level, VillageData village, RouteSurvey.Kind kind) {
        if (village == null || !village.isBought())
            return false;
        activate(level.getServer());
        String requestedKey = key(village.getUUID(), kind);
        return !requestedKey.equals(jobKey) && forcedRequests.add(requestedKey);
    }

    public static void tick(MinecraftServer server) {
        activate(server);
        ServerLevel level = server.overworld();
        if (level == null)
            return;
        var db = ModEvents.getRouteSurveyDatabase(level);
        if (job != null) {
            try {
                if (job.advance(8)) {
                    RouteSurvey.Saved saved = db.getData(jobKey).finish(job.result(), System.currentTimeMillis());
                    db.putData(jobKey, saved);
                    publishDiscovery(level, jobKey, saved);
                    LOGGER.info("Route survey {}: {}, {} segments, {} blocks", jobKey, saved.status(),
                            saved.latest().segments().size(), Math.round(saved.latest().length()));
                    job = null;
                    jobKey = null;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Route survey {} failed; preserving previous successful measurement", jobKey, exception);
                var previous = db.getData(jobKey);
                db.putData(jobKey,
                        previous.finish(new RouteSurvey.Result(List.of(), Set.of("survey failed; see server log")),
                                System.currentTimeMillis()));
                job = null;
                jobKey = null;
            }
            return;
        }
        if (!forcedRequests.isEmpty()) {
            String requestedKey = forcedRequests.iterator().next();
            forcedRequests.remove(requestedKey);
            String[] parts = requestedKey.split("/", 2);
            VillageData village = ModEvents.getVillageDatabase(level).getData(UUID.fromString(parts[0]));
            if (village != null && village.isBought()) {
                startSurvey(level, village, RouteSurvey.Kind.valueOf(parts[1]), true, System.currentTimeMillis());
                return;
            }
        }
        if (++checkTicks < 200)
            return;
        checkTicks = 0;
        long now = System.currentTimeMillis();
        for (VillageData village : ModEvents.getVillageDatabase(level).getValues().stream()
                .sorted(Comparator.comparing(v -> v.getUUID().toString())).toList()) {
            if (!village.isBought())
                continue;
            for (RouteSurvey.Kind kind : RouteSurvey.Kind.values()) {
                String key = key(village.getUUID(), kind);
                RouteSurvey.Saved saved = db.getOrCreate(key, RouteSurvey.Saved::empty);
                publishDiscovery(level, key, saved);
                if (!saved.due(now))
                    continue;
                startSurvey(level, village, kind, false, now);
                return;
            }
        }
    }

    private static void activate(MinecraftServer server) {
        if (activeServer == server)
            return;
        if (worker != null)
            worker.shutdownNow();
        activeServer = server;
        job = null;
        jobKey = null;
        checkTicks = 0;
        forcedRequests.clear();
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "farmupcraft-route-measurement");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void startSurvey(ServerLevel level, VillageData village, RouteSurvey.Kind kind,
            boolean forced, long now) {
        var db = ModEvents.getRouteSurveyDatabase(level);
        String surveyKey = key(village.getUUID(), kind);
        RouteSurvey.Saved saved = db.getOrCreate(surveyKey, RouteSurvey.Saved::empty);
        db.putData(surveyKey, forced ? saved.beginForced(now) : saved.begin(now));
        jobKey = surveyKey;
        try {
            job = new RouteSurvey(new RouteSurveyWorld(level, village, kind, kind == RouteSurvey.Kind.ROAD), kind,
                    level.dimension().toString() + "/" + village.getUUID(), worker);
            LOGGER.info("Starting {} {} survey for {}", forced ? "requested" : "automatic", kind, village.getName());
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot start route survey {}; preserving previous successful measurement", surveyKey,
                    exception);
            db.putData(surveyKey, db.getData(surveyKey).finish(
                    new RouteSurvey.Result(List.of(), Set.of("survey setup failed; see server log")), now));
            jobKey = null;
        }
    }

    public static void stop(MinecraftServer server) {
        if (activeServer != server)
            return;
        if (worker != null)
            worker.shutdownNow();
        worker = null;
        job = null;
        jobKey = null;
        forcedRequests.clear();
        activeServer = null;
        VillageRoads.clear();
    }

    private static void publishDiscovery(ServerLevel level, String key, RouteSurvey.Saved saved) {
        String[] parts = key.split("/", 2);
        try {
            VillageRoads.discover(level, UUID.fromString(parts[0]), RouteSurvey.Kind.valueOf(parts[1]), saved);
        } catch (RuntimeException error) {
            LOGGER.error("Cannot publish village route discoveries for {}; will retry without rescanning", key, error);
        }
    }

    public static int show(CommandSourceStack source, String name) {
        var villages = ModEvents.getVillageDatabase(source.getLevel());
        VillageData village;
        if (name != null) {
            village = villages.getValues().stream().filter(v -> v.getName().equalsIgnoreCase(name)).findFirst()
                    .orElse(null);
        } else {
            var player = source.getPlayer();
            var data = player == null ? null : ModEvents.getPlayerDatabase().getData(player.getUUID());
            village = data == null ? null : villages.getData(data.getHomeVillageUUID());
        }
        if (village == null) {
            source.sendFailure(Component.literal("No village found. Use /village routes <villageName>."));
            return 0;
        }
        source.sendSystemMessage(
                Component.literal("Saved routes for " + village.getName() + " (automatic, every 24 real hours):"));
        for (RouteSurvey.Kind kind : RouteSurvey.Kind.values()) {
            var saved = ModEvents.getRouteSurveyDatabase(source.getLevel()).getData(key(village.getUUID(), kind));
            if (saved == null) {
                source.sendSystemMessage(Component.literal(kind + ": not checked yet."));
                continue;
            }
            var result = saved.latest();
            var segments = VillageRoads.filterSegments(source.getLevel(), village, kind,
                    RouteSurvey.coalesceSegments(result.segments()));
            double inside = segments.stream().mapToDouble(RouteSurvey.Segment::inside).sum();
            double outside = segments.stream().mapToDouble(RouteSurvey.Segment::outside).sum();
            long connections = segments.stream().flatMap(s -> s.claims().stream())
                    .filter(s -> s.startsWith("village:")).distinct().count();
            long plots = segments.stream().flatMap(s -> s.claims().stream())
                    .filter(s -> s.startsWith("plot:")).distinct().count();
            long ruins = segments.stream().flatMap(s -> s.ruins().stream()).distinct().count();
            source.sendSystemMessage(Component.literal(kind + ": " + saved.status() + "; "
                    + segments.size() + " segments; " + Math.round(inside + outside)
                    + " blocks (" + Math.round(inside) + " inside / " + Math.round(outside) + " outside); "
                    + connections + " other villages, " + plots + " claimed plots, " + ruins + " surface ruins."));
            source.sendSystemMessage(Component.literal("Last checked: " + datetime(saved.lastChecked())
                    + "; last complete: " + datetime(saved.lastSuccess()) + "; next eligible: "
                    + datetime(saved.lastChecked() == 0 ? 0 : saved.lastChecked() + RouteSurvey.INTERVAL_MILLIS)));
            if (!result.complete() && saved.lastSuccess() != 0)
                source.sendSystemMessage(Component.literal("Previous complete measurement retained (stale): "
                        + Math.round(saved.successful().length()) + " blocks."));
        }
        return 1;
    }

    private static String datetime(long millis) {
        return millis == 0 ? "never" : Instant.ofEpochMilli(millis).toString();
    }
}
