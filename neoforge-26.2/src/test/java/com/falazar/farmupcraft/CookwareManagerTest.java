package com.falazar.farmupcraft;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CookwareManagerTest {
    @Test
    void recognizesAllNineRealHarvestCraftTools() {
        for (String id : List.of("cuttingboarditem", "grinderitem", "juiceritem", "mixingbowlitem",
                "potitem", "rolleritem", "saucepanitem", "skilletitem", "bakewareitem")) {
            ItemStack tool = cookware(id);
            assertFalse(tool.is(Items.AIR), id);
            assertTrue(CookwareManager.isCookware(tool), id);
        }
        assertFalse(CookwareManager.isCookware(ItemStack.EMPTY));
        assertFalse(CookwareManager.isCookware(new ItemStack(Items.STICK)));
    }

    @Test
    void startsWith500Or10000UsesOnlyOnFirstUse() {
        ItemStack ordinary = cookware("grinderitem");
        ItemStack kitchen = cookware("potitem");
        assertEquals(500, CookwareManager.getRemainingUses(ordinary));
        assertEquals(499, CookwareManager.consumeUse(ordinary, false));
        assertEquals(498, CookwareManager.consumeUse(ordinary, true));
        assertEquals(9999, CookwareManager.consumeUse(kitchen, true));
        assertEquals(9998, CookwareManager.consumeUse(kitchen, false));
        assertEquals(10000, CookwareManager.getMaxUsesForDisplay(kitchen));
    }

    @Test
    void realHarvestCraftRemainderRetainsUsesAndOtherCustomData() {
        ItemStack tool = cookware("cuttingboarditem");
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("other_mod", "retained"));
        CookwareManager.consumeUse(tool, false);
        var remainder = tool.getCraftingRemainder();
        assertNotNull(remainder);
        ItemStack returned = remainder.create();
        assertEquals(499, CookwareManager.getRemainingUses(returned));
        assertEquals("retained", returned.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_mod").orElseThrow());
        assertEquals(498, CookwareManager.consumeUse(returned, false));
    }

    @Test
    void exhaustedToolKeepsRecipeInputButHasNoCraftingRemainder() {
        ItemStack tool = cookware("juiceritem");
        for (int i = 0; i < 500; i++) {
            assertEquals(499 - i, CookwareManager.consumeUse(tool, false));
        }
        assertEquals(1, tool.getCount());
        assertTrue(CookwareManager.isExhausted(tool));
        assertEquals(0, CookwareManager.getRemainingUses(tool));
        assertNull(tool.getCraftingRemainder());
        var remaining = CraftingRecipe.defaultCraftingReminder(CraftingInput.of(1, 1, List.of(tool)));
        assertTrue(remaining.getFirst().isEmpty());
        assertFalse(tool.isEmpty());
        assertThrows(IllegalStateException.class, () -> CookwareManager.consumeUse(tool, true));
    }

    @Test
    void sharedMixinProvidesLegacyDurabilityBarWithoutChangingVanillaRemainders() {
        ItemStack tool = cookware("skilletitem");
        assertFalse(tool.isBarVisible());
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putInt("farmupcraft_cookware_uses", 250));
        assertTrue(tool.isBarVisible());
        assertEquals(7, tool.getBarWidth());
        assertEquals(0xFFFF00, tool.getBarColor());
        var vanillaRemainder = new ItemStack(Items.MILK_BUCKET).getCraftingRemainder();
        assertNotNull(vanillaRemainder);
        assertTrue(vanillaRemainder.create().is(Items.BUCKET));
    }

    @Test
    void rejectsMalformedCountersAndNonCookwareMutation() {
        ItemStack tool = cookware("bakewareitem");
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("farmupcraft_cookware_uses", "bad"));
        assertThrows(IllegalArgumentException.class, () -> CookwareManager.getRemainingUses(tool));
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putInt("farmupcraft_cookware_uses", -1));
        assertThrows(IllegalArgumentException.class, () -> CookwareManager.getRemainingUses(tool));
        assertThrows(IllegalArgumentException.class, () -> CookwareManager.consumeUse(new ItemStack(Items.STICK), false));
    }

    private static ItemStack cookware(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("pamhc2foodcore", path)));
    }
}
