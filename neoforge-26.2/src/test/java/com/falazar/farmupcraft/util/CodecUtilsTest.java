package com.falazar.farmupcraft.util;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodecUtilsTest {
    @Test
    void longStringsKeepSignedSixtyFourBitPrecisionAndNormalizeParsedNumbers() {
        for (long value : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            assertEquals(new JsonPrimitive(Long.toString(value)),
                    CodecUtils.LONG_CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow());
            assertEquals(StringTag.valueOf(Long.toString(value)),
                    CodecUtils.LONG_CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
            assertEquals(value, CodecUtils.LONG_CODEC.parse(NbtOps.INSTANCE,
                    StringTag.valueOf(Long.toString(value))).getOrThrow());
        }
        assertEquals(12L, CodecUtils.LONG_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("+0012")).getOrThrow());
        for (String invalid : new String[]{"", " 12", "1.5", "9223372036854775808", "not a long"}) {
            assertTrue(CodecUtils.LONG_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(invalid)).error().isPresent());
            assertTrue(CodecUtils.CHUNK_POS_CODEC_STRING.parse(JsonOps.INSTANCE,
                    new JsonPrimitive(invalid)).error().isPresent());
        }
        assertTrue(CodecUtils.LONG_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(12)).error().isPresent());
    }

    @Test
    void chunksUseLegacyPackedLongObjectOrStringRatherThanModernCoordinateArray() {
        for (int[] pair : new int[][]{{0, 0}, {-1, -1}, {-17, 33},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE}}) {
            ChunkPos chunk = new ChunkPos(pair[0], pair[1]);
            long legacyPacked = (pair[0] & 0xffffffffL) | ((pair[1] & 0xffffffffL) << 32);
            var json = JsonParser.parseString("{\"chunk_long\":" + legacyPacked + "}");
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("chunk_long", legacyPacked);
            assertEquals(json, CodecUtils.CHUNK_POS_CODEC.encodeStart(JsonOps.INSTANCE, chunk).getOrThrow());
            assertEquals(nbt, CodecUtils.CHUNK_POS_CODEC.encodeStart(NbtOps.INSTANCE, chunk).getOrThrow());
            assertEquals(chunk, CodecUtils.CHUNK_POS_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            assertEquals(chunk, CodecUtils.CHUNK_POS_CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
            assertEquals(new JsonPrimitive(Long.toString(legacyPacked)),
                    CodecUtils.CHUNK_POS_CODEC_STRING.encodeStart(JsonOps.INSTANCE, chunk).getOrThrow());
            assertEquals(StringTag.valueOf(Long.toString(legacyPacked)),
                    CodecUtils.CHUNK_POS_CODEC_STRING.encodeStart(NbtOps.INSTANCE, chunk).getOrThrow());
            assertEquals(chunk, CodecUtils.CHUNK_POS_CODEC_STRING.parse(NbtOps.INSTANCE,
                    StringTag.valueOf(Long.toString(legacyPacked))).getOrThrow());
        }
        assertTrue(CodecUtils.CHUNK_POS_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).error().isPresent());
        assertTrue(CodecUtils.CHUNK_POS_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[-1,2]")).error().isPresent());
        assertTrue(CodecUtils.CHUNK_POS_CODEC_STRING.parse(NbtOps.INSTANCE, LongTag.valueOf(0)).error().isPresent());
    }
}
