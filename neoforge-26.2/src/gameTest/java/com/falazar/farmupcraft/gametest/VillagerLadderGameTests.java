package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillagerLadderGameTests {
    private VillagerLadderGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_ladder_descent"), VillagerLadderGameTests::ladderDescent));
    }

    private static void ladderDescent(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            BlockPos base = fixture.pos;
            Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
            Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (villager == null || zombie == null) throw new IllegalStateException("Could not create ladder test mobs");
            try {
                for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, 6, 1))) {
                    previous.put(pos.immutable(), level.getBlockState(pos));
                    level.setBlock(pos, pos.getY() < base.getY()
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = 0; y < 6; y++) {
                    level.setBlock(base.above(y).south(), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(base.above(y), Blocks.LADDER.defaultBlockState()
                            .setValue(LadderBlock.FACING, Direction.NORTH), 2);
                }
                for (int age : new int[] {0, -24000}) {
                    villager.setAge(age);
                    placeOnLadder(villager, base);
                    double startY = villager.getY();
                    helper.assertTrue(villager.onClimbable(), "Test villager must actually be on the ladder");
                    for (int tick = 0; tick < 80; tick++) {
                        villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0.12);
                        villager.travel(Vec3.ZERO);
                        helper.assertTrue(villager.getDeltaMovement().y <= 0,
                                "Wall collision must not boost villagers up the ladder");
                    }
                    helper.assertTrue(villager.getY() < startY - 3,
                            "Adults and children must descend at least three blocks within four seconds"
                                    + " (age=" + age + ", start=" + startY + ", end=" + villager.getY()
                                    + ", grounded=" + villager.onGround() + ")");
                    helper.assertTrue(villager.onGround(), "Villager must reach the floor normally");
                    for (int tick = 0; tick < 12; tick++) {
                        villager.setDeltaMovement(0, 0, -0.12);
                        villager.travel(Vec3.ZERO);
                    }
                    helper.assertTrue(!villager.onClimbable(), "Villager must be able to walk off the ladder");
                }
                placeOnLadder(fixture.player, base);
                helper.assertTrue(climbingMovement(fixture.player).y > 0,
                        "Player wall-collision ladder climbing must remain unchanged");
                placeOnLadder(zombie, base);
                helper.assertTrue(climbingMovement(zombie).y > 0,
                        "Other mobs must retain ladder climbing");

                level.setBlock(base.above(3), Blocks.VINE.defaultBlockState()
                        .setValue(VineBlock.SOUTH, true), 2);
                villager.setAge(0);
                placeOnLadder(villager, base);
                helper.assertTrue(climbingMovement(villager).y > 0,
                        "Villager vine climbing must remain unchanged");
            } finally {
                villager.discard();
                zombie.discard();
                previous.forEach((pos, state) -> level.setBlock(pos, state, 2));
            }
        }
        helper.succeed();
    }

    private static void placeOnLadder(LivingEntity entity, BlockPos base) {
        entity.setPos(base.getX() + 0.5, base.getY() + 3.25, base.getZ() + 0.5);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
    }

    private static Vec3 climbingMovement(LivingEntity entity) {
        entity.setDeltaMovement(0, 0, 0.12);
        entity.travel(Vec3.ZERO);
        return entity.getDeltaMovement();
    }
}
