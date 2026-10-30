package com.falazar.farmupcraft.database.fileformats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;

public class NbtExportStrategy implements ExportFormatStrategy {
    @Override
    public void export(CompoundTag data, Path filePath) throws IOException {
        NbtIo.writeCompressed(data, filePath);
    }

    @Override
    public String getExtension() {
        return ".nbt";
    }
}
