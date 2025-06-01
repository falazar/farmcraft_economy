package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.overlay.PlayerDataOverlay;
import com.falazar.farmupcraft.entity.FUCEntities;
import com.falazar.farmupcraft.entity.FlyingBlockChunkEntity;
import com.falazar.farmupcraft.entity.renderer.FlyingBlockChunkRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {


    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("player_data", PlayerDataOverlay.HUD_PLAYER_DATA);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(FUCEntities.FLYING_BLOCK_CHUNK.get(), FlyingBlockChunkRenderer::new);
        });
    }

}
