package com.falazar.farmupcraft.database.message;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WaypointPayloadTest {
    @Test
    void addRoundTripsDefaultsAndExplicitSettings() {
        var defaults = new AddJMWaypointPacket(-31, 72, 400, "Gold field", "minecraft:overworld");
        assertEquals(defaults, roundTrip(AddJMWaypointPacket.STREAM_CODEC, defaults));
        assertTrue(defaults.persistent());
        assertEquals(0xFFD700, defaults.color());
        assertEquals("farmupcraft:add_jm_waypoint", defaults.type().id().toString());
        var custom = new AddJMWaypointPacket(1, -64, 2, "Custom \u2605", "farmupcraft:other", false, 0x123456);
        assertEquals(custom, roundTrip(AddJMWaypointPacket.STREAM_CODEC, custom));
    }

    @Test
    void removeRoundTripsPreviewFilterAndNearestSelection() {
        var payload = new RemoveModWaypointsPacket(-1.5, 70.25, 30, 128, true, "Field", true);
        assertEquals(payload, roundTrip(RemoveModWaypointsPacket.STREAM_CODEC, payload));
        var unfiltered = new RemoveModWaypointsPacket(0, 0, 0, 0, false);
        assertEquals(unfiltered, roundTrip(RemoveModWaypointsPacket.STREAM_CODEC, unfiltered));
        assertNull(new RemoveModWaypointsPacket(0, 0, 0, 1, false, " ").nameFilter());
        assertEquals("farmupcraft:remove_mod_waypoints", payload.type().id().toString());
    }

    @Test
    void scanRoundTripsAddAndClearAndSnapshotsEntryLists() {
        var entries = new ArrayList<>(List.of(new ScanWaypointsPacket.Entry("NW", -1, 64, 2, 0x00FF00)));
        var add = new ScanWaypointsPacket(entries);
        entries.clear();
        assertEquals(1, add.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> add.entries().clear());
        assertEquals(add, roundTrip(ScanWaypointsPacket.STREAM_CODEC, add));
        var clear = new ScanWaypointsPacket();
        assertEquals(clear, roundTrip(ScanWaypointsPacket.STREAM_CODEC, clear));
        assertEquals("farmupcraft:scan_waypoints", clear.type().id().toString());
    }

    @Test
    void constructorsRejectInvalidWireValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddJMWaypointPacket(0, 0, 0, "x", "bad dimension"));
        assertThrows(IllegalArgumentException.class,
                () -> new AddJMWaypointPacket(0, 0, 0, "x", "minecraft:overworld", true, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanWaypointsPacket.Entry("x".repeat(1025), 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoveModWaypointsPacket(Double.NaN, 0, 0, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoveModWaypointsPacket(0, Double.POSITIVE_INFINITY, 0, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoveModWaypointsPacket(0, 0, 0, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoveModWaypointsPacket(0, 0, 0, 1, false, "x".repeat(257)));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanWaypointsPacket(ScanWaypointsPacket.CLEAR,
                        List.of(new ScanWaypointsPacket.Entry("x", 0, 0, 0, 1))));
    }

    @Test
    void wireRejectsUnknownActionsUnboundedCountsAndInvalidFields() {
        rejects(ScanWaypointsPacket.STREAM_CODEC, buffer -> buffer.writeByte(2));
        for (int count : new int[]{-1, ScanWaypointsPacket.MAX_ENTRIES + 1, Integer.MAX_VALUE}) {
            rejects(ScanWaypointsPacket.STREAM_CODEC, buffer -> {
                buffer.writeByte(ScanWaypointsPacket.ADD);
                buffer.writeInt(count);
            });
        }
        rejects(AddJMWaypointPacket.STREAM_CODEC, buffer -> {
            buffer.writeInt(0).writeInt(0).writeInt(0);
            buffer.writeUtf("name");
            buffer.writeUtf("Bad Dimension");
            buffer.writeBoolean(false);
            buffer.writeInt(1);
        });
        rejects(RemoveModWaypointsPacket.STREAM_CODEC, buffer -> {
            buffer.writeDouble(Double.NEGATIVE_INFINITY).writeDouble(0).writeDouble(0);
            buffer.writeInt(1);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
        });
        rejects(AddJMWaypointPacket.STREAM_CODEC, buffer -> {
            buffer.writeInt(0).writeInt(0).writeInt(0);
            buffer.writeUtf("x".repeat(1025));
        });
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            T decoded = codec.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static <T> void rejects(StreamCodec<FriendlyByteBuf, T> codec, Consumer<FriendlyByteBuf> writer) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            assertThrows(DecoderException.class, () -> codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
