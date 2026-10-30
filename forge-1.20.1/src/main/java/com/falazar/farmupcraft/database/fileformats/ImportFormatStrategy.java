package com.falazar.farmupcraft.database.fileformats;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;

public interface ImportFormatStrategy {
    CompoundTag importData(Path filePath) throws IOException;
    String getExtension();
}
