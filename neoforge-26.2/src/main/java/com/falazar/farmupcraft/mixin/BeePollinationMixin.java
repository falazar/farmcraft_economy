package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.saveddata.PollinationData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.animal.bee.Bee$BeeGrowCropGoal")
public abstract class BeePollinationMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean farmupcraft$limitPollination(Level level, BlockPos pos, BlockState state) {
        return PollinationData.grow(level, pos, state);
    }
}
