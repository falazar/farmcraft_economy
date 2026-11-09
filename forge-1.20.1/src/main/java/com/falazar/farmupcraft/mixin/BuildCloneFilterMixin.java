package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.command.BuildCommand;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

@Mixin(CloneCommands.class)
public abstract class BuildCloneFilterMixin {
    @ModifyVariable(method = "clone", at = @At("HEAD"), argsOnly = true)
    private static Predicate<BlockInWorld> farmupcraft$includeOnlyPlannedBlocks(Predicate<BlockInWorld> original) {
        return BuildCommand.cloneFilter(original);
    }
}
