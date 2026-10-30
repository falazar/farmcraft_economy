package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JourneyMapWaypointsTest {
    @AfterEach
    void reset() {
        JourneyMapIntegration.clearScanWaypoints();
        new JourneyMapIntegration().initialize(null);
    }

    @Test
    void addUsesModernFactoryWithNativeOwnershipPersistenceColorAndProvenance() {
        var fixture = new Fixture();
        var payload = new AddJMWaypointPacket(-20, 72, 30, "Field", "farmupcraft:dimension", false, 0x123456);

        JourneyMapIntegration.addWaypoint(payload, fixture.api, fixture);

        var waypoint = fixture.added.getFirst();
        assertEquals("journeymap", waypoint.getModId());
        assertEquals("Field", waypoint.getName());
        assertEquals(new BlockPos(-20, 72, 30), waypoint.getBlockPos());
        assertEquals("farmupcraft:dimension", waypoint.getPrimaryDimension());
        assertFalse(waypoint.isPersistent());
        assertEquals(0x123456, waypoint.getColor());
        assertEquals("farmupcraft", waypoint.getCustomData("farmupcraft:waypoint"));
    }

    @Test
    void defaultAddRemainsPersistentAndGold() {
        var fixture = new Fixture();
        Waypoint waypoint = JourneyMapIntegration.createWaypoint(
                new AddJMWaypointPacket(0, 64, 0, "Gold", "minecraft:overworld"), fixture);
        assertTrue(waypoint.isPersistent());
        assertEquals(0xFFD700, waypoint.getColor());
    }

    @Test
    void scansUseModOwnershipOverworldAndTransientPersistenceAndClearOnlyScans() {
        var fixture = new Fixture();
        JourneyMapIntegration.addWaypoint(new AddJMWaypointPacket(0, 64, 0, "Keep", "minecraft:overworld"),
                fixture.api, fixture);
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(List.of(
                new ScanWaypointsPacket.Entry("NW", -16, 70, 32, 0x00FF00),
                new ScanWaypointsPacket.Entry("SE", 16, 70, 64, 0xFF0000))), fixture.api, fixture);

        assertEquals(2, JourneyMapIntegration.getScanWaypoints().size());
        for (var waypoint : JourneyMapIntegration.getScanWaypoints()) {
            assertEquals("farmupcraft", waypoint.getModId());
            assertEquals("minecraft:overworld", waypoint.getPrimaryDimension());
            assertFalse(waypoint.isPersistent());
        }
        assertThrows(UnsupportedOperationException.class, () -> JourneyMapIntegration.getScanWaypoints().clear());
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(), fixture.api, fixture);
        assertEquals(2, fixture.removed.size());
        assertTrue(JourneyMapIntegration.getScanWaypoints().isEmpty());
        assertFalse(fixture.removed.contains(fixture.added.getFirst()));
    }

    @Test
    void selectionHonorsThreeDimensionalRangeNameNearestAndNeverTouchesUnrelatedNativeWaypoints() {
        var fixture = new Fixture();
        var far = fixture.create("farmupcraft", new BlockPos(4, 3, 0), "FIELD far", "minecraft:overworld", false);
        var near = JourneyMapIntegration.createWaypoint(
                new AddJMWaypointPacket(1, 0, 0, "field near", "minecraft:overworld"), fixture);
        var personal = fixture.create("journeymap", BlockPos.ZERO, "field personal", "minecraft:overworld", true);
        var other = fixture.create("anothermod", BlockPos.ZERO, "field other", "minecraft:overworld", false);
        var high = fixture.create("farmupcraft", new BlockPos(0, 6, 0), "field high", "minecraft:overworld", false);
        var noMatch = fixture.create("farmupcraft", BlockPos.ZERO, "village", "minecraft:overworld", false);
        var candidates = List.of(far, near, personal, other, high, noMatch);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of(far, near), JourneyMapIntegration.selectWaypoints(candidates,
                    new RemoveModWaypointsPacket(0, 0, 0, 5, false, "field")));
        } finally {
            Locale.setDefault(previous);
        }
        assertEquals(List.of(near), JourneyMapIntegration.selectWaypoints(candidates,
                new RemoveModWaypointsPacket(0, 0, 0, 5, false, "field", true)));
    }

    @Test
    void previewReportsButDoesNotRemoveAndActualClearUpdatesScanTracking() {
        var fixture = new Fixture();
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(List.of(
                new ScanWaypointsPacket.Entry("Scan", 0, 0, 0, 1))), fixture.api, fixture);
        var messages = new ArrayList<Component>();
        JourneyMapIntegration.removeWaypoints(new RemoveModWaypointsPacket(0, 0, 0, 1, true),
                fixture.api, messages::add);
        assertTrue(fixture.removed.isEmpty());
        assertTrue(messages.getFirst().getString().contains("Would remove 1"));
        assertEquals(1, JourneyMapIntegration.getScanWaypoints().size());

        messages.clear();
        JourneyMapIntegration.removeWaypoints(new RemoveModWaypointsPacket(0, 0, 0, 1, false, null, true),
                fixture.api, messages::add);
        assertEquals(1, fixture.removed.size());
        assertTrue(JourneyMapIntegration.getScanWaypoints().isEmpty());
        assertTrue(messages.getFirst().getString().contains("Removed nearest waypoint: Scan"));
    }

    @Test
    void failedScanRemovalRemainsTrackedForRetryAndDoesNotClaimSuccess() {
        var fixture = new Fixture();
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(List.of(
                new ScanWaypointsPacket.Entry("Retry", 0, 0, 0, 1))), fixture.api, fixture);
        fixture.failRemoval = true;
        var messages = new ArrayList<Component>();
        JourneyMapIntegration.removeWaypoints(new RemoveModWaypointsPacket(0, 0, 0, 1, false, null, true),
                fixture.api, messages::add);
        assertEquals(1, JourneyMapIntegration.getScanWaypoints().size());
        assertTrue(messages.getFirst().getString().contains("Removed 0"));
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(), fixture.api, fixture);
        assertEquals(1, JourneyMapIntegration.getScanWaypoints().size());
        fixture.failRemoval = false;
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(), fixture.api, fixture);
        assertTrue(JourneyMapIntegration.getScanWaypoints().isEmpty());
    }

    @Test
    void failedScanAdditionIsNotTrackedAndLogoutClearsSessionState() {
        var fixture = new Fixture();
        var packet = new ScanWaypointsPacket(List.of(new ScanWaypointsPacket.Entry("Scan", 0, 0, 0, 1)));
        fixture.failAddition = true;
        JourneyMapIntegration.scanWaypoints(packet, fixture.api, fixture);
        assertTrue(JourneyMapIntegration.getScanWaypoints().isEmpty());
        fixture.failAddition = false;
        JourneyMapIntegration.scanWaypoints(packet, fixture.api, fixture);
        new JourneyMapIntegration().initialize(fixture.api);
        JourneyMapIntegration.loggedOut();
        assertEquals(1, fixture.removed.size());
        assertTrue(JourneyMapIntegration.getScanWaypoints().isEmpty());
    }

    private static final class Fixture implements JourneyMapIntegration.WaypointCreator {
        final List<Waypoint> added = new ArrayList<>();
        final List<Waypoint> removed = new ArrayList<>();
        boolean failAddition;
        boolean failRemoval;
        final IClientAPI api = (IClientAPI) Proxy.newProxyInstance(IClientAPI.class.getClassLoader(),
                new Class<?>[]{IClientAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAllWaypoints")) {
                        return List.copyOf(added);
                    }
                    assertEquals("farmupcraft", args[0], "API calls must use the registered plugin ID, not native owner");
                    switch (method.getName()) {
                        case "addWaypoint" -> {
                            if (failAddition) throw new IllegalStateException("Expected addition failure");
                            added.add((Waypoint) args[1]);
                        }
                        case "removeWaypoint" -> {
                            if (failRemoval) throw new IllegalStateException("Expected removal failure");
                            removed.add((Waypoint) args[1]);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                });

        @Override
        public Waypoint create(String owner, BlockPos position, String name, String dimension, boolean persistent) {
            Map<String, String> customData = new HashMap<>();
            int[] color = {0};
            return (Waypoint) Proxy.newProxyInstance(Waypoint.class.getClassLoader(), new Class<?>[]{Waypoint.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getModId" -> owner;
                        case "getName" -> name;
                        case "getBlockPos" -> position;
                        case "getX" -> position.getX();
                        case "getY" -> position.getY();
                        case "getZ" -> position.getZ();
                        case "getPrimaryDimension" -> dimension;
                        case "isPersistent" -> persistent;
                        case "getColor" -> color[0];
                        case "setColor" -> { color[0] = (int) args[0]; yield null; }
                        case "setCustomData" -> { customData.put((String) args[0], (String) args[1]); yield null; }
                        case "getCustomData" -> customData.get((String) args[0]);
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> name;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
