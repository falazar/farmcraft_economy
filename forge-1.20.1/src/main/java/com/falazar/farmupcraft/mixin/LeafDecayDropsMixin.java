package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.TreeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Block.class)
public abstract class LeafDecayDropsMixin {
    // Player breaking uses the separate overload with a breaker and tool, already gated by TreeManager.
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void farmupcraft$limitLeafDecayDrops(BlockState state, ServerLevel level, BlockPos pos,
            BlockEntity blockEntity, CallbackInfoReturnable<List<ItemStack>> callback) {
        if (state.is(BlockTags.LEAVES) && !TreeManager.allowSaplingDrop(pos, level)) {
            callback.setReturnValue(List.of());
        }
    }
}
