package com.falazar.farmupcraft;

import com.pam.pamhc2trees.PamFruitBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Registry and policy coverage only; this fixture does not create ServerLevels. */
@ExtendWith(EphemeralTestServerProvider.class)
class TreeManagerTest {
    @Test
    void restrictedSaplingsUseExactBiomeIdentifiersIncludingLoadedBopBiomes(MinecraftServer server) {
        Map<String, String> restrictions = Map.of(
                "dark_oak", "minecraft:dark_forest",
                "redwood", "biomesoplenty:redwood_forest",
                "magic", "biomesoplenty:mystic_grove",
                "cherry", "minecraft:cherry_grove",
                "umbran", "biomesoplenty:ominous_woods");
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        restrictions.forEach((sapling, biome) -> {
            Identifier id = Identifier.parse(biome);
            assertNotNull(biomes.getValue(id), biome);
            assertTrue(TreeManager.isSaplingBiomeAllowed(sapling, id));
            assertFalse(TreeManager.isSaplingBiomeAllowed(sapling, Identifier.parse("minecraft:plains")));
            assertFalse(TreeManager.isSaplingBiomeAllowed(sapling,
                    Identifier.fromNamespaceAndPath("unrelated", id.getPath())));
            String namespace = biome.startsWith("biomesoplenty:") ? "biomesoplenty" : "minecraft";
            Identifier saplingId = Identifier.fromNamespaceAndPath(namespace, sapling + "_sapling");
            assertTrue(BuiltInRegistries.ITEM.containsKey(saplingId), saplingId.toString());
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(saplingId));
            String shortName = stack.getItem().getDescriptionId().replace("block.", "").replace("_sapling", "")
                    .replace("minecraft.", "").replace("biomesoplenty.", "");
            assertEquals(sapling, shortName);
        });
        assertTrue(TreeManager.isSaplingBiomeAllowed("dark_oak", Identifier.parse("minecraft:dark_forest_hills")));
        assertTrue(TreeManager.isSaplingBiomeAllowed("redwood", Identifier.parse("minecraft:giant_tree_taiga")));
        assertTrue(TreeManager.isSaplingBiomeAllowed("oak", Identifier.parse("minecraft:plains")));
        assertTrue(TreeManager.isSaplingBiomeAllowed(null, Identifier.parse("minecraft:plains")));
        assertTrue(TreeManager.isSaplingBiomeAllowed("magic", null));
    }

    @Test
    void jungleSaplingsAndCocoaShareTheJungleLikeBiomeRule(MinecraftServer server) {
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        for (String id : new String[]{"minecraft:jungle", "biomesoplenty:rainforest", "biomesoplenty:tropics"}) {
            Identifier biome = Identifier.parse(id);
            assertNotNull(biomes.getValue(biome), id);
            assertTrue(TreeManager.isJungleBiome(biome));
            assertTrue(TreeManager.isSaplingBiomeAllowed("jungle", biome));
            assertNull(TreeManager.cocoaRejection(true, biome));
            assertEquals("Cocoa can only be planted on nursery plots.", TreeManager.cocoaRejection(false, biome));
        }
        for (String path : new String[]{"rain_forest", "tropical_island"}) {
            assertTrue(TreeManager.isJungleBiome(Identifier.fromNamespaceAndPath("example", path)));
        }
        Identifier plains = Identifier.parse("minecraft:plains");
        assertFalse(TreeManager.isJungleBiome(plains));
        assertFalse(TreeManager.isJungleBiome(null));
        assertFalse(TreeManager.isSaplingBiomeAllowed("jungle", plains));
        assertEquals("Cocoa can only be planted in jungle-like biomes.", TreeManager.cocoaRejection(true, plains));
        assertNotNull(TreeManager.cocoaRejection(true, null));
    }

    @Test
    void leafDropsAndSaplingPlantingRetainNurseryFruitAndExperienceRates() {
        assertEquals(20, TreeManager.saplingDropRate(false, 10));
        assertEquals(30, TreeManager.saplingDropRate(true, 1));
        assertEquals(60, TreeManager.saplingDropRate(true, 2));
        assertEquals(90, TreeManager.saplingDropRate(true, 3));
        assertEquals(120, TreeManager.saplingDropRate(true, 4));
        assertEquals(10, TreeManager.plantingSuccessRate(false, false, 0));
        assertEquals(10, TreeManager.plantingSuccessRate(false, false, 1));
        assertEquals(11, TreeManager.plantingSuccessRate(false, false, 2));
        assertEquals(65, TreeManager.plantingSuccessRate(true, false, 0));
        assertEquals(100, TreeManager.plantingSuccessRate(false, true, 0));
        assertEquals(100, TreeManager.plantingSuccessRate(true, true, 0));
        assertEquals(110, TreeManager.plantingSuccessRate(false, true, 20));
        Identifier fruitSapling = Identifier.parse("pamhc2trees:apple_sapling");
        assertTrue(BuiltInRegistries.ITEM.containsKey(fruitSapling));
        assertTrue(BuiltInRegistries.ITEM.getValue(fruitSapling).getDescriptionId().contains("pamhc2trees"));
    }

    @Test
    void actualMatureFruitBlocksResolveTheirRealItemsAndVanillaAppleException() {
        for (String fruit : new String[]{"apple", "almond", "cherry", "walnut"}) {
            Identifier blockId = Identifier.parse("pamhc2trees:pam" + fruit);
            assertTrue(BuiltInRegistries.BLOCK.containsKey(blockId), blockId.toString());
            var block = BuiltInRegistries.BLOCK.getValue(blockId);
            assertNotSame(Blocks.AIR, block);
            assertTrue(block.defaultBlockState().hasProperty(PamFruitBlock.AGE), fruit);
            assertEquals(7, block.defaultBlockState().setValue(PamFruitBlock.AGE, 7).getValue(PamFruitBlock.AGE));
            Identifier fruitId = TreeManager.fruitItemId(blockId);
            assertEquals(Identifier.parse(fruit.equals("apple") ? "minecraft:apple" : "pamhc2trees:" + fruit + "item"),
                    fruitId);
            assertTrue(BuiltInRegistries.ITEM.containsKey(fruitId), fruitId.toString());
            assertNotSame(Items.AIR, BuiltInRegistries.ITEM.getValue(fruitId));
        }
        assertSame(Items.APPLE, BuiltInRegistries.ITEM.getValue(TreeManager.fruitItemId(
                Identifier.parse("pamhc2trees:pamapple"))));
    }

    @Test
    void fruitRatesUseWholeHundredsExperienceAndOrchardPath() {
        Identifier forest = Identifier.parse("minecraft:forest");
        assertEquals(40, TreeManager.fruitSuccessRate(0, 0, forest));
        assertEquals(40, TreeManager.fruitSuccessRate(99, 0, forest));
        assertEquals(42, TreeManager.fruitSuccessRate(100, 0, forest));
        assertEquals(69, TreeManager.fruitSuccessRate(200, 25, forest));
        assertEquals(60, TreeManager.fruitSuccessRate(0, 0, Identifier.parse("biomesoplenty:orchard")));
        assertEquals(60, TreeManager.fruitSuccessRate(0, 0, Identifier.parse("example:orchard")));
        assertEquals(40, TreeManager.fruitSuccessRate(0, 0, Identifier.parse("example:old_orchard")));
        assertEquals(40, TreeManager.fruitSuccessRate(0, 0, null));
        assertEquals(140, TreeManager.fruitSuccessRate(0, 100, forest));
    }

    @Test
    void fruitAndNurseryBonusesPreserveInclusiveBoundariesAndUncappedScaling() {
        assertEquals(1, TreeManager.fruitBonusCount(0, 40));
        assertEquals(1, TreeManager.fruitBonusCount(20, 40));
        assertEquals(0, TreeManager.fruitBonusCount(21, 40));
        assertEquals(2, TreeManager.fruitBonusCount(15, 80));
        assertEquals(1, TreeManager.fruitBonusCount(16, 80));
        assertEquals(1, TreeManager.fruitBonusCount(40, 80));
        assertEquals(0, TreeManager.fruitBonusCount(41, 80));
        assertEquals(0, TreeManager.nurseryBonusChance(0));
        assertEquals(0, TreeManager.nurseryBonusChance(1));
        assertEquals(20, TreeManager.nurseryBonusChance(2));
        assertEquals(40, TreeManager.nurseryBonusChance(3));
        assertEquals(100, TreeManager.nurseryBonusChance(6));
        assertEquals(120, TreeManager.nurseryBonusChance(7));
    }

    @Test
    void pickupsReportOnlyTheFirstCrossedMilestoneUsingNetHarvestCounts() {
        assertEquals(0, TreeManager.firstHarvestMilestone(0, 4));
        assertEquals(5, TreeManager.firstHarvestMilestone(4, 5));
        assertEquals(5, TreeManager.firstHarvestMilestone(0, 64));
        assertEquals(10, TreeManager.firstHarvestMilestone(5, 12));
        assertEquals(15, TreeManager.firstHarvestMilestone(14, 16));
        assertEquals(20, TreeManager.firstHarvestMilestone(19, 20));
        assertEquals(0, TreeManager.firstHarvestMilestone(20, 49));
        assertEquals(50, TreeManager.firstHarvestMilestone(49, 50));
        assertEquals(80, TreeManager.firstHarvestMilestone(50, 90));
        assertEquals(110, TreeManager.firstHarvestMilestone(109, 110));
        assertEquals(0, TreeManager.firstHarvestMilestone(-10, 0));
        assertEquals(0, TreeManager.firstHarvestMilestone(5, 5));
    }

    @Test
    void automaticHooksRetainAttemptPhaseInteractionPriorityAndPostPickupTiming() throws Exception {
        assertEquals("farmupcraft", TreeManager.class.getAnnotation(EventBusSubscriber.class).modid());
        assertHandler("onBreakLeaves", BreakBlockEvent.class, EventPriority.NORMAL);
        assertHandler("onLogBreakNurseryBonus", BreakBlockEvent.class, EventPriority.LOW);
        assertHandler("onRightClickCocoaPlanting", PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGH);
        assertHandler("onRightClickSaplingPlant", PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGH);
        assertHandler("onRightClickHarvestTrees", PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGH);
        assertHandler("onFruitPickup", ItemEntityPickupEvent.Post.class, EventPriority.NORMAL);
    }

    private static void assertHandler(String name, Class<?> eventType, EventPriority priority) throws Exception {
        SubscribeEvent handler = TreeManager.class.getMethod(name, eventType).getAnnotation(SubscribeEvent.class);
        assertNotNull(handler, name);
        assertEquals(priority, handler.priority(), name);
        assertFalse(handler.receiveCanceled(), name);
    }
}
