package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.util.RouteDiscovery;
import com.falazar.farmupcraft.util.RouteDisplay;
import com.falazar.farmupcraft.util.RouteSurvey;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.time.Instant;
import java.util.*;

public final class VillageRoads {
    public static final Codec<RouteDiscovery> CODEC = Codec.STRING.listOf().xmap(RouteDiscovery::decode,
            RouteDiscovery::encode);

    private VillageRoads() {
    }

    private static net.minecraft.server.MinecraftServer activeServer;
    private static final Map<String, RouteSurvey.Result> processed = new HashMap<>();

    public static void discover(ServerLevel level, UUID villageId, RouteSurvey.Kind kind, RouteSurvey.Saved saved) {
        if (activeServer != level.getServer()) {
            activeServer = level.getServer();
            processed.clear();
        }
        String key = VillageRouteSurveys.key(villageId, kind);
        if (processed.get(key) == saved.latest())
            return;
        var village = ModEvents.getVillageDatabase(level).getData(villageId);
        if (village == null || saved.latest().segments().isEmpty())
            return;
        var players = ModEvents.getPlayerDatabase(level);
        Set<String> members = new TreeSet<>();
        for (UUID id : players.getKeys()) {
            var player = players.getData(id);
            if (player != null && villageId.equals(player.getHomeVillageUUID()))
                members.add(id.toString());
        }
        var db = ModEvents.getRouteDiscoveryDatabase(level);
        var previous = db.getData(key);
        var state = previous == null ? RouteDiscovery.empty() : previous;
        var update = state.discover(saved.latest(), village.getName(), kind, members, saved.lastChecked());
        if (!update.state().equals(state))
            db.putData(key, update.state());
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            if (members.contains(online.getUUID().toString()))
                deliver(online);
        }
        processed.put(key, saved.latest());
    }

    public static void clear() {
        processed.clear();
        activeServer = null;
    }

    public static void deliver(ServerPlayer player) {
        var level = player.level().getServer().overworld();
        var data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        if (data == null || data.getHomeVillageUUID() == null)
            return;
        var db = ModEvents.getRouteDiscoveryDatabase(level);
        String member = player.getUUID().toString();
        for (RouteSurvey.Kind kind : RouteSurvey.Kind.values()) {
            String key = VillageRouteSurveys.key(data.getHomeVillageUUID(), kind);
            RouteDiscovery state = db.getData(key);
            if (state == null)
                continue;
            List<String> messages = state.pending().getOrDefault(member, List.of());
            for (String message : messages)
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
            if (!messages.isEmpty())
                db.putData(key, state.delivered(member));
        }
    }

    public static int show(CommandSourceStack source, String name, int page, RouteSurvey.Kind kind) {
        var villages = ModEvents.getVillageDatabase(source.getLevel());
        VillageData village;
        if (name != null) {
            village = villages.getValues().stream().filter(v -> v.getName().equalsIgnoreCase(name)).findFirst()
                    .orElse(null);
        } else {
            var player = source.getPlayer();
            var data = player == null ? null : ModEvents.getPlayerDatabase(source.getLevel()).getData(player.getUUID());
            village = data == null || data.getHomeVillageUUID() == null ? null
                    : villages.getData(data.getHomeVillageUUID());
        }
        String command = kind == RouteSurvey.Kind.ROAD ? "roads" : "railroads";
        if (village == null) {
            source.sendFailure(Component.literal("No village found. Use /village " + command + " <villageName>."));
            return 0;
        }
        var saved = ModEvents.getRouteSurveyDatabase(source.getLevel())
                .getData(VillageRouteSurveys.key(village.getUUID(), kind));
        if (saved == null) {
            source.sendSystemMessage(
                    Component.literal(village.getName() + ": " + command + " have not been scanned yet."));
            return 1;
        }
        var center = village.getPosition().getMiddleBlockPosition(64);
        var segments = filterSegments(source.getLevel(), village, kind,
                RouteSurvey.coalesceSegments(saved.latest().segments())).stream()
                .map(s -> RouteSurvey.orientOutward(s, center.getX(), center.getZ()))
                .sorted(Comparator.comparing(RouteSurvey.Segment::id)).toList();
        double totalLength = segments.stream().mapToDouble(RouteSurvey.Segment::length).sum();
        int pages = Math.max(1, (segments.size() + RouteDisplay.PAGE_SIZE - 1) / RouteDisplay.PAGE_SIZE);
        if (page < 1 || page > pages) {
            source.sendFailure(Component.literal("Page must be between 1 and " + pages + "."));
            return 0;
        }
        source.sendSystemMessage(Component.literal(village.getName() + " " + command + " | " + segments.size()
                + " " + command + " (" + segments.size() + " segments) | " + Math.round(totalLength) + " blocks | page "
                + page + "/" + pages)
                .withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal(saved.status() + " | last checked "
                + (saved.lastChecked() == 0 ? "never" : Instant.ofEpochMilli(saved.lastChecked()).toString())));
        if (segments.isEmpty())
            source.sendSystemMessage(Component.literal("No qualifying " + command + " in this saved survey."));
        int first = (page - 1) * RouteDisplay.PAGE_SIZE;
        for (int i = first; i < Math.min(segments.size(), first + RouteDisplay.PAGE_SIZE); i++) {
            var segment = segments.get(i);
            source.sendSystemMessage(Component.literal(RouteDisplay.segment(i + 1, segment, kind,
                    contact -> contactName(source.getLevel(), contact))));
        }
        if (!saved.latest().complete() && saved.lastSuccess() != 0)
            source.sendSystemMessage(Component.literal("Partial results; previous complete total retained: "
                    + Math.round(saved.successful().length()) + " blocks (stale)."));
        if (page < pages)
            source.sendSystemMessage(Component.literal("Next: /village " + command + " \""
                    + village.getName() + "\" " + (page + 1)));
        return 1;
    }

    public static List<RouteSurvey.Segment> filterSegments(ServerLevel level, VillageData village,
            RouteSurvey.Kind kind, List<RouteSurvey.Segment> segments) {
        if (kind != RouteSurvey.Kind.ROAD || segments.isEmpty())
            return segments;
        var world = new com.falazar.farmupcraft.util.RouteSurveyWorld(level, village, kind, false);
        List<RouteSurvey.Segment> filtered = new ArrayList<>();
        for (RouteSurvey.Segment s : segments) {
            int checked = 0, valid = 0;
            for (RouteSurvey.Point p : s.points()) {
                if (level.getChunkSource().getChunkNow(p.x() >> 4, p.z() >> 4) != null) {
                    checked++;
                    if (world.isValidRoadPoint(p))
                        valid++;
                }
            }
            if (checked == 0 || valid * 2 >= checked) {
                filtered.add(s);
            }
        }
        return List.copyOf(filtered);
    }

    private static String contactName(ServerLevel level, String contact) {
        if (contact.startsWith("ruin:")) {
            String id = contact.substring(5).split("@", 2)[0];
            return "surface ruin " + id;
        }
        if (contact.startsWith("village:")) {
            String id = contact.substring(8);
            var village = ModEvents.getVillageDatabase(level).getData(UUID.fromString(id));
            return "village " + (village == null ? id : village.getName());
        }
        if (contact.startsWith("plot:")) {
            String[] fields = contact.substring(5).split("/", 3);
            var village = ModEvents.getVillageDatabase(level).getData(UUID.fromString(fields[0]));
            long chunk = Long.parseLong(fields[1]);
            return "claimed " + fields[2] + " plot [chunk " + (int) chunk + ", " + (int) (chunk >> 32) + "] ("
                    + (village == null ? fields[0] : village.getName()) + ")";
        }
        throw new IllegalArgumentException("Unknown saved route connection: " + contact);
    }
}
