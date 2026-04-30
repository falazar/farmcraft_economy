package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * Allows a hopper placed directly below a lava cauldron to extract lava as a
 * lava bucket.
 *
 * Behaviour:
 * - The hopper must have room for at least one lava bucket.
 * - The lava cauldron is emptied (becomes a plain cauldron) when extracted.
 * - Only fires server-side; throttled to once every 8 ticks per hopper to match
 * vanilla hopper speed.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CauldronHopperHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (event.level.isClientSide())
            return;

        // Throttle: check every 8 ticks (same cadence as vanilla hopper transfer).
        tickCounter++;
        if (tickCounter % 8 != 0)
            return;

        Level level = event.level;
         if (!(level instanceof ServerLevel serverLevel))
            return;

        // Iterate all loaded hopper block entities across loaded chunks.
        for (net.minecraft.world.level.block.entity.BlockEntity be : new java.util.ArrayList<>(serverLevel.blockEntityList)) {
            if (be instanceof HopperBlockEntity) {
                tryExtractLavaFromCauldron(serverLevel, be);
            }
        }
    }

    private static void tryExtractLavaFromCauldron(ServerLevel level, BlockEntity be) {
        BlockPos hopperPos = be.getBlockPos();
        BlockPos cauldronPos = hopperPos.above();

        // Only operate inside a refinery plot.
        String plotType = ChunkManager.getPlotType(hopperPos, level);
        if (!plotType.equals("refinery"))
            return;

        BlockState cauldronState = level.getBlockState(cauldronPos);

        // Must be a lava cauldron (LayeredCauldronBlock with lava fluid).
        if (!cauldronState.is(Blocks.LAVA_CAULDRON))
            return;

        // Require an empty iron bucket already in the hopper; consume it, produce a
        // lava bucket.
        IItemHandler hopperInv = new InvWrapper((HopperBlockEntity) be);
        int bucketSlot = findSlotWithItem(hopperInv, Items.BUCKET);
        if (bucketSlot == -1)
            return;

        // Consume the iron bucket and replace with a lava bucket in the same slot.
        hopperInv.getStackInSlot(bucketSlot).shrink(1);
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        ItemStack remainder = ItemHandlerHelper.insertItem(hopperInv, lavaBucket, false);
        if (!remainder.isEmpty()) {
            // No room for lava bucket — drop it at the hopper position.
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
