package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AddJMWaypointPacket(int x, int y, int z, String name, String dimId,
                                  boolean persistent, int color) implements CustomPacketPayload {
    public static final Type<AddJMWaypointPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "add_jm_waypoint"));
    public static final StreamCodec<FriendlyByteBuf, AddJMWaypointPacket> STREAM_CODEC =
            WaypointPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), AddJMWaypointPacket::new);

    public AddJMWaypointPacket(int x, int y, int z, String name, String dimId) {
        this(x, y, z, name, dimId, true, 0xFFD700);
    }

    public AddJMWaypointPacket {
        name = WaypointPayloadValidation.name(name);
        Identifier dimension = Identifier.tryParse(java.util.Objects.requireNonNull(dimId, "Waypoint dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("Invalid waypoint dimension: " + dimId);
        }
        dimId = dimension.toString();
        if (dimId.length() > WaypointPayloadValidation.MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("Waypoint dimension identifier is too long");
        }
        color = WaypointPayloadValidation.color(color);
    }

    public AddJMWaypointPacket(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readUtf(WaypointPayloadValidation.MAX_NAME_LENGTH),
                buffer.readUtf(WaypointPayloadValidation.MAX_DIMENSION_LENGTH),
                buffer.readBoolean(), buffer.readInt());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeUtf(name, WaypointPayloadValidation.MAX_NAME_LENGTH);
        buffer.writeUtf(dimId, WaypointPayloadValidation.MAX_DIMENSION_LENGTH);
        buffer.writeBoolean(persistent);
        buffer.writeInt(color);
    }

    @Override
    public Type<AddJMWaypointPacket> type() {
        return TYPE;
    }
}
