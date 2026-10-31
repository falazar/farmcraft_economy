package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlotSpawnPolicyGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("breeding_spawn_policy"), helper -> {
                    for (String plot : new String[]{"farm", "pasture", "village", ""}) {
                        for (int y : new int[]{44, 50, 64}) {
                            for (int light : new int[]{0, 10}) {
                                for (String species : new String[]{"jengu", "vespidqueen", "spriggan", "concapede"}) {
                                    var id = Identifier.parse("lycanitesmobs:" + species);
                                    helper.assertValueEqual(
                                            ChunkManager.joinedMobSuppressionChance(id, y, light, plot, true),
                                            ChunkManager.joinedMobSuppressionChance(id, y, light, plot, false),
                                            "Breeding never changes Lycanites policy: " + id);
                                }
                            }
                        }
                    }
                    helper.assertValueEqual(ChunkManager.joinedMobSuppressionChance(
                            Identifier.parse("naturalist:duck"), 64, 10, "pasture"), 0.0, "Duck allowed on lit pasture");
                    helper.assertValueEqual(ChunkManager.joinedMobSuppressionChance(
                            Identifier.parse("naturalist:deer"), 64, 10, "pasture", true), 0.0,
                            "Bred offspring allowed on lit pasture");
                    helper.assertValueEqual(ChunkManager.joinedMobSuppressionChance(
                            Identifier.parse("naturalist:deer"), 64, 10, "pasture", false), 1.0,
                            "Other spawns remain blocked on lit pasture");
                    helper.assertValueEqual(ChunkManager.joinedMobSuppressionChance(
                            Identifier.parse("lycanitesmobs:jengu"), 64, 10, "pasture", true), 1.0,
                            "Always-block Lycanites remain blocked even if marked bred");
                    for (String id : new String[]{"lycanitesmobs:jengu", "lycanitesmobs:spriggan",
                            "naturalist:lion", "minecraft:zombie"}) {
                        for (String plot : new String[]{"", "village", "farm", "pasture"}) {
                            for (var reason : new net.minecraft.world.entity.EntitySpawnReason[]{
                                    net.minecraft.world.entity.EntitySpawnReason.SPAWNER,
                                    net.minecraft.world.entity.EntitySpawnReason.TRIAL_SPAWNER}) {
                                helper.assertValueEqual(ChunkManager.joinedMobSuppressionChance(
                                        Identifier.parse(id), 64, 10, plot, false, reason), 0.0,
                                        "Spawner mobs bypass custom suppression: " + id + " " + plot);
                            }
                        }
                    }
                    var level = helper.getLevel();
                    for (var reason : new net.minecraft.world.entity.EntitySpawnReason[]{
                            net.minecraft.world.entity.EntitySpawnReason.SPAWNER,
                            net.minecraft.world.entity.EntitySpawnReason.TRIAL_SPAWNER}) {
                        var mob = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, reason);
                        helper.assertTrue(mob != null, "Create spawner-origin mob");
                        try {
                            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).above(130);
                            mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                            var spawnEvent = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                                    mob, level, mob.getX(), mob.getY(), mob.getZ(),
                                    level.getCurrentDifficultyAt(mob.blockPosition()), reason, null, null);
                            ChunkManager.onMobSpawnCheck(spawnEvent);
                            helper.assertTrue(!spawnEvent.isSpawnCancelled(), "Finalize permits spawner mobs");
                            helper.assertTrue(mob.getPersistentData().getBooleanOr(
                                    com.falazar.farmupcraft.util.PlotSpawnExemptions.SPAWNER_ORIGIN, false),
                                    "Spawner provenance survives skipped mob finalization and saves");
                            helper.assertTrue(level.addFreshEntity(mob), "Real world join permits spawner mob");
                        } finally {
                            mob.discard();
                        }
                    }
                    helper.succeed();
                }));
    }
}
