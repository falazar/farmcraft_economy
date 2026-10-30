package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CommandPayloadHandler {
    private CommandPayloadHandler() {
    }

    public static void handleHighlight(HighlightChunkPacket payload, IPayloadContext context) {
        if (available("journeymap", context)) {
            JourneyMapChunkOverlays.highlight(payload);
        }
    }

    public static void handleVillage(ShowVillageChunksPacket payload, IPayloadContext context) {
        if (available("journeymap", context)) {
            JourneyMapChunkOverlays.showVillage(payload);
        }
    }

    public static void handleRecipe(OpenJeiRecipePacket payload, IPayloadContext context) {
        if (available("jei", context)) {
            JeiIntegration.openRecipe(payload.itemId(), context.player()::sendSystemMessage);
        }
    }

    public static void loggedOut() {
        if (ModList.get().isLoaded("journeymap")) {
            JourneyMapChunkOverlays.loggedOut();
        }
    }

    private static boolean available(String modId, IPayloadContext context) {
        if (ModList.get().isLoaded(modId)) {
            return true;
        }
        context.player().sendSystemMessage(Component.literal("Cannot display command result: " + modId + " is not installed."));
        return false;
    }
}
