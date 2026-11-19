package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Duration;
import java.util.Locale;

public record ExplorationStats(Area area, long mappedChunks, long updatedAt) {
    public static final Codec<ExplorationStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Area.CODEC.fieldOf("area").forGetter(ExplorationStats::area),
            Codec.LONG.fieldOf("mapped_chunks").forGetter(ExplorationStats::mappedChunks),
            Codec.LONG.fieldOf("updated_at").forGetter(ExplorationStats::updatedAt)
    ).apply(instance, ExplorationStats::new));

    public ExplorationStats {
        if (mappedChunks < 0 || mappedChunks > area.totalChunks() || updatedAt < 0) {
            throw new IllegalArgumentException("Invalid exploration counts or timestamp");
        }
    }

    public boolean isFresh(Area currentArea, long now) {
        return area.equals(currentArea) && now >= updatedAt
                && now - updatedAt < Duration.ofDays(1).toMillis();
    }

    public String summary() {
        return String.format(Locale.ROOT, "Explored (JourneyMap, overworld): %,d / %,d chunks (%d%%)",
                mappedChunks, area.totalChunks(), Math.round(100.0 * mappedChunks / area.totalChunks()));
    }

    // Block-coordinate rectangle, with exclusive upper bounds. Edge chunks count once.
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        public static final Codec<Area> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("min_x").forGetter(Area::minX),
                Codec.INT.fieldOf("min_z").forGetter(Area::minZ),
                Codec.INT.fieldOf("max_x").forGetter(Area::maxX),
                Codec.INT.fieldOf("max_z").forGetter(Area::maxZ)
        ).apply(instance, Area::new));

        public Area {
            if (minX >= maxX || minZ >= maxZ || minX < -30_000_000 || minZ < -30_000_000
                    || maxX > 30_000_000 || maxZ > 30_000_000) {
                throw new IllegalArgumentException("Invalid exploration area");
            }
        }

        public long totalChunks() {
            long width = (long) Math.floorDiv(maxX - 1, 16) - Math.floorDiv(minX, 16) + 1;
            long height = (long) Math.floorDiv(maxZ - 1, 16) - Math.floorDiv(minZ, 16) + 1;
            return width * height;
        }
    }
}
