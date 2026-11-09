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
            return;
        }
        if (!player.isCreative() && hasLevelSevenVillagePlotBonus(event.getPos(), level)
                && player.hasCorrectToolForDrops(state)) {
            net.minecraft.world.level.block.Block.dropResources(state, level, event.getPos(),
                    level.getBlockEntity(event.getPos()), player, player.getMainHandItem());
        }
    }

    static boolean hasLevelSevenVillagePlotBonus(net.minecraft.core.BlockPos pos, ServerLevel level) {
        var chunk = ChunkManager.getPlot(pos, level);
        if (chunk == null || chunk.getVillageId() == null) {
            return false;
        }
        var village = com.falazar.farmupcraft.events.ModEvents.getVillageDatabase(level).getData(chunk.getVillageId());
        return isBoughtClaimPlotWithLevelSevenBonus(chunk.getType(), village == null ? 0 : village.getLevel());
    }

    static boolean isBoughtClaimPlotWithLevelSevenBonus(String plotType, int villageLevel) {
        return villageLevel >= 7 && plotType != null && !plotType.isBlank()
                && !"village".equalsIgnoreCase(plotType.trim());
    }

    static boolean isAffectedBlock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_NETHER);
    }

    static int successRate(int experienceLevel, int blocksMined) {
        return 30 + experienceLevel * 2 + blocksMined / 10000;
    }
}
