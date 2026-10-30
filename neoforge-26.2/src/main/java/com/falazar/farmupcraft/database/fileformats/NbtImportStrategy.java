package com.falazar.farmupcraft.database.fileformats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;

import java.io.IOException;
import java.nio.file.Path;

public class NbtImportStrategy implements ImportFormatStrategy {
    @Override
    public CompoundTag importData(Path filePath) throws IOException {
        try {
            return NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
        } catch (ReportedNbtException exception) {
            throw new IOException("Malformed compressed NBT in " + filePath, exception);
        }
    }

    @Override
    public String getExtension() {
        return ".nbt";
    }
}
