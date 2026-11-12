package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VillageLevelUpResultPacket(String message, boolean success) implements CustomPacketPayload {
    private static final int MAX_MESSAGE_LENGTH = 1024;
    public static final Type<VillageLevelUpResultPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farmupcraft", "village_level_up_result"));
    public static final StreamCodec<FriendlyByteBuf, VillageLevelUpResultPacket> STREAM_CODEC = CommandPayloadValidation
            .codec((buffer, packet) -> packet.toBytes(buffer), VillageLevelUpResultPacket::new);

    public VillageLevelUpResultPacket {
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Invalid village level-up result message");
    }

    public VillageLevelUpResultPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(MAX_MESSAGE_LENGTH), buffer.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(message, MAX_MESSAGE_LENGTH);
        buffer.writeBoolean(success);
    }

    @Override
    public Type<VillageLevelUpResultPacket> type() {
        return TYPE;
    }
}