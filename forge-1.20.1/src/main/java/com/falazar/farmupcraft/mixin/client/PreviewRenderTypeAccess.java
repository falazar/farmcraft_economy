package com.falazar.farmupcraft.mixin.client;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

public final class PreviewRenderTypeAccess {
    private PreviewRenderTypeAccess() {}

    @Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
    public interface Type {
        @Accessor("state") RenderType.CompositeState farmupcraft$state();
    }

    @Mixin(RenderType.CompositeState.class)
    public interface State {
        @Accessor("textureState") RenderStateShard.EmptyTextureStateShard farmupcraft$texture();
    }

    @Mixin(RenderStateShard.EmptyTextureStateShard.class)
    public interface Texture {
        @Invoker("cutoutTexture") Optional<ResourceLocation> farmupcraft$textureLocation();
    }
}
