package com.falazar.farmupcraft;

import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WoodManager {

    static final CustomLogger LOGGER = new CustomLogger("WoodManager");

    // On breaking logs, sometimes it will fail and you will not get back any items.
    // You can increase the rate with skills and special items.
    @SubscribeEvent
    public static void onBreakLogs(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        final BlockState blockState = event.getLevel().getBlockState(event.getPos());

        // Only do rule if logs.
        if (!blockState.is(BlockTags.LOGS)) {
            return;
        }

        // STEP 1: Calculate SuccessRate.
        int baseSuccessRate = 40; // 40% chance to get drops at start.
        int successRate = baseSuccessRate;

        // Make generic skills?
        // // CHECK 1: Add skill percent now.
        // if (player.hasSkill("moreStoneDrops")) {
        // successRate += player.getRoleLevel("miner") * 4;
        // }
        // else {
        // STEP 2: Add basic smaller skill percent now for non-loggers.
        successRate += player.experienceLevel;
        LOGGER.info("DEBUG3: added player exp successRate = " + successRate);
        // }

        // STEP 3: Add in bonus for nursery plots.
        Level level = event.getPlayer().getCommandSenderWorld();
        if (ChunkManager.getPlotType(event.getPos(), level).equals("nursery")) {
            successRate += 20;
            LOGGER.info("DEBUG3: target block is in a nursery plot, adding bonus.  successRate = " + successRate);

            // STEP 4: If in nursery, add in for certain biomes.
            // For birch if in any birch biome, add bonus.
            String biomeName = level.getBiome(event.getPos()).unwrapKey().map(ResourceKey::location)
                    .map(ResourceLocation::getPath).orElse("unknown");
            if (biomeName.contains("birch")) {
                // Add bonus for birch and forest biomes.
                successRate += 20;
                LOGGER.info("DEBUG3: target block is in a birch biome, adding bonus. " + biomeName + " successRate = "
                        + successRate);
            }
        }

        // STEP 5: TODO add in bonus for logs itemsBroken, like in fruit trees method,
        // so we get better over time!

        // STEP 6: Roll and check for success.
        LOGGER.info("DEBUG: LOG successRate = " + successRate);
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum >= successRate) {
            LOGGER.info("DEBUG: DESTROYING Log block, no drops..." + successRate);
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);
            // TODO send a failure message on occasion if havnt since logged in.
            return;
        }

        // STEP 7: Add bonus wood for high rolls.
        randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum <= successRate / 6) {
            // Give bonus wood.
            int bonusWood = 1;
            ItemStack stack = new ItemStack(blockState.getBlock().asItem(), bonusWood);
            player.addItem(stack);
            // Only show this message 1 out of 10 times.
            randomNum = rand.nextInt(10); // 10% 0-9
            if (randomNum == 0) {
                player.displayClientMessage(Component.literal("You got " + bonusWood + " bonus logs!"), false);
            }
            LOGGER.info("DEBUG: Giving bonus wood: " + bonusWood + " at " + event.getPos());
        }

        // STEP 8: Bonus extra item for special log types (cinnamon, maple) in nursery.
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        if (blockId != null && ChunkManager.getPlotType(event.getPos(), level).equals("nursery")) {
            String blockPath = blockId.getPath();
            if (blockPath.contains("cinnamon") || blockPath.contains("maple")) {
                player.addItem(new ItemStack(blockState.getBlock().asItem(), 1));
                LOGGER.info("DEBUG: Giving special bonus log for " + blockId + " at " + event.getPos());
            }
        }
    }
}
