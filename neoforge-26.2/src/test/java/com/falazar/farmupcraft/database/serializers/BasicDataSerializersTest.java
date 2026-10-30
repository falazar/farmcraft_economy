package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BasicDataSerializersTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @Test
    void primitivesRetainExactLegacyNamesAndTagTypes() {
        BoolDataSerializer bools = new BoolDataSerializer();
        for (boolean value : List.of(false, true)) {
            assertLegacyRoundTrip(bools, value, field("bool_value", ByteTag.valueOf(value)));
        }
        IntDataSerializer ints = new IntDataSerializer();
        for (int value : new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            assertLegacyRoundTrip(ints, value, field("int_value", IntTag.valueOf(value)));
        }
        LongDataSerializer longs = new LongDataSerializer();
        for (long value : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
            assertLegacyRoundTrip(longs, value, field("long_val", LongTag.valueOf(value)));
        }
        StringDataSerializer strings = new StringDataSerializer();
        for (String value : List.of("", "legacy", "negative: -12\n雪\u0000")) {
            assertLegacyRoundTrip(strings, value, field("string_value", StringTag.valueOf(value)));
        }
    }

    @Test
    void uuidUsesLegacyFourIntArrayWithCorrectSignedBitOrder() {
        UUIDDataSerializer serializer = new UUIDDataSerializer();
        assertLegacyRoundTrip(serializer, UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210"),
                field("uuid", new IntArrayTag(new int[] {0x01234567, 0x89abcdef, 0xfedcba98, 0x76543210})));
        assertLegacyRoundTrip(serializer, new UUID(0, 0),
                field("uuid", new IntArrayTag(new int[] {0, 0, 0, 0})));
        assertLegacyRoundTrip(serializer, new UUID(Long.MIN_VALUE, Long.MAX_VALUE),
                field("uuid", new IntArrayTag(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE, -1})));
        assertLegacyRoundTrip(serializer, new UUID(-1, -1),
                field("uuid", new IntArrayTag(new int[] {-1, -1, -1, -1})));
    }

    @Test
    void coordinatesRetainUnpackedLegacyIntegersWithoutDatabaseMetadata() {
        BlockPosDataSerializer blocks = new BlockPosDataSerializer();
        ChunkPosDataSerializer chunks = new ChunkPosDataSerializer();
        for (int[] xyz : new int[][] {{0, 0, 0}, {-17, -64, -33},
                {Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE},
                {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
            CompoundTag block = field("x", IntTag.valueOf(xyz[0]));
            block.putInt("y", xyz[1]);
            block.putInt("z", xyz[2]);
            assertLegacyRoundTrip(blocks, new BlockPos(xyz[0], xyz[1], xyz[2]), block);
            CompoundTag chunk = field("x", IntTag.valueOf(xyz[0]));
            chunk.putInt("z", xyz[2]);
            assertLegacyRoundTrip(chunks, new ChunkPos(xyz[0], xyz[2]), chunk);
            block.putString("database_name", "legacy-debug-metadata");
            chunk.putString("database_name", "legacy-debug-metadata");
            assertEquals(new BlockPos(xyz[0], xyz[1], xyz[2]), blocks.deserialize(block, REGISTRIES));
            assertEquals(new ChunkPos(xyz[0], xyz[2]), chunks.deserialize(chunk, REGISTRIES));
        }
    }

    @Test
    void missingAndNonnumericPrimitiveFieldsFailRatherThanDefault() {
        rejectsMissingAndWrongType(new BoolDataSerializer(), "bool_value",
                StringTag.valueOf("true"), new CompoundTag());
        rejectsMissingAndWrongType(new IntDataSerializer(), "int_value",
                StringTag.valueOf("1"), new CompoundTag());
        rejectsMissingAndWrongType(new LongDataSerializer(), "long_val",
                StringTag.valueOf("1"), new CompoundTag());
        rejectsMissingAndWrongType(new StringDataSerializer(), "string_value",
                IntTag.valueOf(1), new CompoundTag());
        rejectsMissingAndWrongType(new UUIDDataSerializer(), "uuid",
                StringTag.valueOf("01234567-89ab-cdef-fedc-ba9876543210"), new CompoundTag());
        for (int length : new int[] {0, 1, 3, 5}) {
            assertThrows(IllegalArgumentException.class, () -> new UUIDDataSerializer()
                    .deserialize(field("uuid", new IntArrayTag(new int[length])), REGISTRIES));
        }
    }

    @Test
    void everyCoordinateIsRequiredAndMustBeLosslesslyRepresentable() {
        assertInvalidCoordinates(new BlockPosDataSerializer(), List.of("x", "y", "z"));
        assertInvalidCoordinates(new ChunkPosDataSerializer(), List.of("x", "z"));
    }

    private static void assertInvalidCoordinates(DataSerializer<?> serializer, List<String> names) {
        for (String name : names) {
            CompoundTag complete = new CompoundTag();
            names.forEach(coordinate -> complete.putInt(coordinate, -1));
            complete.remove(name);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(complete, REGISTRIES));
            complete.putDouble(name, -1.5);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(complete, REGISTRIES));
            complete.putLong(name, (long) Integer.MAX_VALUE + 1);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(complete, REGISTRIES));
            complete.putDouble(name, Double.NaN);
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(complete, REGISTRIES));
        }
    }

    @Test
    void numericWidthsAndIntegralFloatingValuesAreCoercedLosslessly() {
        var ints = new IntDataSerializer();
        var longs = new LongDataSerializer();
        for (Tag numeric : List.of(ByteTag.valueOf((byte) -12), ShortTag.valueOf((short) -12),
                IntTag.valueOf(-12), LongTag.valueOf(-12), FloatTag.valueOf(-12), DoubleTag.valueOf(-12))) {
            assertEquals(Integer.valueOf(-12), ints.deserialize(field("int_value", numeric), REGISTRIES));
            assertEquals(Long.valueOf(-12), longs.deserialize(field("long_val", numeric), REGISTRIES));
            CompoundTag position = field("x", numeric);
            position.put("y", numeric);
            position.put("z", numeric);
            assertEquals(new BlockPos(-12, -12, -12), new BlockPosDataSerializer().deserialize(position, REGISTRIES));
            assertEquals(new ChunkPos(-12, -12), new ChunkPosDataSerializer().deserialize(position, REGISTRIES));
        }
        for (int value : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(Integer.valueOf(value),
                    ints.deserialize(field("int_value", DoubleTag.valueOf(value)), REGISTRIES));
        }
        for (long value : new long[] {Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L}) {
            assertEquals(Long.valueOf(value), longs.deserialize(field("long_val", LongTag.valueOf(value)), REGISTRIES));
        }
        for (double value : new double[] {-0x1p63, Math.nextDown(0x1p63), -0.0, 0x1p53}) {
            assertEquals(Long.valueOf((long) value),
                    longs.deserialize(field("long_val", DoubleTag.valueOf(value)), REGISTRIES));
        }
    }

    @Test
    void fractionsOverflowAndNonfiniteNumbersAreRejected() {
        var ints = new IntDataSerializer();
        for (Tag invalid : List.of(LongTag.valueOf((long) Integer.MIN_VALUE - 1),
                LongTag.valueOf((long) Integer.MAX_VALUE + 1), DoubleTag.valueOf(0.5),
                FloatTag.valueOf(-1.5F), FloatTag.valueOf((float) Integer.MAX_VALUE),
                DoubleTag.valueOf(Double.NaN), FloatTag.valueOf(Float.NaN),
                DoubleTag.valueOf(Double.POSITIVE_INFINITY), FloatTag.valueOf(Float.NEGATIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> ints.deserialize(field("int_value", invalid), REGISTRIES));
        }
        var longs = new LongDataSerializer();
        for (Tag invalid : List.of(DoubleTag.valueOf(0x1p63), DoubleTag.valueOf((double) Long.MAX_VALUE),
                DoubleTag.valueOf(Math.nextDown(-0x1p63)), FloatTag.valueOf(0x1p63F),
                DoubleTag.valueOf(0.5), FloatTag.valueOf(-1.5F),
                DoubleTag.valueOf(Double.NaN), FloatTag.valueOf(Float.NaN),
                DoubleTag.valueOf(Double.POSITIVE_INFINITY), FloatTag.valueOf(Float.NEGATIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> longs.deserialize(field("long_val", invalid), REGISTRIES));
        }
    }

    @Test
    void booleansAcceptOnlyNumericZeroOrOneRegardlessOfWidth() {
        var serializer = new BoolDataSerializer();
        for (int value : new int[] {0, 1}) {
            for (Tag numeric : List.of(ByteTag.valueOf((byte) value), ShortTag.valueOf((short) value),
                    IntTag.valueOf(value), LongTag.valueOf(value),
                    FloatTag.valueOf(value), DoubleTag.valueOf(value))) {
                assertEquals(value == 1, serializer.deserialize(field("bool_value", numeric), REGISTRIES));
            }
        }
        for (Tag invalid : List.of(ByteTag.valueOf((byte) -1), IntTag.valueOf(2), LongTag.valueOf(256),
                LongTag.valueOf(Long.MAX_VALUE), DoubleTag.valueOf(0.5), DoubleTag.valueOf(0x1p63),
                DoubleTag.valueOf(Double.NaN), FloatTag.valueOf(Float.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class,
                    () -> serializer.deserialize(field("bool_value", invalid), REGISTRIES));
        }
    }

    @Test
    void uuidAcceptsLosslessNumericArraysAndListsAfterJsonImport() {
        var serializer = new UUIDDataSerializer();
        UUID small = new UUID(0x0000000100000002L, 0x0000000300000004L);
        for (Tag components : List.of(new ByteArrayTag(new byte[] {1, 2, 3, 4}),
                new IntArrayTag(new int[] {1, 2, 3, 4}), new LongArrayTag(new long[] {1, 2, 3, 4}),
                numericList(ByteTag.ONE, ShortTag.valueOf((short) 2), FloatTag.valueOf(3), DoubleTag.valueOf(4)))) {
            assertEquals(small, serializer.deserialize(field("uuid", components), REGISTRIES));
        }
        UUID extreme = new UUID(0x800000007fffffffL, 0xffffffff00000000L);
        for (Tag components : List.of(new LongArrayTag(new long[] {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0}),
                numericList(DoubleTag.valueOf(Integer.MIN_VALUE), DoubleTag.valueOf(Integer.MAX_VALUE),
                        LongTag.valueOf(-1), ByteTag.ZERO))) {
            assertEquals(extreme, serializer.deserialize(field("uuid", components), REGISTRIES));
        }
        UUID signedBytes = new UUID(0xffffff80ffffffffL, 0x000000000000007fL);
        assertEquals(signedBytes, serializer.deserialize(
                field("uuid", new ByteArrayTag(new byte[] {-128, -1, 0, 127})), REGISTRIES));
    }

    @Test
    void uuidRejectsWrongLengthsNonnumericComponentsAndLossyNumbers() {
        var serializer = new UUIDDataSerializer();
        for (int length : new int[] {0, 1, 3, 5}) {
            for (Tag components : List.of(new ByteArrayTag(new byte[length]), new LongArrayTag(new long[length]),
                    numericList(new Tag[length]))) {
                assertThrows(IllegalArgumentException.class,
                        () -> serializer.deserialize(field("uuid", components), REGISTRIES));
            }
        }
        for (Tag invalid : List.of(LongTag.valueOf((long) Integer.MAX_VALUE + 1),
                LongTag.valueOf((long) Integer.MIN_VALUE - 1), DoubleTag.valueOf(1.5),
                DoubleTag.valueOf(Double.NaN), FloatTag.valueOf(Float.POSITIVE_INFINITY),
                DoubleTag.valueOf(Double.NEGATIVE_INFINITY), StringTag.valueOf("1"), new CompoundTag())) {
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(
                    field("uuid", numericList(IntTag.valueOf(1), IntTag.valueOf(2), invalid, IntTag.valueOf(4))), REGISTRIES));
        }
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(
                field("uuid", new LongArrayTag(new long[] {0, 0, Long.MAX_VALUE, 0})), REGISTRIES));
    }

    private static ListTag numericList(Tag... components) {
        ListTag list = new ListTag();
        for (Tag component : components) {
            list.add(component == null ? IntTag.valueOf(0) : component);
        }
        return list;
    }

    private static void rejectsMissingAndWrongType(DataSerializer<?> serializer, String name, Tag... wrong) {
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(new CompoundTag(), REGISTRIES));
        for (Tag tag : wrong) {
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(field(name, tag), REGISTRIES));
        }
    }

    private static <T> void assertLegacyRoundTrip(DataSerializer<T> serializer, T value, CompoundTag legacy) {
        assertEquals(legacy, serializer.serialize(value, REGISTRIES));
        assertEquals(value, serializer.deserialize(legacy, REGISTRIES));
    }

    private static CompoundTag field(String name, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.put(name, value);
        return tag;
    }
}
