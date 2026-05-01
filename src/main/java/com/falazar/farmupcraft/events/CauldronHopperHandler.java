package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * Allows a hopper placed directly below a lava cauldron to extract lava as a
 * lava bucket. Triggered by block neighbor updates (e.g. dripstone fills the
 * cauldron). Only operates inside a refinery plot. Requires an empty iron
 * bucket in the hopper; consumes it and produces a lava bucket.
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
        BlockPos hopperPos = cauldronPos.below();
        BlockEntity be = level.getBlockEntity(hopperPos);
        if (!(be instanceof HopperBlockEntity hopper))
            return;

        // Only operate inside a refinery plot.
        String plotType = ChunkManager.getPlotType(hopperPos, level);
        if (!plotType.equals("refinery"))
            return;

        // Block automation: if any chest or barrel is adjacent (4 sides + below), skip.
        for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[] {
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST,
                net.minecraft.core.Direction.DOWN }) {
            BlockState adj = level.getBlockState(hopperPos.relative(dir));
            if (adj.is(Blocks.CHEST) || adj.is(Blocks.TRAPPED_CHEST) || adj.is(Blocks.BARREL))
                return;
        }

        // Require an empty iron bucket in the hopper; consume it, produce a lava
        // bucket.
        IItemHandler hopperInv = new InvWrapper(hopper);
        int bucketSlot = findSlotWithItem(hopperInv, Items.BUCKET);
        if (bucketSlot == -1)
            return;

        // Consume the iron bucket.
        hopperInv.getStackInSlot(bucketSlot).shrink(1);

        // Insert lava bucket; if no room, drop it.
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        ItemStack remainder = ItemHandlerHelper.insertItem(hopperInv, lavaBucket, false);
        if (!remainder.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, hopperPos.getX() + 0.5, hopperPos.getY() + 0.5, hopperPos.getZ() + 0.5, remainder);
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
