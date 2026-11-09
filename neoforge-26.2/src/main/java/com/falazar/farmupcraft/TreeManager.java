package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class TreeManager {
    public static final CustomLogger LOGGER = new CustomLogger(TreeManager.class.getSimpleName());
    private static final Identifier UNKNOWN_BIOME = Identifier.withDefaultNamespace("unknown");
    private static final Map<String, List<String>> SAPLING_LIMITS = Map.of(
            "dark_oak", List.of("minecraft:dark_forest", "minecraft:dark_forest_hills"),
            "pale_oak", List.of("minecraft:pale_garden"),
            "redwood", List.of("minecraft:giant_tree_taiga", "biomesoplenty:redwood_forest"),
            "magic", List.of("biomesoplenty:mystic_grove"),
            "cherry", List.of("minecraft:cherry_grove"),
            "umbran", List.of("biomesoplenty:ominous_woods"));

    private TreeManager() {}

    public static boolean isSaplingBiomeAllowed(String shortSaplingName, Identifier biomeId) {
        if (shortSaplingName == null || biomeId == null) {
            return true;
        }
        if ("jungle".equals(shortSaplingName)) {
            return isJungleBiome(biomeId);
        }
        List<String> allowed = SAPLING_LIMITS.get(shortSaplingName);
        return allowed == null || allowed.contains(biomeId.toString());
    }

    public static boolean isJungleBiome(Identifier biomeId) {
        if (biomeId == null) {
            return false;
        }
        String key = biomeId.toString().toLowerCase(Locale.ROOT);
        return key.contains("jungle") || key.contains("rainforest") || key.contains("rain_forest")
                || key.contains("tropics") || key.contains("tropical");
    }

    public static boolean isNurseryPlot(BlockPos pos, Level level) {
        return "nursery".equals(ChunkManager.getPlotType(pos, level));
    }

    public static com.falazar.farmupcraft.util.NurseryTreeCounter.Counts countFruitTrees(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkAt(pos);
        var chunkPos = chunk.getPos();
        var logs = new java.util.HashMap<BlockPos, java.util.Set<String>>();
        for (BlockPos cursor : BlockPos.betweenClosed(chunkPos.getMinBlockX(), level.getMinY(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), level.getMaxY() - 1, chunkPos.getMaxBlockZ())) {
            if (!chunk.getBlockState(cursor).is(BlockTags.LOGS)) continue;
            var fruits = new java.util.HashSet<String>();
            for (BlockPos nearby : BlockPos.betweenClosed(cursor.offset(-1, -1, -1), cursor.offset(1, 1, 1))) {
                if (!level.hasChunkAt(nearby)) continue;
                var state = level.getBlockState(nearby);
                var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (state.is(Blocks.COCOA) || id.getNamespace().equals("pamhc2trees")
                        && state.hasProperty(BlockStateProperties.AGE_7)) {
                    fruits.add(state.getBlock().getName().getString());
                }
            }
            logs.put(cursor.immutable(), fruits);
        }
        return com.falazar.farmupcraft.util.NurseryTreeCounter.count(logs,
                log -> java.util.Arrays.stream(net.minecraft.core.Direction.values()).map(log::relative).toList());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickCocoaPlanting(PlayerInteractEvent.RightClickBlock event) {
        if (canHandleInteraction(event)) {
            handleCocoaPlantingRestriction(event);
        }
    }

    public static boolean handleCocoaPlantingRestriction(PlayerInteractEvent.RightClickBlock event) {
        if (!canHandleInteraction(event) || !event.getItemStack().is(Items.COCOA_BEANS)
                || !event.getLevel().getBlockState(event.getPos()).is(BlockTags.LOGS)) {
            return false;
        }
        String rejection = cocoaRejection(isNurseryPlot(event.getPos(), event.getLevel()),
                biomeId(event.getLevel(), event.getPos()));
        if (rejection != null) {
            event.getEntity().sendSystemMessage(
                    Component.literal(rejection).withStyle(ChatFormatting.RED));
            event.setCanceled(true);
        }
        return true;
    }

    static String cocoaRejection(boolean nursery, Identifier biome) {
        if (!nursery) {
            return "Cocoa can only be planted on nursery plots.";
        }
        return isJungleBiome(biome) ? null : "Cocoa can only be planted in jungle-like biomes.";
    }

    @SubscribeEvent
    public static void onBreakLeaves(BreakBlockEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer)
                || !event.getState().is(BlockTags.LEAVES)) {
            return;
        }
        if (!allowSaplingDrop(event.getPos(), level)) {
            level.destroyBlock(event.getPos(), false);
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    public static boolean allowSaplingDrop(BlockPos pos, ServerLevel level) {
        ChunkData plot = ChunkManager.getPlot(pos, level);
        int rate = saplingDropRate(isNurseryPlot(pos, level), plot == null ? 1 : plot.getPlotLevel());
        return level.getRandom().nextInt(100) < rate;
    }

    static int saplingDropRate(boolean nursery, int plotLevel) {
        return nursery ? plotLevel * 30 : 20;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickSaplingPlant(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.getEntity().isCreative()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        String name = stack.getItem().getDescriptionId();
        if (!(stack.getItem() instanceof BlockItem blockItem) || !isSaplingItem(stack)) {
            return;
        }
        BlockPlaceContext placement = new BlockPlaceContext(event.getEntity(), event.getHand(), stack,
                event.getHitVec());
        BlockPos target = placement.getClickedPos();
        if (!placement.canPlace()
                || !blockItem.getBlock().defaultBlockState().canSurvive(event.getLevel(), target)) {
            return;
        }
        String shortName = name.replace("block.", "").replace("_sapling", "")
                .replace("minecraft.", "").replace("biomesoplenty.", "");
        Player player = event.getEntity();
        if (!isSaplingBiomeAllowed(shortName, biomeId(event.getLevel(), target))) {
            player.sendSystemMessage(Component.literal(
                    "§eYou cannot plant " + stack.getHoverName().getString() + " in this biome."));
            event.setCanceled(true);
            return;
        }
        int chance = plantingSuccessRate(isNurseryPlot(target, event.getLevel()),
                name.contains("pamhc2trees"), player.experienceLevel);
        if (event.getLevel().getRandom().nextInt(100) >= chance) {
            stack.shrink(1);
            player.sendOverlayMessage(Component.literal("The sapling failed to take root.")
                    .withStyle(ChatFormatting.YELLOW));
            event.setCanceled(true);
        }
    }

    static int plantingSuccessRate(boolean nursery, boolean fruitSapling, int experienceLevel) {
        return (fruitSapling ? 100 : nursery ? 65 : 10) + experienceLevel / 2;
    }

    static boolean isSaplingItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && (blockItem.getBlock() instanceof SaplingBlock
                || stack.is(Items.MANGROVE_PROPAGULE)
                || stack.getItem().getDescriptionId().contains("sapling"));
    }

    // Tree fruit also extends CropBlock; run before Harvest With Ease's HIGH handler.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickHarvestTrees(PlayerInteractEvent.RightClickBlock event) {
        if (!canHandleInteraction(event) || event.getItemStack().is(Items.BONE_MEAL)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Identifier blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"pamhc2trees".equals(blockKey.getNamespace()) || !state.hasProperty(BlockStateProperties.AGE_7)
                || state.getValue(BlockStateProperties.AGE_7) < 7) {
            return;
        }
        Identifier fruitId = fruitItemId(blockKey);
        Item item = BuiltInRegistries.ITEM.getValue(fruitId);
        if (item == null || item == Items.AIR) {
            LOGGER.error("Cannot resolve tree fruit item: " + fruitId);
            return;
        }
        int harvested = effectiveFruitCount(player, item);
        var biome = biomeId(level, pos);
        int rate = fruitSuccessRate(harvested, player.experienceLevel, biome);
        int threshold = Math.min(rate, 98);
        int roll = level.getRandom().nextInt(100);
        boolean failed = roll >= threshold;
        LOGGER.info("Tree fruit harvest roll: player={}, block={}, pos={}, biome={}, harvested={}, xp={}, rawRate={}, roll={}, successIfRollBelow={}, result={}",
                player.getName().getString(), blockKey, pos, biome, harvested, player.experienceLevel,
                rate, roll, threshold, failed ? "FAILURE" : "SUCCESS");
        if (failed) {
            event.setCanceled(true);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            player.sendSystemMessage(Component.literal("You failed to harvest the fruit!"));
            return;
        }
        reduceOtherFruitSkill(player, item);
        if (player.experienceLevel > 25) {
            int bonus = fruitBonusCount(level.getRandom().nextInt(100), rate);
            if (bonus > 0) {
                player.addItem(new ItemStack(item, bonus));
                if (level.getRandom().nextInt(10) == 0) {
                    player.sendSystemMessage(Component.literal("You got " + bonus + " bonus fruit!"));
                }
            }
        }
        String path = blockKey.getPath();
        if (path.contains("cinnamon") || path.contains("maple") || path.contains("paperbark")) {
            player.addItem(new ItemStack(item, 1));
        }
        if ("nursery".equalsIgnoreCase(ChunkManager.getPlotType(pos, level))) {
            ChunkData plot = ChunkManager.getPlot(pos, level);
            if (level.getRandom().nextInt(100) < nurseryBonusChance(plot == null ? 1 : plot.getPlotLevel())) {
                player.addItem(new ItemStack(item, 1));
            }
        }
    }

    static Identifier fruitItemId(Identifier blockId) {
        if ("pamapple".equals(blockId.getPath())) {
            return Identifier.withDefaultNamespace("apple");
        }
        if ("pammaple".equals(blockId.getPath())) {
            return Identifier.fromNamespaceAndPath("pamhc2trees", "maplesyrupitem");
        }
        if ("pampaperbark".equals(blockId.getPath())) {
            return Identifier.withDefaultNamespace("paper");
        }
        if ("pamspiderweb".equals(blockId.getPath())) {
            return Identifier.withDefaultNamespace("string");
        }
        String path = blockId.getPath();
        return Identifier.fromNamespaceAndPath("pamhc2trees",
                (path.startsWith("pam") ? path.substring(3) : path) + "item");
    }

    private static int effectiveFruitCount(ServerPlayer player, Item item) {
        return effectiveFruitCount(player, item, 0);
    }

    private static int effectiveFruitCount(ServerPlayer player, Item item, int incoming) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        int net = player.getStats().getValue(Stats.ITEM_PICKED_UP.get(item))
                - player.getStats().getValue(Stats.ITEM_DROPPED.get(item)) + incoming;
        if (!FruitHarvestSkill.isFruitOrNut(id)) return net;
        int penalty = player.getPersistentData().getCompoundOrEmpty(FruitHarvestSkill.DATA_KEY).getIntOr(id, 0);
        return FruitHarvestSkill.effectiveCount(net, penalty);
    }

    private static void reduceOtherFruitSkill(ServerPlayer player, Item harvested) {
        String current = BuiltInRegistries.ITEM.getKey(harvested).toString();
        if (!FruitHarvestSkill.isFruitOrNut(current)
                || !FruitHarvestSkill.shouldReduce(player.level().getRandom().nextInt(100))) return;
        Map<String, Integer> progress = new java.util.HashMap<>();
        for (var block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("pamhc2trees")
                    || !block.defaultBlockState().hasProperty(BlockStateProperties.AGE_7)) continue;
            Item fruit = BuiltInRegistries.ITEM.getValue(fruitItemId(id));
            if (fruit != null && fruit != Items.AIR) {
                progress.put(BuiltInRegistries.ITEM.getKey(fruit).toString(), effectiveFruitCount(player, fruit));
            }
        }
        var candidates = FruitHarvestSkill.candidates(progress, current);
        if (candidates.isEmpty()) return;
        String target = candidates.get(player.level().getRandom().nextInt(candidates.size()));
        var penalties = player.getPersistentData().getCompoundOrEmpty(FruitHarvestSkill.DATA_KEY);
        penalties.putInt(target, Math.addExact(penalties.getIntOr(target, 0), 1));
        player.getPersistentData().put(FruitHarvestSkill.DATA_KEY, penalties);
        LOGGER.info("Tree fruit skill reduction: player={}, harvested={}, reduced={}, effectiveBefore={}, effectiveAfter={}",
                player.getName().getString(), current, target, progress.get(target), progress.get(target) - 1);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        var oldData = event.getOriginal().getPersistentData();
        if (oldData.contains(FruitHarvestSkill.DATA_KEY)) {
            event.getEntity().getPersistentData().put(FruitHarvestSkill.DATA_KEY,
                    oldData.getCompoundOrEmpty(FruitHarvestSkill.DATA_KEY).copy());
        }
    }

    static int fruitSuccessRate(int harvested, int experienceLevel, Identifier biome) {
        return 40 + (harvested / 100) * 2 + experienceLevel
                + (biome != null && biome.getPath().equals("orchard") ? 20 : 0);
    }

    static int fruitBonusCount(int roll, int successRate) {
        int doubleChance = successRate / 2;
        return roll > doubleChance ? 0 : roll <= doubleChance - 25 ? 2 : 1;
    }

    static int nurseryBonusChance(int plotLevel) {
        return plotLevel <= 1 ? 0 : (plotLevel - 1) * 20;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogBreakNurseryBonus(BreakBlockEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()
                || !event.getState().is(BlockTags.LOGS)
                || !"nursery".equalsIgnoreCase(ChunkManager.getPlotType(event.getPos(), level))) {
            return;
        }
        ChunkData plot = ChunkManager.getPlot(event.getPos(), level);
        if (level.getRandom().nextInt(100) < nurseryBonusChance(plot == null ? 1 : plot.getPlotLevel())) {
            player.addItem(new ItemStack(event.getState().getBlock().asItem(), 1));
        }
    }

    @SubscribeEvent
    public static void onFruitPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack original = event.getOriginalStack();
        Item item = original.getItem();
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (!"pamhc2trees".equals(key.getNamespace()) && !key.equals(Identifier.withDefaultNamespace("apple"))) {
            return;
        }
        // Post runs after inventory insertion but before the pickup statistic is awarded.
        int before = effectiveFruitCount(player, item);
        int after = effectiveFruitCount(player, item, original.getCount() - event.getCurrentStack().getCount());
        int milestone = firstHarvestMilestone(before, after);
        if (milestone > 0) {
            String shortName = key.toString().replace("pamhc2trees:", "").replace("item", "")
                    .replace("pam", "").replace("minecraft:", "");
            player.sendSystemMessage(Component.literal("You're getting better at harvesting " + shortName
                    + "! (" + milestone + " harvested)").withStyle(ChatFormatting.GREEN));
        }
    }

    static int firstHarvestMilestone(int before, int after) {
        for (int h = before + 1; h <= after; h++) {
            if ((h > 0 && h <= 20 && h % 5 == 0) || (h > 20 && (h - 20) % 30 == 0)) {
                return h;
            }
        }
        return 0;
    }

    private static Identifier biomeId(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(ResourceKey::identifier).orElse(UNKNOWN_BIOME);
    }

    private static boolean canHandleInteraction(PlayerInteractEvent.RightClickBlock event) {
        return !event.isCanceled() && !event.getLevel().isClientSide()
                && event.getHand() == InteractionHand.MAIN_HAND && !event.getEntity().isCreative();
    }
}
