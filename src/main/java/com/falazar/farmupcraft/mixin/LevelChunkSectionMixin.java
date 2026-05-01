package com.falazar.farmupcraft.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelChunkSection.class)
public class LevelChunkSectionMixin {


    @ModifyVariable(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            index = 4,
            argsOnly = true)
    public BlockState farmUpcraft$replaceBlockState(BlockState value) {
        if (value.is(Blocks.WATER) || value.is(Blocks.IRON_ORE)) {
            return Blocks.STONE.defaultBlockState();
        }
        return value;
    }
}