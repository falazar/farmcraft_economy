package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

@JeiPlugin
public class JeiIntegration implements IModPlugin {
    private static IJeiRuntime jeiRuntime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }

    public static IJeiRuntime getRuntime() {
        return jeiRuntime;
    }

    public static void openRecipe(String itemId, Consumer<Component> feedback) {
        if (jeiRuntime == null) {
            feedback.accept(Component.literal("Cannot open recipe: JEI is not ready."));
            return;
        }
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            feedback.accept(Component.literal("Cannot open recipe: unknown or empty item " + itemId));
            return;
        }
        showRecipe(jeiRuntime, stack);
    }

    static void showRecipe(IJeiRuntime runtime, ItemStack stack) {
        var focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        runtime.getRecipesGui().show(List.of(focus));
    }
}
