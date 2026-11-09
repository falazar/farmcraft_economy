package com.falazar.farmupcraft.ai;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class RetaliationObstacleGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("retaliation_obstacles"), RetaliationObstacleGameTests::verify));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("retaliation_cooldown"), RetaliationObstacleGameTests::verifyCooldown));
    }

    private static void verifyCooldown(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var base = new BlockPos(origin.getX(), Math.max(65, origin.getY()), origin.getZ());
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cooldown-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        var mob = new Zombie(EntityTypes.ZOMBIE, level);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(4, 3, 2))) {
            before.put(pos.immutable(), level.getBlockState(pos));
            boolean wall = Math.abs(pos.getX() - base.getX()) == 1 || Math.abs(pos.getZ() - base.getZ()) == 1;
            level.setBlock(pos, pos.getY() < base.getY() || wall
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var oldDifficulty = level.getDifficulty();
        boolean oldGriefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
        mob.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        player.setPos(base.getX() + 3.5, base.getY(), base.getZ() + 0.5);
        mob.setTarget(player);
        var goal = new RetaliationObstacleGoal(mob);
        goal.rememberHit(player);
        var obstacle = base.east().above();
        long start = level.getGameTime();
        Runnable cleanup = () -> {
            before.forEach((pos, state) -> level.setBlock(pos, state, 2));
            level.getServer().setDifficulty(oldDifficulty, true);
            level.getGameRules().set(GameRules.MOB_GRIEFING, oldGriefing, level.getServer());
            mob.discard();
            player.discard();
        };
        // Find a guaranteed winning seed, independent of Minecraft's random-source implementation.
        long seed = 0;
        var random = net.minecraft.util.RandomSource.create();
        while (true) {
            random.setSeed(seed);
            if (RetaliationObstacleGoal.winsRoll(random.nextInt(100))) break;
            seed++;
        }
        final long winningSeed = seed;
        helper.runAfterDelay(39, () -> {
            try {
                helper.assertValueEqual(level.getGameTime() - start, 39L, "Check exactly before cooldown expires");
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE),
                        "A guaranteed winning roll still cannot break before tick 40");
            } catch (RuntimeException | AssertionError error) {
                cleanup.run();
                throw error;
            }
        });
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertValueEqual(level.getGameTime() - start, 40L, "Check exact two-second interval");
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).isAir(), "A winning roll breaks the wall at tick 40");
                level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE),
                        "Repeated updates cannot roll again within the same cooldown");
            } catch (RuntimeException | AssertionError error) {
                cleanup.run();
                throw error;
            }
        });
        helper.runAfterDelay(80, () -> {
            try {
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE),
                        "A new cooldown and winning roll cannot break another block without a new hit");
                goal.rememberHit(player);
                goal.rememberHit(player);
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).isAir(),
                        "A fresh hit permits another action once the cooldown has expired");
                level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
            } catch (RuntimeException | AssertionError error) {
                cleanup.run();
                throw error;
            }
        });
        helper.runAfterDelay(120, () -> {
            try {
                mob.getRandom().setSeed(winningSeed);
                goal.tick();
                helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE),
                        "Multiple hits before an action do not stockpile extra block breaks");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var base = new BlockPos(origin.getX(), Math.max(65, origin.getY()), origin.getZ());
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "obstacle-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        var zombie = new Zombie(EntityTypes.ZOMBIE, level);
        var cow = new Cow(EntityTypes.COW, level);
        boolean oldGriefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        var oldDifficulty = level.getDifficulty();
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var pos : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(5, 10, 3))) {
            before.put(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, pos.getY() == base.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        try {
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
            zombie.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            player.setPos(base.getX() + 3.5, base.getY(), base.getZ() + 0.5);
            zombie.setTarget(player);
            var unprovoked = new RetaliationObstacleGoal(zombie);
            helper.assertTrue(!unprovoked.canUse(), "Targeting alone does not arm obstacle behavior");
            zombie.hurtServer(level, player.damageSources().playerAttack(player), 1);
            var goal = zombie.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                    .filter(RetaliationObstacleGoal.class::isInstance).map(RetaliationObstacleGoal.class::cast)
                    .findFirst().orElseThrow();
            helper.assertTrue(goal.canUse(), "An actual player hit arms a hostile mob: target=" + zombie.getTarget()
                    + " mobAlive=" + zombie.isAlive() + " noAi=" + zombie.isNoAi()
                    + " playerAlive=" + player.isAlive() + " creative=" + player.isCreative());
            cow.hurtServer(level, player.damageSources().playerAttack(player), 1);
            helper.assertTrue(cow.goalSelector.getAvailableGoals().stream()
                    .noneMatch(w -> w.getGoal() instanceof RetaliationObstacleGoal),
                    "Passive mobs never receive retaliation behavior");
            int wins = 0;
            for (int roll = 0; roll < 100; roll++) if (RetaliationObstacleGoal.winsRoll(roll)) wins++;
            helper.assertValueEqual(wins, 20, "Exactly 20 percent of rolls succeed");

            var obstacle = base.east().above();
            level.setBlock(obstacle, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            goal.tick();
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.OAK_PLANKS),
                    "No immediate attempt before the two-second cooldown");
            helper.assertTrue(goal.breakObstacle(player), "A nearby wall on the line to the attacker can break");
            helper.assertTrue(level.getBlockState(obstacle).isAir(), "Wall block is actually removed");
            level.setBlock(obstacle, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            helper.assertTrue(!goal.breakObstacle(player), "One hit cannot break a replacement block");
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.OAK_PLANKS),
                    "Replacement wall survives without another hit");
            zombie.invulnerableTime = 0;
            zombie.hurtServer(level, player.damageSources().playerAttack(player), 1);
            helper.assertTrue(goal.canUse(), "A second actual damage event rearms the existing goal");
            for (var protectedBlock : new net.minecraft.world.level.block.Block[]{Blocks.CHEST, Blocks.BEDROCK}) {
                level.setBlock(obstacle, protectedBlock.defaultBlockState(), 3);
                helper.assertTrue(!goal.breakObstacle(player), "Containers and unbreakable blocks are protected");
                helper.assertTrue(level.getBlockState(obstacle).is(protectedBlock), "Protected obstacle remains");
            }
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            helper.assertTrue(!goal.breakObstacle(player), "Mob griefing false disables breaking");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 3);
            player.setPos(base.getX() + 0.5, base.getY() + 4, base.getZ() + 0.5);
            zombie.setOnGround(true);
            helper.assertTrue(!goal.startPillar(player), "Mob griefing false disables pillars");
            level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
            level.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!goal.startPillar(player), "Low ceilings prevent jumping into blocks");
            level.setBlock(base.above(2), Blocks.AIR.defaultBlockState(), 3);
            for (int step = 0; step < 6; step++) {
                goal.rememberHit(player);
                zombie.setPos(base.getX() + 0.5, base.getY() + step, base.getZ() + 0.5);
                zombie.setOnGround(true);
                player.setPos(base.getX() + 0.5, base.getY() + 40, base.getZ() + 0.5);
                helper.assertTrue(goal.startPillar(player), "A higher attacker allows a pillar jump");
                goal.finishPillar(level, player);
                helper.assertTrue(level.getBlockState(base.above(step)).isAir(),
                        "Stone is never placed inside the grounded mob");
                zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                for (int tick = 0; tick < 12 && level.getBlockState(base.above(step)).isAir(); tick++) {
                    zombie.tick();
                    goal.tick();
                }
                helper.assertTrue(level.getBlockState(base.above(step)).is(Blocks.STONE),
                        "A real mob jump places stone underneath after it rises");
                helper.assertTrue(!goal.canUse(), "Placing one pillar block consumes this hit's action");
                zombie.setPos(base.getX() + 0.5, base.getY() + step + 1, base.getZ() + 0.5);
                zombie.setOnGround(true);
                helper.assertTrue(!goal.startPillar(player), "A second pillar block requires a new hit");
                player.setPos(base.getX() + 3.5, base.getY() + step + 1, base.getZ() + 0.5);
                var nextObstacle = base.above(step + 2).east();
                level.setBlock(nextObstacle, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                helper.assertTrue(!goal.breakObstacle(player), "A pillar action cannot also fund a block break");
                helper.assertTrue(level.getBlockState(nextObstacle).is(Blocks.OAK_PLANKS),
                        "Wall survives after the hit was spent on a pillar");
                level.setBlock(nextObstacle, Blocks.AIR.defaultBlockState(), 3);
            }
            zombie.setPos(base.getX() + 0.5, base.getY() + 6, base.getZ() + 0.5);
            zombie.setOnGround(true);
            player.setPos(base.getX() + 0.5, base.getY() + 40, base.getZ() + 0.5);
            goal.rememberHit(player);
            helper.assertTrue(goal.startPillar(player), "Pillaring continues beyond six blocks toward a distant higher player");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!goal.canUse(), "Creative players do not trigger retaliation");
            player.setGameMode(GameType.SURVIVAL);
            zombie.setTarget(null);
            helper.assertTrue(!goal.canUse(), "No block changes without a current player target");
        } finally {
            level.getServer().setDifficulty(oldDifficulty, true);
            level.getGameRules().set(GameRules.MOB_GRIEFING, oldGriefing, level.getServer());
            for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
            zombie.discard();
            cow.discard();
            player.discard();
        }
        helper.succeed();
    }
}
