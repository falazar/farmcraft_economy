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

class CommandPayloadTest {
    @Test
    void highlightAndRecipeRoundTripNamedPayloads() {
        var highlight = new HighlightChunkPacket(-10, 12, "Farm \u2605", "minecraft:the_nether", 0x123456);
        assertEquals(highlight, roundTrip(HighlightChunkPacket.STREAM_CODEC, highlight));
        assertEquals("farmupcraft:highlight_chunk", highlight.type().id().toString());
        var recipe = new OpenJeiRecipePacket("minecraft:bread");
        assertEquals(recipe, roundTrip(OpenJeiRecipePacket.STREAM_CODEC, recipe));
        assertEquals("farmupcraft:open_jei_recipe", recipe.type().id().toString());
    }

    @Test
    void villageRoundTripsShowAndClearAndSnapshotsNestedMutableInput() {
        int[] entry = {-12, 31, 0x0055FF};
        List<int[]> source = new ArrayList<>();
        source.add(entry);
        var payload = new ShowVillageChunksPacket(source, true, "minecraft:overworld");
        entry[0] = 100;
        source.clear();
        payload.chunks().getFirst()[0] = 200;
        assertEquals(-12, payload.entries().getFirst().x());
        assertThrows(UnsupportedOperationException.class, () -> payload.entries().clear());
        var decoded = roundTrip(ShowVillageChunksPacket.STREAM_CODEC, payload);
        assertEquals(payload.entries(), decoded.entries());
        assertEquals(payload.dimId(), decoded.dimId());
        assertTrue(decoded.show());
        assertEquals("farmupcraft:show_village_chunks", decoded.type().id().toString());
        var clear = roundTrip(ShowVillageChunksPacket.STREAM_CODEC,
                new ShowVillageChunksPacket(List.of(), false, "minecraft:the_end"));
        assertFalse(clear.show());
        assertTrue(clear.entries().isEmpty());
    }

    @Test
    void constructorsRejectBadIdentifiersColorsCoordinatesAndEntryShapes() {
        assertThrows(IllegalArgumentException.class, () -> new OpenJeiRecipePacket("Bad Item"));
        assertThrows(IllegalArgumentException.class, () -> new OpenJeiRecipePacket(""));
        assertThrows(IllegalArgumentException.class, () -> new OpenJeiRecipePacket("a".repeat(257)));
        assertThrows(IllegalArgumentException.class,
                () -> new HighlightChunkPacket(0, 0, "x", "bad dimension", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HighlightChunkPacket(Integer.MAX_VALUE, 0, "x", "minecraft:overworld", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HighlightChunkPacket(0, 0, "x".repeat(1025), "minecraft:overworld", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HighlightChunkPacket(0, 0, "x", "minecraft:overworld", 0xFF000000));
        assertThrows(IllegalArgumentException.class,
                () -> new ShowVillageChunksPacket(List.of(new int[]{1, 2}), true, "minecraft:overworld"));
        assertThrows(IllegalArgumentException.class,
                () -> new ShowVillageChunksPacket(List.of(new int[]{1, 2, -1}), true, "minecraft:overworld"));
        assertThrows(IllegalArgumentException.class, () -> new ShowVillageChunksPacket(
                java.util.Collections.nCopies(ShowVillageChunksPacket.MAX_CHUNKS + 1, new int[]{0, 0, 0}),
                true, "minecraft:overworld"));
    }

    @Test
    void wireRejectsUnboundedCountsTruncationAndInvalidValues() {
        for (int count : new int[]{-1, ShowVillageChunksPacket.MAX_CHUNKS + 1, Integer.MAX_VALUE}) {
            rejects(ShowVillageChunksPacket.STREAM_CODEC, buffer -> buffer.writeInt(count));
        }
        rejects(ShowVillageChunksPacket.STREAM_CODEC, buffer -> buffer.writeInt(1).writeInt(0));
        rejects(OpenJeiRecipePacket.STREAM_CODEC, buffer -> buffer.writeUtf("Bad Item"));
        rejects(OpenJeiRecipePacket.STREAM_CODEC, buffer -> buffer.writeUtf("a".repeat(257)));
        rejects(HighlightChunkPacket.STREAM_CODEC, buffer -> {
            buffer.writeInt(0).writeInt(0).writeUtf("x").writeUtf("minecraft:overworld").writeInt(-1);
        });
        rejects(ShowVillageChunksPacket.STREAM_CODEC, buffer -> {
            buffer.writeInt(1).writeInt(Integer.MIN_VALUE).writeInt(0).writeInt(1);
            buffer.writeBoolean(true).writeUtf("minecraft:overworld");
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
