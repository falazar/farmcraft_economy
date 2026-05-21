package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import static com.pam.pamhc2trees.blocks.BlockPamFruit.AGE;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TreeManager {

    public static final CustomLogger LOGGER = new CustomLogger(TreeManager.class.getSimpleName());
    private static final ResourceKey<Biome> UNKNOWN_BIOME_RK = ResourceKey.create(Registries.BIOME,
            new ResourceLocation("unknown"));

    private TreeManager() {
    }

    private static final Map<String, List<String>> SAPLING_LIMITS = Map.of(
            "dark_oak", List.of("minecraft:dark_forest", "minecraft:dark_forest_hills"),
            "redwood", List.of("minecraft:giant_tree_taiga", "biomesoplenty:redwood_forest"),
            "magic", List.of("biomesoplenty:mystic_grove"),
            "cherry", List.of("minecraft:cherry_grove"),
            "umbran", List.of("biomesoplenty:ominous_woods"));

    public static boolean isSaplingBiomeAllowed(String shortSaplingName, ResourceLocation biomeId) {
        if (shortSaplingName == null || biomeId == null) {
            return true;
        }

        // Jungle saplings are allowed in any jungle-like biome, including BOP variants.
        if ("jungle".equals(shortSaplingName)) {
            return isJungleBiome(biomeId);
        }

        List<String> allowed = SAPLING_LIMITS.get(shortSaplingName);
        if (allowed == null) {
            return true;
        }

        return allowed.contains(biomeId.toString());
    }

    public static boolean isJungleBiome(ResourceLocation biomeId) {
        if (biomeId == null) {
            return false;
        }
        String key = biomeId.toString().toLowerCase();
        return key.contains("jungle")
                || key.contains("rainforest")
                || key.contains("rain_forest")
                || key.contains("tropics")
                || key.contains("tropical");
    }

    public static boolean isNurseryPlot(BlockPos pos, Level level) {
        return "nursery".equals(ChunkManager.getPlotType(pos, level));
    }

    @SubscribeEvent
    public static void onRightClickCocoaPlanting(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        handleCocoaPlantingRestriction(event);
    }

    // Returns true when cocoa handling is complete (blocked or allowed to continue
    // with
    // vanilla placement).
    public static boolean handleCocoaPlantingRestriction(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.COCOA_BEANS)) {
            return false;
        }

        // Only apply when right-clicking a log block (cocoa only attaches to logs).
        // This prevents triggering when opening chests, barrels, etc.
        BlockState clickedBlockState = event.getLevel().getBlockState(event.getPos());
        if (!clickedBlockState.is(BlockTags.LOGS)) {
            return false;
        }

        Player player = (Player) event.getEntity();
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();

        if (!isNurseryPlot(clickedPos, level)) {
            player.displayClientMessage(
                    Component.literal("Cocoa can only be planted on nursery plots.").withStyle(ChatFormatting.RED),
                    false);
            event.setCanceled(true);
            return true;
        }

        ResourceLocation biomeId = level.getBiome(clickedPos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(UNKNOWN_BIOME_RK.location());
        if (!isJungleBiome(biomeId)) {
            player.displayClientMessage(
                    Component.literal("Cocoa can only be planted in jungle-like biomes.")
                            .withStyle(ChatFormatting.RED),
                    false);
            event.setCanceled(true);
            return true;
        }

        // Restrictions passed; let vanilla cocoa placement continue.
        return true;
    }

    // Make a method that lowers event that causes saplings to fall from tree leaves
    // block rate by a lot.
    @SubscribeEvent
    public static void onBreakLeaves(BlockEvent.BreakEvent event) {
        Player playerSource = event.getPlayer();
        if (playerSource == null) {
            return;
        }

        final BlockState blockState = event.getLevel().getBlockState(event.getPos());

        // Only do rule if leaves
        if (!blockState.is(BlockTags.LEAVES)) {
            return;
        }

        if (!allowSaplingDrop(event.getPos(), (ServerLevel) event.getLevel())) {
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);
        }
    }

    public static boolean allowSaplingDrop(BlockPos pos, ServerLevel level) {
        // STEP 1: Set base rate for success.
        int successRate = 20; // 20% base chance

        // STEP 2: If in a nursery plot, scale by plot level (1=20%, 2=40%, 3=60%).
        if (isNurseryPlot(pos, level)) {
            ChunkPos chunkPos = new ChunkPos(pos);
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDb.getData(chunkPos.toLong());
            int plotLevel = chunkData != null ? chunkData.getPlotLevel() : 1;
            successRate += plotLevel * 20; // plot level 1=20%, 2=40%, 3=60%
        }

        // STEP 3: Roll for success now.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 0-99
        return randomNum < successRate;
    }

    // When trying to saplings, check our biome rules to see what is allowed there.
    @SubscribeEvent
    public static void onRightClickSaplingPlant(PlayerInteractEvent.RightClickBlock event) {
        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player playerSource = (Player) event.getEntity();
        if (playerSource.getUsedItemHand() != InteractionHand.MAIN_HAND)
            return;
        if (playerSource.isCreative()) {
            return;
        }

        // Skip if client side
        if (event.getLevel().isClientSide) {
            return;
        }

        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();

        // STEP 2: Test if holding a sapling item, if not leave.
        ItemStack stack = event.getItemStack();
        String stackName = stack.getDescriptionId();
        if (!stackName.contains("sapling")) {
            return;
        }

        // STEP 3: Only apply rules if actually planting on a dirt surface.
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(BlockTags.DIRT)) {
            return;
        }

        // STEP 4: Check if the sapling is allowed in this biome.
        String shortName = stackName.replace("block.", "").replace("_sapling", "").replace("minecraft.", "")
                .replace("biomesoplenty.", "");
        LOGGER.info("DEBUG: onRightClickSaplingPlant shortName = " + shortName);

        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
        ResourceLocation biomeId = biome.unwrapKey().orElse(UNKNOWN_BIOME_RK).location();
        LOGGER.info("DEBUG: onRightClickSaplingPlant biome = " + biomeId);
        if (!TreeManager.isSaplingBiomeAllowed(shortName, biomeId)) {
            MutableComponent component = Component
                    .translatable("§eYou cannot plant " + stack.getHoverName().getString() + " in this biome.");
            playerSource.displayClientMessage(component, false);
            event.setCanceled(true);
            return;
        }

        int goodChance = 10;

        // STEP 5: Roll random chance of failure, if not in a nursery.
        if (isNurseryPlot(clickedPos, level)) {
            LOGGER.info("DEBUG: allowSapling target block is in a nursery plot, adding bonus. ");
            goodChance = 65;
        }

        // STEP 6: If a fruit tree always allow, expensive those.
        if (stackName.contains("pamhc2trees")) {
            LOGGER.info("DEBUG: allowSapling target block is a fruit tree sapling, adding bonus. ");
            goodChance = 100;
        }

        // STEP X: add in players level.
        goodChance += playerSource.experienceLevel / 2;

        // STEP 6: Roll to see if allowed.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum >= goodChance) {
            LOGGER.info("DEBUG: NOT allowing sapling plant " + goodChance);
            stack.shrink(1);
            event.setCanceled(true);
            return;
        }
        LOGGER.info("DEBUG: allowing sapling plant " + goodChance);
    }

    @SubscribeEvent
    public static void onRightClickHarvestTrees(PlayerInteractEvent.RightClickBlock event) {
        // Check if the event is on client side, then skip.
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // Ensure the event is only processed for the main hand
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        // Step 1: If in creative mode, skip all rules and allow.
        Player player = (Player) event.getEntity();
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND)
            return;
        if (player.isCreative()) {
            return;
        }

        // STEP 2: If holding bone meal skip area also!
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.BONE_MEAL)) {
            return;
        }

        // STEP 3: Test if target block is tree fruit, else leave.
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockState blockState = level.getBlockState(clickedPos);
        Block block = blockState.getBlock();
        String blockId = block.getDescriptionId();
        if (!blockId.contains("pamhc2trees")) {
            return;
        }

        // STEP 4: Get age of fruit. TODO test cinnamon
        int age = blockState.getValue(AGE);
        if (age < 7) {
            return;
        }

        // Get Item and name.
        ServerPlayer serverPlayer = (ServerPlayer) event.getEntity();
        // DEBUG: itemname is item.pamhc2trees.pamchestnutitem
        String itemName = blockId.replace("block.pamhc2trees.pam", "pamhc2trees:") + "item";
        // Special case: Convert apple to old item name.
        if (blockId.equals("block.pamhc2trees.pamapple")) {
            itemName = "minecraft:apple";
        }
        // Get item from new name.
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));

        // Our base success rate.
        int successPercent = 40;

        // STEP 5: Get how many fruits of this kind you have harvested from statistics.
        int timesPickedUp = serverPlayer.getStats().getValue(Stats.ITEM_PICKED_UP.get(item));
        // get times dropped, subtract the two will be close.
        int timesDropped = serverPlayer.getStats().getValue(Stats.ITEM_DROPPED.get(item));
        int timesHarvested = timesPickedUp - timesDropped;
        successPercent += (timesHarvested / 100) * 2; // 2% per 100 harvested.
        LOGGER.info("DEBUG: " + itemName + " timesharvested " + timesHarvested + " times. successPercent is "
                + successPercent);

        // STEP 6: Add player exp level bonus to success.
        successPercent += player.experienceLevel;
        LOGGER.info("DEBUG: fruit harvest after player exp successPercent is " + successPercent);

        // STEP 7: Biome orchard bonus — extra success chance when fruit is in an
        // orchard.
        Biome biome = level.getBiome(event.getPos()).value();
        ResourceLocation biomeRes = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
        String biomeName = biomeRes.toString().replaceAll("^[^:]+:", "");
        if (biomeName.equals("orchard")) {
            successPercent += 20;
            LOGGER.info("DEBUG3: target fruit is in an orchard, adding bonus. successPercent is " + successPercent);
        }

        // STEP 8: Roll for success or failure.
        int finalSuccessPercent = Math.min(successPercent, 98); // max 98 percent.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum >= finalSuccessPercent) {
            // Cancel event and return now.
            event.setCanceled(true);

            // Break the fruit block. (set to air)
            BlockState air = Blocks.AIR.defaultBlockState();
            level.setBlock(clickedPos, air, 3);

            // Show message to player.
            player.displayClientMessage(Component.literal("You failed to harvest the fruit!"), false);
            return;
        }

        // STEP 8b: Roll for extra fruit (experience-based double chance).
        int doubleSuccessPercent = successPercent / 2;
        // If over 25 get bonus fruits.
        if (player.experienceLevel > 25) {
            LOGGER.info("DEBUG: fruit harvest doubleSuccessPercent is " + doubleSuccessPercent);
            randomNum = rand.nextInt(100); // 100% 0-99
            if (randomNum <= doubleSuccessPercent) {
                int bonusCnt = 1;
                if (randomNum <= doubleSuccessPercent - 25) {
                    // Add another!
                    bonusCnt = 2;
                }

                // Give player a fruit item.
                ItemStack itemStack = new ItemStack(item, bonusCnt);
                player.addItem(itemStack);
                // Only show this message 1 out of 10 times.
                randomNum = rand.nextInt(10); // 10% 0-9
                if (randomNum == 0) {
                    player.displayClientMessage(Component.literal("You got " + bonusCnt + " bonus fruit!"), false);
                }
                LOGGER.info("DEBUG: block got bonus fruit named " + blockId);
            }
        }

        // STEP 9: Bonus extra item for special trees (cinnamon, maple, paperbark).
        // Always give 1 extra item on a successful harvest from these trees.
        if (blockId.contains("cinnamon") || blockId.contains("maple") || blockId.contains("paperbark")) {
            ItemStack bonusStack = new ItemStack(item, 1);
            player.addItem(bonusStack);
            LOGGER.info("DEBUG: Giving special bonus harvest for " + blockId);
        }

        // STEP 10: Nursery plot-level bonus fruit (level 2=+20%, level 3=+40%, level
        // 4=+60%).
        // Does NOT affect success rate — only adds extra drops on a successful harvest.
        if (ChunkManager.getPlotType(clickedPos, level).equalsIgnoreCase("nursery")) {
            ChunkData nurseryPlot = ChunkManager.getPlot(clickedPos);
            int nurseryLevel = nurseryPlot != null ? nurseryPlot.getPlotLevel() : 1;
            int nurseryBonusChance = nurseryLevel <= 1 ? 0 : (nurseryLevel - 1) * 20;
            if (nurseryBonusChance > 0 && rand.nextInt(100) < nurseryBonusChance) {
                player.addItem(new ItemStack(item, 1));
                LOGGER.info("DEBUG: Nursery level {} bonus fruit for {}!", nurseryLevel, itemName);
            }
        }
    }

    /**
     * When a log is broken in a nursery plot, roll for a bonus log drop based on
     * plot level.
     * Level 1 = no bonus, Level 2 = +20%, Level 3 = +40%, Level 4 = +60%.
     */
    @SubscribeEvent
    public static void onLogBreakNurseryBonus(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide())
            return;

        Player player = event.getPlayer();
        if (player == null || player.isCreative())
            return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!state.is(BlockTags.LOGS))
            return;

        Level level = player.getCommandSenderWorld();
        if (!ChunkManager.getPlotType(pos, level).equalsIgnoreCase("nursery"))
            return;

        ChunkData nurseryPlot = ChunkManager.getPlot(pos);
        int nurseryLevel = nurseryPlot != null ? nurseryPlot.getPlotLevel() : 1;
        int bonusChance = nurseryLevel <= 1 ? 0 : (nurseryLevel - 1) * 20;
        if (bonusChance <= 0)
            return;

        if (new Random().nextInt(100) < bonusChance) {
            ItemStack bonusLog = new ItemStack(state.getBlock().asItem(), 1);
            player.addItem(bonusLog);
            LOGGER.info("DEBUG: Nursery level {} bonus log for {}!", nurseryLevel,
                    state.getBlock().getDescriptionId());
        }
    }

    /**
     * Fires when a player picks up a pam fruit item (or apple).
     * Shows a skill milestone message every 5 harvests up to 20, then every 30.
     * Runs on pickup so the stat count reflects actual fruit received.
     */
    @SubscribeEvent
    public static void onFruitPickup(EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        Item item = event.getItem().getItem().getItem();
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(item);
        if (itemKey == null)
            return;
        String itemId = itemKey.toString();
        if (!itemId.startsWith("pamhc2trees:") && !itemId.equals("minecraft:apple"))
            return;

        // Current total before this pickup; after = current + amount being picked up.
        int currentPickedUp = serverPlayer.getStats().getValue(Stats.ITEM_PICKED_UP.get(item));
        int currentDropped = serverPlayer.getStats().getValue(Stats.ITEM_DROPPED.get(item));
        int before = currentPickedUp - currentDropped;
        int after = before + event.getItem().getItem().getCount();

        // Find the first milestone crossed in this pickup.
        for (int h = before + 1; h <= after; h++) {
            boolean isMilestone = (h <= 20 && h % 5 == 0)
                    || (h > 20 && (h - 20) % 30 == 0);
            if (isMilestone) {
                String fruitShortName = itemId.replace("pamhc2trees:", "").replace("item", "")
                        .replace("pam", "").replace("minecraft:", "");
                serverPlayer.displayClientMessage(
                        Component.literal("You're getting better at harvesting " + fruitShortName
                                + "! (" + h + " harvested)").withStyle(ChatFormatting.GREEN),
                        false);
                break; // only one message per pickup
            }
        }
    }
}
