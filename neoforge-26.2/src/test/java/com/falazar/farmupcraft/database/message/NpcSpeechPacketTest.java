package com.falazar.farmupcraft.database.message;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcSpeechPacketTest {
    @Test
    void roundTripsIdentityAndText() {
        var packet = new NpcSpeechPacket(UUID.randomUUID(), "Hello, farmer!");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcSpeechPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, NpcSpeechPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void boundsPayloadAndRejectsEmptySpeech() {
        UUID npc = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new NpcSpeechPacket(npc, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new NpcSpeechPacket(npc, "x".repeat(NpcSpeechPacket.MAX_TEXT_LENGTH + 1)));
        assertEquals("Hello farmer", NpcSpeechPacket.speech(npc, "Hello\n farmer").text());
        assertEquals(NpcSpeechPacket.MAX_TEXT_LENGTH,
                NpcSpeechPacket.speech(npc, "x".repeat(4000)).text().length());
    }
}
