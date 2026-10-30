package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.ai.BreakTorchGoal;
import com.falazar.farmupcraft.command.FarmCraftCommand;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class TorchBreakingGameTests {
    private TorchBreakingGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("torch_breaking"), TorchBreakingGameTests::torches));
    }

    private static void torches(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var database = ModEvents.getWorldDataDatabase(level);
        WorldData settings = database.getOrCreate(0, WorldData::new);
        boolean previousSetting = settings.isMobTorchBreaking();
        boolean previousRule = level.getGameRules().get(GameRules.MOB_GRIEFING);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        var origin = helper.absolutePos(new BlockPos(0, 1, 0));
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -1, 0), origin.offset(9, 3, 4))) {
            var immutable = pos.immutable();
            previous.put(immutable, level.getBlockState(immutable));
            level.setBlock(immutable, pos.getY() == origin.getY() - 1 || pos.getY() == origin.getY() + 3
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        settings.setMobTorchBreaking(true);
        database.putData(0, settings);
        level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
        List<Mob> mobs = new ArrayList<>();
        mobs.add(helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1)));
        mobs.add(helper.spawn(EntityTypes.SKELETON, new BlockPos(2, 1, 3)));
        for (int i = 0; i < mobs.size(); i++) {
            mobs.get(i).setPos(origin.getX() + 2.5, origin.getY(), origin.getZ() + 1.5 + 2 * i);
            mobs.get(i).setOnGround(true);
            mobs.get(i).setNoGravity(false);
        }
        mobs.forEach(mob -> {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            BreakTorchGoal.onJoin(new EntityJoinLevelEvent(mob, level));
            helper.assertValueEqual(mob.goalSelector.getAvailableGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof BreakTorchGoal).count(), 1L,
                    "Join and rejoin must install exactly one torch goal");
        });
        Runnable cleanup = () -> {
            mobs.forEach(Mob::discard);
            previous.forEach((pos, state) -> level.setBlock(pos, state, 3));
            settings.setMobTorchBreaking(previousSetting);
            database.putData(0, settings);
            level.getGameRules().set(GameRules.MOB_GRIEFING, previousRule, server);
        };
        for (Mob mob : mobs) level.setBlock(mob.blockPosition().east(), Blocks.TORCH.defaultBlockState(), 3);
        helper.runAfterDelay(110, () -> {
            try {
                for (Mob mob : mobs) {
                    BreakTorchGoal goal = (BreakTorchGoal) mob.goalSelector.getAvailableGoals().stream()
                            .map(wrapped -> wrapped.getGoal()).filter(BreakTorchGoal.class::isInstance)
                            .findFirst().orElseThrow();
                    var torch = mob.blockPosition().east();
                    helper.assertTrue(goal.canUse(), "A nearby visible torch must have a reachable path: "
                            + mob.getType() + " at " + mob.position() + ", torch=" + level.getBlockState(torch)
                            + ", target=" + mob.getTarget() + ", path=" + mob.getNavigation().createPath(torch, 1)
                            + ", alive=" + mob.isAlive());
                    goal.start();
                    helper.assertValueEqual(FarmCraftCommand.setAdminSetting(
                            server.createCommandSourceStack(), "mobTorchBreaking", false), 1,
                            "Admin toggle must accept the new setting");
                    goal.tick();
                    helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),
                            "Disabling must stop an in-progress break");
                    settings.setMobTorchBreaking(true);
                    database.putData(0, settings);
                    level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
                    goal.tick();
                    helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),
                            "mobGriefing=false must stop an in-progress break");
                    level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
                    mob.setTarget(mobs.get(mob == mobs.getFirst() ? 1 : 0));
                    goal.tick();
                    helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),
                            "Combat must take precedence over torch breaking");
                    mob.setTarget(null);
                    var position = mob.position();
                    mob.setPos(position.x - 5, position.y, position.z);
                    goal.tick();
                    helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),
                            "Mobs must not break torches remotely");
                    mob.setPos(position);
                    goal.tick();
                    helper.assertTrue(level.getBlockState(torch).isAir(), "Zombie/skeleton must break a torch in reach");
                    goal.stop();
                    level.setBlock(mob.blockPosition().offset(5, 0, 0), Blocks.TORCH.defaultBlockState(), 3);
                    mob.goalSelector.removeAllGoals(other -> !(other instanceof BreakTorchGoal));
                    mob.targetSelector.removeAllGoals(other -> true);
                    mob.setNoAi(false);
                }
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        helper.runAfterDelay(350, () -> {
            try {
                for (int z : List.of(1, 3)) {
                    helper.assertTrue(level.getBlockState(origin.offset(7, 0, z)).isAir(),
                            "Real goal ticks must navigate five blocks and break the torch");
                }
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }
}
