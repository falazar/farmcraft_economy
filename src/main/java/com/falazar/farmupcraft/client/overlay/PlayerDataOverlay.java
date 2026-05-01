package com.falazar.farmupcraft.client.overlay;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.UUID;

public class PlayerDataOverlay {

    public static final IGuiOverlay HUD_PLAYER_DATA = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        UUID playerUUID = mc.player.getUUID();
        var level = mc.level;
        PlayerData playerData = ModEvents.getPlayerDatabase(level).getData(playerUUID);
        if (playerData == null)
            return; // log error?

        // Get current chunk and village info
        // TODO helper method.
        BlockPos playerPos = mc.player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos);
        long chunkKey = chunkPos.toLong();
        DataBase<Long, ChunkData> chunkDataDataBase = ModEvents.getChunkDataDatabase(level);
        ChunkData chunkData = chunkDataDataBase.getData(chunkKey);
        String currentPlotType = "None";
        String currentVillage = "wilderness";
        // TODO make method
        if (chunkData != null) {
            if (chunkData.getType() != null)
                currentPlotType = chunkData.getType();
            UUID villageId = chunkData.getVillageId();
            if (villageId != null) {
                VillageData standingVillage = ModEvents.getVillageDatabase(level).getData(villageId);
                if (standingVillage != null) {
                    currentVillage = standingVillage.getName();
                }
            }
        }

        // Show 4 lines of text in the top left corner of the screen.
        // Top-left position
        int paddingLeft = 6;
        int paddingTop = 6;
        int lineSpacing = 10;
        int currentY = paddingTop;

        // Show Current village/location player is standing in.
        guiGraphics.drawString(mc.font, Component.literal("Location: " + currentVillage), paddingLeft, currentY,
                0xAAAAAA, true);
        currentY += lineSpacing;

        // Show Plot type.
        if (currentPlotType.equals("village")) {
            currentPlotType = "village unclaimed";
        }
        if (!currentPlotType.equals("None")) {
            guiGraphics.drawString(mc.font, Component.literal("Plot: " + currentPlotType), paddingLeft, currentY,
                    0xCCCCCC, true);
            currentY += lineSpacing;
        }

        // Show Home village.
        VillageData homeVillage = null;
        if (playerData.getHomeVillageUUID() != null) {
            homeVillage = ModEvents.getVillageDatabase(level).getData(playerData.getHomeVillageUUID());
        }
        String homeVillageText = homeVillage != null ? "Home: " + homeVillage.getName() : "No Village";
        guiGraphics.drawString(mc.font, Component.literal(homeVillageText), paddingLeft, currentY, 0xFFFFFF, true);
        currentY += lineSpacing;

        guiGraphics.drawString(mc.font, Component.literal("Coins: " + playerData.getCoins()), paddingLeft, currentY,
                0xFFD700, true);
    };
}
