package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import com.mojang.brigadier.arguments.StringArgumentType;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

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
        IJeiRuntime runtime = JeiIntegration.getRuntime();
        if (runtime == null)
            return;

        ResourceLocation rl = new ResourceLocation(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null)
            return;

        ItemStack stack = new ItemStack(item);
        var focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        runtime.getRecipesGui().show(List.of(focus));
    }
}
