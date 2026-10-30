package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.saveddata.PollinationData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.world.entity.animal.butterfly.Butterfly$ButterflyGrowCropGoal", remap = false)
public abstract class ButterflyPollinationMixin {
    @Redirect(method = "onReachedTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CropBlock;growCrops(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void farmupcraft$limitPollination(CropBlock crop, Level level, BlockPos pos, BlockState state) {
        PollinationData.butterfly(crop, level, pos, state);
    }
}
