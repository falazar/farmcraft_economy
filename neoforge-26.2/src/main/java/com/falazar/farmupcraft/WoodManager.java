package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class WoodManager {
    static final CustomLogger LOGGER = new CustomLogger("WoodManager");

    @SubscribeEvent
    public static void onBreakLogs(BreakBlockEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(BlockTags.LOGS)) {
            return;
        }
        String biome = level.getBiome(event.getPos()).unwrapKey().map(ResourceKey::identifier)
                .map(Identifier::getPath).orElse("unknown");
        int rate = successRate(player.experienceLevel,
                ChunkManager.getPlotType(event.getPos(), level), biome);
        if (level.getRandom().nextInt(100) >= rate) {
            level.destroyBlock(event.getPos(), false);
            event.setNotifyClient(true);
            event.setCanceled(true);
            return;
        }
        if (earnsBonus(level.getRandom().nextInt(100), rate)) {
            player.addItem(new ItemStack(state.getBlock().asItem(), 1));
            if (level.getRandom().nextInt(10) == 0) {
                player.sendSystemMessage(Component.literal("You got 1 bonus logs!"));
            }
        }
    }

    static int successRate(int experienceLevel, String plotType, String biomePath) {
        int rate = 40 + experienceLevel;
        if ("nursery".equals(plotType)) {
            rate += 20;
            if (biomePath.contains("birch")) {
                rate += 20;
            }
        }
        return rate;
    }

    static boolean earnsBonus(int roll, int successRate) {
        return roll <= successRate / 6;
    }
}
