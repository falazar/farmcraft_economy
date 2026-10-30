package com.falazar.farmupcraft.database.fileformats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the legacy ordinary JSON representation, not tagged NBT JSON.
 * Numeric tag widths and array tag kinds cannot be preserved by this format.
 */
public class JsonExportStrategy implements ExportFormatStrategy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void export(CompoundTag data, Path filePath) throws IOException {
        JsonElement jsonElement = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data);
        try (var writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(jsonElement));
        }
    }

    @Override
    public String getExtension() {
        return ".json";
    }
}
