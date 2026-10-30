package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class StoneManager {
    static final CustomLogger LOGGER = new CustomLogger("StoneManager");

    @SubscribeEvent
    public static void onBreakStone(BreakBlockEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (!isAffectedBlock(state)) {
            return;
        }
        int mined = player.getStats().getValue(Stats.BLOCK_MINED.get(state.getBlock()));
        if (level.getRandom().nextInt(100) >= successRate(player.experienceLevel, mined)) {
            level.destroyBlock(event.getPos(), false);
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    static boolean isAffectedBlock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_NETHER);
    }

    static int successRate(int experienceLevel, int blocksMined) {
        return 30 + experienceLevel * 2 + blocksMined / 10000;
    }
}
