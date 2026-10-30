package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RemoveModWaypointsPacket(double originX, double originY, double originZ,
                                       int rangeBlocks, boolean testOnly, String nameFilter,
                                       boolean nearestOnly) implements CustomPacketPayload {
    public static final Type<RemoveModWaypointsPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "remove_mod_waypoints"));
    public static final StreamCodec<FriendlyByteBuf, RemoveModWaypointsPacket> STREAM_CODEC =
            WaypointPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), RemoveModWaypointsPacket::new);

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly) {
        this(x, y, z, rangeBlocks, testOnly, null, false);
    }

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly, String nameFilter) {
        this(x, y, z, rangeBlocks, testOnly, nameFilter, false);
    }

    public RemoveModWaypointsPacket {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)) {
            throw new IllegalArgumentException("Waypoint removal origin must be finite");
        }
        if (rangeBlocks < 0) {
            throw new IllegalArgumentException("Waypoint removal range must not be negative");
        }
        if (nameFilter != null) {
            if (nameFilter.length() > WaypointPayloadValidation.MAX_FILTER_LENGTH) {
                throw new IllegalArgumentException("Waypoint name filter is too long");
            }
            if (nameFilter.isBlank()) {
                nameFilter = null;
            }
        }
    }

    public RemoveModWaypointsPacket(FriendlyByteBuf buffer) {
        this(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readBoolean(),
                buffer.readBoolean() ? buffer.readUtf(WaypointPayloadValidation.MAX_FILTER_LENGTH) : null,
                buffer.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeDouble(originX);
        buffer.writeDouble(originY);
        buffer.writeDouble(originZ);
        buffer.writeInt(rangeBlocks);
        buffer.writeBoolean(testOnly);
        buffer.writeBoolean(nameFilter != null);
        if (nameFilter != null) {
            buffer.writeUtf(nameFilter, WaypointPayloadValidation.MAX_FILTER_LENGTH);
        }
        buffer.writeBoolean(nearestOnly);
    }

    @Override
    public Type<RemoveModWaypointsPacket> type() {
        return TYPE;
    }
}
