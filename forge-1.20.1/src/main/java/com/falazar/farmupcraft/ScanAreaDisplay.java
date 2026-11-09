package com.falazar.farmupcraft;

import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/** Server-owned discoveries; JourneyMap is optional and titles use vanilla packets. */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScanAreaDisplay {
    private static final Map<ServerLevel, List<Area>> AREAS = new IdentityHashMap<>();
    private static final Map<UUID, Presence> PLAYERS = new HashMap<>();
    private static final Set<ServerLevel> LOADING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int ticks;
    private static boolean stopping;

    record Area(String type, String name, int x, int y, int z, int minX, int minZ, int maxX, int maxZ) {
        static Area read(JsonObject value, int defaultY) {
            String type = value.get("type").getAsString();
            if ((!type.equals("lake") && !type.equals("island")) || !value.has("name")) return null;
            if (!ScanFeatureRules.largeEnough(type, value.get("block_count").getAsInt())) return null;
            JsonObject center = value.getAsJsonObject("center");
            JsonObject box = value.getAsJsonObject("bounding_box");
            Area area = new Area(type, value.get("name").getAsString(),
                    center.get("x").getAsInt(), center.has("y") ? center.get("y").getAsInt() : defaultY,
                    center.get("z").getAsInt(), box.get("min_x").getAsInt(), box.get("min_z").getAsInt(),
                    box.get("max_x").getAsInt(), box.get("max_z").getAsInt());
            if (area.minX > area.maxX || area.minZ > area.maxZ || area.name.isBlank())
                throw new IllegalArgumentException("Invalid named scan bounding box");
            return area;
        }

        boolean contains(double x, double z) {
            return x >= minX && x < (double) maxX + 1 && z >= minZ && z < (double) maxZ + 1;
        }

        long size() {
            return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        }

        String key() { return type + ":" + x + ":" + z; }

        ScanWaypointsPacket.Entry marker() {
            return new ScanWaypointsPacket.Entry(ScanFeatureRules.displayName(name) + " "
                    + (type.equals("lake") ? "Lake" : "Island"), x, y, z, type.equals("lake") ? 0x55FFFF : 0xD2B48C);
        }
    }

    private static final class Presence {
        String current;
        long nextTitle;
        final Map<String, Long> lastShown = new HashMap<>();
    }

    private static List<Area> areas(ServerLevel level) {
        List<Area> known = AREAS.get(level);
        if (known != null) return known;
        if (LOADING.add(level)) {
            IslandScanManager.submit(level.getServer(), () -> {
                try {
                    publish(level, ScanAreaStorage.findings(level));
                } catch (Exception e) {
                    IslandScanManager.LOGGER.error("Cannot reload scan areas for " + level.dimension().location(), e);
                    publish(level, List.of());
                }
            });
        }
        return List.of();
    }

    static void publish(ServerLevel level, List<JsonObject> findings) {
        List<Area> result = new ArrayList<>();
        for (JsonObject value : findings) {
            try {
                Area area = Area.read(value, level.getSeaLevel());
                if (area != null) result.add(area);
            } catch (RuntimeException e) {
                IslandScanManager.LOGGER.error("Invalid scan area in " + level.dimension().location(), e);
            }
        }
        result.sort(Comparator.comparingLong(Area::size).thenComparing(Area::key));
        List<Area> snapshot = List.copyOf(result);
        level.getServer().execute(() -> {
            if (stopping || level.getServer() != ServerLifecycleHooks.getCurrentServer()) return;
            LOADING.remove(level);
            AREAS.put(level, snapshot);
            for (ServerPlayer player : level.players()) sync(player);
        });
    }

    private static void sync(ServerPlayer player) {
        if (stopping) return;
        EDBMessages.sendToPlayer(new ScanWaypointsPacket(), player);
        List<ScanWaypointsPacket.Entry> markers = areas(player.serverLevel()).stream().map(Area::marker).toList();
        // Bound individual payloads for worlds with many discoveries.
        for (int start = 0; start < markers.size(); start += 128) {
            EDBMessages.sendToPlayer(new ScanWaypointsPacket(
                    markers.subList(start, Math.min(start + 128, markers.size()))), player);
        }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        stopping = false;
        ticks = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) areas(level);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PLAYERS.remove(player.getUUID());
            sync(player);
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Presence state = PLAYERS.get(player.getUUID());
            if (state != null) state.current = null;
            sync(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (stopping || event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            Presence state = PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new Presence());
            Area selected = null;
            if (!player.isSpectator() && level.hasChunkAt(player.blockPosition())) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        player.getBlockX(), player.getBlockZ());
                if (Math.abs(player.getY() - surface) <= 16) {
                    selected = areas(level).stream().filter(a -> a.contains(player.getX(), player.getZ()))
                            .findFirst().orElse(null);
                }
            }
            String current = selected == null ? null : level.dimension().location() + ":" + selected.key();
            if (Objects.equals(current, state.current)) continue;
            state.current = current;
            long now = server.getTickCount();
            if (selected == null || now < state.nextTitle
                    || now - state.lastShown.getOrDefault(current, Long.MIN_VALUE / 2) < 400) continue;
            state.nextTitle = now + 400;
            state.lastShown.entrySet().removeIf(entry -> now - entry.getValue() >= 400);
            state.lastShown.put(current, now);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(
                    ScanFeatureRules.displayName(selected.name()) + " "
                            + (selected.type().equals("lake") ? "Lake" : "Island"))));
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        stopping = true;
        IslandScanManager.stop(event.getServer());
        AREAS.clear();
        LOADING.clear();
        PLAYERS.clear();
        ticks = 0;
    }
}
