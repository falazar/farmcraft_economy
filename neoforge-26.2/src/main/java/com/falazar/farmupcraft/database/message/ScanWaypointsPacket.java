package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ScanWaypointsPacket(byte action, List<Entry> entries) implements CustomPacketPayload {
    public static final byte ADD = 0;
    public static final byte CLEAR = 1;
    public static final int MAX_ENTRIES = 256;
    public static final Type<ScanWaypointsPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "scan_waypoints"));
    public static final StreamCodec<FriendlyByteBuf, ScanWaypointsPacket> STREAM_CODEC =
            WaypointPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), ScanWaypointsPacket::new);

    public record Entry(String name, int x, int y, int z, int color) {
        public Entry {
            name = WaypointPayloadValidation.name(name);
            color = WaypointPayloadValidation.color(color);
        }
    }

    public ScanWaypointsPacket(List<Entry> entries) {
        this(ADD, entries);
    }

    public ScanWaypointsPacket() {
        this(CLEAR, List.of());
    }

    public ScanWaypointsPacket {
        if (action != ADD && action != CLEAR) {
            throw new IllegalArgumentException("Unknown scan waypoint action: " + action);
        }
        if (entries.size() > MAX_ENTRIES || (action == CLEAR && !entries.isEmpty())) {
            throw new IllegalArgumentException("Invalid scan waypoint entry count: " + entries.size());
        }
        entries = List.copyOf(entries);
    }

    public ScanWaypointsPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte(), buffer);
    }

    private ScanWaypointsPacket(byte action, FriendlyByteBuf buffer) {
        this(action, readEntries(action, buffer));
    }

    private static List<Entry> readEntries(byte action, FriendlyByteBuf buffer) {
        if (action != ADD) {
            return List.of();
        }
        int count = buffer.readInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid scan waypoint entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buffer.readUtf(WaypointPayloadValidation.MAX_NAME_LENGTH),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt()));
        }
        return entries;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeByte(action);
        if (action == ADD) {
            buffer.writeInt(entries.size());
            for (Entry entry : entries) {
                buffer.writeUtf(entry.name(), WaypointPayloadValidation.MAX_NAME_LENGTH);
                buffer.writeInt(entry.x());
                buffer.writeInt(entry.y());
                buffer.writeInt(entry.z());
                buffer.writeInt(entry.color());
            }
        }
    }

    @Override
    public Type<ScanWaypointsPacket> type() {
        return TYPE;
    }
}
