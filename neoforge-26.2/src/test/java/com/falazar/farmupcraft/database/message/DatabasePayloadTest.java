package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class DatabasePayloadTest {
    private static final String NAME = "farmupcraft:test_database";

    @Test
    void entryAndFullPayloadCodecsPreserveNbtAndIdentifiers() {
        var key = new CompoundTag();
        key.putString("string_value", "key");
        var value = new CompoundTag();
        value.putInt("int_value", 17);
        var entry = roundTrip(DataBaseEntryS2C.STREAM_CODEC, new DataBaseEntryS2C(key, value, NAME));
        assertEquals(key, entry.key());
        assertEquals(value, entry.value());
        assertEquals(NAME, entry.databaseName());
        assertEquals(Identifier.parse("farmupcraft:database_entry"), entry.type().id());

        var snapshot = snapshot();
        var full = roundTrip(DataBaseFullS2C.STREAM_CODEC, new DataBaseFullS2C(snapshot, NAME));
        assertEquals(snapshot, full.database());
        assertEquals(NAME, full.databaseName());
        assertEquals(Identifier.parse("farmupcraft:database_full"), full.type().id());
    }

    @Test
    void payloadsDefensivelySnapshotInputsAndDoNotExposeMutableNbt() {
        var key = new CompoundTag();
        key.putString("string_value", "key");
        var value = new CompoundTag();
        value.putInt("int_value", 1);
        var entry = new DataBaseEntryS2C(key, value, NAME);
        key.putString("string_value", "changed");
        value.putInt("int_value", 2);
        entry.key().putString("string_value", "changed again");
        entry.value().putInt("int_value", 3);
        assertEquals("key", entry.key().getString("string_value").orElseThrow());
        assertEquals(1, entry.value().getInt("int_value").orElseThrow());

        var input = snapshot();
        var full = new DataBaseFullS2C(input, NAME);
        input.putString("database_name", "changed");
        full.database().putString("database_name", "changed again");
        assertEquals(NAME, full.database().getString("database_name").orElseThrow());
    }

    @Test
    void clientboundSnapshotCodecSupportsNbtBeyondTheDefaultTwoMiBQuota() {
        var snapshot = snapshot();
        byte[] content = new byte[3 * 1024 * 1024];
        content[0] = 17;
        content[content.length - 1] = 31;
        snapshot.putByteArray("large_value", content);
        var decoded = roundTrip(DataBaseFullS2C.STREAM_CODEC, new DataBaseFullS2C(snapshot, NAME));
        assertArrayEquals(content, decoded.database().getByteArray("large_value").orElseThrow());
    }

    @Test
    void missingCompoundPayloadIsRejectedByTheWireCodec() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(0);
            buffer.writeUtf(NAME);
            assertThrows(DecoderException.class, () -> DataBaseFullS2C.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void entryApplicationDecodesBothFieldsBeforeChangingTheDatabase() {
        var keys = new StringDataSerializer();
        var values = new IntDataSerializer();
        var registries = RegistryAccess.EMPTY;
        var database = new DataBase<>(NAME, keys, values, false, 0, false, registries);
        try {
            database.putData("existing", 1);
            var key = keys.serialize("existing", registries);
            var value = values.serialize(2, registries);
            DataBasePayloadHandler.applyEntry(database, keys, values, registries, new DataBaseEntryS2C(key, value, NAME));
            assertEquals(2, database.getData("existing"));
            var invalid = new CompoundTag();
            invalid.putString("int_value", "not a number");
            assertThrows(IllegalArgumentException.class, () -> DataBasePayloadHandler.applyEntry(
                    database, keys, values, registries, new DataBaseEntryS2C(key, invalid, NAME)));
            assertEquals(2, database.getData("existing"));
            assertThrows(IllegalArgumentException.class, () -> DataBasePayloadHandler.applyEntry(
                    database, keys, values, registries, new DataBaseEntryS2C(key, value, "farmupcraft:other")));
            assertEquals(1, database.getSize());
        } finally {
            database.shutdown();
        }
    }

    @Test
    void fullSnapshotsReplaceOrClearButMalformedSnapshotsNeverEraseGoodData() {
        var keys = new StringDataSerializer();
        var values = new IntDataSerializer();
        var registries = RegistryAccess.EMPTY;
        var source = new DataBase<>(NAME, keys, values, false, 0, false, registries);
        var target = new DataBase<>(NAME, keys, values, false, 0, false, registries);
        try {
            source.putData("new", 17);
            target.putData("old", 3);
            var full = new DataBaseFullS2C(source.save(new CompoundTag()), NAME);
            DataBasePayloadHandler.applyFull(target, registries, full);
            assertNull(target.getData("old"));
            assertEquals(17, target.getData("new"));

            var malformed = snapshot();
            var entries = new ListTag();
            entries.add(new CompoundTag());
            malformed.put("map_entry", entries);
            assertThrows(IllegalArgumentException.class, () -> DataBasePayloadHandler.applyFull(
                    target, registries, new DataBaseFullS2C(malformed, NAME)));
            assertEquals(17, target.getData("new"));
            assertThrows(IllegalArgumentException.class, () -> DataBasePayloadHandler.applyFull(
                    target, registries, new DataBaseFullS2C(snapshot(), "farmupcraft:other")));
            assertEquals(17, target.getData("new"));
            var wrongIdentity = snapshot();
            wrongIdentity.putString("database_name", "farmupcraft:other");
            assertThrows(IllegalArgumentException.class, () -> DataBasePayloadHandler.applyFull(
                    target, registries, new DataBaseFullS2C(wrongIdentity, NAME)));
            assertEquals(17, target.getData("new"));

            DataBasePayloadHandler.applyFull(target, registries, new DataBaseFullS2C(snapshot(), NAME));
            assertEquals(0, target.getSize());
        } finally {
            source.shutdown();
            target.shutdown();
        }
    }

    @Test
    void entryApplicationUsesTheReceivingWorldRegistryContext(MinecraftServer server) {
        var keys = new StringDataSerializer();
        var values = new CodecDataSerializer<>(Biome.CODEC);
        var registries = server.registryAccess();
        var database = new DataBase<>(NAME, keys, values, false, 0, false, registries);
        try {
            var plains = registries.lookupOrThrow(Registries.BIOME)
                    .get(Identifier.parse("minecraft:plains")).orElseThrow();
            var key = keys.serialize("home", registries);
            var value = values.serialize(plains, registries);
            var payload = roundTrip(DataBaseEntryS2C.STREAM_CODEC, new DataBaseEntryS2C(key, value, NAME));
            DataBasePayloadHandler.applyEntry(database, keys, values, registries, payload);
            assertSame(plains, database.getData("home"));
        } finally {
            database.shutdown();
        }
    }

    private static CompoundTag snapshot() {
        var tag = new CompoundTag();
        tag.putString("database_name", NAME);
        tag.put("map_entry", new ListTag());
        return tag;
    }

    private static <T> T roundTrip(StreamCodec<? super FriendlyByteBuf, T> codec, T payload) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
