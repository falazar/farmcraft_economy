package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the /frecipe client-side command.
 * Clicking an item name in /market show high triggers this — no server
 * involved.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientRecipeCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("frecipe")
                        .then(Commands.argument("itemId", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String itemId = StringArgumentType.getString(ctx, "itemId");
                                    openRecipeForItem(itemId);
                                    return 1;
                                })));
    }

    private static void openRecipeForItem(String itemId) {
        JeiIntegration.openRecipe(itemId, message -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
