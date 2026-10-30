package com.falazar.farmupcraft.database.fileformats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

public class NbtImportStrategy implements ImportFormatStrategy {

    @Override
    public CompoundTag importData(Path filePath) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            return NbtIo.readCompressed(inputStream);
        }
    }

    @Override
    public String getExtension() {
        return ".nbt";
    }
}
