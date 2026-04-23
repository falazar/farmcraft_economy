package com.falazar.farmupcraft.client.border;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
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
import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class BorderRenderer {
    private static final ResourceLocation DENSE_SNOW_LOCATION = prefix("textures/environment/dense_snow.png");
    private static final ResourceLocation BORDER = new ResourceLocation("textures/misc/forcefield.png");

    public static boolean renderClaimedChunk(LevelRenderer levelRenderer, LightTexture pLightTexture,
            float pPartialTick, double pCamX, double pCamY, double pCamZ) {

        Player player = ClientUtils.getClientPlayer();
        if (player == null)
            return false;

        boolean showBorders = player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.DIAMOND);
        if (!showBorders) {
            DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            if (playerDb != null) {
                PlayerData playerData = playerDb.getData(player.getUUID());
                if (playerData != null) {
                    showBorders = playerData.isBorderShow();
                }
            }
        }

        if (isNearClaim(pCamX, pCamZ) && showBorders) {
            renderClaimedChunkBorders(levelRenderer, pLightTexture, pPartialTick, pCamX, pCamY, pCamZ);
        }
        return true;
    }

    private static void renderClaimedChunkBorders(LevelRenderer renderer, LightTexture lightTexture, float partialTick,
            double camX, double camY, double camZ) {
        Level level = Minecraft.getInstance().level;
        if (level == null)
            return;

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
        DataBase<Long, ChunkData> chunkDataDataBase = ModEvents.getChunkDataDatabase(level);

        // Vec3 camera = new Vec3(camX, camY, camZ);
        int camChunkX = Mth.floor(camX) >> 4;
        int camChunkZ = Mth.floor(camZ) >> 4;
        int radius = 12;

        for (int chunkX = camChunkX - radius; chunkX <= camChunkX + radius; chunkX++) {
            for (int chunkZ = camChunkZ - radius; chunkZ <= camChunkZ + radius; chunkZ++) {
                if (!hasClaim(chunkX, chunkZ))
                    continue;

                double x = chunkX * 16;
                double z = chunkZ * 16;
                double yMin = 0;
                double yMax = level.getMaxBuildHeight(); // Or a fixed height, like 256

                float alpha = 0.5f;

                long longKey = ChunkPos.asLong(chunkX, chunkZ);
                // boolean containsKey = chunkDataDataBase.containsKey(ChunkPos.asLong(chunkX,
                // chunkZ));
                //
                // boolean isFarmType = containsKey &&
                // chunkDataDataBase.getData(longKey).getType().equalsIgnoreCase("farm");
                // String type = chunkDataDataBase.getData(longKey).getType().toLowerCase();

                float[] color = getClaimColor();
                float red = color[0], green = color[1], blue = color[2];

                // Temporary for now
                // if (containsKey) {
                // switch (type) {
                // case "farm":
                // red = 0.2f; green = 0.6f; blue = 1.0f; // Light blue
                // break;
                // case "nursery":
                // red = 0.8f; green = 0.4f; blue = 0.0f; // Orange
                // break;
                // case "village":
                // red = 0.4f; green = 0.8f; blue = 0.4f; // Green
                // break;
                // default:
                // red = 0.7f; green = 0.7f; blue = 0.7f; // Gray for unknown types
                // break;
                // }
                // }

                float time = renderer.getTicks() + partialTick;
                float scrollSpeed = 0.05f;
                float scroll = -time * scrollSpeed;

                int light = 0xF000F0;

                // SOUTH wall (Z + 1)
                if (!hasClaim(chunkX, chunkZ + 1)) {
                    renderWall(buffer, x, z + 16, x + 16, z + 16, yMin, yMax, red, green, blue, alpha, scroll, light,
                            camX, camY, camZ);
                }

                if (!hasClaim(chunkX + 1, chunkZ)) {
                    renderWall(buffer, x + 16, z, x + 16, z + 16, yMin, yMax, red, green, blue, alpha, scroll, light,
                            camX, camY, camZ);
                }

                if (!hasClaim(chunkX, chunkZ - 1)) {
                    renderWall(buffer, x + 16, z, x, z, yMin, yMax, red, green, blue, alpha, scroll, light, camX, camY,
                            camZ);
                }

                if (!hasClaim(chunkX - 1, chunkZ)) {
                    renderWall(buffer, x, z, x, z + 16, yMin, yMax, red, green, blue, alpha, scroll, light, camX, camY,
                            camZ);
                }
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
            float scroll, int light, double camX, double camY, double camZ) {
        double height = yMax - yMin;
        float vMin = scroll;
        float vMax = (float) height + scroll;

        buffer.vertex(x1 - camX, yMax - camY, z1 - camZ).uv(0, vMin).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x2 - camX, yMax - camY, z2 - camZ).uv(1, vMin).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x2 - camX, yMin - camY, z2 - camZ).uv(1, vMax).color(r, g, b, a).uv2(light).endVertex();
        buffer.vertex(x1 - camX, yMin - camY, z1 - camZ).uv(0, vMax).color(r, g, b, a).uv2(light).endVertex();
    }

    private static boolean isNearClaim(double xIn, double zIn) {
        final int range = 5;
        int x = (int) xIn >> 4;
        int z = (int) zIn >> 4;

        Level level = Minecraft.getInstance().level;
        Player player = ClientUtils.getClientPlayer();
        if (level == null || player == null)
            return false;

        UUID playerUUID = player.getUUID();

        DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase(level);
        if (!playerDataDataBase.containsKey(playerUUID))
            return false;
        UUID villageId = playerDataDataBase.getData(playerUUID).getHomeVillageUUID();

        DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(level);
        if (!villageDataDB.containsKey(villageId))
            return false;
        boolean nearClaim = false;

        VillageData villageData = villageDataDB.getData(villageId);

        for (int chunkX = -range; (chunkX < range) && !nearClaim; chunkX++) {
            for (int chunkZ = -range; (chunkZ < range) && !nearClaim; chunkZ++) {
                long claimLong = ChunkPos.asLong(x + chunkX, z + chunkZ);
                nearClaim = villageData.getClaimedChunkSet().contains(claimLong);
            }
        }

        return nearClaim;
    }

    // todo move these lookups to before for loop
    private static boolean isClaimedWithSameType(int chunkX, int chunkZ, String currentType) {
        if (!hasClaim(chunkX, chunkZ))
            return false;

        Level level = Minecraft.getInstance().level;
        if (level == null)
            return false;

        DataBase<Long, ChunkData> chunkDataDB = ModEvents.getChunkDataDatabase(level);
        long key = ChunkPos.asLong(chunkX, chunkZ);

        if (!chunkDataDB.containsKey(key))
            return false;

        String neighborType = chunkDataDB.getData(key).getType();
        return neighborType.equalsIgnoreCase(currentType);
    }

    private static boolean hasClaim(int chunkX, int chunkZ) {
        Level level = Minecraft.getInstance().level;
        Player player = ClientUtils.getClientPlayer();
        if (level == null || player == null)
            return false;

        UUID playerUUID = player.getUUID();

        DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase(level);
        if (!playerDataDataBase.containsKey(playerUUID))
            return false;
        UUID villageId = playerDataDataBase.getData(playerUUID).getHomeVillageUUID();

        DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(level);

        if (!villageDataDB.containsKey(villageId))
            return false;

        VillageData village = villageDataDB.getData(villageId);
        return village.getClaimedChunkSet().contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    // todo put this color in village data
    private static float[] getClaimColor() { // border not claim plot?
        Player player = ClientUtils.getClientPlayer();
        String colorName = "blue";
        if (player != null) {
            DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            if (playerDb != null) {
                PlayerData playerData = playerDb.getData(player.getUUID());
                if (playerData != null) {
                    colorName = playerData.getBorderColor();
                }
            }
        }
        return getColorForName(colorName);
    }

    private static float[] getColorForName(String colorName) {
        return switch (colorName.toLowerCase()) {
            case "yellow" -> new float[] { 1.0f, 1.0f, 0.0f };
            case "orange" -> new float[] { 1.0f, 0.5f, 0.0f };
            case "pink" -> new float[] { 1.0f, 0.4f, 0.8f };
            case "teal" -> new float[] { 0.0f, 0.8f, 0.8f };
            case "green" -> new float[] { 0.2f, 0.8f, 0.2f };
            case "red" -> new float[] { 1.0f, 0.2f, 0.2f };
            case "purple" -> new float[] { 0.7f, 0.0f, 1.0f };
            case "white" -> new float[] { 0.9f, 0.9f, 0.9f };
            default -> new float[] { 0.2f, 0.6f, 1.0f }; // blue
        };
    }

}
