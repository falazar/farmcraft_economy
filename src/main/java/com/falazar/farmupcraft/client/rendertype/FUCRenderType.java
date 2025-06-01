package com.falazar.farmupcraft.client.rendertype;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;


public class FUCRenderType extends RenderType{
    private static final RenderType GHOST_BLOCK = RenderType.create(
            "ghost_block",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setTextureState(RenderType.BLOCK_SHEET_MIPPED)
                    .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                    .setOutputState(RenderType.ITEM_ENTITY_TARGET) // preserves alpha
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setCullState(CULL)
                    .createCompositeState(false)
    );

    public static RenderType getGhostBlock() {
        return GHOST_BLOCK;
    }

    private static final RenderType GHOST_CUTOUT_MIPPED = RenderType.create(
            "ghost_cutout_mipped",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            131072,
            true,  // affects crumbling
            false, // sortOnUpload
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_CUTOUT_MIPPED_SHADER)
                    .setTextureState(RenderType.BLOCK_SHEET_MIPPED)
                    .setLightmapState(RenderType.LIGHTMAP)
                    .setOverlayState(RenderType.OVERLAY)
                    // optional: add translucent blend for "ghosty" fade
                    // if you want alpha blending instead of full opaque
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderType.ITEM_ENTITY_TARGET) // preserves alpha
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.CULL)
                    .createCompositeState(true)
    );

    public static RenderType getGhostCutoutMipped() {
        return GHOST_CUTOUT_MIPPED;
    }

    public FUCRenderType(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }
}

