package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.data.ExplorationStats.Area;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public record ExplorationRequestPacket(long requestId, Area area) implements CustomPacketPayload {
    public static final Type<ExplorationRequestPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "exploration_request"));
    public static final StreamCodec<FriendlyByteBuf, ExplorationRequestPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, packet) -> {
                buffer.writeLong(packet.requestId());
                buffer.writeInt(packet.area().minX());
                buffer.writeInt(packet.area().minZ());
                buffer.writeInt(packet.area().maxX());
                buffer.writeInt(packet.area().maxZ());
            }, buffer -> new ExplorationRequestPacket(buffer.readLong(),
                    new Area(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt())));

    public ExplorationRequestPacket {
        Objects.requireNonNull(area);
    }

    @Override
    public Type<ExplorationRequestPacket> type() { return TYPE; }
}
