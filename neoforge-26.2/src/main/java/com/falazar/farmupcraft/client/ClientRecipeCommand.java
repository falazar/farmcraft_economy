package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.function.Consumer;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipeCommand {
    private ClientRecipeCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("frecipe")
                .then(Commands.argument("itemId", StringArgumentType.greedyString()).executes(context -> {
                    openRecipeForItem(StringArgumentType.getString(context, "itemId"),
                            context.getSource()::sendSystemMessage);
                    return 1;
                })));
    }

    static void openRecipeForItem(String itemId, Consumer<Component> feedback) {
        if (Identifier.tryParse(itemId) == null) {
            feedback.accept(Component.literal("Cannot open recipe: invalid item identifier " + itemId));
        } else if (!ModList.get().isLoaded("jei")) {
            feedback.accept(Component.literal("Cannot open recipe: JEI is not installed."));
        } else {
            JeiIntegration.openRecipe(itemId, feedback);
        }
    }
}
