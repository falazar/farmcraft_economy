package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record NpcSpeechPacket(UUID npc, String text) implements CustomPacketPayload {
    public static final int MAX_TEXT_LENGTH = 2048;
    public static final Type<NpcSpeechPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "npc_speech"));
    public static final StreamCodec<FriendlyByteBuf, NpcSpeechPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.npc());
                buffer.writeUtf(packet.text(), MAX_TEXT_LENGTH);
            }, buffer -> new NpcSpeechPacket(buffer.readUUID(), buffer.readUtf(MAX_TEXT_LENGTH)));

    public NpcSpeechPacket {
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(text, "text");
        if (text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("NPC speech must contain 1-" + MAX_TEXT_LENGTH + " characters");
        }
    }

    public static NpcSpeechPacket speech(UUID npc, String text) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        return new NpcSpeechPacket(npc, normalized.substring(0, Math.min(normalized.length(), MAX_TEXT_LENGTH)));
    }

    @Override
    public Type<NpcSpeechPacket> type() {
        return TYPE;
    }
}
