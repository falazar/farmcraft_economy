package com.falazar.farmupcraft.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class JeiRecipeOpeningTest {
    @AfterEach
    void reset() {
        new JeiIntegration().onRuntimeUnavailable();
    }

    @Test
    void recipeRequestsOpenNativeOutputRecipesWithoutLookingUpUses(MinecraftServer server) {
        var fixture = new Fixture();
        new JeiIntegration().onRuntimeAvailable(fixture.runtime);
        List<Component> feedback = new ArrayList<>();

        JeiIntegration.openRecipe("minecraft:bread", feedback::add);

        assertTrue(feedback.isEmpty());
        assertEquals(RecipeIngredientRole.OUTPUT, fixture.role);
        assertSame(VanillaTypes.ITEM_STACK, fixture.ingredientType);
        assertTrue(fixture.stack.is(Items.BREAD));
        assertEquals(List.of(fixture.focus), fixture.shown);
    }

    @Test
    void usesInputFocusAndReportsNoMatches(MinecraftServer server) {
        var fixture = new Fixture();
        new JeiIntegration().onRuntimeAvailable(fixture.runtime);
        List<Component> feedback = new ArrayList<>();

        JeiIntegration.openUses("minecraft:bread", feedback::add);

        assertEquals(1, feedback.size());
        assertTrue(feedback.getFirst().getString().contains("No recipes found"));
        assertEquals(RecipeIngredientRole.INPUT, fixture.role);
        assertSame(VanillaTypes.ITEM_STACK, fixture.ingredientType);
        assertTrue(fixture.stack.is(Items.BREAD));
        assertEquals(1, fixture.stack.getCount());
        assertNull(fixture.shown);
    }

    @Test
    void sortsExactItemInputsBeforeTaggedOrAlternativeInputs(MinecraftServer server) {
        ItemStack carrot = new ItemStack(Items.CARROT);
        ItemUseRecipe grouped = new ItemUseRecipe("Food", false,
                List.of(List.of(carrot, new ItemStack(Items.POTATO))), List.of(List.of(new ItemStack(Items.BREAD))));
        ItemUseRecipe exact = new ItemUseRecipe("Crafting", true,
                List.of(List.of(new ItemStack(Items.CARROT))), List.of(List.of(new ItemStack(Items.BREAD))));

        List<ItemUseRecipe> sorted = JeiIntegration.sortUses(List.of(grouped, exact));

        assertSame(exact, sorted.getFirst());
        assertSame(grouped, sorted.get(1));
        assertTrue(JeiIntegration.isExactItemInput(carrot, false, List.of(new ItemStack(Items.CARROT))));
        assertFalse(JeiIntegration.isExactItemInput(carrot, true, List.of(new ItemStack(Items.CARROT))));
        assertFalse(JeiIntegration.isExactItemInput(carrot, false,
                List.of(new ItemStack(Items.CARROT), new ItemStack(Items.POTATO))));
    }

    @Test
    void usesCanMoveMatchingSecondIngredientAheadOfOtherIngredients(MinecraftServer server) {
        var fixture = new Fixture();
        var bowl = new ItemStack(Items.BOWL);
        var carrot = new ItemStack(Items.CARROT);
        IRecipeSlotView first = slot(bowl);
        IRecipeSlotView second = slot(carrot);
        IRecipeSlotView output = slot(new ItemStack(Items.RABBIT_STEW));
        var slots = proxy(IRecipeSlotsView.class, (method, arguments) -> switch (method) {
            case "getSlotViews" -> arguments[0] == RecipeIngredientRole.INPUT
                    ? List.of(first, second) : List.of(output);
            default -> throw new AssertionError(method);
        });
        var layout = proxy(IRecipeLayoutDrawable.class, (method, arguments) -> switch (method) {
            case "getRecipeSlotsView" -> slots;
            default -> throw new AssertionError(method);
        });
        var limited = proxy(IRecipeLookup.class, (method, arguments) -> switch (method) {
            case "get" -> Stream.of(new Object());
            default -> throw new AssertionError(method);
        });
        var lookup = proxy(IRecipeLookup.class, (method, arguments) -> switch (method) {
            case "limitFocus" -> limited;
            default -> throw new AssertionError(method);
        });
        var manager = proxy(IRecipeManager.class, (method, arguments) -> switch (method) {
            case "createRecipeLookup" -> lookup;
            case "createRecipeLayoutDrawable" -> Optional.of(layout);
            default -> throw new AssertionError(method);
        });
        IRecipeCategory<?> category = proxy(IRecipeCategory.class, (method, arguments) -> switch (method) {
            case "getRecipeType" -> null;
            case "getTitle" -> Component.literal("Crafting");
            default -> throw new AssertionError(method);
        });
        List<ItemUseRecipe> uses = new ArrayList<>();

        JeiIntegration.collectUses(manager, category, fixture.focus, fixture.focusGroup, carrot, uses);

        assertEquals(1, uses.size());
        assertTrue(uses.getFirst().inputSlots().getFirst().getFirst().is(Items.CARROT));
        assertTrue(uses.getFirst().inputSlots().get(1).getFirst().is(Items.BOWL));
        assertTrue(uses.getFirst().outputSlots().getFirst().getFirst().is(Items.RABBIT_STEW));
        assertTrue(uses.getFirst().exactMatch());
    }

    private static IRecipeSlotView slot(ItemStack item) {
        return proxy(IRecipeSlotView.class, (method, arguments) -> switch (method) {
            case "getItemStacks" -> Stream.of(item);
            case "getTagKey" -> Optional.empty();
            default -> throw new AssertionError(method);
        });
    }

    @Test
    void reportsUnavailableRuntimeUnknownItemAndAirWithoutOpeningGui(MinecraftServer server) {
        List<Component> feedback = new ArrayList<>();
        JeiIntegration.openRecipe("minecraft:bread", feedback::add);
        assertEquals(1, feedback.size());

        var fixture = new Fixture();
        new JeiIntegration().onRuntimeAvailable(fixture.runtime);
        JeiIntegration.openRecipe("farmupcraft:missing_recipe_test_item", feedback::add);
        JeiIntegration.openRecipe("minecraft:air", feedback::add);
        assertEquals(3, feedback.size());
        assertNull(fixture.shown);
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> calls.apply(method.getName(), arguments)));
    }

    private static final class Fixture {
        RecipeIngredientRole role;
        Object ingredientType;
        ItemStack stack;
        Object shown;
        final IFocus<?> focus = proxy(IFocus.class, (method, arguments) -> {
            throw new AssertionError("Unexpected focus call: " + method);
        });
        final IFocusGroup focusGroup = proxy(IFocusGroup.class, (method, arguments) -> {
            if (method.equals("getAllFocuses")) {
                return List.of(focus);
            }
            throw new AssertionError("Unexpected focus group call: " + method);
        });
        final IFocusFactory factory = proxy(IFocusFactory.class, (method, arguments) -> {
            return switch (method) {
                case "createFocus" -> {
                    role = (RecipeIngredientRole) arguments[0];
                    ingredientType = arguments[1];
                    stack = (ItemStack) arguments[2];
                    yield focus;
                }
                case "createFocusGroup" -> focusGroup;
                default -> throw new AssertionError("Unexpected focus factory call: " + method);
            };
        });
        final IRecipeCategoriesLookup categories = proxy(IRecipeCategoriesLookup.class, (method, arguments) -> {
            assertEquals(RecipeIngredientRole.INPUT, role, "only uses requests collect input recipes");
            if (method.equals("limitFocus")) {
                return proxy(IRecipeCategoriesLookup.class, (lookupMethod, lookupArguments) -> {
                    if (lookupMethod.equals("get")) {
                        return Stream.empty();
                    }
                    throw new AssertionError("Unexpected limited category lookup call: " + lookupMethod);
                });
            }
            if (method.equals("get")) {
                return Stream.empty();
            }
            throw new AssertionError("Unexpected recipe category lookup call: " + method);
        });
        final IRecipeManager recipeManager = proxy(IRecipeManager.class, (method, arguments) -> {
            if (method.equals("createRecipeCategoryLookup")) {
                return categories;
            }
            throw new AssertionError("Unexpected recipe manager call: " + method);
        });
        final IJeiHelpers helpers = new RecordingHelpers(factory);
        final IRecipesGui gui = proxy(IRecipesGui.class, (method, arguments) -> {
            if (method.equals("show")) {
                shown = arguments[0];
                return null;
            }
            if (method.equals("showRecipes")) {
                shown = arguments[1];
                return null;
            }
            throw new AssertionError("Unexpected recipes GUI call: " + method);
        });
        final IJeiRuntime runtime = proxy(IJeiRuntime.class, (method, arguments) -> switch (method) {
            case "getJeiHelpers" -> helpers;
            case "getRecipesGui" -> gui;
            case "getRecipeManager" -> recipeManager;
            default -> throw new AssertionError("Unexpected JEI runtime call: " + method);
        });
    }

    // Proxy enumerates every API method with the API's app loader, including Identifier signatures.
    // A concrete fixture stays in the test's TRANSFORMER loader alongside the real Minecraft types.
    private record RecordingHelpers(IFocusFactory factory) implements IJeiHelpers {
        @Override
        public IFocusFactory getFocusFactory() {
            return factory;
        }

        @Override
        public IGuiHelper getGuiHelper() {
            throw unexpected();
        }

        @Override
        public IStackHelper getStackHelper() {
            throw unexpected();
        }

        @Override
        public IModIdHelper getModIdHelper() {
            throw unexpected();
        }

        @Override
        public IColorHelper getColorHelper() {
            throw unexpected();
        }

        @Override
        public IPlatformFluidHelper<?> getPlatformFluidHelper() {
            throw unexpected();
        }

        @Override
        public <T> Optional<IRecipeType<T>> getRecipeType(Identifier id, Class<? extends T> recipeClass) {
            throw unexpected();
        }

        @Override
        public Optional<IRecipeType<?>> getRecipeType(Identifier id) {
            throw unexpected();
        }

        @Override
        public Stream<IRecipeType<?>> getAllRecipeTypes() {
            throw unexpected();
        }

        @Override
        public IIngredientManager getIngredientManager() {
            throw unexpected();
        }

        @Override
        public ICodecHelper getCodecHelper() {
            throw unexpected();
        }

        @Override
        public IVanillaRecipeFactory getVanillaRecipeFactory() {
            throw unexpected();
        }

        @Override
        public IIngredientVisibility getIngredientVisibility() {
            throw unexpected();
        }

        private static AssertionError unexpected() {
            return new AssertionError("Only getFocusFactory is expected when opening a recipe");
        }
    }
}
