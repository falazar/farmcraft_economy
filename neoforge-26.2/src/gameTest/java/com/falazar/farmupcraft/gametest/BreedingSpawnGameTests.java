package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.ChunkManager;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BreedingSpawnGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("breeding_spawn_exemptions"), BreedingSpawnGameTests::test));
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
            if (net.neoforged.fml.ModList.get().isLoaded("naturalist")) {
                for (String id : List.of("minecraft:egg", "naturalist:duck_egg")) {
                    for (String plotType : List.of("", "village", "farm", "pasture")) {
                    long key = net.minecraft.world.level.ChunkPos.pack(first.blockPosition());
                    var plots = com.falazar.farmupcraft.events.ModEvents.getChunkDataDatabase(level);
                    var previous = plots.getData(key);
                    try {
                    if (plotType.isEmpty()) plots.removeDataBatch(List.of(key));
                    else plots.putData(key, new com.falazar.farmupcraft.data.ChunkData(
                            plotType, -1, java.util.UUID.randomUUID()));
                    var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
                    helper.assertTrue(type != null, "Loaded egg type: " + id);
                    var egg = type.create(level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(egg != null, "Construct real egg projectile");
                    cleanup.add(egg);
                    egg.setPos(first.position().add(0, 2, 0));
                    long seed = 0;
                    while (true) {
                        egg.getRandom().setSeed(seed);
                        if (egg.getRandom().nextInt(8) == 0 && egg.getRandom().nextInt(32) != 0) break;
                        seed++;
                    }
                    egg.getRandom().setSeed(seed);
                    var prior = level.getEntitiesOfClass(Animal.class, bounds).stream().map(Entity::getUUID).toList();
                    var impact = egg.getClass().getDeclaredMethod("onHit", net.minecraft.world.phys.HitResult.class);
                    impact.setAccessible(true);
                    impact.invoke(egg, new net.minecraft.world.phys.BlockHitResult(egg.position(),
                            net.minecraft.core.Direction.UP, first.blockPosition().below(), false));
                    var hatchlings = level.getEntitiesOfClass(Animal.class, bounds,
                            animal -> !prior.contains(animal.getUUID()));
                    cleanup.addAll(hatchlings);
                    helper.assertValueEqual(hatchlings.size(), plotType.equals("pasture") ? 1 : 0,
                            "Real egg impact enforces pasture-only hatching: " + id + " plot=" + plotType);
                    helper.assertTrue(egg.isRemoved(), "Egg still breaks normally, even when hatching is blocked");
                    if (!hatchlings.isEmpty()) helper.assertTrue(hatchlings.getFirst().isBaby(), "Egg produces a baby");
                    } finally {
                        if (previous == null) plots.removeDataBatch(List.of(key));
                        else plots.putData(key, previous);
                    }
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
}
