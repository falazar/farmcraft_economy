package com.falazar.farmupcraft;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
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
        int successRate = 20; // 20% chance to get drops at start.

        // STEP 2: If in a nursery plot, add bonus.
        if (isNurseryPlot(pos, level)) {
            successRate += 30;
        }

        // STEP 3: Roll for success now.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
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
}
