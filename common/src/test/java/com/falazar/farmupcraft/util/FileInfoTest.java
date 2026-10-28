package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileInfoTest {
    @Test
    void formatsTheSuppliedClassCodeSourceModificationTime() throws Exception {
        Path codeSource = Path.of(FileInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String expected = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date(Files.getLastModifiedTime(codeSource).toMillis()));

        assertEquals(expected, FileInfo.getJarModificationTime(FileInfo.class));
    }
}
