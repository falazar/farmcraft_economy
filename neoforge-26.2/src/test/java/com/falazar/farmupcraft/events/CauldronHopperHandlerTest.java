package com.falazar.farmupcraft.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CauldronHopperHandlerTest {
    @Test
    void inputHopperMustPointAtCauldronAndCannotBeBelowIt(MinecraftServer server) {
        assertNull(server.overworld(), "These tests exercise actual hopper inventories and states, not world updates");
        for (Direction side : new Direction[] {
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
        }) {
            var state = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, side.getOpposite());
            assertTrue(CauldronHopperHandler.isInputHopper(state, side), side.toString());
            var wrong = state.setValue(HopperBlock.FACING, side == Direction.UP ? Direction.NORTH : Direction.DOWN);
            assertFalse(CauldronHopperHandler.isInputHopper(wrong, side), side.toString());
        }
        assertFalse(CauldronHopperHandler.isInputHopper(Blocks.HOPPER.defaultBlockState(), Direction.DOWN));
        assertFalse(CauldronHopperHandler.isInputHopper(Blocks.STONE.defaultBlockState(), Direction.NORTH));
    }

    @Test
    void firstEmptyBucketSlotIsSelectedAndExactlyOneBucketBecomesLava() {
        var input = hopper();
        var output = hopper();
        input.setItem(0, new ItemStack(Items.WATER_BUCKET));
        input.setItem(1, new ItemStack(Items.BUCKET, 3));
        input.setItem(2, new ItemStack(Items.BUCKET, 2));
        var inputInventory = new InvWrapper(input);
        int slot = CauldronHopperHandler.findSlotWithItem(inputInventory, Items.BUCKET);
        assertEquals(1, slot);
        assertTrue(CauldronHopperHandler.transferBucket(inputInventory, new InvWrapper(output), slot).isEmpty());
        assertEquals(2, input.getItem(1).getCount());
        assertEquals(2, input.getItem(2).getCount());
        assertTrue(input.getItem(0).is(Items.WATER_BUCKET));
        assertTrue(output.getItem(0).is(Items.LAVA_BUCKET));
        assertEquals(1, output.getItem(0).getCount());
    }

    @Test
    void fullOutputReturnsOneLavaBucketForWorldDropRatherThanLosingIt() {
        var input = hopper();
        var output = hopper();
        input.setItem(4, new ItemStack(Items.BUCKET));
        for (int slot = 0; slot < output.getContainerSize(); slot++) {
            output.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        ItemStack remainder = CauldronHopperHandler.transferBucket(new InvWrapper(input), new InvWrapper(output), 4);
        assertTrue(input.getItem(4).isEmpty());
        assertTrue(remainder.is(Items.LAVA_BUCKET));
        assertEquals(1, remainder.getCount());
        for (int slot = 0; slot < output.getContainerSize(); slot++) {
            assertTrue(output.getItem(slot).is(Items.COBBLESTONE));
            assertEquals(64, output.getItem(slot).getCount());
        }
    }

    @Test
    void existingUnstackableLavaBucketUsesAnotherSlotAndEmptyInputIsNotFound() {
        var input = hopper();
        var output = hopper();
        assertEquals(-1, CauldronHopperHandler.findSlotWithItem(new InvWrapper(input), Items.BUCKET));
        input.setItem(0, new ItemStack(Items.BUCKET, 2));
        output.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        assertTrue(CauldronHopperHandler.transferBucket(new InvWrapper(input), new InvWrapper(output), 0).isEmpty());
        assertEquals(1, output.getItem(0).getCount());
        assertTrue(output.getItem(1).is(Items.LAVA_BUCKET));
        assertEquals(1, output.getItem(1).getCount());
        assertEquals(1, input.getItem(0).getCount());
    }

    private static HopperBlockEntity hopper() {
        return new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState());
    }
}
