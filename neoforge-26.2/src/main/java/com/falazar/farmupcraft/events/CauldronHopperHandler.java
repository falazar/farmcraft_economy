package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * Refinery automation: an adjacent hopper facing a lava cauldron supplies an empty
 * bucket, and a hopper below receives the lava bucket (or drops it when full).
 */
@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class CauldronHopperHandler {
    private static final CustomLogger LOGGER = new CustomLogger(CauldronHopperHandler.class.getSimpleName());
    private static final Direction[] INPUT_SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        BlockPos pos = event.getPos();
        if (event.getState().is(Blocks.LAVA_CAULDRON)) processCauldron(pos, level);
        for (Direction side : event.getNotifiedSides()) {
            BlockPos neighbor = pos.relative(side);
            if (level.getBlockState(neighbor).is(Blocks.LAVA_CAULDRON)) processCauldron(neighbor, level);
        }
    }

    private static void processCauldron(BlockPos pos, Level level) {
        // Neighbor events can be nested or carry a state captured before another handler ran.
        if (!level.getBlockState(pos).is(Blocks.LAVA_CAULDRON)) return;
        LOGGER.info("[CauldronHopper] Lava cauldron neighbor-notify at {}", pos);
        String plot = ChunkManager.getPlotType(pos, level);
        if (!plot.equalsIgnoreCase("refinery")) {
            LOGGER.info("[CauldronHopper] Skipped — plot type is '{}' (need 'refinery') at {}", plot, pos);
            return;
        }
        HopperBlockEntity input = null;
        for (Direction side : INPUT_SIDES) {
            BlockPos adjacent = pos.relative(side);
            BlockState state = level.getBlockState(adjacent);
            if (!isInputHopper(state, side)) continue;
            if (level.getBlockEntity(adjacent) instanceof HopperBlockEntity hopper) {
                input = hopper;
                break;
            }
        }
        if (input == null) {
            LOGGER.info("[CauldronHopper] No bucket hopper found adjacent and facing cauldron at {}", pos);
            return;
        }
        BlockPos outputPos = pos.below();
        if (!(level.getBlockEntity(outputPos) instanceof HopperBlockEntity output)) {
            LOGGER.info("[CauldronHopper] No output hopper below cauldron at {}", outputPos);
            return;
        }
        var inputInventory = new InvWrapper(input);
        int slot = findSlotWithItem(inputInventory, Items.BUCKET);
        if (slot < 0) {
            LOGGER.info("[CauldronHopper] No empty bucket in bucket hopper at {} — cannot process", pos);
            return;
        }
        // Empty first: block updates may reenter this handler before insertion finishes.
        if (!level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3)) return;
        ItemStack remainder = transferBucket(inputInventory, new InvWrapper(output), slot);
        input.setChanged();
        output.setChanged();
        if (!remainder.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, outputPos.getX() + 0.5,
                    outputPos.getY() + 0.5, outputPos.getZ() + 0.5, remainder));
            LOGGER.info("[CauldronHopper] Output hopper full — dropped lava bucket at {}", outputPos);
        } else {
            LOGGER.info("[CauldronHopper] SUCCESS — lava bucket inserted into output hopper at {}", outputPos);
        }
        LOGGER.info("[CauldronHopper] Cauldron emptied at {}", pos);
    }

    static boolean isInputHopper(BlockState state, Direction side) {
        return side != Direction.DOWN && state.is(Blocks.HOPPER)
                && state.getValue(HopperBlock.FACING) == side.getOpposite();
    }

    static ItemStack transferBucket(IItemHandler input, IItemHandler output, int slot) {
        ItemStack bucket = input.extractItem(slot, 1, false);
        if (!bucket.is(Items.BUCKET) || bucket.isEmpty()) {
            throw new IllegalStateException("Bucket hopper changed during refinery processing");
        }
        return ItemHandlerHelper.insertItem(output, new ItemStack(Items.LAVA_BUCKET), false);
    }

    static int findSlotWithItem(IItemHandler inventory, Item item) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item)) return slot;
        }
        return -1;
    }
}
