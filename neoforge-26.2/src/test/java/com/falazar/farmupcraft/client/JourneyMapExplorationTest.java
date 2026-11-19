package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.data.ExplorationStats.Area;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JourneyMapExplorationTest {
    @TempDir
    Path directory;

    @Test
    void countsUniqueMappedChunksAcrossNegativeAndPositiveTiles() throws IOException {
        var negative = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        negative.setRGB(511, 511, 0xFF123456);
        ImageIO.write(negative, "png", directory.resolve("-1,-1.png").toFile());
        var positive = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        positive.setRGB(0, 0, 0xFF123456);
        positive.setRGB(15, 15, 0xFF123456);
        positive.setRGB(16, 0, 0xFF123456);
        positive.setRGB(32, 0, 0x00123456);
        ImageIO.write(positive, "png", directory.resolve("0,0.png").toFile());
        Files.writeString(directory.resolve("100,100.png"), "outside the border: do not decode");

        assertEquals(3, JourneyMapExploration.countChunks(directory, new Area(-16, -16, 48, 16)));
    }

    @Test
    void ignoresMappedPixelsOutsideBorderEvenWithinAnIntersectingChunk() throws IOException {
        var image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF123456);
        image.setRGB(16, 16, 0xFF123456);
        ImageIO.write(image, "png", directory.resolve("0,0.png").toFile());
        var area = new Area(1, 1, 17, 17);

        assertEquals(4, area.totalChunks());
        assertEquals(1, JourneyMapExploration.countChunks(directory, area));
    }

    @Test
    void missingOrCorruptTilesFailRatherThanReportZeroCoverage() throws IOException {
        var area = new Area(0, 0, 16, 16);
        assertEquals(0, JourneyMapExploration.countChunks(directory, area));
        assertThrows(IOException.class, () -> JourneyMapExploration.countChunks(directory.resolve("missing"), area));
        Files.writeString(directory.resolve("0,0.png"), "broken");
        assertThrows(IOException.class, () -> JourneyMapExploration.countChunks(directory, area));
    }
}
