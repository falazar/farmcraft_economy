package com.falazar.farmupcraft.structure;

import com.falazar.farmupcraft.structure.plan.element.PlanElement;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.Vec3i;

import java.util.*;

public class StructurePlanner {
    private final Map<Vec2i, PlanElement> layout;
    private final Vec2i origin;
    private final int width, depth;

    public static final CustomLogger LOGGER = new CustomLogger(StructurePlanner.class.getSimpleName());

    private StructurePlanner(Map<Vec2i, PlanElement> layout, Vec2i origin, int width, int depth) {
        this.layout = layout;
        this.origin = origin;
        this.width = width;
        this.depth = depth;
    }

    public Map<Vec2i, PlanElement> getLayout() {
        return layout;
    }

    public Vec2i getOrigin() {
        return origin;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public static Builder builder(char originChar) {
        return new Builder(originChar);
    }

    public record Vec2i(int x, int z) {}




    public static class Builder {
        private final Map<Character, PlanElement> symbolMap = new HashMap<>();
        private final List<char[]> pattern = new ArrayList<>();
        private final char originChar;
        private int spacingX = 0, spacingZ = 0;

        public Builder(char originChar) {
            this.originChar = originChar;
        }

        public Builder define(char symbol, PlanElement element) {
            symbolMap.put(symbol, element);
            return this;
        }

        public Builder row(String line) {
            pattern.add(line.toCharArray());
            return this;
        }

        public Builder setSpacing(int spacingX, int spacingZ) {
            this.spacingX = spacingX;
            this.spacingZ = spacingZ;
            return this;
        }

        public StructurePlanner build() {
            int rows = pattern.size();
            int cols = pattern.get(0).length;

            int originX = -1, originZ = -1;
            for (int z = 0; z < rows; z++) {
                for (int x = 0; x < pattern.get(z).length; x++) {
                    if (pattern.get(z)[x] == originChar) {
                        originX = x;
                        originZ = z;
                        break;
                    }
                }
            }
            if (originX == -1 || originZ == -1) {
                throw new IllegalStateException("Origin character '" + originChar + "' not found!");
            }

            Map<Integer, Integer> colWidths = new HashMap<>();
            Map<Integer, Integer> rowHeights = new HashMap<>();

            for (int z = 0; z < rows; z++) {
                for (int x = 0; x < pattern.get(z).length; x++) {
                    PlanElement elem = symbolMap.get(pattern.get(z)[x]);
                    if (elem == null) continue;
                    int safeSize = Math.max(elem.getSize().getX(), elem.getSize().getZ());
                    colWidths.merge(x, safeSize, Math::max);
                    rowHeights.merge(z, safeSize, Math::max);

                }
            }

            Map<Integer, Integer> xOffsets = new HashMap<>();
            Map<Integer, Integer> zOffsets = new HashMap<>();
            int cumulativeX = 0;
            for (int x = 0; x < cols; x++) {
                xOffsets.put(x, cumulativeX);
                cumulativeX += colWidths.getOrDefault(x, 0) + spacingX;
            }
            int cumulativeZ = 0;
            for (int z = 0; z < rows; z++) {
                zOffsets.put(z, cumulativeZ);
                cumulativeZ += rowHeights.getOrDefault(z, 0) + spacingZ;
            }

            int originXOffset = xOffsets.get(originX);
            int originZOffset = zOffsets.get(originZ);

            Map<Vec2i, PlanElement> layout = new HashMap<>();
            for (int z = 0; z < rows; z++) {
                for (int x = 0; x < pattern.get(z).length; x++) {
                    char symbol = pattern.get(z)[x];
                    PlanElement elem = symbolMap.get(symbol);
                    if (elem == null) continue;

                    int realX = xOffsets.get(x) - originXOffset;
                    int realZ = zOffsets.get(z) - originZOffset;
                    layout.put(new Vec2i(realX, realZ), elem);
                }
            }

            return new StructurePlanner(layout, new Vec2i(0, 0), cumulativeX, cumulativeZ);
        }
    }
}
