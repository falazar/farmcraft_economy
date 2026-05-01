package com.falazar.farmupcraft.structure.plan;

import com.falazar.farmupcraft.structure.plan.element.PlanElement;
import com.falazar.farmupcraft.structure.rotation.FixedRotation;
import com.falazar.farmupcraft.structure.rotation.RotationElement;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public class EmptyElement implements PlanElement {
    private final Vec3i size;

    public EmptyElement(Vec3i size) {
        this.size = size;
    }

    @Override
    public Vec3i getSize() {
        return size;
    }

    @Override
    public ResourceLocation getStructureId() {
        return null;
    }

    @Override
    public RotationElement getRotationElement() {
        return new FixedRotation(Rotation.NONE);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toString() {
        return "EmptyElement{" +
                "size=" + size +
                '}';
    }
}
