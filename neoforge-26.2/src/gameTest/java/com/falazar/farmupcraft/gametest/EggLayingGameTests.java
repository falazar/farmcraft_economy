package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class EggLayingGameTests {
    private EggLayingGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("egg_laying"), EggLayingGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var plots = ModEvents.getChunkDataDatabase(level);
        long key = ChunkPos.containing(pos).pack();
        var previous = plots.getData(key);
        var cleanup = new ArrayList<Entity>();
        try {
            var types = new ArrayList<EntityType<?>>();
            types.add(EntityTypes.CHICKEN);
            if (ModList.get().isLoaded("naturalist")) {
                types.add(BuiltInRegistries.ENTITY_TYPE.getValue(
                        net.minecraft.resources.Identifier.parse("naturalist:duck")));
                helper.assertTrue(types.getLast() != null, "Real Naturalist duck must be tested");
            }
            for (var type : types) {
                for (String plot : List.of("", "pasture", "village", "village center", "farm",
                        "nursery", "kitchen", "plot")) {
                    cleanup.forEach(Entity::discard);
                    cleanup.clear();
                    if (plot.isEmpty()) plots.removeDataBatch(List.of(key));
                    else plots.putData(key, new ChunkData(plot, -1, UUID.randomUUID(), null, List.of(), pos.getY()));
                    var bird = (Animal) type.create(level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(bird != null, "Create laying bird");
                    cleanup.add(bird);
                    bird.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    bird.setAge(0);
                    var timer = bird.getClass().getField("eggTime");
                    timer.setInt(bird, 1);
                    bird.aiStep();
                    var eggs = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4));
                    cleanup.addAll(eggs);
                    boolean allowed = plot.isEmpty() || plot.equals("pasture");
                    helper.assertValueEqual(eggs.size(), allowed ? 1 : 0,
                            "Egg laying must respect plot type: " + plot + " for " + type);
                    int remaining = timer.getInt(bird);
                    helper.assertTrue(remaining >= 6000 && remaining <= 11999,
                            "Allowed and blocked attempts must reset the normal egg interval");
                    if (!allowed) {
                        // Moving a due bird into a pasture must allow laying again.
                        plots.putData(key, new ChunkData("pasture", -1, UUID.randomUUID(), null, List.of(), pos.getY()));
                        timer.setInt(bird, 1);
                        bird.aiStep();
                        var movedEggs = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4));
                        cleanup.addAll(movedEggs);
                        helper.assertValueEqual(movedEggs.size(), 1, "Pasture movement must restore laying");
                    }
                }
            }
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot test real egg-laying timer", error);
        } finally {
            cleanup.forEach(Entity::discard);
            if (previous == null) plots.removeDataBatch(List.of(key));
            else plots.putData(key, previous);
        }
    }
}
