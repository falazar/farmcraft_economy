package com.falazar.farmupcraft.mixin;

import net.minecraft.world.entity.Display;
import com.mojang.math.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface SpeechDisplayAccessor {
    @Invoker("setBillboardConstraints")
    void farmupcraft$billboard(Display.BillboardConstraints billboard);

    @Invoker("setTransformation")
    void farmupcraft$transformation(Transformation transformation);
}
