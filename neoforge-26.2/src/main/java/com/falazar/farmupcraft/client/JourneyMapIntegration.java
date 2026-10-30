package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class JourneyMapIntegration implements IClientPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = FarmUpCraftNeoForge.MOD_ID;
    private static final String NATIVE_OWNER = "journeymap";
    private static final String ORIGIN_KEY = MOD_ID + ":waypoint";
    private static IClientAPI jmAPI;
    private static final List<Waypoint> scanWaypoints = new ArrayList<>();

    @Override
    public String getModId() {
        return FarmUpCraftNeoForge.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
    }

    public static IClientAPI getApi() {
        return jmAPI;
    }

    public static List<Waypoint> getScanWaypoints() {
        return List.copyOf(scanWaypoints);
    }

    public static void addScanWaypoint(Waypoint waypoint) {
        scanWaypoints.add(waypoint);
    }

    public static void clearScanWaypoints() {
        scanWaypoints.clear();
    }

    public static void addWaypoint(AddJMWaypointPacket payload) {
        IClientAPI api = requireApi();
        if (api == null) {
            return;
        }
        addWaypoint(payload, api, WaypointFactory::createWaypoint);
    }

    static void addWaypoint(AddJMWaypointPacket payload, IClientAPI api, WaypointCreator factory) {
        try {
            api.addWaypoint(MOD_ID, createWaypoint(payload, factory));
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to add JourneyMap waypoint '{}'", payload.name(), error);
        }
    }

    static Waypoint createWaypoint(AddJMWaypointPacket payload, WaypointCreator factory) {
        // Native ownership replaces the removed v1 setEditable flag and keeps normal JM editing/removal.
        Waypoint waypoint = factory.create(NATIVE_OWNER,
                new BlockPos(payload.x(), payload.y(), payload.z()), payload.name(),
                payload.dimId(), payload.persistent());
        waypoint.setColor(payload.color());
        waypoint.setCustomData(ORIGIN_KEY, MOD_ID);
        return waypoint;
    }

    public static void scanWaypoints(ScanWaypointsPacket payload) {
        IClientAPI api = requireApi();
        if (api == null) {
            return;
        }
        scanWaypoints(payload, api, WaypointFactory::createWaypoint);
    }

    static void scanWaypoints(ScanWaypointsPacket payload, IClientAPI api, WaypointCreator factory) {
        if (payload.action() == ScanWaypointsPacket.CLEAR) {
            removeScanWaypoints(api);
            return;
        }
        for (var entry : payload.entries()) {
            try {
                Waypoint waypoint = factory.create(MOD_ID,
                        new BlockPos(entry.x(), entry.y(), entry.z()), entry.name(), "minecraft:overworld", false);
                waypoint.setColor(entry.color());
                api.addWaypoint(MOD_ID, waypoint);
                addScanWaypoint(waypoint);
            } catch (RuntimeException error) {
                LOGGER.warn("Failed to show JourneyMap scan waypoint '{}'", entry.name(), error);
            }
        }
    }

    private static void removeScanWaypoints(IClientAPI api) {
        for (Waypoint waypoint : getScanWaypoints()) {
            try {
                api.removeWaypoint(MOD_ID, waypoint);
                scanWaypoints.remove(waypoint);
            } catch (RuntimeException error) {
                LOGGER.warn("Failed to remove JourneyMap scan waypoint '{}'", waypoint.getName(), error);
            }
        }
    }

    public static void loggedOut() {
        if (jmAPI != null) {
            removeScanWaypoints(jmAPI);
        }
        clearScanWaypoints();
    }

    public static void removeWaypoints(RemoveModWaypointsPacket payload, Consumer<Component> feedback) {
        IClientAPI api = requireApi();
        if (api == null) {
            feedback.accept(Component.literal("[Waypoints] JourneyMap is not available.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        removeWaypoints(payload, api, feedback);
    }

    static void removeWaypoints(RemoveModWaypointsPacket payload, IClientAPI api, Consumer<Component> feedback) {
        List<Waypoint> matched;
        try {
            matched = selectWaypoints(api.getAllWaypoints(), payload);
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to query JourneyMap waypoints", error);
            feedback.accept(Component.literal("[Waypoints] Failed to query JourneyMap waypoints.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (matched.isEmpty()) {
            String suffix = payload.nameFilter() == null ? "" : " matching filter '" + payload.nameFilter() + "'";
            feedback.accept(Component.literal("[Waypoints] No mod waypoints within " + payload.rangeBlocks()
                    + " blocks" + suffix + ".").withStyle(ChatFormatting.YELLOW));
        } else if (payload.testOnly()) {
            feedback.accept(Component.literal("[Waypoints TEST] Would remove " + matched.size() + " waypoint(s):")
                    .withStyle(ChatFormatting.AQUA));
            for (Waypoint waypoint : matched) {
                feedback.accept(Component.literal("  - " + describe(waypoint)).withStyle(ChatFormatting.WHITE));
            }
        } else {
            int removed = 0;
            for (Waypoint waypoint : matched) {
                try {
                    api.removeWaypoint(MOD_ID, waypoint);
                    scanWaypoints.remove(waypoint);
                    removed++;
                } catch (RuntimeException error) {
                    LOGGER.warn("Failed to remove JourneyMap waypoint '{}'", waypoint.getName(), error);
                }
            }
            if (payload.nearestOnly() && removed == 1) {
                feedback.accept(Component.literal("[Waypoints] Removed nearest waypoint: " + describe(matched.getFirst()))
                        .withStyle(ChatFormatting.GREEN));
            } else {
                feedback.accept(Component.literal("[Waypoints] Removed " + removed + " mod waypoint(s) within "
                        + payload.rangeBlocks() + " blocks.").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    static List<Waypoint> selectWaypoints(List<? extends Waypoint> candidates, RemoveModWaypointsPacket payload) {
        if (candidates == null) {
            return List.of();
        }
        String filter = payload.nameFilter() == null ? null : payload.nameFilter().toLowerCase(Locale.ROOT);
        double rangeSquared = (double) payload.rangeBlocks() * payload.rangeBlocks();
        List<Waypoint> matches = new ArrayList<>();
        for (Waypoint waypoint : candidates) {
            // Never clear a player's unrelated native JourneyMap waypoints.
            if (!MOD_ID.equals(waypoint.getModId())
                    && !(NATIVE_OWNER.equals(waypoint.getModId()) && MOD_ID.equals(waypoint.getCustomData(ORIGIN_KEY)))) {
                continue;
            }
            if (filter != null && (waypoint.getName() == null
                    || !waypoint.getName().toLowerCase(Locale.ROOT).contains(filter))) {
                continue;
            }
            if (distanceSquared(waypoint, payload) <= rangeSquared) {
                matches.add(waypoint);
            }
        }
        if (payload.nearestOnly() && matches.size() > 1) {
            return List.of(matches.stream().min(Comparator.comparingDouble(wp -> distanceSquared(wp, payload))).orElseThrow());
        }
        return List.copyOf(matches);
    }

    private static double distanceSquared(Waypoint waypoint, RemoveModWaypointsPacket payload) {
        double dx = waypoint.getX() - payload.originX();
        double dy = waypoint.getY() - payload.originY();
        double dz = waypoint.getZ() - payload.originZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String describe(Waypoint waypoint) {
        return waypoint.getName() + " @ " + waypoint.getBlockPos().toShortString();
    }

    private static IClientAPI requireApi() {
        if (jmAPI == null) {
            LOGGER.warn("Cannot process FarmUpCraft waypoints: JourneyMap client API has not initialized");
        }
        return jmAPI;
    }

    @FunctionalInterface
    interface WaypointCreator {
        Waypoint create(String owner, BlockPos position, String name, String dimension, boolean persistent);
    }
}
