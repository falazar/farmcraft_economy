package com.falazar.farmupcraft.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
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
    void opensRecipesGuiWithActualRegistryItemAsOutputFocus(MinecraftServer server) {
        var fixture = new Fixture();
        new JeiIntegration().onRuntimeAvailable(fixture.runtime);
        List<Component> feedback = new ArrayList<>();

        JeiIntegration.openRecipe("minecraft:bread", feedback::add);

        assertTrue(feedback.isEmpty());
        assertEquals(RecipeIngredientRole.OUTPUT, fixture.role);
        assertSame(VanillaTypes.ITEM_STACK, fixture.ingredientType);
        assertTrue(fixture.stack.is(Items.BREAD));
        assertEquals(1, fixture.stack.getCount());
        assertEquals(List.of(fixture.focus), fixture.shown);
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
        final IFocusFactory factory = proxy(IFocusFactory.class, (method, arguments) -> {
            assertEquals("createFocus", method);
            role = (RecipeIngredientRole) arguments[0];
            ingredientType = arguments[1];
            stack = (ItemStack) arguments[2];
            return focus;
        });
        final IJeiHelpers helpers = new RecordingHelpers(factory);
        final IRecipesGui gui = proxy(IRecipesGui.class, (method, arguments) -> {
            assertEquals("show", method);
            shown = arguments[0];
            return null;
        });
        final IJeiRuntime runtime = proxy(IJeiRuntime.class, (method, arguments) -> switch (method) {
            case "getJeiHelpers" -> helpers;
            case "getRecipesGui" -> gui;
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
