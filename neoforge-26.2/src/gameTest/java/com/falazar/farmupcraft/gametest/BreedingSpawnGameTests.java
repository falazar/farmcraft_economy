package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.AnimalsManager;
import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.PlotSpawnExemptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BreedingSpawnGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("breeding_spawn_exemptions"), BreedingSpawnGameTests::test);
            if (net.neoforged.fml.ModList.get().isLoaded("alexsmobs")) {
                functions.register(FarmUpCraftNeoForge.prefix("alexsmobs_bison_village_breeding"),
                        BreedingSpawnGameTests::alexsMobsBisonVillageBreeding);
            }
        });
    }

    private static void alexsMobsBisonVillageBreeding(GameTestHelper helper) {
        var level = helper.getLevel();
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("alexsmobs:bison"));
        helper.assertTrue(type != null, "Alex's Mobs bison entity is registered");
        if (type == null)
            return;
        Entity firstEntity = helper.spawn(type, new BlockPos(5, 1, 1));
        Entity secondEntity = helper.spawn(type, new BlockPos(6, 1, 1));
        if (!(firstEntity instanceof Animal first) || !(secondEntity instanceof Animal second)) {
            throw new IllegalStateException("Alex's Mobs bison must be breedable animals");
        }
        var wheat = new ItemStack(Items.WHEAT);
        helper.assertTrue(first.isFood(wheat), "Wheat is accepted as bison breeding food");
        first.setAge(0);
        second.setAge(0);
        first.setNoAi(true);
        second.setNoAi(true);

        long key = ChunkPos.pack(first.blockPosition());
        var plots = ModEvents.getChunkDataDatabase(level);
        ChunkData previous = plots.getData(key);
        UUID villageId = UUID.randomUUID();
        plots.putData(key, new ChunkData("village", -1, villageId));
        var bounds = new AABB(first.blockPosition()).inflate(5);
        List<Entity> cleanup = new ArrayList<>(List.of(first, second));
        try {
            List<UUID> before = level.getEntitiesOfClass(Animal.class, bounds,
                    animal -> animal.getType() == type).stream().map(Entity::getUUID).toList();
            first.setInLove(null);
            second.setInLove(null);
            first.spawnChildFromBreeding(level, second);
            var offspring = level.getEntitiesOfClass(Animal.class, bounds,
                    animal -> animal.getType() == type && !before.contains(animal.getUUID()));
            cleanup.addAll(offspring);
            helper.assertValueEqual(offspring.size(), 1, "Bison offspring spawns inside village bounds");
            if (!offspring.isEmpty()) {
                helper.assertTrue(offspring.getFirst().getPersistentData().getBooleanOr(
                        PlotSpawnExemptions.BRED_OFFSPRING, false),
                        "Bison offspring is exempt from plot spawn suppression");
            }
        } finally {
            cleanup.forEach(Entity::discard);
            if (previous == null)
                plots.removeDataBatch(List.of(key));
            else
                plots.putData(key, previous);
        }
        helper.succeed();
    }

    private static void test(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
        var second = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 2));
        var child = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 3));
        List<Entity> cleanup = new ArrayList<>(List.of(first, second, child));
        try {
            var canceled = new BabyEntitySpawnEvent(first, second, child);
            canceled.setCanceled(true);
            ChunkManager.onBabyEntitySpawn(canceled);
            helper.assertTrue(!child.getPersistentData().contains(PlotSpawnExemptions.BRED_OFFSPRING),
                    "Canceled breeding does not grant exemption");
            child.discard();
            var bounds = new AABB(first.blockPosition()).inflate(5);
            var before = level.getEntitiesOfClass(Animal.class, bounds).stream().map(Entity::getUUID).toList();
            first.spawnChildFromBreeding(level, second);
            var babies = level.getEntitiesOfClass(Animal.class, bounds,
                    animal -> !before.contains(animal.getUUID()));
            cleanup.addAll(babies);
            helper.assertValueEqual(babies.size(), 1, "Real breeding spawns offspring");
            helper.assertTrue(babies.getFirst().getPersistentData().getBooleanOr(
                    PlotSpawnExemptions.BRED_OFFSPRING, false), "Real breeding event records provenance");
            if (net.neoforged.fml.ModList.get().isLoaded("alexsmobs"))
                emuEggHatching(helper);
            for (String id : net.neoforged.fml.ModList.get().isLoaded("naturalist")
                    ? List.of("minecraft:egg", "naturalist:duck_egg") : List.of("minecraft:egg")) {
                for (String plotType : List.of("", "village", "farm", "pasture")) {
                long key = net.minecraft.world.level.ChunkPos.pack(first.blockPosition());
                var plots = com.falazar.farmupcraft.events.ModEvents.getChunkDataDatabase(level);
                var previous = plots.getData(key);
                try {
                    if (plotType.isEmpty())
                        plots.removeDataBatch(List.of(key));
                    else
                        plots.putData(key, new com.falazar.farmupcraft.data.ChunkData(
                                plotType, -1, java.util.UUID.randomUUID(), null, List.of(),
                                first.blockPosition().getY()));
                    var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
                    helper.assertTrue(type != null, "Loaded egg type: " + id);
                    var egg = type.create(level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(egg != null, "Construct real egg projectile");
                    cleanup.add(egg);
                    egg.setPos(first.position().add(0, 2, 0));
                    long seed = 0;
                    while (true) {
                            egg.getRandom().setSeed(seed);
                        if (egg.getRandom().nextInt(8) == 0 && egg.getRandom().nextInt(32) != 0)
                            break;
                        seed++;
                    }
                    egg.getRandom().setSeed(seed);
                    var prior = level.getEntitiesOfClass(Animal.class, bounds).stream().map(Entity::getUUID)
                            .toList();
                    var impact = egg.getClass().getDeclaredMethod("onHit",
                            net.minecraft.world.phys.HitResult.class);
                    impact.setAccessible(true);
                    impact.invoke(egg, new net.minecraft.world.phys.BlockHitResult(egg.position(),
                            net.minecraft.core.Direction.UP, first.blockPosition().below(), false));
                    var hatchlings = level.getEntitiesOfClass(Animal.class, bounds,
                            animal -> !prior.contains(animal.getUUID()));
                    cleanup.addAll(hatchlings);
                    helper.assertValueEqual(hatchlings.size(), plotType.equals("pasture") ? 1 : 0,
                            "Real egg impact enforces pasture-only hatching: " + id + " plot=" + plotType);
                    helper.assertTrue(egg.isRemoved(),
                            "Egg still breaks normally, even when hatching is blocked");
                    if (!hatchlings.isEmpty())
                        helper.assertTrue(hatchlings.getFirst().isBaby(), "Egg produces a baby");
                } finally {
                    if (previous == null)
                        plots.removeDataBatch(List.of(key));
                    else
                        plots.putData(key, previous);
                }
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot test installed egg impact", error);
        } finally {
            cleanup.forEach(Entity::discard);
        }
        helper.succeed();
    }

    private static void emuEggHatching(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var eggType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("alexsmobs:emu_egg")).orElseThrow();
        var emuType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("alexsmobs:emu")).orElseThrow();
        for (String scenario : List.of("wilderness", "village", "village center", "farm", "pasture",
                "pasture-outside-height", "pasture-almost-full", "pasture-full", "village-underground")) {
            BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1))
                    .atY(scenario.equals("village-underground") ? 20 : 80);
            long key = ChunkPos.pack(pos);
            var plots = ModEvents.getChunkDataDatabase(level);
            ChunkData previous = plots.getData(key);
            List<Entity> cleanup = new ArrayList<>();
            try {
                if (scenario.equals("wilderness"))
                    plots.removeDataBatch(List.of(key));
                else
                    plots.putData(key, new ChunkData(
                            scenario.startsWith("pasture") ? "pasture" : scenario,
                            -1, UUID.randomUUID(), null, List.of(),
                            pos.getY() + (scenario.equals("pasture-outside-height") ? 20 : 0)));
                if (scenario.equals("pasture-almost-full") || scenario.equals("pasture-full")) {
                    int target = scenario.equals("pasture-full") ? 30 : 29;
                    for (int count = AnimalsManager.countAnimalsInChunk(level, pos); count < target; count++) {
                        var cow = EntityTypes.COW.create(level, EntitySpawnReason.TRIGGERED);
                        cow.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        cow.setNoAi(true);
                        cleanup.add(cow);
                        helper.assertTrue(level.addFreshEntity(cow), "Populate the pasture for cap testing");
                    }
                    helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(level, pos), target,
                            "Pasture starts at the tested animal count");
                }
                var egg = eggType.create(level, EntitySpawnReason.TRIGGERED);
                cleanup.add(egg);
                egg.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                long seed = 0;
                while (true) {
                    egg.getRandom().setSeed(seed);
                    if (egg.getRandom().nextInt(8) == 0 && egg.getRandom().nextInt(32) == 0) break;
                    seed++;
                }
                egg.getRandom().setSeed(seed);
                var bounds = new AABB(pos).inflate(5);
                var before = level.getEntitiesOfClass(Animal.class, bounds).stream().map(Entity::getUUID).toList();
                var impact = egg.getClass().getDeclaredMethod("onHit", net.minecraft.world.phys.HitResult.class);
                impact.setAccessible(true);
                impact.invoke(egg, new net.minecraft.world.phys.BlockHitResult(egg.position(),
                        net.minecraft.core.Direction.UP, pos.below(), false));
                var babies = level.getEntitiesOfClass(Animal.class, bounds,
                        animal -> animal.getType() == emuType && !before.contains(animal.getUUID()));
                cleanup.addAll(babies);
                int expected = scenario.equals("wilderness") || scenario.equals("pasture") ? 4
                        : scenario.equals("pasture-almost-full") ? 1 : 0;
                helper.assertValueEqual(babies.size(), expected, "Real emu egg hatching: " + scenario);
                helper.assertTrue(egg.isRemoved(), "Emu egg breaks normally: " + scenario);
                for (var baby : babies) {
                    helper.assertTrue(baby.isBaby(), "Emu hatchling is a baby");
                    helper.assertTrue(baby.getPersistentData().getBooleanOr(PlotSpawnExemptions.BRED_OFFSPRING, false),
                            "Emu hatchling keeps its plot-spawn exemption");
                    var rejoin = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(baby, level, true);
                    ChunkManager.onEntityJoinLevel(rejoin);
                    helper.assertTrue(!rejoin.isCanceled(), "Allowed emu remains exempt on reload");
                }
                if (scenario.equals("pasture-almost-full"))
                    helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(level, pos), 30,
                            "Four-baby emu roll cannot overflow the pasture cap");
            } finally {
                cleanup.forEach(Entity::discard);
                if (previous == null)
                    plots.removeDataBatch(List.of(key));
                else
                    plots.putData(key, previous);
            }
        }
        AnimalsManager.LOGGER.info("[EMU-EGG-ACCEPTANCE] Passed all nine real egg-impact scenarios.");
    }
}
