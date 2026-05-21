package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.ShowBiomesCommand;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.AsyncLocator;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CropsManager {
    public static final CustomLogger LOGGER = new CustomLogger(CropsManager.class.getSimpleName());

    // Bonemeal recipe was too easy powerful, made it make 1 instead of 3.

    // Main Method here:
    // When trying to plant crops, check our biome rules to see what crops are
    // allowed there.
    @SubscribeEvent
    public static void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player player = (Player) event.getEntity();
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND)
            return;
        if (player.isCreative()) {
            // LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
            return;
        }

        // STEP 2: Test if target block is farmland, if not leave.
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = event.getItemStack();

        // Skip if the player is interacting with a container or interactive block
        // (chest, barrel, door, etc.) — don't interfere with normal block use.
        if (clickedState.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock
                || clickedState.is(net.minecraft.tags.BlockTags.DOORS)
                || clickedState.is(net.minecraft.tags.BlockTags.TRAPDOORS)
                || clickedState.is(net.minecraft.tags.BlockTags.FENCE_GATES)) {
            return;
        }

        // Return if clicked block is not farmland
        boolean isFarmBelow = false;
        // Notice allowing some planting in non farm plot areas, if already plowed.
        // Return if the placement would be on top of farmland (if face is known)
        if (event.getFace() != null) {
            BlockPos placementPos = clickedPos.relative(event.getFace());
            BlockPos blockBelow = placementPos.below();
            BlockState stateBelow = level.getBlockState(blockBelow);

            if (stateBelow.is(FUCTags.FARMLAND)) {
                isFarmBelow = true;
            }
        }
        // STEP 3: If sugarcane or sweetberries, make sure they are on a farm plot only.
        // Must happen BEFORE the farmland check since these are planted on
        // dirt/sand/grass, not farmland.
        if (stack.is(Items.SUGAR_CANE) || stack.is(Items.SWEET_BERRIES)) {
            boolean useFarms = ModEvents.getWorldData().isUseVillageFarms();
            if (useFarms && !ChunkManager.getPlotType(clickedPos, level).equals("farm")) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                        "Sugar cane and sweet berries can only be planted on farm plots. Use /plot buy farm to purchase one.")
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            // Valid farm plot (or useFarms is off) — allow placement without requiring
            // farmland below.
            return;
        }

        if (!clickedState.is(FUCTags.FARMLAND) && !isFarmBelow) {
            return;
        }

        // STEP 4: Test if holding a vanilla or harvestcraft item, if not leave.
        if (!stack.is(FUCTags.MODDED_CROPS) && !stack.is(FUCTags.VANILLA_CROPS) && !stack.is(FUCTags.MODDED_SEEDS)) {
            return;
        }

        // Setup all of our biomes and crops rules allowed, saves to cache.
        // TODO move me, call one time only at start.
        // not needed anymore, is defined in CropItemDataJsonManager
        // setupBiomeCrops(event); // TEMP TESTER AREA.

        // STEP 5: Get current biome the block is in.
        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());

        // The biome has rules defined for what can happen in it or not!
        if (!ModEvents.getWorldData().isUseBiomeCropRules())
            return;

        BiomeRulesManager manager = BiomeRulesManager.get(event.getLevel());
        if (manager == null || !manager.hasRules())
            return;

        BiomeRulesInstance instance = manager.getBiomeRules(biome);
        if (instance == null)
            return;

        // Check if the crop is allowed in the biome
        if (!isCropAllowed(manager, instance, stack, biome, event)) {
            // Cancel event and return now.
            event.setCanceled(true);
        }
    }

    // When trying to hoe a dirt plot, you MUST be on a farm.
    @SubscribeEvent
    public static void onRightClickHoeing(PlayerInteractEvent.RightClickBlock event) {
        try {
            // Step 1: If in creative mode, skip all rules and allow planting all.
            Player player = (Player) event.getEntity();
            Level level = event.getLevel();

            if (player.isCreative()) {
                // LOGGER.info("DEBUG2: Player is in creative mode, skipping all hoe rules.");
                return;
            }

            // STEP 2: Test if holding a hoe.
            ItemStack stack = event.getItemStack();
            if (!stack.is(ItemTags.HOES)) {
                return;
            }

            // STEP 3: Test if target block is dirt.
            final BlockState blockState = event.getLevel().getBlockState(event.getPos());
            if (!blockState.is(Blocks.DIRT) && !blockState.is(Blocks.GRASS_BLOCK) && !blockState.is(Blocks.PODZOL)
                    && !blockState.is(Blocks.COARSE_DIRT)) {
                return;
            }

            // TODO see if its our own farm!!!

            // STEP 4: See if we are on a farm plot now.
            if (!ModEvents.getWorldData().isUseVillageFarms()
                    || ChunkManager.getPlotType(event.getPos(), level).equals("farm")) {
                // LOGGER.info("DEBUG3: target block is in a farm plot, allowing hoeing. ");
            } else {
                // Cancel event and return now.
                // Send message to player now.
                player.displayClientMessage(Component.literal(
                        "You can only hoe on farm plots.  Use /plot buy command to buy plots in your village. "),
                        false);
                event.setCanceled(true);
            }
        } catch (Exception ex) {
            LOGGER.error("Error in onRightClickHoeing: " + ex.getMessage());
            ex.printStackTrace();

            // and send to chat
            Player player = (Player) event.getEntity();
            player.displayClientMessage(Component.literal("Error in onRightClickHoeing: " + ex.getMessage()), false);
        }
    }

    /**
     * When a fully-grown PAM HC2 crop is right-click harvested on a farm plot,
     * roll for a bonus crop drop based on the farm plot's level.
     * Level 1 = no bonus, Level 2 = +20%, Level 3 = +40%, Level 4 = +60%.
     */
    @SubscribeEvent
    public static void onRightClickHarvestCrops(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Player player = (Player) event.getEntity();
        if (player.isCreative())
            return;

        // Skip when holding bone meal.
        if (event.getItemStack().is(Items.BONE_MEAL))
            return;

        // Only handle PAM HC2 crops.
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockState blockState = level.getBlockState(clickedPos);
        Block block = blockState.getBlock();
        String blockId = block.getDescriptionId();
        if (!blockId.contains("pamhc2crops"))
            return;

        // Must be fully grown.
        if (!(block instanceof CropBlock cropBlock))
            return;
        if (!cropBlock.isMaxAge(blockState))
            return;

        // Must be on a farm plot (quiet type check first).
        if (!ChunkManager.getPlotType(clickedPos, level).equalsIgnoreCase("farm"))
            return;

        // Get farm plot level.
        ChunkData farmChunk = ChunkManager.getPlot(clickedPos);
        int farmLevel = farmChunk != null ? farmChunk.getPlotLevel() : 1;
        // Level 1 = no bonus, Level 2 = +20%, Level 3 = +40%, Level 4 = +60%.
        int bonusChance = farmLevel <= 1 ? 0 : (farmLevel - 1) * 20;
        if (bonusChance <= 0)
            return;

        // Derive crop item name from block description ID.
        // e.g. block.pamhc2crops.pamasparaguscrop -> pamhc2crops:asparagusitem
        String itemName = "pamhc2crops:" + blockId.replace("block.pamhc2crops.pam", "").replace("crop", "") + "item";
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
        if (item == null) {
            LOGGER.info("DEBUG: onRightClickHarvestCrops: no item found for {} -> {}", blockId, itemName);
            return;
        }

        // Roll for bonus crop.
        Random rand = new Random();
        int roll = rand.nextInt(100);
        if (roll < bonusChance) {
            player.addItem(new ItemStack(item, 1));
            LOGGER.info("DEBUG: Farm level {} crop bonus! item={} roll={}/{}%", farmLevel, itemName, roll, bonusChance);
        }
    }

    // NOTE: Is about 3 hours now with 400 growth.
    // Now based on actual growth stat instead? onGrow event
    // Slow down crop growth, tooooo fast!!!
    // TODO make a config var for base?
    @SubscribeEvent
    public static void slowCropsEvent(BlockEvent.CropGrowEvent.Pre event) {
        BlockPos blockPos = event.getPos();
        BlockState blockState = event.getLevel().getBlockState(blockPos);
        Block block = blockState.getBlock();

        // If not a crop block, leave.
        if (CropBlockDataJsonManager.getCropBlockDataEntries() == null
                || !CropBlockDataJsonManager.getCropBlockDataEntries().containsKey(block)) {
            // LOGGER.info("DEBUG: NON CROP BLOCK slowCropsEvent: allowed, this target block
            // is " + block.getName().toString());
            return;
        }

        // Get the crop data to do stuff with it!
        CropBlockData data = CropBlockDataJsonManager.getCropBlockDataEntries().get(block);

        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99

        // TODO testing with this. slow min.
        // The rate is now defined in the crop data!
        int baseSuccessRate = (int) data.getGrowthSuccesRate();

        // TODO add all modifiers here.

        if (randomNum >= baseSuccessRate) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Else allow to grow as normal.

        // LOGGER.info("DEBUG: slowCropsEvent: " + randomNum + " allowed, this target
        // block is " + block.getName().toString() + " at "+ blockPos.toShortString());
    }

    // DEBUG: Testing, Looking for village.
    public static void findNearestVillage(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.info("DEBUG: testDebugMethod: this target block is "
                + event.getLevel().getBlockState(event.getPos()).getBlock().getName().toString());
        if (true)
            return;
        // Leave if on client side.
        if (event.getLevel().isClientSide) {
            // LOGGER.info("DEBUG: Skipping if client.");
            return;
        }

        // Find the nearest village.
        Player player = (Player) event.getEntity();

        // TEST 4: findNearestMapStructure code search.
        // REF:
        // https://github.com/NikitaCartes-archive/MinecraftDeobfuscated-Mojang/blob/9b007d733d7cb6df13dce886d05a2dbab1ff5e04/minecraft/src/net/minecraft/server/level/ServerLevel.java#L1076
        // inside ChunkGeneratorWrapper
        // public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel
        // level, HolderSet<Structure> targetStructures, BlockPos pos, int searchRadius,
        // boolean skipKnownStructures) {

        // @Nullable
        // Pair<BlockPos, Holder<Structure>> nearest =
        // super.findNearestMapStructure(level, targetStructures, pos, searchRadius,
        // skipKnownStructures);

        // And another one, similar.

        // Call
        // BlockPos blockPos =
        // serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED,
        // player.blockPosition(), 100, false);
        // Calls
        // public BlockPos findNearestMapStructure(TagKey<Structure> tagKey, BlockPos
        // blockPos, int i, boolean bl) {
        // Calls

        if (player == null) {
            return;
        }

        // Get Server
        MinecraftServer server = player.getServer();

        if (server == null) {
            LOGGER.info("Error: server is null. ");
            return;
        }

        // Get the block position
        BlockPos blockPos = event.getPos();
        // Get serverLevel
        ServerLevel serverLevel = (ServerLevel) event.getLevel();

        // TagKey<Structure> tagKey = StructureTags.EYE_OF_ENDER_LOCATED;
        TagKey<Structure> tagKey = StructureTags.VILLAGE; // hardcoded testing.
        // TagKey<Structure> tagKey = StructureTags.MINESHAFT; // hardcoded testing.
        LOGGER.info("DEBUG: blockPos = " + blockPos);

        int i = 100; // hardcoded const.
        boolean bl = false; // hardcoded const.

        if (!server.getWorldData().worldGenOptions().generateStructures()) {
            LOGGER.info("DEBUG: generateStructures is empty. ");
            return;
        }

        Optional<HolderSet.Named<Structure>> optional = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getTag(tagKey);
        if (optional.isEmpty()) {
            LOGGER.info("DEBUG: structure registry is empty. ");
            return;
        }

        // locate the structure async in order to not freeze the server
        var async = AsyncLocator.locate(
                serverLevel, optional.get(), blockPos, i, true);

        async.thenOnServerThread(e -> {
            // make sure stuff gets merged to main thread on here
            LOGGER.info("DEBUG: pair = " + e);
            Structure structure = e.getSecond().get();
            BlockPos structurePos = e.getFirst();
            LOGGER.info("DEBUG: structurePos = " + structurePos);
            // DEBUG: pair = (BlockPos{x=-864, y=0, z=-352},
            // Reference{ResourceKey[minecraft:worldgen/structure /
            // minecraft:village_desert]=net.minecraft.world.level.levelgen.structure.structures.JigsawStructure@4e1acf2e})
            // TODO: get actual name of village type like desert_village.
            if (structurePos == null) {
                LOGGER.info("DEBUG: structurepos is null. ");
                return;
            }

            if (structure == null) {
                LOGGER.info("DEBUG: structure is null. ");
                return;
            }

            // Get the structure settings
            // LOGGER.info("DEBUG: structure = " + structure);
            LOGGER.info(
                    "DEBUG: structure.getModifiedStructureSettings() = " + structure.getModifiedStructureSettings());

            // TODO Get name from registry somehow now.

            // Cant get bounding box, may need to check all buildings.
            // Need building count, location and name also.
            StructureManager structureManager = serverLevel.structureManager();
            LOGGER.info("DEBUG: structureManager = " + structureManager);
            List<StructureStart> starts = structureManager.startsForStructure(new ChunkPos(structurePos),
                    structureToCheck -> true);
            LOGGER.info("DEBUG: starts = " + starts);
            // check for null

            BoundingBox boundingBox = starts.get(0).getBoundingBox();
            LOGGER.info("DEBUG: boundingBox = " + boundingBox);

            // Loop over each structure start and add up the bounding boxes.
            structureManager.startsForStructure(new ChunkPos(structurePos), structureToCheck -> true).forEach(start -> {
                LOGGER.info("DEBUG: start = " + start);
                LOGGER.info("DEBUG: start.getBoundingBox() = " + start.getBoundingBox());
            });

            LOGGER.info("DEBUG: DONE TEST AREA ");

            // TODO if new village pick a random name, and save it to a file.

            // Results:
            // [18:57:31] [Server thread/INFO] [co.mc.tu.Tutorial1Basics/]: DEBUG: blockPos
            // = MutableBlockPos{x=292, y=70, z=73}
            // 18:57:31.891
            // game
            // [18:57:31] [Server thread/INFO] [co.mc.tu.Tutorial1Basics/]: DEBUG: pair =
            // (BlockPos{x=272, y=0, z=16},
            // Reference{ResourceKey[minecraft:worldgen/structure /
            // ctov:small/village_jungle]=net.minecraft.world.level.levelgen.structure.structures.JigsawStructure@6c539992})
            // YAY correcto!
            // now just loop the hell out of this and look for villages? or as we walk start
            // logging them all?
            // need to save then to external feed!
            // TODO how to save to data file.
        });

    }

    private static final ResourceKey<Biome> UKNOWN_RK = ResourceKey.create(Registries.BIOME,
            new ResourceLocation("unknown"));

    // Given a crop stack item, and biome, check if it is allowed to be planted
    // here.
    // Show crop info data if not allowed.
    public static boolean isCropAllowed(BiomeRulesManager manager, BiomeRulesInstance instance, ItemStack stack,
            Holder<Biome> biome, PlayerInteractEvent event) {
        // This crop is allowed here in this biome, return now and allow planting. Else
        // show some crop biome info.
        if (instance.biomeHasCrops(stack)) {
            return true;
        }

        // TODO MAKE METHOD

        // Step 1Get the name of the crop item
        String cropItemShow = stack.getHoverName().getString();
        // Get the biome name
        ResourceKey<Biome> rl = biome.unwrapKey().orElse(UKNOWN_RK);
        Component biomeNameShow = Component.translatable(getBiomeLangKey(rl.location())).withStyle(ChatFormatting.AQUA);

        Player playerSource = event.getEntity();
        if (event.getLevel().isClientSide) {
            return false;
        }

        // Display message that this crop cannot be planted in this biome
        MutableComponent component = Component.literal("§eYou cannot plant " + cropItemShow + " in ")
                .append(biomeNameShow);
        playerSource.displayClientMessage(component, false);

        // STEP 3: List the crops allowed in the current biome
        // TODO make method, copied in one spot.
        Component cropsAllowedShow = instance.getCrops((ServerLevel) event.getLevel()).stream()
                // Map item to a custom string for special cases and then translate
                .map(item -> {
                    String locationString = item.getDescriptionId();
                    String translatedName = Component.translatable(locationString).getString();

                    // Handle special cases where we want to avoid filtering out specific seeds
                    if (locationString.contains("sesameseedsseeditem")) {
                        translatedName = "Sesame";
                    } else if (locationString.contains("mustardseedsseeditem")) {
                        translatedName = "Mustard";
                    } else if (locationString.contains("sesameseedsitem")) {
                        translatedName = "Sesame Seeds"; // Same display name to keep only one of them
                    } else if (locationString.contains("mustardseedsitem")) {
                        translatedName = "Mustard Seeds"; // Same display name to keep only one of them
                    } else if (locationString.contains("wheat_seeds")) {
                        translatedName = "Wheat"; // Same display name to keep only one of them
                    }

                    return translatedName; // Return the adjusted or original translated name
                })
                // Filter out the remaining names that still include "Seed" or "Seeds" but not
                // the special cases
                .filter(translatedName -> !translatedName.toLowerCase().contains("seed"))
                // Sort the remaining names alphabetically
                .sorted()
                .distinct() // Ensure each name is unique
                // Map the filtered names back to Component
                .map(Component::literal)
                // Join the names with commas
                .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
                .orElse(Component.literal("None"));
        // Construct the message for the crops that can be planted in the biome
        component = Component.literal("§aCrops you can plant in ")
                .append(biomeNameShow)
                .append(": §2")
                .append(cropsAllowedShow);
        playerSource.displayClientMessage(component, false);

        // STEP 4: List the biomes where this crop can be planted.
        if (manager.hasItems()) {
            // TODO Get all this farms biomes, compare and note.
            // TODO Get all this villages biomes, compare and note now.

            // Player playerSource, BiomeRulesManager manager, Item cropItem
            Component biomesListShow = ShowBiomesCommand.getBiomeCropsChat(playerSource, manager, stack.getItem());

            // // Get the village data for the player.
            // DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
            // PlayerData playerData =
            // ModEvents.getPlayerDatabase(playerSource.level()).getData(playerSource.getUUID());
            // VillageData villageData =
            // villageDataDB.getData(playerData.getHomeVillageUUID());
            //
            // // Get the list of biomes in the village.
            // Map<String, Integer> biomes = getVillageBiomes(villageData);
            // // Map to a single set of biomes.
            // Set<String> biomeSet = new HashSet<>();
            // for (String biomeString : biomes.keySet()) {
            // biomeSet.add(biomeString);
            // }
            //
            //
            // // Step 1: Get the list of formatted biome components
            // LOGGER.info("DEBUG1 biomeSet=" + biomeSet);
            // LOGGER.info("DEBUG2 biomesForItem=" +
            // manager.getBiomesForItem(stack.getItem()));
            //
            // List<MutableComponent> biomeComponents =
            // manager.getBiomesForItem(stack.getItem()).stream()
            // .map(b -> {
            // String biomeName = getBiomeLangKey(b.unwrapKey().get().location());
            //// LOGGER.info("DEBUG3 comparing to biomeSet biomeName=" + biomeName + "*");
            // String biomeName2 = biomeName.replace("biome.", ""); // Remove the "biome."
            // prefix
            // biomeName2 = biomeName2.replace(".", ":");
            //// LOGGER.info("DEBUG3 comparing to biomeSet biomeName=" + biomeName + "*");
            //
            // ChatFormatting color = biomeSet.contains(biomeName2) ? ChatFormatting.GREEN :
            // ChatFormatting.AQUA;
            // return Component.translatable(biomeName).withStyle(color);
            // })
            // .distinct() // Ensure each biome is unique
            // .sorted(Comparator.comparing(Component::getString)) // Sort alphabetically
            // .toList();
            //
            // // Step 2: Combine the components into a single component
            // Component biomesListShow = biomeComponents.stream()
            // .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
            // .orElse(Component.literal("None"));

            // Create the final message component
            // component = Component.literal("§bBiomes you can plant " + cropItemShow + " in
            // §3").append(biomesListShow);
            component = Component.literal("Biomes you can plant " + cropItemShow + " in §3").append(biomesListShow);

            playerSource.displayClientMessage(component, false);
        }

        return false;
    }

    public static String getBiomeLangKey(ResourceLocation location) {
        String name = location.getPath();
        String id = location.getNamespace();
        return "biome." + id + "." + name;
    }

    // TODO move to a player class.
    // Make a player use food faster always!
    // REF: https://minecraft.fandom.com/wiki/Hunger
    // TODO make configurable.
    // but then also how do we handle bonuses for player? just check with that math?
    @SubscribeEvent
    public static void hunger(TickEvent.PlayerTickEvent event) {
        // Leave if on client side.
        if (!event.side.isServer()) {
            // LOGGER.info("DEBUG: leaving if not on server side.");
            return;
        }

        Player player = event.player;
        if (player.isCreative()) {
            // LOGGER.info("DEBUG: Player is in creative mode, skipping hunger rules.");
            return;
        }

        Random random = new Random();

        // 500 will starve in about 40 minutes.
        // Testing at 250, twice as fast.
        if (random.nextInt(500) == 1) {
            Difficulty difficulty = player.getCommandSenderWorld().getDifficulty(); // todo test
            // TODO: skip if easy mode? less for normal more for hard?
            // LOGGER.info("DEBUG: difficulty = " + difficulty.toString());
            // "HARD"

            player.getFoodData().addExhaustion(0.5f);
            // LOGGER.info("DEBUG: Adding exhaustion: foodlevel:" +
            // player.getFoodData().getFoodLevel()
            // + " saturation level: " + player.getFoodData().getSaturationLevel()
            // + " exhaustion level: " + player.getFoodData().getExhaustionLevel());
        }

        // Note: this also affect potions and such though, oops.
        // Disable regeneration if it is on.
        // The higher the player's experience level, the less often this check happens.
        int baseInterval = 11000;
        int intervalPerLevel = 1000; // Increase interval by 1000 per exp level
        int regenCheckInterval = baseInterval + (player.experienceLevel * intervalPerLevel);
        if (random.nextInt(regenCheckInterval) == 1) { // Disable regeneration if it is on.
            // LOGGER.info("DEBUG: REGEN: Disabling regeneration now.");

            // See if it is on, then turn it off.
            // It is player special effect regeneration
            MobEffectInstance effect = player.getEffect(MobEffects.REGENERATION);
            if (effect != null) {
                player.removeEffect(MobEffects.REGENERATION);
                player.sendSystemMessage(Component.literal("Your regeneration has faded."));
            }

        }

    }

    // TODO cooking stuffs testing next.
    // Get a list of all cooked items for market!
    // Generic method to get a list of items...
    // notice was static
    public static List<String> getAllFoods(ServerLevel serverLevel, long worldSeed) {
        // Get the list of all items
        Iterable<Item> allItems = ForgeRegistries.ITEMS;

        // Filter information about items from the target mod
        List<String> itemNames = new ArrayList<>();
        for (Item item : allItems) {
            // TODO add in basic foods and tree foods.

            // Check if the item belongs to the target mod
            String itemName = item.getDescriptionId();
            // Example: 'pamhc2foodcore:baconcheeseburgeritem'
            // rolleritem bad ones, filter out.
            // Look for edible as well.
            if ((itemName.contains("pamhc2foodcore") || itemName.contains("pamhc2foodextended"))
                    && itemName.contains("item")
                    && item.isEdible()) {
                // LOGGER.info("DEBUG: name = " + itemName);
                itemNames.add(itemName);
            }
        }

        // Print not showing right.
        int count = itemNames.size();
        LOGGER.info("DEBUG: itemNames1 = " + count + " " + itemNames.toString());

        // Get a random list of 15 foods for market.
        // TODO currently we are calling this on both server and client....
        // How do we call on server side and then push to client?
        // TODO lessen the amount of tree fruits by about half....

        List<String> itemNamesCopy = new ArrayList<>(itemNames); // Create a copy of the original list
        Collections.shuffle(itemNamesCopy, new Random(worldSeed)); // Shuffle the copy

        // Grab the first 15 items and sort them.
        List<String> selectedItems = itemNamesCopy.subList(0, Math.min(itemNamesCopy.size(), 20));
        Collections.sort(selectedItems);
        LOGGER.info("DEBUG: marketItems = " + selectedItems.size() + " " + selectedItems.toString());

        // TODO SAVE AND LOAD TO DB FILE!

        // TODO May need to grab 20 first, then filter out the tree fruits, up to 5.

        // Need to check actual recipe here now though, filter out some tree fruits.
        // todo make method.
        Iterator<String> iterator = selectedItems.iterator();
        while (iterator.hasNext()) {
            String itemName = iterator.next();
            // Shift name format to resource location.
            String itemResourceName = itemName.replace("item.", "").replace(".", ":");

            // Get ingredients to double-check some things.
            final List<ItemStack> inputs = getIngredients(serverLevel, itemResourceName);

            // Check for any tree fruit
            for (ItemStack stack : inputs) {
                if (stack.getDescriptionId().contains("pamhc2trees")) {
                    LOGGER.info("DEBUG3: tree fruit found, removing from list.");
                    // todo might filter apple and vanilla tooo, cinnamon, pepper?
                    iterator.remove();
                    break;
                }
            }
        }

        // NOTICE: item.pamhc2foodextended.schnitzelitem
        // has tofacon as main ingredient, could also be bacon, need to get all recipes
        // probably. or tags?
        // egg tart also fails on avocadoa substitute, we need to look at tags.
        // .pamhc2foodextended.eggtartitem

        // Trim it down to 15 now.
        selectedItems = selectedItems.subList(0, Math.min(selectedItems.size(), 15));
        LOGGER.info("DEBUG4: final marketItems = " + selectedItems.size() + " " + selectedItems.toString());

        return selectedItems;
    }

    public static List<ItemStack> getIngredients(ServerLevel serverLevel, String itemResourceName) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemResourceName));
        LOGGER.info("###################");
        LOGGER.info("DEBUG: item name = " + item.getDescriptionId());

        // todo make a method. a couple.

        // todo test a not shapeless recipe? same? just ordered? no? hmmm

        // Now get recipe...
        Recipe recipe = serverLevel.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(recipe1 -> recipe1.getResultItem(serverLevel.registryAccess()).getItem() == item)
                .findFirst()
                .orElse(null);

        // And Loop over all ingredients now.
        if (recipe != null) {
            // LOGGER.info("DEBUG: recipe = " + recipe); // shapeless or shaped recipe.

            final List<ItemStack> inputs = recipe.getIngredients().stream()
                    .map(ingredient -> ((Ingredient) ingredient).getItems()[0])
                    .toList();

            LOGGER.info("DEBUG1: inputs = " + inputs);

            // Log out each one now.
            for (ItemStack stack : inputs) {
                LOGGER.info("DEBUG2: stack name== " + stack.getDescriptionId());
            }

            return inputs;
        }

        // recipe not found.
        return null;
    }

    /**
     * Middle-click on any crop block (vanilla or HarvestCraft 2) to pick its seed
     * and show the name in chat. Client-only.
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @SubscribeEvent
    public static void onMiddleClick(net.minecraftforge.client.event.InputEvent.MouseButton event) {
        // button 2 = middle mouse, action 1 = press
        if (event.getButton() != 2 || event.getAction() != 1)
            return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        if (mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
            return;

        net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
        net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);

        // getCloneItemStack is the Forge hook pick-block uses — handles HC2 seed blocks
        // too.
        net.minecraft.world.item.ItemStack seedStack = state.getCloneItemStack(blockHit, mc.level, pos, mc.player);
        if (seedStack.isEmpty() || !seedStack.is(com.falazar.farmupcraft.util.FUCTags.VANILLA_AND_MODDED_CROPS))
            return;

        // Show seed name in chat always
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Seed: ").withStyle(net.minecraft.ChatFormatting.GOLD)
                        .append(seedStack.getDisplayName().copy().withStyle(net.minecraft.ChatFormatting.WHITE)),
                false);

        // Move seed from inventory to active hotbar slot (no item creation).
        pickItemFromInventory(mc.player.getInventory(), seedStack);
        event.setCanceled(true);
    }

    /**
     * Finds the first matching seed stack in the player's inventory and selects or
     * swaps it into the active hotbar slot. Does nothing if the seed is not found.
     *
     * @param inv       the player's inventory
     * @param seedStack the seed item to look for (matched by item + tags)
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void pickItemFromInventory(net.minecraft.world.entity.player.Inventory inv,
            net.minecraft.world.item.ItemStack seedStack) {
        int foundSlot = -1;
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, seedStack)) {
                foundSlot = i;
                break;
            }
        }
        if (foundSlot == -1)
            return; // not in inventory — chat message already shown

        if (foundSlot < 9) {
            // Already in hotbar — just select that slot
            inv.selected = foundSlot;
        } else {
            // In main inventory — swap with current hotbar slot
            net.minecraft.world.item.ItemStack hotbarStack = inv.getItem(inv.selected);
            inv.setItem(inv.selected, inv.getItem(foundSlot));
            inv.setItem(foundSlot, hotbarStack);
        }
    }

    // Prevent farmland from being trampled on claimed farm plots.
    // Non-members: always blocked.
    // Members: blocked once the farm plot is level 2+.
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel().isClientSide())
            return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(chunkPos.toLong());
        if (chunkData == null)
            return;

        // Only protect claimed farm/village plots.
        String plotType = chunkData.getType();
        if (!plotType.equalsIgnoreCase("farm") && !plotType.equalsIgnoreCase("village center"))
            return;

        Entity trampler = event.getEntity();
        if (!(trampler instanceof Player player)) {
            // Block animal/mob trampling on any claimed farm plot.
            event.setCanceled(true);
            return;
        }

        if (player.isCreative())
            return;

        if (!isVillageMember(player, chunkData)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You can't trample crops here — not a village member.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // Members: block trampling on level 2+ farm plots.
        if (chunkData.getPlotLevel() >= 2) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("Protected farm — watch your step!")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
    }

    private static boolean isVillageMember(Player player, ChunkData chunkData) {
        java.util.UUID chunkVillageId = chunkData.getVillageId();
        if (chunkVillageId == null) return true;
        PlayerData playerData = ModEvents.getPlayerDatabase().getData(player.getUUID());
        return playerData != null && chunkVillageId.equals(playerData.getHomeVillageUUID());
    }

}
