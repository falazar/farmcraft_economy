package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.structure.BuildableStructure;
import com.falazar.farmupcraft.structure.BuildableStructureInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GhostStructureRenderer {
    private final GhostBlockRenderer internal = new GhostBlockRenderer();
    private BuildableStructureInstance instance;
    private AABB cachedBounds;

    public void setStructure(BuildableStructureInstance instance) {
        this.instance = instance;
        this.cachedBounds = null;
        internal.clearGhostBlocks();

        for (BuildableStructure.StructureBuildTask task : instance.getStructure().blockTasks) {
            BlockPos rotated = instance.rotate(task.relativePos);
            BlockPos worldPos = instance.getOrigin().offset(rotated);
            BlockState rotatedState = task.state.rotate(instance.getRotation());

            internal.addGhostBlock(worldPos, rotatedState);
        }
    }

    public AABB getBoundingBox() {
        if (cachedBounds == null && instance != null) {
            BoundingBox bb = instance.getStructure().getBoundingBox(instance.getOrigin());
            cachedBounds = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
        }
        return cachedBounds != null ? cachedBounds : new AABB(0, 0, 0, 0, 0, 0);
    }

    public boolean isInRange(Vec3 camPos) {
        return getBoundingBox().inflate(128).contains(camPos);
    }

    public void tick() {
        internal.tick();
    }

    public void render(PoseStack stack, Vec3 camPos) {
        internal.render(stack, camPos);
    }

    public void clear() {
        internal.clear();
    }

    public void setAlpha(float alpha) {
        internal.setAlpha(alpha);
    }

    public void setColorTint(int rgb) {
        internal.setColorTint(rgb);
    }

    public void setColorTint(float r, float g, float b) {
        internal.setColorTint(r, g, b);
    }

    public void setColorTint(int r, int g, int b) {
        internal.setColorTint(r, g, b);
    }
}

