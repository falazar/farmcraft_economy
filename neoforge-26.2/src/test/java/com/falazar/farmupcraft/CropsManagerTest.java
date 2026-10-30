package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real registries and recipes; world-dependent event paths belong in GameTests. */
@ExtendWith(EphemeralTestServerProvider.class)
class CropsManagerTest {
    @Test
    void levelThreeLooseRuleAcceptsAnyRegisteredBiomeWithoutRelaxingLowerLevelRules(MinecraftServer server) {
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                Identifier.parse("minecraft:plains")));
        var desert = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                Identifier.parse("minecraft:desert")));
        BiomeRulesManager manager = new BiomeRulesManager();
        manager.setBiomeRules(plains, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.CARROT)));
        manager.setBiomeRules(desert, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.POTATO)));
        ItemStack potato = new ItemStack(Items.POTATO);
        assertFalse(manager.getBiomeRules(plains).biomeHasCrops(potato));
        assertFalse(CropsManager.isCropAllowedInBiomes(manager, potato, List.of(plains)));
        assertTrue(CropsManager.isCropAllowedInBiomes(manager, potato, List.of(plains, desert)));
        assertFalse(CropsManager.isCropAllowedInBiomes(manager, new ItemStack(Items.WHEAT), List.of(plains, desert)));
        assertFalse(CropsManager.isCropAllowedInBiomes(manager, potato, List.of()));
    }

    @Test
    void realMatureHarvestCraftCropsResolveTheirHarvestProductAndTaggedSeed(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (String crop : new String[]{"onion", "corn", "sesameseeds", "mustardseeds"}) {
            Identifier id = Identifier.parse("pamhc2crops:pam" + crop + "crop");
            var block = BuiltInRegistries.BLOCK.getValue(id);
            CropBlock cropBlock = assertInstanceOf(CropBlock.class, block, id.toString());
            assertTrue(cropBlock.isMaxAge(cropBlock.getStateForAge(cropBlock.getMaxAge())));
            var product = CropsManager.harvestItem(id);
            assertNotSame(Items.AIR, product);
            assertEquals(Identifier.parse("pamhc2crops:" + crop + "item"), BuiltInRegistries.ITEM.getKey(product));
            ItemStack seed = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse("pamhc2crops:" + crop + "seeditem")));
            assertNotSame(Items.AIR, seed.getItem());
            assertTrue(seed.is(FUCTags.VANILLA_AND_MODDED_CROPS), crop);
        }
        assertSame(Items.AIR, CropsManager.harvestItem(Identifier.parse("minecraft:wheat")));
    }

    @Test
    void growthUsesConfiguredTruncatedPercentageWithoutForcingVanillaGrowth() {
        assertFalse(CropsManager.growthAllowed(0, 0));
        assertFalse(CropsManager.growthAllowed(-10, 0));
        assertTrue(CropsManager.growthAllowed(20.9, 19));
        assertFalse(CropsManager.growthAllowed(20.9, 20));
        assertTrue(CropsManager.growthAllowed(100, 99));
        assertTrue(CropsManager.growthAllowed(120, 99));
        assertEquals(0, CropsManager.harvestBonusChance(0));
        assertEquals(0, CropsManager.harvestBonusChance(1));
        assertEquals(20, CropsManager.harvestBonusChance(2));
        assertEquals(40, CropsManager.harvestBonusChance(3));
        assertEquals(60, CropsManager.harvestBonusChance(4));
    }

    @Test
    void villageMembershipProtectsOutsidersAndAcceptsUnassignedPlots() {
        UUID village = UUID.randomUUID();
        ChunkData plot = new ChunkData("farm", 1, village);
        PlayerData member = new PlayerData(1, UUID.randomUUID(), village, 0);
        PlayerData outsider = new PlayerData(2, UUID.randomUUID(), UUID.randomUUID(), 0);
        assertTrue(CropsManager.isVillageMember(plot, member));
        assertFalse(CropsManager.isVillageMember(plot, outsider));
        assertFalse(CropsManager.isVillageMember(plot, null));
        plot.setVillageId(null);
        assertTrue(CropsManager.isVillageMember(plot, outsider));
        assertTrue(CropsManager.isVillageMember(plot, null));
    }

    @Test
    void hungerRegenerationIntervalScalesWithExperienceWithoutIntegerOverflow() {
        assertEquals(11000, CropsManager.regenerationCheckInterval(0));
        assertEquals(12000, CropsManager.regenerationCheckInterval(1));
        assertEquals(61000, CropsManager.regenerationCheckInterval(50));
        assertEquals(11000, CropsManager.regenerationCheckInterval(-1));
        assertEquals(Integer.MAX_VALUE, CropsManager.regenerationCheckInterval(Integer.MAX_VALUE));
    }

    @Test
    void loadedRecipesResolveModernDisplaysAndIngredientHoldersWithoutAWorld(MinecraftServer server) {
        ContextMap context = new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, server.registryAccess())
                .create(SlotDisplayContext.CONTEXT);
        var craftingTable = CropsManager.getIngredients(server.getRecipeManager(), context, "minecraft:crafting_table");
        assertEquals(4, craftingTable.size());
        assertTrue(craftingTable.stream().allMatch(stack -> stack.is(net.minecraft.tags.ItemTags.PLANKS)));
        var sugar = CropsManager.getIngredients(server.getRecipeManager(), context, "minecraft:sugar");
        assertFalse(sugar.isEmpty());
        assertTrue(sugar.stream().anyMatch(stack -> stack.is(Items.SUGAR_CANE) || stack.is(Items.HONEY_BOTTLE)));
        assertTrue(CropsManager.getIngredients(server.getRecipeManager(), context, "minecraft:air").isEmpty());
        assertTrue(CropsManager.getIngredients(server.getRecipeManager(), context, "farmupcraft:missing_food").isEmpty());
    }

    @Test
    void cropLabelsKeepSpecialHarvestCraftSeedsButHideOrdinarySeedProducts(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        assertEquals("Wheat", CropsManager.cropDisplayName(Items.WHEAT_SEEDS));
        assertEquals("Sesame", CropsManager.cropDisplayName(BuiltInRegistries.ITEM.getValue(
                Identifier.parse("pamhc2crops:sesameseedsseeditem"))));
        assertEquals("Mustard", CropsManager.cropDisplayName(BuiltInRegistries.ITEM.getValue(
                Identifier.parse("pamhc2crops:mustardseedsseeditem"))));
        assertEquals("Sesame Seeds", CropsManager.cropDisplayName(BuiltInRegistries.ITEM.getValue(
                Identifier.parse("pamhc2crops:sesameseedsitem"))));
        assertEquals("biome.biomesoplenty.mystic_grove",
                CropsManager.getBiomeLangKey(Identifier.parse("biomesoplenty:mystic_grove")));
    }
}
