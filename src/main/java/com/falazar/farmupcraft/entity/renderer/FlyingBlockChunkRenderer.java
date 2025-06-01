package com.falazar.farmupcraft.entity.renderer;

import com.falazar.farmupcraft.entity.FlyingBlockChunkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class FlyingBlockChunkRenderer extends EntityRenderer<FlyingBlockChunkEntity> {

    public FlyingBlockChunkRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(FlyingBlockChunkEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getBlockState().getRenderShape() == RenderShape.INVISIBLE) return;

        // Smooth interpolated position
        double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());

        poseStack.pushPose();
        poseStack.translate(- 0.5, 0,  - 0.5);
        //poseStack.mulPose(Axis.YP.rotationDegrees(entity.age * 10F));
        //poseStack.mulPose(Axis.XP.rotationDegrees(entity.age * 5F));

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getBlockState(),
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }



    @Override
    public ResourceLocation getTextureLocation(FlyingBlockChunkEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
