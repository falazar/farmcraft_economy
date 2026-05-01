package com.falazar.farmupcraft.structure.plan.element;

import com.falazar.farmupcraft.structure.rotation.RotationElement;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public interface PlanElement {


    Vec3i getSize();
    @Nullable
    ResourceLocation getStructureId();
    RotationElement getRotationElement();

    default Rotation resolveRotation(RandomSource random) {
        return getRotationElement().resolve(random);
    }

    default boolean isEmpty() {
        return getStructureId() == null;
    }}
