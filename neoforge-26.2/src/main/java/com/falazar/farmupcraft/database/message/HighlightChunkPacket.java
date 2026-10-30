package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HighlightChunkPacket(int chunkX, int chunkZ, String label, String dimId,
                                   int color) implements CustomPacketPayload {
    public static final Type<HighlightChunkPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "highlight_chunk"));
    public static final StreamCodec<FriendlyByteBuf, HighlightChunkPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), HighlightChunkPacket::new);

    public HighlightChunkPacket {
        chunkX = CommandPayloadValidation.chunk(chunkX);
        chunkZ = CommandPayloadValidation.chunk(chunkZ);
        label = WaypointPayloadValidation.name(label);
        dimId = CommandPayloadValidation.identifier(dimId);
        color = WaypointPayloadValidation.color(color);
    }

    public HighlightChunkPacket(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt(), buffer.readUtf(WaypointPayloadValidation.MAX_NAME_LENGTH),
                buffer.readUtf(CommandPayloadValidation.MAX_IDENTIFIER_LENGTH), buffer.readInt());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeUtf(label, WaypointPayloadValidation.MAX_NAME_LENGTH);
        buffer.writeUtf(dimId, CommandPayloadValidation.MAX_IDENTIFIER_LENGTH);
        buffer.writeInt(color);
    }

    @Override
    public Type<HighlightChunkPacket> type() {
        return TYPE;
    }
}
