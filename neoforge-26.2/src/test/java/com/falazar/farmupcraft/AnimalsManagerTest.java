package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.util.AnimalGrainAssigner;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Registry-only fixture: animal policy tests never construct an entity or a fake level. */
@ExtendWith(EphemeralTestServerProvider.class)
class AnimalsManagerTest {
    @Test
    void everyAssignedGrainIsAnActualLoadedItemAndAcceptedBreedingFeed(MinecraftServer server) {
        var registry = server.registryAccess().lookupOrThrow(Registries.ITEM);
        assertEquals(8, AnimalGrainAssigner.GRAIN_POOL.size());
        assertEquals(8, new HashSet<>(AnimalGrainAssigner.GRAIN_POOL).size());
        for (String id : AnimalGrainAssigner.GRAIN_POOL) {
            Identifier key = Identifier.parse(id);
            assertTrue(BuiltInRegistries.ITEM.containsKey(key), id);
            Item item = registry.getValue(key);
            assertNotNull(item, id);
            assertNotSame(Items.AIR, item, id);
            assertEquals(key, BuiltInRegistries.ITEM.getKey(item));
            assertTrue(AnimalsManager.isBreedingFeed(new ItemStack(item)), id);
        }
        assertFalse(AnimalsManager.isBreedingFeed(new ItemStack(Items.SHEARS)));
        assertFalse(AnimalsManager.isBreedingFeed(new ItemStack(Items.BUCKET)));
        ItemStack redDye = new ItemStack(Items.DYE.pick(DyeColor.RED));
        assertEquals(DyeColor.RED, redDye.get(DataComponents.DYE));
        assertFalse(AnimalsManager.isBreedingFeed(redDye));
    }

    @Test
    void grainRotationIsStableDistinctAndCoversEveryLoadedChoice(MinecraftServer server) {
        for (int offset = 0; offset < 8; offset++) {
            VillageData village = village(new ChunkPos(0, offset));
            AnimalGrainAssigner.assignGrains(village);
            String saved = village.getAnimalGrains();
            List<String> all = new ArrayList<>();
            for (int speciesIndex = 0; speciesIndex < 4; speciesIndex++) {
                String species = AnimalGrainAssigner.ANIMAL_TYPES.get(speciesIndex);
                List<String> choices = village.getGrainsForAnimal(species);
                assertEquals(List.of(
                        AnimalGrainAssigner.GRAIN_POOL.get((offset + speciesIndex * 2) % 8),
                        AnimalGrainAssigner.GRAIN_POOL.get((offset + speciesIndex * 2 + 1) % 8)), choices);
                assertNotEquals(choices.getFirst(), choices.getLast());
                choices.forEach(id -> assertNotNull(server.registryAccess().lookupOrThrow(Registries.ITEM)
                        .getValue(Identifier.parse(id)), id));
                all.addAll(choices);
            }
            assertEquals(new HashSet<>(AnimalGrainAssigner.GRAIN_POOL), new HashSet<>(all));
            AnimalGrainAssigner.assignGrains(village);
            assertEquals(saved, village.getAnimalGrains());
        }
        VillageData negative = village(new ChunkPos(-3, -7));
        AnimalGrainAssigner.assignGrains(negative);
        assertEquals(List.of("pamhc2crops:ryeitem", "pamhc2crops:oatsitem"), negative.getGrainsForAnimal("cow"));
        VillageData extremes = village(new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertDoesNotThrow(() -> AnimalGrainAssigner.assignGrains(extremes));
        assertTrue(extremes.hasAnimalGrainsAssigned());
        assertEquals(List.of("minecraft:wheat", "pamhc2crops:alfalfaitem"),
                extremes.getGrainsForAnimal("cow"));
        VillageData positive = village(new ChunkPos(3, 7));
        AnimalGrainAssigner.assignGrains(positive);
        assertEquals(negative.getAnimalGrains(), positive.getAnimalGrains());
    }

    @Test
    void grainDisplayNamesRetainLegacyCommandLabels() {
        assertEquals("Corn", AnimalGrainAssigner.displayName("pamhc2crops:cornitem"));
        assertEquals("Wheat", AnimalGrainAssigner.displayName("minecraft:wheat"));
        assertEquals("Alfalfa", AnimalGrainAssigner.displayName("pamhc2crops:alfalfaitem"));
        assertEquals("Oats", AnimalGrainAssigner.displayName("pamhc2crops:oatsitem"));
        assertEquals("Rice", AnimalGrainAssigner.displayName("riceitem"));
        assertEquals("", AnimalGrainAssigner.displayName(""));
    }

    @Test
    void farmSpeciesAndPastureOnlySpeciesRetainTheirDifferentRules() {
        assertEquals("cow", AnimalsManager.getAnimalSpeciesName(Cow.class));
        assertEquals("cow", AnimalsManager.getAnimalSpeciesName(MushroomCow.class));
        assertEquals("sheep", AnimalsManager.getAnimalSpeciesName(Sheep.class));
        assertEquals("pig", AnimalsManager.getAnimalSpeciesName(Pig.class));
        assertEquals("chicken", AnimalsManager.getAnimalSpeciesName(Chicken.class));
        assertTrue(AnimalsManager.isFarmAnimal(MushroomCow.class));
        assertFalse(AnimalsManager.isFarmAnimal(Horse.class));
        assertTrue(AnimalsManager.isFarmAnimal(Goat.class));
        assertEquals("sheep", AnimalsManager.getAnimalSpeciesName(Goat.class));
        assertTrue(AnimalsManager.isPastureAnimal(Horse.class));
        assertTrue(AnimalsManager.isPastureAnimal(Goat.class));
        assertFalse(AnimalsManager.isPastureAnimal(Donkey.class));
        assertFalse(AnimalsManager.isPastureAnimal(Rabbit.class));
        assertEquals("unknown", AnimalsManager.getAnimalSpeciesName(Rabbit.class));
    }

    @Test
    void breedingRequiresAdultZeroAgeAndNoExistingLoveMode() {
        assertTrue(AnimalsManager.canEnterLoveMode(0, true));
        assertFalse(AnimalsManager.canEnterLoveMode(-24000, true));
        assertFalse(AnimalsManager.canEnterLoveMode(6000, true));
        assertFalse(AnimalsManager.canEnterLoveMode(0, false));
    }

    @Test
    void pastureBonusesDyeOddsAndShearingRestrictionsKeepTheirBoundaries() {
        for (int level = -1; level <= 6; level++) {
            assertEquals(level >= 2 && level <= 4 ? level - 1 : 0,
                    AnimalsManager.getExtraDropsForPastureLevel(level));
        }
        assertEquals(20, AnimalsManager.dyeSuccessChance(1));
        assertEquals(40, AnimalsManager.dyeSuccessChance(2));
        assertEquals(60, AnimalsManager.dyeSuccessChance(3));
        assertEquals(80, AnimalsManager.dyeSuccessChance(4));
        assertEquals(100, AnimalsManager.dyeSuccessChance(5));
        assertEquals(100, AnimalsManager.dyeSuccessChance(Integer.MAX_VALUE));
        assertTrue(AnimalsManager.canShearInPlot(""));
        assertTrue(AnimalsManager.canShearInPlot("pasture"));
        assertFalse(AnimalsManager.canShearInPlot("farm"));
        assertFalse(AnimalsManager.canShearInPlot("residential"));
    }

    @Test
    void persistedShearingCooldownIsSevenMinecraftDays() {
        CompoundTag persisted = new CompoundTag();
        persisted.putLong("farmupcraft_last_sheared_tick", 250L);
        long shearedAt = persisted.getLongOr("farmupcraft_last_sheared_tick", 0L);
        assertTrue(AnimalsManager.woolRegrowthBlocked(shearedAt, shearedAt));
        assertTrue(AnimalsManager.woolRegrowthBlocked(shearedAt + 168000 - 1, shearedAt));
        assertFalse(AnimalsManager.woolRegrowthBlocked(shearedAt + 168000, shearedAt));
        assertFalse(AnimalsManager.woolRegrowthBlocked(shearedAt + 168001, shearedAt));
    }

    @Test
    void pastureCountBoxKeepsLegacyVerticalBandAndExclusiveUpperBuildBoundary() {
        assertEquals(new AABB(-16, 62, -32, 0, 67, -16),
                AnimalsManager.pastureCountingBox(new BlockPos(-1, 64, -17), -64, 319));
        assertEquals(new AABB(0, -64, 0, 16, -61, 16),
                AnimalsManager.pastureCountingBox(new BlockPos(0, -64, 0), -64, 319));
        assertEquals(new AABB(0, 317, 0, 16, 320, 16),
                AnimalsManager.pastureCountingBox(new BlockPos(15, 319, 15), -64, 319));
    }

    @Test
    void allWoolColorsResolveActualLoadedWoolItems(MinecraftServer server) {
        HashSet<Item> woolItems = new HashSet<>();
        for (DyeColor color : DyeColor.values()) {
            Identifier expected = Identifier.parse("minecraft:" + color.name().toLowerCase(Locale.ROOT) + "_wool");
            Item item = AnimalsManager.getWoolItemForColor(color);
            assertSame(server.registryAccess().lookupOrThrow(Registries.ITEM).getValue(expected),
                    item);
            assertSame(Items.WOOL.pick(color), item);
            BlockItem blockItem = assertInstanceOf(BlockItem.class, item);
            assertSame(Blocks.WOOL.pick(color), blockItem.getBlock());
            ItemStack bonusDrop = new ItemStack(item, AnimalsManager.getExtraDropsForPastureLevel(4));
            assertTrue(bonusDrop.is(item));
            assertEquals(3, bonusDrop.getCount());
            assertTrue(woolItems.add(item), color.getName());
        }
        assertEquals(16, woolItems.size());
    }

    @Test
    void subscriberPreservesLowestPriorityGoalWrappingWithoutReceivingCanceledJoins() throws Exception {
        EventBusSubscriber subscriber = AnimalsManager.class.getAnnotation(EventBusSubscriber.class);
        assertEquals("farmupcraft", subscriber.modid());
        SubscribeEvent handler = AnimalsManager.class.getMethod("onEntityJoinLevel", EntityJoinLevelEvent.class)
                .getAnnotation(SubscribeEvent.class);
        assertEquals(EventPriority.LOWEST, handler.priority());
        assertFalse(handler.receiveCanceled());
    }

    @Test
    void animalTagsPreserveNamespaceAndRegistryKinds(MinecraftServer server) {
        assertTag(FUCTags.FOOD, Registries.ITEM, "food");
        assertTag(FUCTags.VANILLA_CROPS, Registries.ITEM, "vanilla_crops");
        assertTag(FUCTags.MODDED_CROPS, Registries.ITEM, "modded_crops");
        assertTag(FUCTags.MODDED_SEEDS, Registries.ITEM, "modded_seeds");
        assertTag(FUCTags.MARKET_FOODS, Registries.ITEM, "market_foods");
        assertTag(FUCTags.VANILLA_AND_MODDED_CROPS, Registries.ITEM, "vanilla_and_modded_crops");
        assertTag(FUCTags.BARRIER, Registries.BLOCK, "barrier");
        assertTag(FUCTags.FARMLAND, Registries.BLOCK, "farmland");
        assertTag(FUCTags.VANILLA_CROPS_BLOCKS, Registries.BLOCK, "vanilla_crops");
        assertTag(FUCTags.MODDED_CROPS_BLOCKS, Registries.BLOCK, "modded_crops");
        assertTag(FUCTags.EMPTY_BIOME_TAG, Registries.BIOME, "empty_biome_tag");
        assertTag(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG, Registries.BIOME, "biomes_o_plenty_overworld_biomes");
        assertTag(FUCTags.BIOMES_O_PLENTY_TAG, Registries.BIOME, "biomes_o_plenty_biomes");
        assertTag(FUCTags.OVERWORLD_NO_OCEAN, Registries.BIOME, "overworld_no_ocean");
        assertTrue(server.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(FUCTags.MODDED_CROPS)
                .iterator().hasNext());
    }

    private static void assertTag(TagKey<?> tag, Object registry, String path) {
        assertEquals(registry, tag.registry());
        assertEquals(Identifier.parse("farmupcraft:" + path), tag.location());
    }

    private static VillageData village(ChunkPos position) {
        return new VillageData(UUID.randomUUID(), "Test", position, 1, List.of(), false, 0,
                "", "", "", "", List.of());
    }
}
