package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.Rarity;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodecAndVersionedDataSerializerTest {
    @Test
    void codecSerializerRetainsWrapperAndSupportsScalarAndListPayloads() {
        var integers = new CodecDataSerializer<>(Codec.INT);
        var expected = new CompoundTag();
        expected.put("codec_value", IntTag.valueOf(-123));
        assertEquals(expected, integers.serialize(-123, RegistryAccess.EMPTY));
        assertEquals(-123, integers.deserialize(expected, RegistryAccess.EMPTY));

        var strings = new CodecDataSerializer<>(Codec.STRING.listOf());
        var list = new ListTag();
        list.add(StringTag.valueOf("first"));
        list.add(StringTag.valueOf("second"));
        expected.put("codec_value", list);
        assertEquals(expected, strings.serialize(List.of("first", "second"), RegistryAccess.EMPTY));
        assertEquals(List.of("first", "second"), strings.deserialize(expected, RegistryAccess.EMPTY));
    }

    @Test
    void currencyModelCodecRetainsItsLegacyNestedCompoundShape() {
        var coin = new Coin(Identifier.parse("farmupcraft:bronze_coin"), "bronze_coin", 10, Rarity.COMMON);
        var serializer = new CodecDataSerializer<>(CoinStack.CODEC);
        var encoded = serializer.serialize(new CoinStack(coin, 7), RegistryAccess.EMPTY);
        var payload = encoded.getCompound("codec_value").orElseThrow();
        var coinTag = payload.getCompound("coin").orElseThrow();
        assertEquals(7, payload.getInt("amount").orElseThrow());
        assertEquals("farmupcraft:bronze_coin", coinTag.getString("id").orElseThrow());
        assertEquals("bronze_coin", coinTag.getString("translation_key").orElseThrow());
        assertEquals("common", coinTag.getString("rarity").orElseThrow());
        assertEquals(10, coinTag.getInt("value").orElseThrow());
        var decoded = serializer.deserialize(encoded, RegistryAccess.EMPTY);
        assertEquals(7, decoded.getAmount());
        assertEquals(coin.getId(), decoded.getCoin().getId());
        assertEquals(encoded, serializer.serialize(decoded, RegistryAccess.EMPTY));
    }

    @Test
    void codecErrorsArePropagatedWithoutReturningPartialPayloads() {
        var serializer = new CodecDataSerializer<>(Codec.intRange(0, 5));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(-1, RegistryAccess.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(new CompoundTag(), RegistryAccess.EMPTY));
        var invalid = new CompoundTag();
        invalid.putString("codec_value", "not an integer");
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(invalid, RegistryAccess.EMPTY));
        invalid.putInt("codec_value", 6);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(invalid, RegistryAccess.EMPTY));
        assertThrows(NullPointerException.class, () -> new CodecDataSerializer<>(null));
    }

    @Test
    void currentVersionWritesTheLegacyVersionAndVersionedEnvelope() {
        var oldSerializer = new CodecDataSerializer<>(Codec.STRING);
        var newSerializer = new CodecDataSerializer<>(Codec.STRING.fieldOf("text").codec());
        var serializer = new VersionedDataSerializer<>(newSerializer, oldSerializer, 1);
        var expected = new CompoundTag();
        expected.putInt("version", 1);
        expected.put("versioned", newSerializer.serialize("value", RegistryAccess.EMPTY));
        assertEquals(expected, serializer.serialize("value", RegistryAccess.EMPTY));
        assertEquals("value", serializer.deserialize(expected, RegistryAccess.EMPTY));
        assertTrue(serializer.checkVersioned(expected));
    }

    @Test
    void versionZeroAndUnversionedInputsStillUseTheOldSerializer() {
        var oldSerializer = new CodecDataSerializer<>(Codec.STRING);
        var serializer = new VersionedDataSerializer<>(
                new CodecDataSerializer<>(Codec.STRING.fieldOf("text").codec()), oldSerializer, 1);
        var legacy = oldSerializer.serialize("legacy", RegistryAccess.EMPTY);
        assertFalse(serializer.checkVersioned(legacy));
        assertEquals("legacy", serializer.deserialize(legacy, RegistryAccess.EMPTY));
        var explicitZero = legacy.copy();
        explicitZero.putInt("version", 0);
        assertEquals("legacy", serializer.deserialize(explicitZero, RegistryAccess.EMPTY));

        var wrapped = new CompoundTag();
        wrapped.put("versioned", legacy);
        assertEquals("legacy", serializer.deserialize(wrapped, RegistryAccess.EMPTY));
        wrapped.putInt("version", 0);
        assertEquals("legacy", serializer.deserialize(wrapped, RegistryAccess.EMPTY));
    }

    @Test
    void invalidVersionsAndPayloadsFailExplicitly() {
        var values = new CodecDataSerializer<>(Codec.STRING);
        var serializer = new VersionedDataSerializer<>(values, values, 1);
        for (int version : new int[]{-1, 2, Integer.MAX_VALUE}) {
            var invalid = new CompoundTag();
            invalid.putInt("version", version);
            invalid.put("versioned", values.serialize("value", RegistryAccess.EMPTY));
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(invalid, RegistryAccess.EMPTY));
        }
        var missingPayload = new CompoundTag();
        missingPayload.putInt("version", 1);
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(missingPayload, RegistryAccess.EMPTY));
        missingPayload.putString("versioned", "wrong type");
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(missingPayload, RegistryAccess.EMPTY));
        var invalidVersion = new CompoundTag();
        invalidVersion.putString("version", "one");
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(invalidVersion, RegistryAccess.EMPTY));
        for (double version : new double[]{1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            invalidVersion.putDouble("version", version);
            assertThrows(IllegalArgumentException.class,
                    () -> serializer.deserialize(invalidVersion, RegistryAccess.EMPTY));
        }
        invalidVersion.putLong("version", 0x100000001L);
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(invalidVersion, RegistryAccess.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new VersionedDataSerializer<>(values, values, 0));
        assertThrows(IllegalArgumentException.class, () -> new VersionedDataSerializer<>(values, values, -1));
    }

    @Test
    void versionWrapperDoesNotSwallowDelegateFailures() {
        var values = new CodecDataSerializer<>(Codec.intRange(0, 5));
        var serializer = new VersionedDataSerializer<>(values, values, 1);
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(-1, RegistryAccess.EMPTY));
        var invalidPayload = new CompoundTag();
        invalidPayload.putString("codec_value", "wrong type");
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(invalidPayload, RegistryAccess.EMPTY));
        var current = new CompoundTag();
        current.putInt("version", 1);
        current.put("versioned", invalidPayload);
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(current, RegistryAccess.EMPTY));
    }
}
