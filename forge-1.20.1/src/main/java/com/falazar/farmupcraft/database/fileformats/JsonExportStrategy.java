package com.falazar.farmupcraft.database.fileformats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class JsonExportStrategy implements ExportFormatStrategy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void export(CompoundTag data, Path filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            JsonElement jsonElement = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data);
            GSON.toJson(jsonElement, writer);
        }
    }

    @Override
    public String getExtension() {
        return ".json";
    }
}
