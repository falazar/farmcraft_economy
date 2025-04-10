package com.falazar.farmupcraft.client.border;

import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.ClientUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class BorderRenderer {
    private static final ResourceLocation DENSE_SNOW_LOCATION = prefix("textures/environment/dense_snow.png");
    private static final ResourceLocation BORDER = new ResourceLocation("textures/misc/forcefield.png");
    public static boolean renderClaimedChunk(LevelRenderer levelRenderer, LightTexture pLightTexture, float pPartialTick, double pCamX, double pCamY, double pCamZ) {

        Player player = ClientUtils.getClientPlayer();
        if(player == null) return false;
        boolean showBorders =  player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.DIAMOND);
        if(isNearClaim(pCamX, pCamZ) && showBorders) {
            renderClaimedChunkBorders(levelRenderer, pLightTexture, pPartialTick, pCamX, pCamY, pCamZ);
        }
        return true;
    }
    private static void renderClaimedChunkBorders(LevelRenderer renderer, LightTexture lightTexture, float partialTick, double camX, double camY, double camZ) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        lightTexture.turnOnLightLayer();

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, BORDER);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

        Vec3 camera = new Vec3(camX, camY, camZ);
        int camChunkX = Mth.floor(camX) >> 4;
        int camChunkZ = Mth.floor(camZ) >> 4;
        int radius = 12;

        for (int chunkX = camChunkX - radius; chunkX <= camChunkX + radius; chunkX++) {
            for (int chunkZ = camChunkZ - radius; chunkZ <= camChunkZ + radius; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                if (!hasClaim(pos)) continue;

                double x = chunkX * 16;
                double z = chunkZ * 16;
                double yMin = 0;
                double yMax = level.getMaxBuildHeight(); // Or a fixed height, like 256

                float alpha = 0.5f;
                float[] color = getClaimColor(pos);
                float red = color[0], green = color[1], blue = color[2];

                float time = renderer.getTicks() + partialTick;
                float scrollSpeed = 0.05f;
                float scroll = -time * scrollSpeed;

                int light = 0xF000F0;

                // SOUTH wall (Z + 16)
                renderWall(buffer, x, z + 16, x + 16, z + 16, yMin, yMax, red, green, blue, alpha, scroll, light, camera);
                // NORTH wall (Z)
                renderWall(buffer, x + 16, z, x, z, yMin, yMax, red, green, blue, alpha, scroll, light, camera);
                // WEST wall (X)
                renderWall(buffer, x, z + 16, x, z, yMin, yMax, red, green, blue, alpha, scroll, light, camera);
                // EAST wall (X + 16)
                renderWall(buffer, x + 16, z, x + 16, z + 16, yMin, yMax, red, green, blue, alpha, scroll, light, camera);
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    private static void renderWall(BufferBuilder buffer, double x1, double z1, double x2, double z2,
                                   double yMin, double yMax,
                                   float r, float g, float b, float a,
                                   float scroll, int light, Vec3 cam) {
        double height = yMax - yMin;
        float vMin = scroll;
        float vMax = (float) height + scroll;

        buffer.vertex(x1 - cam.x, yMax - cam.y, z1 - cam.z).uv(0, vMin).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x2 - cam.x, yMax - cam.y, z2 - cam.z).uv(1, vMin).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x2 - cam.x, yMin - cam.y, z2 - cam.z).uv(1, vMax).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x1 - cam.x, yMin - cam.y, z1 - cam.z).uv(0, vMax).color(r, g, b, a).uv2(light).endVertex();
    }


    private static boolean isNearClaim(double xIn, double zIn) {
        final int range = 5;
        int x = (int) xIn >> 4;
        int z = (int) zIn >> 4;
        Level level = Minecraft.getInstance().level;
        boolean nearClaim = false;
        DataBase<String, VillageData> villageDataDataBase = ModEvents.getVillageDatabase(level);
        Optional<VillageData> villageData = villageDataDataBase.getValues().stream().findAny();
        if(villageData.isEmpty()) return false;
        for (int chunkX = -range; (chunkX < range) && !nearClaim; chunkX++) {
            for (int chunkZ = -range; (chunkZ < range) && !nearClaim; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(x + chunkX, z + chunkZ);
                nearClaim = villageData.get().getClaimedChunks().contains(chunkPos);
            }
        }
        return nearClaim;
    }

    private static boolean hasClaim(ChunkPos pos) {
        Level level = Minecraft.getInstance().level;
        DataBase<String, VillageData> villageDataDataBase = ModEvents.getVillageDatabase(level);
        Optional<VillageData> villageData = villageDataDataBase.getValues().stream().findAny();
        if(villageData.isEmpty()) return false;
       return villageData.get().getClaimedChunks().contains(pos);
    }

    private static float[] getClaimColor(ChunkPos pos) {
        return new float[]{0.2f, 0.6f, 1.0f, 0.4f}; // Light blue, transparent
    }


}
