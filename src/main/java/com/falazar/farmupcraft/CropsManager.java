package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.ShowBiomesCommand;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.DataBase;
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
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;
import static com.falazar.farmupcraft.command.VillageCommand.findVillageByChunkPos;
import static com.falazar.farmupcraft.command.VillageCommand.getVillageBiomes;
import static com.pam.pamhc2trees.blocks.BlockPamFruit.AGE;
import static org.apache.commons.lang3.StringUtils.replace;

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
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.SUGAR_CANE) || stack.is(Items.SWEET_BERRIES)) {
            if (!ChunkManager.getPlotType(clickedPos, level).equals("farm")) {
                event.setCanceled(true);
                return;
            }
            // Valid farm plot — allow placement without requiring farmland below.
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
            if (ChunkManager.getPlotType(event.getPos(), level).equals("farm")) {
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
        // triggering multiple times?? once with air?
        // LOGGER.info("\n TRIGGERED: onRightHarvestTrees event. ");

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
        // TODO MAKE METHOD.
        // LOGGER.info("DEBUG blockId is " + blockId);
        String itemName = blockId.replace("block.pamhc2trees.pam", "pamhc2trees:") + "item";
        // Special case: Convert apple to old item name.
        // block.pamhc2trees.pamapple
        if (blockId.equals("block.pamhc2trees.pamapple")) {
            itemName = "minecraft:apple";
        }
        // Get item from new name.
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
        // LOGGER.info("DEBUG: item is " + item);

        // Our base success rate.
        int successPercent = 40;

        // STEP 5: Get how many fruits of this kind you have harvested from statistics.
        int timesPickedUp = serverPlayer.getStats().getValue(Stats.ITEM_PICKED_UP.get(item));
        // get times dropped, subtract the two will be close.
        int timesDropped = serverPlayer.getStats().getValue(Stats.ITEM_DROPPED.get(item));
        int timesHarvested = timesPickedUp - timesDropped;
        // TODO can you hack stats or do they come from multiple servers?
        successPercent += (timesHarvested / 100) * 2; // 2% per 100 harvested.
        LOGGER.info("DEBUG: " + itemName + " timesharvested " + timesHarvested + " times. successPercent is "
                + successPercent);

        // STEP 6: Calc a percent chance of success or failure, and fruit dies.
        // If age is 7 its ripe, break block fruit!
        // 75% chance of success, hardcoded for now.
        successPercent += player.experienceLevel;
        // TODO and New real player level.
        LOGGER.info("DEBUG: fruit harvest after player exp successPercent is " + successPercent);

        // STEP 7: Add in percent if they are in a nursery.
        // TODO cleanup method.
        // TODO add nursery level.
        if (ChunkManager.getPlotType(event.getPos(), level).equals("nursery")) {
            successPercent += 20;
            LOGGER.info("DEBUG3: target fruit is in a nursery plot, adding bonus. successPercent is " + successPercent);
        }
        // TODO make small helper method!!!!!!
        // TODO make small helper method!!!!!!
        // TODO make small helper method!!!!!!
        // TODO make small helper method!!!!!!
        Biome biome = level.getBiome(event.getPos()).value();
        ResourceLocation biomeRes = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
        String biomeName = biomeRes.toString().replaceAll("^[^:]+:", "");
        if (biomeName.equals("orchard")) {
            successPercent += 20;
            LOGGER.info("DEBUG3: target fruit is in a orchard plot, adding bonus. successPercent is " + successPercent);
        }
        // todo test working???

        // STEP 8: Roll for success or failure.
        int finalSuccessPercent = Math.min(successPercent, 98); // max 98 percent.
        // TODO make short roll method.
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

        // TODO calc a percent chance of double fruit,
        // TODO Higher at high nursery and player levels.
        // STEP 8: Roll for Extra fruit.
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

    // TODO: move to stone? manager class.
    // On breaking stone, sometimes it will fail and you will not get back any
    // items.
    // You can increase the rate with skills and special items...
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
        // LOGGER.info("DEBUG1: " + s + " all tags = " +
        // blockState.getTags().map(itemTagKey ->
        // itemTagKey.toString()).collect(Collectors.toList()));

        // Only do rule if base stones or dirt.
        if (!blockState.is(BlockTags.BASE_STONE_OVERWORLD)
                && !blockState.is(BlockTags.DIRT)
                && !blockState.is(BlockTags.BASE_STONE_NETHER)) {
            return;
        }

        // LOGGER.info("DEBUG2: Testing2 here we found base stone/dirt");

        // STEP 1: Get SuccessRate
        int baseSuccessRate = 30; // 50% chance to fail loot at start.
        int successRate = baseSuccessRate;
        // change to 30% start?

        // Make generics.
        // // CHECK 1: Add skill percent now.
        // if (player.hasSkill("moreStoneDrops")) {
        // baseSuccessRate += player.getRoleLevel("miner") * 4;
        // }
        // else {
        // STEP 2: Add basic smaller skill percent now for non miners.
        successRate += player.experienceLevel * 2;
        // }
        // LOGGER.info("DEBUG3: before stoneSuccessRate = " + successRate);

        // STEP 3: Add for blocks broken experience.
        ServerPlayer serverPlayer = (ServerPlayer) event.getPlayer();
        int stoneBroken = serverPlayer.getStats().getValue(Stats.BLOCK_MINED.get(blockState.getBlock()));
        int brokenPercent = stoneBroken / 10000;
        successRate += brokenPercent;
        // LOGGER.info("DEBUG3: test stoneBroken = " + stoneBroken + " brokenPercent = "
        // + brokenPercent +
        // " new successRate = " + successRate);

        // STEP 4: Roll and check for success.
        // TODO make roll a mini method.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        // LOGGER.info("DEBUG3: Random Num = " + randomNum);
        if (randomNum >= successRate) {
            // LOGGER.info("DEBUG: DESTROYING stone block, no drops..." + successRate);
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);
            // TODO send a failure message on occasion if havnt since logged in.
            return;
        }
        // LOGGER.info("DEBUG3: ALLOWING stone block drops...");

        // SECOND ABILITY (only if above worked)
        // trencher
        // if (player.hasSkill("trencher")) {
        // attemptTrenchBreak(player, event.getPos());
        // }

        // TODO add bonus stone.

    }

    // TODO: move to wood? manager class.
    // On breaking logs, sometimes it will fail and you will not get back any items.
    // You can increase the rate with skills and special items...
    @SubscribeEvent
    public static void onBreakLogs(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
        MutableComponent component = Component.translatable(blockState.getBlock().getDescriptionId());
        // String s = component.toString();
        // LOGGER.info("DEBUG1: " + s + " all tags = " +
        // blockState.getTags().map(itemTagKey ->
        // itemTagKey.toString()).collect(Collectors.toList()));

        // Only do rule if logs.
        if (!blockState.is(BlockTags.LOGS)) {
            return;
        }

        // LOGGER.info("DEBUG2: Testing2 here we found logs.");

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
        // so we get better over time!=

        // For Falazar now, increase as faking a skill...
        // successRate = 100;

        // STEP 6: Roll and check for success.
        // TODO make roll a mini method.
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
        // LOGGER.info("DEBUG3: ALLOWING log block drops...");

        // TODO TEST
        // TODO later give bonus wood if high score.
        // STEP 7: Add bonus wood for high rolls.
        randomNum = rand.nextInt(100); // 100% 0-99
        if (randomNum <= successRate / 6) {
            // Give bonus wood.
            int bonusWood = 1;
            ItemStack stack = new ItemStack(Items.OAK_LOG, bonusWood);
            player.addItem(stack);
            // Only show this message 1 out of 10 times.
            randomNum = rand.nextInt(10); // 10% 0-9
            if (randomNum == 0) {
                player.displayClientMessage(Component.literal("You got " + bonusWood + " bonus logs!"), false);
            }
            LOGGER.info("DEBUG: Giving bonus wood: " + bonusWood + " at " + event.getPos());
        }

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

        if (!CropsManager.allowSaplingDrop(event.getPos(), (ServerLevel) event.getLevel())) {
            // LOGGER.info("DEBUG: DESTROYING leaves block, no drops...");
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);
            return;
        }

    }

    public static boolean allowSaplingDrop(BlockPos pos, ServerLevel level) {
        // STEP 1: Set base rate for success.
        int successRate = 20; // 20% chance to get drops at start.

        // LOGGER.info("DEBUG: allowSapling LEAVES BREAK successRate = " + successRate);

        // STEP 2: If in a nursery plot, add bonus.
        if (ChunkManager.getPlotType(pos, level).equals("nursery")) {
            // LOGGER.info("DEBUG3: allowSapling target leaves block is in a nursery plot,
            // adding bonus. ");
            successRate += 30;
        }

        // STEP 3: Roll for success now.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        // LOGGER.info("DEBUG: allowSapling rand leaves block, r=" + randomNum);
        if (randomNum >= successRate) {
            // LOGGER.info("DEBUG: allowSapling DESTROYING leaves block, no drops..." +
            // successRate);
            return false;
        }
        return true;
    }

    // When trying to saplings, check our biome rules to see what is allowed there.
    @SubscribeEvent
    public static void onRightClickSaplingPlant(PlayerInteractEvent.RightClickBlock event) {
        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player playerSource = (Player) event.getEntity();
        if (playerSource.getUsedItemHand() != InteractionHand.MAIN_HAND)
            return;
        if (playerSource.isCreative()) {
            // LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
            return;
        }

        // Skip if client side
        if (event.getLevel().isClientSide) {
            // LOGGER.info("DEBUG: sapling Skipping if client.");
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
        // Without this, right-clicking chests/doors while holding a sapling would
        // trigger the random failure roll and shrink the stack.
        // BlockTags.DIRT covers: dirt, coarse_dirt, rooted_dirt, podzol, mycelium,
        // grass_block.
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(BlockTags.DIRT)) {
            return;
        }

        // STEP 4: Check if the sapling is allowed in this biome.
        // block.minecraft.dark_oak_sapling remove first parts and sapling both.
        String shortName = stackName.replace("block.", "").replace("_sapling", "").replace("minecraft.", "")
                .replace("biomesoplenty.", "");
        LOGGER.info("DEBUG: onRightClickSaplingPlant shortName = " + shortName);
        // Get current biome the block is in.
        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
        String biomeName = biome.unwrapKey().orElse(UKNOWN_RK).location().toString();
        LOGGER.info("DEBUG: onRightClickSaplingPlant biome = " + biomeName);
        // TODO dark oak, redwood, mystic what others? cherry lavender? jungle
        // jacaranda?
        // Create map with list of saplings and list of biomes allowed in.
        Map<String, ArrayList<String>> saplingsLimited = new HashMap<>();
        saplingsLimited.put("dark_oak",
                new ArrayList<>(Arrays.asList("minecraft:dark_forest", "minecraft:dark_forest_hills")));
        saplingsLimited.put("redwood",
                new ArrayList<>(Arrays.asList("minecraft:giant_tree_taiga", "biomesoplenty:redwood_forest")));
        saplingsLimited.put("magic", new ArrayList<>(Arrays.asList("biomesoplenty:mystic_grove")));
        saplingsLimited.put("jungle", new ArrayList<>(Arrays.asList("minecraft:jungle", "minecraft:jungle_hills"))); // couple
                                                                                                                     // more
                                                                                                                     // here?
                                                                                                                     // sparse
                                                                                                                     // edge?
        saplingsLimited.put("cherry", new ArrayList<>(Arrays.asList("minecraft:cherry_grove"))); // check old one?
        saplingsLimited.put("umbran", new ArrayList<>(Arrays.asList("biomesoplenty:ominous_woods")));
        // saplingsLimited.put("lavender", new
        // ArrayList<>(Arrays.asList("minecraft:flower_forest",
        // "minecraft:flower_forest_hills")));

        // todo remove hills off all names?

        // this not working yet.
        if (saplingsLimited.containsKey(shortName) && !saplingsLimited.get(shortName).contains(biomeName)) {
            // Send notice to player.
            MutableComponent component = Component
                    .translatable("§eYou cannot plant " + stack.getHoverName().getString() + " in this biome.");
            playerSource.displayClientMessage(component, false);

            // Cancel event and return now.
            event.setCanceled(true);
        }

        int goodChance = 10;

        // STEP 5: Roll random chance of failure, if not in a nursery.
        String plotType = ChunkManager.getPlotType(clickedPos, level);
        if (plotType.equals("nursery")) {
            LOGGER.info("DEBUG: allowSapling target block is in a nursery plot, adding bonus. ");
            goodChance = 65;
        }

        // STEP 6: If a fruit tree always allow, expensive those.
        // Check item mod name fom pam
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
            // Remove one of the item from hands.
            stack.shrink(1);
            event.setCanceled(true);
            // TODO send a failure message on occasion if havnt since logged in.
            return;
        }
        LOGGER.info("DEBUG: allowing sapling plant " + goodChance);

    }

}
