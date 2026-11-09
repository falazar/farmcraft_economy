package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.ai.CreeperStorageGoal;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CreeperStorageGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("creeper_storage"), CreeperStorageGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var origin = helper.absolutePos(new BlockPos(0, 2, 0));
        boolean previousRule = level.getGameRules().get(GameRules.MOB_GRIEFING);
        var previous = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var pos : BlockPos.betweenClosed(origin.offset(-4, -2, -5), origin.offset(17, 5, 5))) {
            previous.put(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, pos.getY() < origin.getY() ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), 3);
        }
        var mobs = new java.util.ArrayList<Creeper>();
        var creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(0, 2, 0));
        creeper.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        creeper.setNoAi(true);
        creeper.setPersistenceRequired();
        mobs.add(creeper);
        CreeperStorageGoal.onJoin(new EntityJoinLevelEvent(creeper, level));
        helper.assertValueEqual(creeper.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof CreeperStorageGoal).count(), 1L, "Join is idempotent");
        var goal = (CreeperStorageGoal) creeper.goalSelector.getAvailableGoals().stream()
                .map(g -> g.getGoal()).filter(CreeperStorageGoal.class::isInstance).findFirst().orElseThrow();
        for (var block : java.util.List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL))
            helper.assertTrue(CreeperStorageGoal.isStorage(block.defaultBlockState()), "Vanilla storage is included");
        for (var block : java.util.List.of(Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.STONE))
            helper.assertTrue(!CreeperStorageGoal.isStorage(block.defaultBlockState()), "Other blocks are excluded");
        Runnable cleanup = () -> {
            mobs.forEach(Creeper::discard);
            previous.forEach((pos, state) -> level.setBlock(pos, state, 3));
            level.getGameRules().set(GameRules.MOB_GRIEFING, previousRule, server);
        };
        var chest = origin.offset(12, 0, 0);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
        helper.runAfterDelay(50, () -> {
            try {
                helper.assertTrue(!goal.canUse(), "mobGriefing=false disables storage targeting");
                level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
        helper.runAfterDelay(100, () -> {
            try {
                creeper.setOnGround(true);
                helper.assertTrue(goal.canUse(), "Visible reachable chest at exactly 12 blocks is detected: pos="
                        + creeper.position() + ", origin=" + origin + ", chest=" + chest
                        + ", path=" + creeper.getNavigation().createPath(chest, 1) + ", block="
                        + level.getBlockState(chest) + ", alive=" + creeper.isAlive() + ", target=" + creeper.getTarget());
                goal.start();
                goal.tick();
                helper.assertTrue(creeper.getSwellDir() < 0, "No remote detonation");
                creeper.setPos(chest.getX() - 1.0, origin.getY(), origin.getZ() + 0.5);
                goal.tick();
                helper.assertTrue(creeper.getSwellDir() > 0, "Fuse starts close to storage");
                creeper.setTarget(creeper);
                goal.tick();
                helper.assertTrue(creeper.getSwellDir() < 0, "An entity combat target takes precedence");
                creeper.setTarget(null);
                goal.tick();
                helper.assertTrue(creeper.getSwellDir() > 0, "Storage fuse resumes without combat");
                level.setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
                goal.tick();
                helper.assertTrue(creeper.getSwellDir() < 0, "Removing storage cancels fuse");
                goal.stop();
                creeper.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                level.setBlock(origin.offset(13, 0, 0), Blocks.BARREL.defaultBlockState(), 3);
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
        helper.runAfterDelay(150, () -> {
            try {
                helper.assertTrue(!goal.canUse(), "Storage beyond 12 blocks is ignored");
                level.setBlock(origin.offset(13, 0, 0), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(origin.offset(4, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
                for (int z = -2; z <= 2; z++)
                    for (int y = 0; y <= 3; y++)
                        level.setBlock(origin.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
        helper.runAfterDelay(200, () -> {
            try {
                helper.assertTrue(!goal.canUse(), "Walls block storage detection");
                for (int z = -2; z <= 2; z++)
                    for (int y = 0; y <= 3; y++)
                        level.setBlock(origin.offset(2, y, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(origin.offset(4, 0, 0), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(origin.offset(6, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
                creeper.targetSelector.removeAllGoals(g -> true);
                creeper.setNoAi(false);
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
        helper.runAfterDelay(450, () -> {
            try {
                helper.assertTrue(creeper.isRemoved(), "Real AI walks to the chest and explodes with vanilla fuse");
                helper.assertTrue(level.getBlockState(origin.offset(6, 0, 0)).isAir(), "Explosion destroys the chest");
                for (int x = -1; x <= 8; x++)
                    for (int z = -3; z <= 3; z++)
                        level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                var second = helper.spawn(EntityTypes.CREEPER, new BlockPos(0, 2, 0));
                second.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                second.setPersistenceRequired();
                second.targetSelector.removeAllGoals(g -> true);
                mobs.add(second);
                level.setBlock(origin.offset(6, 0, 0), Blocks.BARREL.defaultBlockState(), 3);
            } catch (RuntimeException | AssertionError failure) { cleanup.run(); throw failure; }
        });
        helper.runAfterDelay(700, () -> {
            try {
                helper.assertTrue(mobs.get(1).isRemoved(), "Real AI also explodes beside a barrel");
                helper.assertTrue(level.getBlockState(origin.offset(6, 0, 0)).isAir(), "Explosion destroys the barrel");
                helper.succeed();
            } finally { cleanup.run(); }
        });
    }

}
