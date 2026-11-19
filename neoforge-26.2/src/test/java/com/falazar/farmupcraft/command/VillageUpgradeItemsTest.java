package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class VillageUpgradeItemsTest {
    private static VillageData village() {
        return new VillageData(UUID.randomUUID(), "Home", ChunkPos.ZERO, 6, List.of(), true,
                100, "2000-01-01", "", "2000-01-01", "Founder", List.of());
    }

    @Test
    void foodPoolUsesHighestNutritionDecileWithBoundaryTiesAndIgnoresNonFoods() {
        var tenFoods = List.of(Items.RABBIT_STEW, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.PUMPKIN_PIE,
                Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.BREAD, Items.APPLE, Items.CARROT, Items.MELON_SLICE);
        assertEquals(List.of(Items.RABBIT_STEW), VillageUpgradeItems.highNutritionFoods(tenFoods));
        var elevenFoods = new java.util.ArrayList<>(tenFoods);
        elevenFoods.add(Items.SWEET_BERRIES);
        elevenFoods.add(Items.DIAMOND);
        var pool = VillageUpgradeItems.highNutritionFoods(elevenFoods);
        assertEquals(4, pool.size());
        assertTrue(pool.containsAll(List.of(Items.RABBIT_STEW, Items.COOKED_BEEF,
                Items.COOKED_PORKCHOP, Items.PUMPKIN_PIE)));
        assertThrows(IllegalStateException.class, () -> VillageUpgradeItems.highNutritionFoods(List.of(Items.STONE)));
    }

    @Test
    void selectionsSurviveJsonNbtReloadAndLevelChangesWithoutRerolling() {
        var village = village();
        assertTrue(village.getUpgradeItems().isEmpty());
        var foods = List.of(Items.RABBIT_STEW, Items.COOKED_BEEF);
        var gems = List.of(Items.DIAMOND, Items.EMERALD);
        assertTrue(VillageUpgradeItems.ensureChoices(village, foods, gems, new Random(7)));
        var original = village.getUpgradeItems();
        assertEquals(4, original.size());
        assertFalse(VillageUpgradeItems.ensureChoices(village, foods, gems, new Random(99)));
        assertThrows(UnsupportedOperationException.class, () -> original.clear());
        for (int target = 7; target <= 10; target++) {
            var requirement = VillageUpgradeItems.requirement(village, target);
            assertTrue((target % 2 == 1 ? foods : gems).contains(requirement.item()));
            assertEquals(target < 9 ? 64 : 128, requirement.count());
        }
        assertNull(VillageUpgradeItems.requirement(village, 6));
        for (var ops : List.of(JsonOps.INSTANCE, NbtOps.INSTANCE)) {
            roundTrip(village, ops);
        }
        village.setLevel(8);
        assertFalse(VillageUpgradeItems.ensureChoices(village, foods, gems, new Random(18)));
        assertEquals(original, village.getUpgradeItems());
    }

    private static <T> void roundTrip(VillageData village, com.mojang.serialization.DynamicOps<T> ops) {
        T encoded = VillageData.CODEC.encodeStart(ops, village).getOrThrow();
        VillageData restored = VillageData.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(village.getUpgradeItems(), restored.getUpgradeItems());
        assertEquals(village.getLevel(), restored.getLevel());
        assertEquals(village.getUUID(), restored.getUUID());
        assertEquals(encoded, VillageData.CODEC.encodeStart(ops, restored).getOrThrow());
    }

    @Test
    void productionChoicesResolveRegisteredFoodsAndFarmUpCraftGems() {
        var village = village();
        assertTrue(VillageUpgradeItems.ensureChoices(village));
        var foods = VillageUpgradeItems.highNutritionFoods(BuiltInRegistries.ITEM.stream().toList());
        var gems = GemContent.TYPES.stream().map(GemContent::gem).toList();
        for (int target = 7; target <= 10; target++) {
            var requirement = VillageUpgradeItems.requirement(village, target);
            assertTrue((target % 2 == 1 ? foods : gems).contains(requirement.item()));
            assertFalse(requirement.description(new Inventory(null, new EntityEquipment())).isBlank());
        }
        assertFalse(VillageUpgradeItems.ensureChoices(village));
    }

    @Test
    void savedChoicesRejectInvalidTargetLevelsAndItemIdentifiers() {
        var json = VillageData.CODEC.encodeStart(JsonOps.INSTANCE, village()).getOrThrow().getAsJsonObject();
        var choices = new com.google.gson.JsonObject();
        choices.addProperty("11", "minecraft:emerald");
        json.add("upgrade_items", choices);
        assertTrue(VillageData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        choices.remove("11");
        choices.addProperty("8", "invalid item id");
        assertTrue(VillageData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void failedPaymentsLeaveItemsAndCoinsIntactAndSuccessConsumesExactlyAcrossSlots() {
        var village = village();
        var player = new PlayerData(1, UUID.randomUUID(), village.getUUID(), 30);
        var equipment = new EntityEquipment();
        var inventory = new Inventory(null, equipment);
        inventory.setItem(0, new ItemStack(Items.EMERALD, 64));
        inventory.setItem(1, new ItemStack(Items.EMERALD, 60));
        equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD, 3));
        inventory.setItem(2, new ItemStack(Items.DIAMOND, 12));
        var requirement = new VillageUpgradeItems.Requirement(Items.EMERALD, 128);

        assertFalse(VillageUpgradeItems.pay(village, player, inventory, 120, requirement));
        assertEquals(127, requirement.available(inventory));
        assertEquals(100, village.getCoins());
        assertEquals(30, player.getCoins());
        equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD, 10));
        assertFalse(VillageUpgradeItems.pay(village, player, inventory, 131, requirement));
        assertEquals(134, requirement.available(inventory));
        assertEquals(100, village.getCoins());
        assertEquals(30, player.getCoins());

        assertTrue(VillageUpgradeItems.pay(village, player, inventory, 120, requirement));
        assertEquals(6, requirement.available(inventory));
        assertEquals(12, inventory.getItem(2).getCount());
        assertEquals(0, village.getCoins());
        assertEquals(10, player.getCoins());
        assertEquals(6, equipment.get(EquipmentSlot.OFFHAND).getCount());
    }

    @Test
    void missingSavedItemFailsWithoutReplacingTheChoiceAndLegacyLevelsNeedNoItems() {
        var village = village();
        village.setUpgradeItem(8, "missing:old_gem");
        assertThrows(IllegalStateException.class, () -> VillageUpgradeItems.requirement(village, 8));
        assertEquals("missing:old_gem", village.getUpgradeItems().get("8"));
        assertThrows(IllegalStateException.class, () -> village.setUpgradeItem(8, "minecraft:emerald"));
        var player = new PlayerData(1, UUID.randomUUID(), village.getUUID(), 30);
        assertTrue(VillageUpgradeItems.pay(village, player, new Inventory(null, new EntityEquipment()), 120, null));
        assertEquals(0, village.getCoins());
        assertEquals(10, player.getCoins());
    }
}
