package com.falazar.farmupcraft.database.fileformats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Imports ordinary JSON with JsonOps' value-based numeric narrowing. Booleans become
 * byte tags; JSON arrays become lists, potentially with mixed numeric tag widths. Consumers
 * should decode values with their codecs rather than assume original NBT tag types.
 */
public class JsonImportStrategy implements ImportFormatStrategy {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    @Override
    public CompoundTag importData(Path filePath) throws IOException {
        try (var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            if (json == null || !json.isJsonObject()) {
                throw new IOException("Expected a JSON compound root in " + filePath);
            }
            validate(json);
            return (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        } catch (JsonParseException exception) {
            throw new IOException("Malformed JSON in " + filePath, exception);
        }
    }

    private static void validate(JsonElement json) throws IOException {
        if (json.isJsonNull()) {
            throw new IOException("JSON null has no NBT value");
        }
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                validate(entry.getValue());
            }
        } else if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                validate(element);
            }
        } else if (json.getAsJsonPrimitive().isNumber() && !Double.isFinite(json.getAsDouble())) {
            throw new IOException("JSON number is outside the finite NBT numeric range: " + json);
        }
    }

    @Override
    public String getExtension() {
        return ".json";
    }
}
