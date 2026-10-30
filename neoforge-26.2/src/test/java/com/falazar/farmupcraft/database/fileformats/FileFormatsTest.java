package com.falazar.farmupcraft.database.fileformats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileFormatsTest {
    @TempDir
    Path directory;

    private record Preferences(boolean enabled, List<Integer> scores) {
        private static final Codec<Preferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Preferences::enabled),
                Codec.INT.listOf().fieldOf("scores").forGetter(Preferences::scores)
        ).apply(instance, Preferences::new));
    }

    private record Account(String name, long balance, UUID owner, Preferences preferences, List<Preferences> history) {
        private static final Codec<Account> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Account::name),
                Codec.LONG.fieldOf("balance").forGetter(Account::balance),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Account::owner),
                Preferences.CODEC.fieldOf("preferences").forGetter(Account::preferences),
                Preferences.CODEC.listOf().fieldOf("history").forGetter(Account::history)
        ).apply(instance, Account::new));
    }

    private static Account account() {
        return new Account("Zoë 農場 🌾", 9_007_199_254_740_993L,
                UUID.fromString("00000001-0000-0080-0001-000080000000"),
                new Preferences(true, List.of(1, 128, 32_768, -1)),
                List.of(new Preferences(false, List.of()), new Preferences(true, List.of(7, 70_000))));
    }

    private static CompoundTag database(Account account) {
        CompoundTag key = new CompoundTag();
        key.putString("string_value", "農場-économie");
        CompoundTag value = new CompoundTag();
        value.put("codec_value", Account.CODEC.encodeStart(NbtOps.INSTANCE, account).getOrThrow());
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.put("value", value);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag database = new CompoundTag();
        database.putString("database_name", "économie_日本");
        database.put("map_entry", entries);
        return database;
    }

    private static Account readAccount(CompoundTag database) {
        ListTag entries = database.getList("map_entry").orElseThrow();
        CompoundTag entry = entries.getCompound(0).orElseThrow();
        assertEquals("農場-économie",
                entry.getCompound("key").orElseThrow().getString("string_value").orElseThrow());
        Tag encoded = entry.getCompound("value").orElseThrow().get("codec_value");
        return Account.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void legacyDatabasePayloadRoundTripsThroughCodecs(FileFormat format) throws IOException {
        Path file = directory.resolve("accounts" + format.getExtension());
        CompoundTag original = database(account());
        format.getExportStrategy().export(original, file);
        CompoundTag imported = format.getImportStrategy().importData(file);
        assertEquals("économie_日本", imported.getString("database_name").orElseThrow());
        assertEquals(account(), readAccount(imported));
        if (format == FileFormat.NBT) {
            assertEquals(original, imported);
        }
    }

    @Test
    void jsonExportRemainsOrdinaryUtf8JsonAndNotTaggedNbt() throws IOException {
        Path file = directory.resolve("database.json");
        new JsonExportStrategy().export(database(account()), file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("Zoë 農場 🌾"));
        assertTrue(text.contains("économie_日本"));
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        JsonObject value = json.getAsJsonArray("map_entry").get(0).getAsJsonObject()
                .getAsJsonObject("value").getAsJsonObject("codec_value");
        assertEquals(account().balance(), value.get("balance").getAsLong());
        assertEquals(account().name(), value.get("name").getAsString());
        assertEquals(4, value.getAsJsonArray("owner").size());
        assertEquals(1, value.getAsJsonObject("preferences").get("enabled").getAsInt());
        assertEquals(128, value.getAsJsonObject("preferences").getAsJsonArray("scores").get(1).getAsInt());
    }

    @Test
    void importsHandwrittenLegacyJsonWithBooleanAndMixedWidthNumbers() throws IOException {
        Path file = directory.resolve("legacy.JSON");
        Files.writeString(file, """
                {
                  "database_name": "économie_日本",
                  "map_entry": [{
                    "key": {"string_value": "農場-économie"},
                    "value": {"codec_value": {
                      "name": "Zoë 農場 🌾", "balance": 9007199254740993,
                      "owner": [1, 128, 65536, -2147483648],
                      "preferences": {"enabled": true, "scores": [1, 128, 32768, -1]},
                      "history": [{"enabled": false, "scores": []}, {"enabled": true, "scores": [7, 70000]}]
                    }}
                  }]
                }
                """, StandardCharsets.UTF_8);
        assertEquals(account(), readAccount(new JsonImportStrategy().importData(file)));
    }

    @Test
    void jsonOpsNarrowsNumbersAndUsesPlainListsRatherThanPreservingArrayTags() throws IOException {
        Path file = directory.resolve("types.json");
        Files.writeString(file, """
                {"byte": 1, "short": 128, "int": 32768, "long": 2147483648,
                 "float": 0.5, "double": 0.1, "enabled": true, "disabled": false,
                 "mixed": [1, 128, 32768, 2147483648, 0.5, 0.1]}
                """, StandardCharsets.UTF_8);
        CompoundTag imported = new JsonImportStrategy().importData(file);
        assertInstanceOf(ByteTag.class, imported.get("byte"));
        assertInstanceOf(ShortTag.class, imported.get("short"));
        assertInstanceOf(IntTag.class, imported.get("int"));
        assertInstanceOf(LongTag.class, imported.get("long"));
        assertInstanceOf(FloatTag.class, imported.get("float"));
        assertInstanceOf(DoubleTag.class, imported.get("double"));
        assertEquals(ByteTag.ONE, imported.get("enabled"));
        assertEquals(ByteTag.ZERO, imported.get("disabled"));
        ListTag mixed = assertInstanceOf(ListTag.class, imported.get("mixed"));
        assertInstanceOf(ByteTag.class, mixed.get(0));
        assertInstanceOf(ShortTag.class, mixed.get(1));
        assertInstanceOf(IntTag.class, mixed.get(2));
        assertInstanceOf(LongTag.class, mixed.get(3));
        assertEquals(List.of(1.0, 128.0, 32768.0, 2147483648.0, 0.5, 0.1),
                Codec.DOUBLE.listOf().parse(NbtOps.INSTANCE, mixed).getOrThrow());
    }

    @Test
    void jsonArraysAndUuidKeepCodecValuesButNotNbtTypes() throws IOException {
        CompoundTag original = new CompoundTag();
        original.putByteArray("bytes", new byte[]{-128, 0, 127});
        original.putIntArray("ints", new int[]{1, 128, 32_768, Integer.MAX_VALUE});
        original.putLongArray("longs", new long[]{1, 128, Long.MIN_VALUE, Long.MAX_VALUE});
        original.put("uuid", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, account().owner()).getOrThrow());
        original.putInt("smallInt", 1);
        Path file = directory.resolve("arrays.json");
        new JsonExportStrategy().export(original, file);
        CompoundTag imported = new JsonImportStrategy().importData(file);
        assertNotEquals(original, imported);
        assertInstanceOf(ByteTag.class, imported.get("smallInt"));
        assertInstanceOf(ListTag.class, imported.get("ints"));
        assertFalse(imported.get("ints") instanceof IntArrayTag);
        ByteBuffer bytes = NbtOps.INSTANCE.getByteBuffer(imported.get("bytes")).getOrThrow();
        byte[] actualBytes = new byte[bytes.remaining()];
        bytes.get(actualBytes);
        assertArrayEquals(new byte[]{-128, 0, 127}, actualBytes);
        assertArrayEquals(new int[]{1, 128, 32_768, Integer.MAX_VALUE},
                Codec.INT_STREAM.parse(NbtOps.INSTANCE, imported.get("ints")).getOrThrow().toArray());
        assertArrayEquals(new long[]{1, 128, Long.MIN_VALUE, Long.MAX_VALUE},
                Codec.LONG_STREAM.parse(NbtOps.INSTANCE, imported.get("longs")).getOrThrow().toArray());
        assertEquals(account().owner(), UUIDUtil.CODEC.parse(NbtOps.INSTANCE, imported.get("uuid")).getOrThrow());
    }

    @Test
    void compressedNbtPreservesEveryTagKindExactly() throws IOException {
        CompoundTag original = database(account());
        original.putByte("byte", Byte.MIN_VALUE);
        original.putShort("short", Short.MAX_VALUE);
        original.putInt("int", Integer.MIN_VALUE);
        original.putLong("long", Long.MAX_VALUE);
        original.putFloat("float", -0.0F);
        original.putDouble("double", Math.PI);
        original.putByteArray("bytes", new byte[]{-128, 0, 127});
        original.putIntArray("ints", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        original.putLongArray("longs", new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE});
        original.put("emptyCompound", new CompoundTag());
        original.put("emptyList", new ListTag());
        ListTag mixed = new ListTag();
        mixed.add(StringTag.valueOf("非ASCII"));
        mixed.add(IntTag.valueOf(1));
        mixed.add(new CompoundTag());
        original.put("mixedList", mixed);
        Path file = directory.resolve("all-types.nbt");
        new NbtExportStrategy().export(original, file);
        byte[] compressed = Files.readAllBytes(file);
        assertEquals(0x1f, Byte.toUnsignedInt(compressed[0]));
        assertEquals(0x8b, Byte.toUnsignedInt(compressed[1]));
        assertEquals(original, new NbtImportStrategy().importData(file));
    }

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void propagatesMissingFilesAndExportIoFailures(FileFormat format) {
        assertThrows(IOException.class,
                () -> format.getImportStrategy().importData(directory.resolve("missing" + format.getExtension())));
        assertThrows(IOException.class, () -> format.getExportStrategy().export(new CompoundTag(), directory));
        assertThrows(IOException.class, () -> format.getExportStrategy()
                .export(new CompoundTag(), directory.resolve("missing-parent").resolve("data" + format.getExtension())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "1", "true", "\"text\"", "{", "{\"a\":}", "{} {}",
            "{unquoted:1}", "{\"a\":null}", "{\"a\":[null]}", "{\"a\":1e9999}"})
    void rejectsMalformedOrNonCompoundJson(String json) throws IOException {
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new JsonImportStrategy().importData(file));
    }

    @Test
    void rejectsMalformedCompressedNbtAndNonCompoundRoot() throws IOException {
        Path corrupt = directory.resolve("corrupt.nbt");
        Files.write(corrupt, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> new NbtImportStrategy().importData(corrupt));

        Path truncated = directory.resolve("truncated.nbt");
        new NbtExportStrategy().export(database(account()), truncated);
        byte[] bytes = Files.readAllBytes(truncated);
        Files.write(truncated, Arrays.copyOf(bytes, bytes.length / 2));
        IOException failure = assertThrows(IOException.class, () -> new NbtImportStrategy().importData(truncated));
        assertInstanceOf(ReportedNbtException.class, failure.getCause());

        Path nonCompound = directory.resolve("string-root.nbt");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(nonCompound)))) {
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF("");
            output.writeUTF("not a compound");
        }
        assertThrows(IOException.class, () -> new NbtImportStrategy().importData(nonCompound));
    }

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void extensionsAndCaseInsensitiveLookupMatchStrategies(FileFormat format) {
        assertEquals(format, FileFormat.fromExtension(format.getExtension()));
        assertEquals(format, FileFormat.fromExtension(format == FileFormat.JSON ? ".JSON" : ".NBT"));
        assertEquals(format.getExtension(), format.getImportStrategy().getExtension());
        assertEquals(format.getExtension(), format.getExportStrategy().getExtension());
    }

    @Test
    void rejectsUnsupportedOrMissingExtension() {
        for (String extension : List.of("", "json", "nbt", ".txt", "data.json", ".json ")) {
            assertThrows(IllegalArgumentException.class, () -> FileFormat.fromExtension(extension));
        }
        assertThrows(IllegalArgumentException.class, () -> FileFormat.fromExtension(null));
    }
}
