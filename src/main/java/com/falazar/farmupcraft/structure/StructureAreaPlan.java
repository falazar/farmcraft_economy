package com.falazar.farmupcraft.structure;

import com.falazar.farmupcraft.structure.plan.element.PlanElement;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;

import java.util.*;

/*public class StructureAreaPlan {
    public record Vec2i(int x, int z) {}

    public enum PlacementStrategy {
        RANDOM, LINE_FROM_MASTER
    }

    private final PlanElement masterStructure;
    private final List<PlanElement> subStructures;
    private final Map<Vec2i, PlanElement> layout = new HashMap<>();
    private final int padding;
    private final PlacementStrategy strategy;
    private final int maxFootprint;

    public StructureAreaPlan(PlanElement masterStructure,
                             List<PlanElement> subStructures,
                             int padding,
                             PlacementStrategy strategy) {
        this.masterStructure = masterStructure;
        this.subStructures = subStructures;
        this.padding = padding;
        this.strategy = strategy;
        this.maxFootprint = calculateMaxFootprint(); // used for spacing and bounding
    }

    private int calculateMaxFootprint() {
        int max = 0;
        List<PlanElement> all = new ArrayList<>(subStructures);
        all.add(masterStructure);
        for (PlanElement elem : all) {
            Vec3i size = elem.getSize();
            max = Math.max(max, Math.max(size.getX(), size.getZ()));
        }
        return max + padding;
    }

    public void generateLayout(RandomSource random) {
        layout.clear();

        // Always place master at center
        Vec2i masterPos = new Vec2i(0, 0);
        layout.put(masterPos, masterStructure);

        List<BoundingBox> occupied = new ArrayList<>();
        occupied.add(boundingBoxAt(masterPos, masterStructure));

        // Generate grid of candidate positions
        int gridSize = (int) Math.ceil(Math.sqrt(subStructures.size() * 2));
        List<Vec2i> candidates = new ArrayList<>();

        int spacing = 8;

        for (int gx = -gridSize; gx <= gridSize; gx++) {
            for (int gz = -gridSize; gz <= gridSize; gz++) {
                if (gx == 0 && gz == 0) continue;
                candidates.add(new Vec2i(gx, gz));
            }
        }

// Shuffle grid coordinates before applying spacing
        Collections.shuffle(candidates, new Random(random.nextLong()));

// Now apply spacing when using the coordinate
        List<Vec2i> spacedCandidates = new ArrayList<>();
        for (Vec2i coord : candidates) {
            spacedCandidates.add(new Vec2i(coord.x * spacing, coord.z * spacing));
        }


        for (PlanElement sub : subStructures) {
            boolean placed = false;
            for (Vec2i tryPos : spacedCandidates) {
                BoundingBox box = boundingBoxAt(tryPos, sub);
                if (!overlapsAny(box, occupied)) {
                    layout.put(tryPos, sub);
                    occupied.add(box);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                System.out.println("Failed to place structure: " + sub);
            }
        }

    }

    private BoundingBox boundingBoxAt(Vec2i pos, PlanElement element) {
        Vec3i size = element.getSize();
        int boxSize = Math.max(size.getX(), size.getZ());
        return new BoundingBox(
                pos.x(), pos.z(),
                pos.x() + boxSize - 1, pos.z() + boxSize - 1
        );
    }

    private boolean overlapsAny(BoundingBox box, List<BoundingBox> others) {
        for (BoundingBox other : others) {
            if (box.intersects(other)) return true;
        }
        return false;
    }

    private static class BoundingBox {
        final int x1, z1, x2, z2;

        public BoundingBox(int x1, int z1, int x2, int z2) {
            this.x1 = Math.min(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.z2 = Math.max(z1, z2);
        }

        public boolean intersects(BoundingBox other) {
            return this.x1 <= other.x2 && this.x2 >= other.x1 &&
                    this.z1 <= other.z2 && this.z2 >= other.z1;
        }
    }

    public Map<Vec2i, PlanElement> getLayout() {
        return layout;
    }

    public PlanElement getMasterStructure() {
        return masterStructure;
    }

    public int getPadding() {
        return padding;
    }

    public PlacementStrategy getStrategy() {
        return strategy;
    }

    public int getFootprint() {
        return maxFootprint;
    }
}*/
public class StructureAreaPlan {
    public record Vec2i(int x, int z) {
        public Vec2i add(int dx, int dz) {
            return new Vec2i(x + dx, z + dz);
        }
    }

    private final PlanElement masterStructure;
    private final List<PlanElement> subStructures;
    private final Map<Vec2i, PlanElement> layout = new HashMap<>();
    private final int padding;

    public StructureAreaPlan(PlanElement masterStructure, List<PlanElement> subStructures, int padding) {
        this.masterStructure = masterStructure;
        this.subStructures = subStructures;
        this.padding = padding;
    }

    public void generateLayout(RandomSource random) {
        layout.clear();

        List<PlacedStructure> placed = new ArrayList<>();

        // Place master at origin
        Vec2i masterPos = new Vec2i(0, 0);
        BoundingBox masterBox = boundingBoxAt(masterPos, masterStructure);
        placed.add(new PlacedStructure(masterPos, masterStructure, masterBox));

        // Estimate bounding area
        int totalArea = 0;
        for (PlanElement e : subStructures) {
            Vec3i s = e.getSize();
            totalArea += (s.getX() + padding) * (s.getZ() + padding);
        }
        int side = (int) Math.ceil(Math.sqrt(totalArea)) * 2; // More room to breathe


        // Place substructures randomly
        for (PlanElement elem : subStructures) {
            boolean placedSuccessfully = false;
            for (int attempt = 0; attempt < 1000; attempt++) {
                int x = random.nextInt(-side / 2, side / 2);
                int z = random.nextInt(-side / 2, side / 2);
                Vec2i pos = new Vec2i(x, z);
                BoundingBox box = boundingBoxAt(pos, elem);

                if (placed.stream().noneMatch(p -> p.box.intersects(box))) {
                    placed.add(new PlacedStructure(pos, elem, box));
                    placedSuccessfully = true;
                    break;
                }
            }
            if (!placedSuccessfully) {
                System.out.println("Failed to place: " + elem);
            }
        }

        // Move all non-master structures inward
        Vec2i center = masterPos;
        boolean moved;
        do {
            moved = false;
            for (PlacedStructure ps : placed) {
                if (ps.element == masterStructure) continue;

                Vec2i delta = directionToward(ps.pos, center);
                Vec2i newPos = ps.pos.add(delta.x, delta.z);
                BoundingBox newBox = boundingBoxAt(newPos, ps.element);

                boolean collision = placed.stream()
                        .filter(p -> p != ps)
                        .anyMatch(p -> p.box.intersects(newBox));

                if (!collision) {
                    ps.pos = newPos;
                    ps.box = newBox;
                    moved = true;
                }
            }
        } while (moved);

        // Finalize layout
        for (PlacedStructure ps : placed) {
            layout.put(ps.pos, ps.element);
        }
    }

    private Vec2i directionToward(Vec2i from, Vec2i to) {
        int dx = Integer.compare(to.x - from.x, 0);
        int dz = Integer.compare(to.z - from.z, 0);
        return new Vec2i(dx, dz);
    }

    private BoundingBox boundingBoxAt(Vec2i pos, PlanElement element) {
        Vec3i size = element.getSize();
        int boxSize = Math.max(size.getX(), size.getZ()) + padding;
        return new BoundingBox(
                pos.x, pos.z,
                pos.x + boxSize - 1,
                pos.z + boxSize - 1
        );
    }

    private static class PlacedStructure {
        Vec2i pos;
        PlanElement element;
        BoundingBox box;

        PlacedStructure(Vec2i pos, PlanElement element, BoundingBox box) {
            this.pos = pos;
            this.element = element;
            this.box = box;
        }
    }

    private static class BoundingBox {
        final int x1, z1, x2, z2;

        BoundingBox(int x1, int z1, int x2, int z2) {
            this.x1 = Math.min(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.z2 = Math.max(z1, z2);
        }

        boolean intersects(BoundingBox other) {
            return this.x1 <= other.x2 && this.x2 >= other.x1 &&
                    this.z1 <= other.z2 && this.z2 >= other.z1;
        }
    }

    public Map<Vec2i, PlanElement> getLayout() {
        return layout;
    }
}

