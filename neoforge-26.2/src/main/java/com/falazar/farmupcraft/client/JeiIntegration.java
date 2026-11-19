package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@JeiPlugin
public class JeiIntegration implements IModPlugin {
    private static IJeiRuntime jeiRuntime;
    private static ItemUsesCategory itemUsesCategory;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        itemUsesCategory = new ItemUsesCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(itemUsesCategory);
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
        openItem(itemId, feedback, false);
    }

    public static void openUses(String itemId, Consumer<Component> feedback) {
        openItem(itemId, feedback, true);
    }

    private static void openItem(String itemId, Consumer<Component> feedback, boolean uses) {
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
        if (uses) {
            showUses(jeiRuntime, stack, feedback);
        } else {
            var focus = jeiRuntime.getJeiHelpers().getFocusFactory()
                    .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
            jeiRuntime.getRecipesGui().show(List.of(focus));
        }
    }

    private static void showUses(IJeiRuntime runtime, ItemStack stack, Consumer<Component> feedback) {
        var focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> focus = focusFactory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
        List<IFocus<?>> focuses = List.of(focus);
        IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
        IRecipeManager recipeManager = runtime.getRecipeManager();
        List<ItemUseRecipe> uses = new ArrayList<>();

        recipeManager.createRecipeCategoryLookup().limitFocus(focuses).get()
                .forEach(category -> collectUses(recipeManager, category, focus, focusGroup, stack, uses));

        if (uses.isEmpty()) {
            feedback.accept(Component.literal("No recipes found that use " + itemId(stack) + "."));
            return;
        }
        if (itemUsesCategory == null) {
            feedback.accept(Component.literal("Cannot open recipe uses: JEI category is not ready."));
            return;
        }
        runtime.getRecipesGui().showRecipes(itemUsesCategory, sortUses(uses), focuses);
    }

    static <T> void collectUses(IRecipeManager recipeManager, IRecipeCategory<T> category,
            IFocus<?> focus, IFocusGroup focusGroup, ItemStack target, List<ItemUseRecipe> uses) {
        recipeManager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get()
                .forEach(recipe -> {
                    var layout = recipeManager.createRecipeLayoutDrawable(category, recipe, focusGroup);
                    if (layout.isEmpty()) {
                        return;
                    }

                    List<IRecipeSlotView> inputSlots = layout.get().getRecipeSlotsView()
                            .getSlotViews(RecipeIngredientRole.INPUT).stream()
                            .filter(slot -> slot.getItemStacks().findAny().isPresent())
                            .toList();
                    int matchingSlot = -1;
                    boolean exactMatch = false;
                    for (int i = 0; i < inputSlots.size(); i++) {
                        IRecipeSlotView slot = inputSlots.get(i);
                        List<ItemStack> items = slot.getItemStacks().toList();
                        if (items.stream().noneMatch(item -> item.is(target.getItem()))) {
                            continue;
                        }
                        if (matchingSlot < 0) {
                            matchingSlot = i;
                        }
                        if (isExactItemInput(target, slot.getTagKey().isPresent(), items)) {
                            exactMatch = true;
                        }
                    }
                    if (matchingSlot < 0) {
                        return;
                    }

                    List<List<ItemStack>> inputs = new ArrayList<>(copySlots(inputSlots));
                    if (matchingSlot > 0) {
                        List<ItemStack> selected = inputs.remove(matchingSlot);
                        inputs.add(0, selected);
                    }
                    List<List<ItemStack>> outputs = copySlots(layout.get().getRecipeSlotsView()
                            .getSlotViews(RecipeIngredientRole.OUTPUT));
                    uses.add(new ItemUseRecipe(category.getTitle().getString(), exactMatch, inputs, outputs));
                });
    }

    static boolean isExactItemInput(ItemStack target, boolean tagged, List<ItemStack> alternatives) {
        return !tagged && alternatives.stream().map(ItemStack::getItem).distinct().count() == 1
                && alternatives.stream().anyMatch(item -> item.is(target.getItem()));
    }

    static List<ItemUseRecipe> sortUses(List<ItemUseRecipe> uses) {
        return uses.stream()
                .sorted(Comparator.comparing(ItemUseRecipe::exactMatch).reversed()
                        .thenComparing(ItemUseRecipe::sourceCategory))
                .toList();
    }

    private static List<List<ItemStack>> copySlots(List<IRecipeSlotView> slots) {
        return slots.stream()
                .map(slot -> slot.getItemStacks().map(ItemStack::copy).toList())
                .filter(items -> !items.isEmpty())
                .toList();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
