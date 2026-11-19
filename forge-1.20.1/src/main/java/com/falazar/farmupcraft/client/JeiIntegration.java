package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers this mod with JEI and stores the runtime for client command use.
 */
@JeiPlugin
public class JeiIntegration implements IModPlugin {

    private static IJeiRuntime jeiRuntime;
    private static List<ItemUseRecipe> currentUses = List.of();

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ItemUsesCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(new IRecipeManagerPlugin() {
            @Override public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) { return List.of(); }
            @Override public <T, V> List<T> getRecipes(IRecipeCategory<T> category, IFocus<V> focus) {
                return List.of();
            }
            @Override public <T> List<T> getRecipes(IRecipeCategory<T> category) {
                if (!category.getRecipeType().equals(ItemUsesCategory.TYPE)) return List.of();
                return currentUses.stream().<T>map(category.getRecipeType().getRecipeClass()::cast).toList();
            }
        });
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(FarmUpCraft.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
        currentUses = List.of();
    }

    public static IJeiRuntime getRuntime() {
        return jeiRuntime;
    }

    public static void openRecipe(String itemId, Consumer<Component> feedback) {
        IJeiRuntime runtime = jeiRuntime;
        if (runtime == null) {
            feedback.accept(Component.literal("Cannot open recipe: JEI is not ready."));
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        ItemStack target = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (target.isEmpty()) {
            feedback.accept(Component.literal("Cannot open recipe: unknown or empty item " + itemId));
            return;
        }
        var factory = runtime.getJeiHelpers().getFocusFactory();
        var focus = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, target);
        IFocusGroup group = factory.createFocusGroup(List.of(focus));
        List<ItemUseRecipe> uses = new ArrayList<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                .filter(category -> !category.getRecipeType().equals(ItemUsesCategory.TYPE))
                .forEach(category -> collectUses(manager, category, focus, group, target, uses));
        currentUses = uses.stream().sorted(Comparator.comparing(ItemUseRecipe::exactMatch).reversed()
                .thenComparing(ItemUseRecipe::sourceCategory)).toList();
        if (currentUses.isEmpty()) {
            feedback.accept(Component.literal("No recipes found that use " + itemId + "."));
            return;
        }
        runtime.getRecipesGui().showTypes(List.of(ItemUsesCategory.TYPE));
    }

    private static <T> void collectUses(IRecipeManager manager, IRecipeCategory<T> category, IFocus<?> focus,
                                       IFocusGroup group, ItemStack target, List<ItemUseRecipe> uses) {
        manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().forEach(recipe ->
                manager.createRecipeLayoutDrawable(category, recipe, group).ifPresent(layout -> {
                    var slots = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream()
                            .filter(slot -> slot.getItemStacks().findAny().isPresent()).toList();
                    int matching = -1;
                    boolean exact = recipe instanceof Recipe<?> vanilla && ItemUseRecipe.hasExactInput(vanilla, target);
                    for (int i = 0; i < slots.size(); i++) {
                        var items = slots.get(i).getItemStacks().toList();
                        if (items.stream().noneMatch(stack -> stack.is(target.getItem()))) continue;
                        if (matching < 0) matching = i;
                        if (!(recipe instanceof Recipe<?>)) {
                            exact |= items.stream().allMatch(stack -> stack.is(target.getItem()));
                        }
                    }
                    if (matching < 0) return;
                    List<List<ItemStack>> inputs = copySlots(slots);
                    if (matching > 0) inputs.add(0, inputs.remove(matching));
                    uses.add(new ItemUseRecipe(category.getTitle().getString(), exact, inputs,
                            copySlots(layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT))));
                }));
    }

    private static List<List<ItemStack>> copySlots(List<IRecipeSlotView> slots) {
        return slots.stream().map(slot -> slot.getItemStacks().map(ItemStack::copy).toList())
                .filter(items -> !items.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
