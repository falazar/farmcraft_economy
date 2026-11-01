package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.AnimalsManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class EggCapacityGameTests {
    private EggCapacityGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("egg_capacity"), EggCapacityGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var plots = ModEvents.getChunkDataDatabase(level);
        long key = ChunkPos.containing(pos).pack();
        var previous = plots.getData(key);
        List<Entity> cleanup = new ArrayList<>();
        var messages = new ArrayList<String>();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "EggThrower")) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                messages.add(message.getString());
                helper.assertTrue(message.getStyle().getColor().equals(
                        net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED)),
                        "Full pasture warning must be red");
            }
        };
        try {
            var eggTypes = new ArrayList<net.minecraft.world.entity.EntityType<?>>();
            eggTypes.add(EntityTypes.EGG);
            if (net.neoforged.fml.ModList.get().isLoaded("naturalist")) {
                var duckClass = Class.forName(
                        "com.crispytwig.naturalist.world.entity.projectile.throwableitemprojectile.ThrownDuckEgg");
                for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                    var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (id.getNamespace().equals("naturalist") && id.getPath().contains("egg")) {
                        var entity = type.create(level, EntitySpawnReason.TRIGGERED);
                        if (duckClass.isInstance(entity)) eggTypes.add(type);
                        if (entity != null) entity.discard();
                    }
                }
                helper.assertTrue(eggTypes.size() == 2, "Real Naturalist duck egg must be tested");
            }
            for (var eggType : eggTypes) {
                for (String plot : List.of("village", "village center", "farm", "pasture")) {
                    for (int count : new int[]{0, 29, 30, 31}) {
                        cleanup.forEach(Entity::discard);
                        cleanup.clear();
                        plots.putData(key, new ChunkData(plot, -1, UUID.randomUUID()));
                        for (int i = 0; i < count; i++) {
                            var animal = EntityTypes.COW.create(level, EntitySpawnReason.TRIGGERED);
                            helper.assertTrue(animal != null, "Create animal fixture");
                            animal.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                            animal.setNoAi(true);
                            helper.assertTrue(level.addFreshEntity(animal), "Add animal fixture");
                            cleanup.add(animal);
                        }
                        helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(level, pos), count,
                                "Fixture must measure exact animal cap");
                        var egg = (net.minecraft.world.entity.projectile.Projectile)
                                eggType.create(level, EntitySpawnReason.TRIGGERED);
                        helper.assertTrue(egg != null, "Create egg fixture");
                        cleanup.add(egg);
                        egg.setOwner(player);
                        egg.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        long seed = 0;
                        while (true) {
                            egg.getRandom().setSeed(seed);
                            if (egg.getRandom().nextInt(8) == 0 && egg.getRandom().nextInt(32) == 0) break;
                            seed++;
                        }
                        egg.getRandom().setSeed(seed);
                        messages.clear();
                        var impact = egg.getClass().getDeclaredMethod("onHit", HitResult.class);
                        impact.setAccessible(true);
                        impact.invoke(egg, new BlockHitResult(egg.position(), Direction.UP, pos.below(), false));
                        var hatchlings = level.getEntitiesOfClass(Animal.class,
                                new net.minecraft.world.phys.AABB(pos).inflate(2),
                                animal -> !cleanup.contains(animal));
                        cleanup.addAll(hatchlings);
                        int expected = plot.equals("pasture") ? Math.max(0, Math.min(4, 30 - count)) : 0;
                        helper.assertValueEqual(hatchlings.size(), expected,
                                "Four-hatchling egg must respect plot and remaining capacity: " + plot + " " + count);
                        helper.assertTrue(egg.isRemoved(), "Blocked eggs must still break");
                        int warningCount = plot.equals("pasture") && count >= 30 ? 1 : 0;
                        helper.assertValueEqual(messages.size(), warningCount, "Warn once per full-pasture impact");
                        if (warningCount == 1) {
                            helper.assertTrue(messages.getFirst().contains(count + "/30 animals"),
                                    "Warning must include current count");
                        }
                        for (boolean owned : new boolean[]{true, false}) {
                            var unluckyEgg = (net.minecraft.world.entity.projectile.Projectile)
                                    eggType.create(level, EntitySpawnReason.TRIGGERED);
                            helper.assertTrue(unluckyEgg != null, "Create non-hatching egg");
                            cleanup.add(unluckyEgg);
                            unluckyEgg.setPos(egg.position());
                            if (owned) unluckyEgg.setOwner(player);
                            long unluckySeed = 0;
                            do {
                                unluckyEgg.getRandom().setSeed(unluckySeed++);
                            } while (unluckyEgg.getRandom().nextInt(8) == 0);
                            unluckyEgg.getRandom().setSeed(unluckySeed - 1);
                            messages.clear();
                            impact.invoke(unluckyEgg, new BlockHitResult(
                                    unluckyEgg.position(), Direction.UP, pos.below(), false));
                            int currentCount = AnimalsManager.countAnimalsInChunk(level, pos);
                            int expectedWarnings = owned && plot.equals("pasture") && currentCount >= 30 ? 1 : 0;
                            helper.assertValueEqual(messages.size(), expectedWarnings,
                                    "Failed hatch rolls still warn; ownerless eggs do not");
                            helper.assertTrue(unluckyEgg.isRemoved(), "Non-hatching eggs still break");
                        }
                    }
                }
            }
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot invoke real egg impact", error);
        } finally {
            player.discard();
            cleanup.forEach(Entity::discard);
            if (previous == null) plots.removeDataBatch(List.of(key));
            else plots.putData(key, previous);
        }
    }
}
