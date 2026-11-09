package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.gems.GemWorld;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class GemGenerationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void farmupcraft$gems(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo callback) {
        GemWorld.generateNew(level, chunk);
    }
}
