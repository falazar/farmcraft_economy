package com.falazar.farmupcraft.structure.plan;

import com.falazar.farmupcraft.structure.BuildableStructureRegistry;
import com.falazar.farmupcraft.structure.plan.element.PlanElement;
import com.falazar.farmupcraft.structure.rotation.RotationElement;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class StructureElement implements PlanElement {
    private final ResourceLocation structureId;
    private final RotationElement rotation;

    public StructureElement(ResourceLocation structureId, RotationElement rotation) {
        this.structureId = structureId;


        this.rotation = rotation;
    }

    @Override
    public Vec3i getSize() {
        return BuildableStructureRegistry.get(structureId).structureSize;
    }

    @Override
    public ResourceLocation getStructureId() {
        return structureId;
    }

    @Override
    public RotationElement getRotationElement() {
        return rotation;
    }

    @Override
    public String toString() {
        return "StructureElement{" +
                "structureId=" + structureId +
                ", size=" + getSize() +
                ", rotation=" + rotation +
                '}';
    }
}
