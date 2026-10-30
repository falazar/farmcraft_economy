package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class AnimalGameTests {
    private AnimalGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("cow_pasture_daily_milking"),
                    helper -> milking(helper, true, false));
            functions.register(FarmUpCraftNeoForge.prefix("cow_wilderness_milking_denied"),
                    helper -> milking(helper, false, false));
            functions.register(FarmUpCraftNeoForge.prefix("cow_baby_milking_unchanged"),
                    helper -> milking(helper, true, true));
            functions.register(FarmUpCraftNeoForge.prefix("sheep_regrowth_cooldown"), AnimalGameTests::sheepCooldown);
            functions.register(FarmUpCraftNeoForge.prefix("duck_goat_pasture_breeding"),
                    AnimalGameTests::duckAndGoatBreeding);
        });
    }

    private static void duckAndGoatBreeding(GameTestHelper helper) {
        List<String> types = new ArrayList<>(List.of("minecraft:goat"));
        if (net.neoforged.fml.ModList.get().isLoaded("naturalist")) types.add("naturalist:duck");
        for (String id : types) {
            try (var plot = new ResourcePlotFixture(helper)) {
                var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
                var entity = helper.spawn(type, new BlockPos(1, 1, 1));
                helper.assertTrue(entity instanceof Animal, id + " must be an animal");
                Animal animal = (Animal) entity;
                animal.setPos(plot.pos.getX() + 0.5, plot.pos.getY(), plot.pos.getZ() + 0.5);
                animal.setAge(0);
                UUID villageId = UUID.randomUUID();
                var villages = ModEvents.getVillageDatabase(plot.level);
                var village = new VillageData(villageId, "breeding-test", ChunkPos.containing(plot.pos),
                        1, List.of(ChunkPos.containing(plot.pos)), true, 0, "2000-01-01", "",
                        "2000-01-01", "", List.of(plot.player.getUUID()));
                String category = id.equals("naturalist:duck") ? "duck" : "sheep";
                village.setAnimalGrainsFromMap(Map.of(category, List.of("minecraft:wheat")));
                villages.putData(villageId, village);
                ModEvents.getPlayerDatabase(plot.level).putData(plot.player.getUUID(),
                        new PlayerData(plot.player.getId(), plot.player.getUUID(), villageId, 0));
                var chunks = ModEvents.getChunkDataDatabase(plot.level);
                try {
                    for (var item : BuiltInRegistries.ITEM) {
                        if (!animal.isFood(new ItemStack(item))) continue;
                        plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 8));
                        plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                        assertBreedingDenied(helper, plot, animal, id + " native food outside pasture", 8);
                    }
                    plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 8));
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertBreedingDenied(helper, plot, animal, id + " outside pasture", 8);

                    chunks.putData(plot.key, new ChunkData("pasture", -1, UUID.randomUUID()));
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertBreedingDenied(helper, plot, animal, id + " in another village", 8);

                    chunks.putData(plot.key, new ChunkData("pasture", -1, villageId));
                    village.setAnimalType(category.equals("duck") ? "sheep" : "chicken");
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertBreedingDenied(helper, plot, animal, id + " wrong village species", 8);

                    village.setAnimalType(category);
                    plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 8));
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertBreedingDenied(helper, plot, animal, id + " wrong assigned grain", 8);

                    plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 8));
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    helper.assertTrue(animal.isInLove(), id + " allowed in matching own-village pasture");
                    helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 7, id + " consumes one feed");
                    helper.assertValueEqual(animal.getPersistentData().getStringOr("farmupcraft_last_bred", ""),
                            LocalDate.now().toString(), id + " daily breeding marker");
                    animal.resetLove();
                    animal.setAge(0);
                    plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertBreedingDenied(helper, plot, animal, id + " second breeding today", 7);
                    if (category.equals("duck")) {
                        animal.getPersistentData().remove("farmupcraft_last_bred");
                        var nativeFood = java.util.stream.StreamSupport.stream(
                                        BuiltInRegistries.ITEM.spliterator(), false)
                                .filter(item -> animal.isFood(new ItemStack(item))).findFirst().orElseThrow();
                        plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(nativeFood, 3));
                        plot.player.interactOn(animal, InteractionHand.MAIN_HAND, Vec3.ZERO);
                        helper.assertTrue(animal.isInLove(), "Native Naturalist duck food remains accepted");
                        helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 2,
                                "Native food consumes one without requiring grain too");
                        animal.resetLove();
                        animal.getPersistentData().remove("farmupcraft_last_bred");
                        village.setAnimalGrainsFromMap(Map.of("chicken", List.of("minecraft:wheat")));
                        var before = village.getGrainsForAnimal("chicken");
                        helper.assertTrue(com.falazar.farmupcraft.util.AnimalGrainAssigner.ensureDuckGrain(village),
                                "Legacy village gets duck grain");
                        helper.assertValueEqual(village.getGrainsForAnimal("duck").size(), 1, "Exactly one duck grain");
                        helper.assertValueEqual(village.getGrainsForAnimal("chicken"), before,
                                "Migration preserves other species");
                        var saved = village.getAnimalGrains();
                        helper.assertTrue(!com.falazar.farmupcraft.util.AnimalGrainAssigner.ensureDuckGrain(village),
                                "Duck grain is not rerolled");
                        helper.assertValueEqual(village.getAnimalGrains(), saved, "Stable persisted grain assignment");
                        var source = plot.player.createCommandSourceStack();
                        village.setAnimalType("chicken");
                        helper.assertValueEqual(com.falazar.farmupcraft.command.VillageCommand
                                        .setVillageAnimalType(source, "duck"), 1,
                                "Village command accepts duck");
                        helper.assertValueEqual(village.getAnimalType(), "duck", "Village stores duck category");
                        helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher()
                                .getCompletionSuggestions(helper.getLevel().getServer().getCommands().getDispatcher()
                                        .parse("village setanimal ", source)).join().getList().stream()
                                .anyMatch(suggestion -> suggestion.getText().equals("duck")), "Duck autocomplete");
                    }
                } finally {
                    villages.removeDataBatch(List.of(villageId));
                    animal.discard();
                }
            }
        }
        helper.succeed();
    }

    private static void assertBreedingDenied(GameTestHelper helper, ResourcePlotFixture plot,
                                             Animal animal, String reason, int expectedFeed) {
        helper.assertTrue(!animal.isInLove(), reason + " must not enter love mode");
        helper.assertValueEqual(plot.player.getMainHandItem().getCount(), expectedFeed,
                reason + " must not consume feed");
    }

    private static void milking(GameTestHelper helper, boolean pasture, boolean baby) {
        var level = helper.getLevel();
        var cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
        if (baby) {
            cow.setAge(-24000);
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var database = ModEvents.getChunkDataDatabase(level);
        long key = ChunkPos.pack(cow.blockPosition());
        ChunkData previous = database.getData(key);
        if (pasture) {
            database.putData(key, new ChunkData("pasture", -1, new UUID(0, 0)));
        } else {
            database.removeDataBatch(List.of(key));
        }
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
            boolean allowed = pasture && !baby;
            helper.assertTrue(player.getMainHandItem().is(allowed ? Items.MILK_BUCKET : Items.BUCKET),
                    "The actual cow interaction must respect pasture and age rules");
            if (allowed) {
                helper.assertValueEqual(cow.getPersistentData().getStringOr("farmupcraft_last_milked", ""),
                        LocalDate.now().toString(), "daily milking marker");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
                helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "A second milking today must be denied");
            } else {
                helper.assertTrue(!cow.getPersistentData().contains("farmupcraft_last_milked"),
                        "A failed interaction must not set the daily marker");
            }
        } finally {
            if (previous == null) {
                database.removeDataBatch(List.of(key));
            } else {
                database.putData(key, previous);
            }
            cow.discard();
        }
        helper.succeed();
    }

    private static void sheepCooldown(GameTestHelper helper) {
        var sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(1, 1, 1));
        sheep.getPersistentData().putLong("farmupcraft_last_sheared_tick", helper.getLevel().getGameTime());
        sheep.setSheared(false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(sheep.tickCount >= 2,
                        "The sheep must actually tick before checking wool regrowth"))
                .thenExecute(() -> {
                    helper.assertTrue(sheep.isSheared(), "Real entity ticks must prevent early wool regrowth");
                    sheep.getPersistentData().putLong("farmupcraft_last_sheared_tick",
                            helper.getLevel().getGameTime() - 7L * 24000L);
                    sheep.setSheared(false);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(!sheep.getPersistentData().contains("farmupcraft_last_sheared_tick"),
                            "Expired cooldown marker must be cleared by the entity tick");
                    helper.assertTrue(!sheep.isSheared(), "Expired cooldown must allow wool");
                })
                .thenExecute(sheep::discard)
                .thenSucceed();
    }
}
