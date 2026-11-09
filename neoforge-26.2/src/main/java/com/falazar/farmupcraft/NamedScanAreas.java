package com.falazar.farmupcraft;

import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "farmupcraft")
public final class NamedScanAreas {
    record Area(String name, String type, int x, int z, int minX, int maxX, int minZ, int maxZ) {
        boolean contains(int px, int pz) {
            return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
        }
        long size() { return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1); }
        String title() { return ScanFeatureRules.displayName(name) + " " + (type.equals("lake") ? "Lake" : "Island"); }
        String key() { return type + ":" + minX + ":" + maxX + ":" + minZ + ":" + maxZ; }
    }
    private record Visit(ServerLevel level, String area, long lastTitle) {}
    private static final Map<ServerLevel, List<Area>> AREAS = new IdentityHashMap<>();
    private static final Map<UUID, Visit> VISITS = new HashMap<>();

    private NamedScanAreas() {}

    static List<Area> parse(JsonArray findings) {
        List<Area> areas = new ArrayList<>();
        for (var value : findings) {
            JsonObject result = value.getAsJsonObject();
            String type = result.get("type").getAsString();
            if (!type.equals("lake") && !type.equals("island")) continue;
            if (!ScanFeatureRules.largeEnough(type, result.get("block_count").getAsInt())) continue;
            if (!result.has("name")) IslandScanManager.formatFeatureMessage(result);
            var box = result.getAsJsonObject("bounding_box");
            var center = result.getAsJsonObject("center");
            Area area = new Area(result.get("name").getAsString(), type,
                    center.get("x").getAsInt(), center.get("z").getAsInt(),
                    box.get("min_x").getAsInt(), box.get("max_x").getAsInt(),
                    box.get("min_z").getAsInt(), box.get("max_z").getAsInt());
            if (area.name().isBlank() || area.minX() > area.maxX() || area.minZ() > area.maxZ())
                throw new IllegalArgumentException("Invalid named scan area: " + area);
            new ScanWaypointsPacket.Entry(area.title(), area.x(), 64, area.z(), 0x55FFFF);
            areas.add(area);
        }
        areas.sort(Comparator.comparingLong(Area::size).thenComparing(Area::key));
        return List.copyOf(areas);
    }

    static Area at(List<Area> areas, int x, int z) {
        return areas.stream().filter(area -> area.contains(x, z)).findFirst().orElse(null);
    }

    static boolean shouldAnnounce(String current, String previous, long elapsed) {
        return !current.isEmpty() && !current.equals(previous) && elapsed >= 400;
    }

    static void publish(ServerLevel level, JsonArray findings) {
        AREAS.put(level, parse(findings));
        for (var player : level.players()) sync(player);
    }

    private static void sync(ServerPlayer player) {
        var areas = AREAS.get(player.level());
        if (areas == null) return;
        EDBMessages.sendToPlayer(new ScanWaypointsPacket(), player);
        List<ScanWaypointsPacket.Entry> entries = new ArrayList<>();
        for (var area : areas) {
            var chunk = player.level().getChunkSource().getChunkNow(area.x() >> 4, area.z() >> 4);
            int y = chunk == null ? 64 : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    area.x() & 15, area.z() & 15) + 1;
            entries.add(new ScanWaypointsPacket.Entry(area.title(), area.x(), y, area.z(),
                    area.type().equals("lake") ? 0x55FFFF : 0xD2B48C));
            if (entries.size() == ScanWaypointsPacket.MAX_ENTRIES) {
                EDBMessages.sendToPlayer(new ScanWaypointsPacket(entries), player);
                entries.clear();
            }
        }
        if (!entries.isEmpty()) EDBMessages.sendToPlayer(new ScanWaypointsPacket(entries), player);
    }

    private static void ensure(ServerLevel level) {
        if (!AREAS.containsKey(level)) {
            AREAS.put(level, List.of());
            IslandScanManager.loadDisplayAsync(level);
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensure(player.level());
            sync(player);
        }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VISITS.remove(player.getUUID());
            ensure(player.level());
            sync(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        VISITS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AREAS.remove(level);
            VISITS.values().removeIf(visit -> visit.level() == level);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        AREAS.keySet().removeIf(level -> level.getServer() == event.getServer());
        VISITS.values().removeIf(visit -> visit.level().getServer() == event.getServer());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            if (level.getGameTime() % 20 != 0) continue;
            ensure(level);
            var pos = player.blockPosition();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15);
            Area area = Math.abs((long) pos.getY() - surface) <= 16
                    ? at(AREAS.get(level), pos.getX(), pos.getZ()) : null;
            String key = area == null ? "" : area.key();
            Visit previous = VISITS.get(player.getUUID());
            long now = level.getGameTime();
            long last = previous == null || previous.level() != level ? now - 400 : previous.lastTitle();
            String previousKey = previous == null || previous.level() != level ? "" : previous.area();
            if (shouldAnnounce(key, previousKey, now - last)) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(area.title())));
                last = now;
            }
            VISITS.put(player.getUUID(), new Visit(level, key, last));
        }
    }
}
