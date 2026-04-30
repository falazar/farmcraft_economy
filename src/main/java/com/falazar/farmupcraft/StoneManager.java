package com.falazar.farmupcraft;

import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StoneManager {

    static final CustomLogger LOGGER = new CustomLogger("StoneManager");

    // On breaking stone, sometimes it will fail and you will not get back any
    // items.
    // You can increase the rate with skills and special items.
    // cobblestone and deepslate drop rate here.
    @SubscribeEvent
    public static void onBreakStone(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
        MutableComponent component = Component.translatable(blockState.getBlock().getDescriptionId());
        String s = component.toString();

        // Only do rule if base stones or dirt.
        if (!blockState.is(BlockTags.BASE_STONE_OVERWORLD)
                && !blockState.is(BlockTags.DIRT)
                && !blockState.is(BlockTags.BASE_STONE_NETHER)) {
            return;
        }

        // STEP 1: Get SuccessRate
        int baseSuccessRate = 30; // 30% chance to fail loot at start.
        int successRate = baseSuccessRate;

        // Make generics.
        // // CHECK 1: Add skill percent now.
        // if (player.hasSkill("moreStoneDrops")) {
        // baseSuccessRate += player.getRoleLevel("miner") * 4;
        // }
        // else {
        // STEP 2: Add basic smaller skill percent now for non miners.
        successRate += player.experienceLevel * 2;
        // }

        // STEP 3: Add for blocks broken experience — 1% per 10,000 blocks mined.
        ServerPlayer serverPlayer = (ServerPlayer) event.getPlayer();
        int stoneBroken = serverPlayer.getStats().getValue(Stats.BLOCK_MINED.get(blockState.getBlock()));
        int brokenPercent = stoneBroken / 10000;
        successRate += brokenPercent;

        // STEP 4: Roll and check for success.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum >= successRate) {
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);
            // TODO send a failure message on occasion if havnt since logged in.
            return;
        }

        // SECOND ABILITY (only if above worked)
        // trencher
        // if (player.hasSkill("trencher")) {
        // attemptTrenchBreak(player, event.getPos());
        // }

        // TODO add bonus stone.
    }
}
