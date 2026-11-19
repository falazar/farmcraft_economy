package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.function.Consumer;

public final class WaypointPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WaypointPayloadHandler() {
    }

    public static void handleAdd(AddJMWaypointPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.addWaypoint(payload);
        } else {
            context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "JourneyMap is not installed."));
        }
    }

    public static void handleRemove(RemoveModWaypointsPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.removeWaypoints(payload, context.player()::sendSystemMessage);
        }
    }

    public static void handleScan(ScanWaypointsPacket payload, IPayloadContext context) {
        if (available()) {
            JourneyMapIntegration.scanWaypoints(payload);
        }
    }

    public static int setMapFilter(boolean enabled, Consumer<net.minecraft.network.chat.Component> feedback) {
        if (!available() || JourneyMapIntegration.getApi() == null) {
            feedback.accept(net.minecraft.network.chat.Component.literal("[Map] JourneyMap is not available."));
            return 0;
        }
        JourneyMapFeatureFilter.setEnabled(enabled);
        int count = JourneyMapIntegration.getScanWaypoints().size();
        feedback.accept(net.minecraft.network.chat.Component.literal(enabled
                ? "[Map] Both maps: lakes/islands only (" + count + " received). Use /fmap all to restore."
                : "[Map] All markers restored, including lakes/islands (" + count + " received)."));
        if (count == 0) {
            feedback.accept(net.minecraft.network.chat.Component.literal(
                    "[Map] No lake/island markers have reached this client in this dimension; "
                            + "the filter cannot display missing discoveries."));
        }
        return 1;
    }

    public static int hideMapFeatures(Consumer<net.minecraft.network.chat.Component> feedback) {
        if (!available() || JourneyMapIntegration.getApi() == null) {
            feedback.accept(net.minecraft.network.chat.Component.literal("[Map] JourneyMap is not available."));
            return 0;
        }
        JourneyMapFeatureFilter.hideFeatures();
        feedback.accept(net.minecraft.network.chat.Component.literal(
                "[Map] Lakes/islands hidden on both maps; other markers restored. Use /fmap all or /fmap lakes to show them."));
        return 1;
    }

    public static int toggleMapFeatures(Consumer<net.minecraft.network.chat.Component> feedback) {
        if (!available() || JourneyMapIntegration.getApi() == null) {
            feedback.accept(net.minecraft.network.chat.Component.literal("[Map] JourneyMap is not available."));
            return 0;
        }
        boolean visible = JourneyMapFeatureFilter.toggleFeatures();
        feedback.accept(net.minecraft.network.chat.Component.literal(visible
                ? "[Map] All markers restored, including lakes/islands. Prefer /fmap all to show them."
                : "[Map] Lakes/islands hidden on both maps; other markers restored. Use /fmap all to show them."));
        return 1;
    }

    public static void loggedOut() {
        if (ModList.get().isLoaded("journeymap")) {
            JourneyMapIntegration.loggedOut();
        }
    }

    private static boolean available() {
        if (ModList.get().isLoaded("journeymap")) {
            return true;
        }
        LOGGER.warn("Cannot process FarmUpCraft waypoint payload: JourneyMap is not installed");
        return false;
    }
}
