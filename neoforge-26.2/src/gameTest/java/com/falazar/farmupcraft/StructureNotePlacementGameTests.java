package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class StructureNotePlacementGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("structure_note_placement"), StructureNotePlacementGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var world = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(8, 2, 8));
        var sd = new GameStructureData(9991L, "Placement test", center, "tower");
        sd.setMinPos(center.offset(-4, -1, -4));
        sd.setMaxPos(center.offset(4, 4, 4));
        var originals = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++)
                for (int y = -1; y <= 5; y++) {
                    var pos = center.offset(x, y, z);
                    originals.put(pos, world.getBlockState(pos));
                    world.setBlock(pos, y == -1 || y == 4 ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 3);
                }
        try {
            var choices = new java.util.HashSet<BlockPos>();
            for (int i = 0; i < 32; i++) {
                var placed = StructureManager.placeChestAtStructureCenter(sd, world);
                helper.assertTrue(placed != null, "A valid central spot is found");
                helper.assertTrue(placed.getY() == center.getY(), "Interior floor must beat roof");
                helper.assertTrue(placed.distSqr(center) <= 16, "Central band must beat corners");
                helper.assertTrue(StructureManager.containsPosition(sd, placed), "Chest stays inside bounds");
                helper.assertTrue(world.getBlockState(placed).is(Blocks.CHEST)
                        && world.getBlockEntity(placed) != null, "Actual usable chest is placed");
                choices.add(placed);
                world.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
            }
            helper.assertTrue(choices.size() > 1, "Placement varies");
            world.setBlock(center, Blocks.CHEST.defaultBlockState(), 3);
            var separate = StructureManager.placeChestAtStructureCenter(sd, world);
            helper.assertTrue(separate != null && separate.distSqr(center) > 1,
                    "New chest must not merge with an existing chest");
            sd.setSpecialChestPos(center);
            helper.assertTrue(!StructureManager.setupSpecialChest(world, sd, java.util.List.of(separate), null)
                    && sd.getSpecialChestPos().equals(center), "Existing special marker is preserved");
            sd.setSpecialChestPos(null);
            sd.setWasVisited(true);
            helper.assertTrue(!StructureManager.setupSpecialChest(world, sd, java.util.List.of(separate), null),
                    "Visited structures are not rewritten");
            world.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(separate, Blocks.AIR.defaultBlockState(), 3);
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    for (int y = 0; y <= 3; y++)
                        world.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            var expanded = StructureManager.placeChestAtStructureCenter(sd, world);
            helper.assertTrue(expanded != null, "Search expands around blocked center");
            helper.assertTrue(expanded.getY() == center.getY()
                    && expanded.distSqr(center) >= 4 && expanded.distSqr(center) <= 20, "Nearest open band is used");
            helper.assertTrue(world.getBlockState(center).is(Blocks.STONE), "Blocked center is not overwritten");
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++)
                    for (int y = 0; y <= 3; y++)
                        world.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(StructureManager.placeChestAtStructureCenter(sd, world) == null,
                    "Filled structure cannot be overwritten or place a chest outside bounds");
        } finally {
            originals.forEach((pos, state) -> world.setBlock(pos, state, 3));
        }
        helper.succeed();
    }
}
