package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * Lava cauldron → lava bucket automation for refinery plots.
 *
 * Setup requires two hoppers:
 * 1. Bucket hopper — placed beside the cauldron (N/S/E/W/above), facing toward
 * it.
 * Must contain empty iron buckets.
 * 2. Output hopper — placed directly below the cauldron.
 * Receives the produced lava bucket.
 *
 * Triggered by block neighbour updates (e.g. dripstone drips lava into the
 * cauldron).
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CauldronHopperHandler {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide())
            return;

        BlockPos cauldronPos = event.getPos();
        BlockState cauldronState = event.getState();

        // Only care about lava cauldrons.
        if (!cauldronState.is(Blocks.LAVA_CAULDRON))
            return;

        Level level = (Level) event.getLevel();

        // Only operate inside a refinery plot.
        if (!ChunkManager.getPlotType(cauldronPos, level).equalsIgnoreCase("refinery"))
            return;

        // Find the bucket hopper: adjacent to the cauldron (N/S/E/W/above) with its
        // FACING pointing toward the cauldron.
        HopperBlockEntity bucketHopper = null;
        for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[] {
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST,
                net.minecraft.core.Direction.UP }) {
            BlockPos adjPos = cauldronPos.relative(dir);
            BlockState adjState = level.getBlockState(adjPos);
            if (!adjState.is(Blocks.HOPPER))
                continue;
            net.minecraft.core.Direction facing = adjState.getValue(HopperBlock.FACING);
            if (facing != dir.getOpposite())
                continue;
            BlockEntity adjBe = level.getBlockEntity(adjPos);
            if (adjBe instanceof HopperBlockEntity hbe) {
                bucketHopper = hbe;
                break;
            }
        }
        if (bucketHopper == null)
            return;

        // Output hopper: directly below the cauldron.
        BlockPos outputPos = cauldronPos.below();
        BlockEntity outBe = level.getBlockEntity(outputPos);
        if (!(outBe instanceof HopperBlockEntity outputHopper))
            return;

        // Require an empty bucket in the bucket hopper.
        IItemHandler bucketInv = new InvWrapper(bucketHopper);
        int bucketSlot = findSlotWithItem(bucketInv, Items.BUCKET);
        if (bucketSlot == -1)
            return;

        // Consume the bucket.
        bucketInv.getStackInSlot(bucketSlot).shrink(1);

        // Insert lava bucket into the output hopper; drop it if no room.
        IItemHandler outputInv = new InvWrapper(outputHopper);
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        ItemStack remainder = ItemHandlerHelper.insertItem(outputInv, lavaBucket, false);
        if (!remainder.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5, remainder);
            level.addFreshEntity(drop);
        }

        // Empty the cauldron.
        level.setBlock(cauldronPos, Blocks.CAULDRON.defaultBlockState(), 3);
    }

    /**
     * Returns the first slot index containing the given item, or -1 if not found.
     */
    private static int findSlotWithItem(IItemHandler inv, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty() && inv.getStackInSlot(i).is(item)) {
                return i;
            }
        }
        return -1;
    }
}
