package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ExplorationResultPacket(long requestId, boolean success, long mappedChunks) implements CustomPacketPayload {
    public static final Type<ExplorationResultPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "exploration_result"));
    public static final StreamCodec<FriendlyByteBuf, ExplorationResultPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, packet) -> {
                buffer.writeLong(packet.requestId());
                buffer.writeBoolean(packet.success());
                buffer.writeLong(packet.mappedChunks());
            }, buffer -> new ExplorationResultPacket(buffer.readLong(), buffer.readBoolean(), buffer.readLong()));

    public ExplorationResultPacket {
        if (mappedChunks < 0 || !success && mappedChunks != 0) {
            throw new IllegalArgumentException("Invalid exploration report count");
        }
    }

    @Override
    public Type<ExplorationResultPacket> type() { return TYPE; }
}
