package com.falazar.farmupcraft.database.fileformats;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class JsonImportStrategy implements ImportFormatStrategy {
    private static final Gson GSON = new Gson();

    @Override
    public CompoundTag importData(Path filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            return (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, jsonElement);
        }
    }

    @Override
    public String getExtension() {
        return ".json";
    }
}
