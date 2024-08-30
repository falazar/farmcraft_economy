package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.CropItemData;
import com.falazar.farmupcraft.data.CropItemDataJsonManager;
import com.falazar.farmupcraft.util.AsyncLocator;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.replace;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CropsManager {
    private static final Logger LOGGER = LogManager.getLogger();

    // Class-level variable to store biome information
    private static Map<String, List<String>> biomeCrops = new HashMap<>();
    private static Map<String, List<String>> cropBiomes = new HashMap<>();

    @SubscribeEvent
    // Main Method here:
    // When trying to plant crops, check our biome rules to see what crops are allowed there.
    public static void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player player = (Player) event.getEntity();
        if(player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
        if (player.isCreative()) {
            LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
            return;
        }

        // STEP 2: Test if target block is farmland, if not leave.
        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
        if (!blockState.is(FUCTags.FARMLAND)) {
            return;
        }

        // TODO sugarcane and sweetberries.

        // STEP 3: Test if holding a vanilla or harvestcraft item, if not leave.
        ItemStack stack = event.getItemStack();
        if (!stack.is(FUCTags.MODDED_CROPS) && !stack.is(FUCTags.VANILLA_CROPS)) {
            return;
        }

        // DEBUG ZONE:
        if (stack.is(Items.WHEAT_SEEDS)) {
            LOGGER.info("DEBUG: is a wheat seeds crop, running DEBUG METHOD.");
            findNearestVillage(event);

            // Only run on server side.
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                long worldSeed = serverLevel.getSeed();
//                getAllFoods(serverLevel, worldSeed);
            }
        }

        // Setup all of our biomes and crops rules allowed, saves to cache.
        // TODO move me, call one time only at start.
        //not needed anymore, is defined in CropItemDataJsonManager
        //setupBiomeCrops(event);  // TEMP TESTER AREA.

        // STEP 4: Get current biome the block is in.
        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
        if (CropItemDataJsonManager.getCropItemDataEntries() == null || !CropItemDataJsonManager.getCropItemDataEntries().containsKey(stack.getItem()))
            return;
        CropItemData data = CropItemDataJsonManager.getCropItemDataEntries().get(stack.getItem());


        //String biomeName = nameOfBiome(event.getLevel(), biome).toString();
        //LOGGER.info("DEBUG: this biome is " + nameOfBiome(event.getLevel(), biome));


        // STEP 5: Implement biome rules now, if fail, cancel plant and leave.
        // Is this an allowed plant for this biome?

        //you can define stuff in the Crop Item Data! :)
        if (data.containsBiome(biome)) {
            // Cancel event and return now.
            event.setCanceled(true);
            return;
        }


        // TODO figure out how to use.
//            BlockEvent.BreakEvent
//            BreakEvent(World world, BlockPos pos, BlockState state, PlayerEntity player)
    }


    // NOTE: Is about 3 hours now with 400 growth.
    // Now based on actual growth stat instead?  onGrow event
    // Slow down crop growth, tooooo fast!!!
    // TODO make a config var for base?
    // NOTICE: Event methods cannot be static.
    @SubscribeEvent
    public static void slowCropsEvent(BlockEvent.CropGrowEvent.Pre event) {
        BlockPos blockPos = event.getPos();
        BlockState blockState = event.getLevel().getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (CropBlockDataJsonManager.getCropBlockDataEntries() == null || !CropBlockDataJsonManager.getCropBlockDataEntries().containsKey(block))
            return;
        //get the crop data to do stuff with it!
        CropBlockData data = CropBlockDataJsonManager.getCropBlockDataEntries().get(block);


        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99

        // TODO testing with this. slow min.
        //the rate is now defined in the crop data!
        int baseSuccessRate = (int) data.getGrowthSuccesRate();

        // TODO add all modifiers here.

        if (randomNum >= baseSuccessRate) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Else allow to grow as normal.

//        LOGGER.info("DEBUG: slowCropsEvent: " + randomNum + " allowed, this target block is " + block.getName().toString());
    }


    // DEBUG: Testing, Looking for village.
    public static void findNearestVillage(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.info("DEBUG: testDebugMethod: this target block is " + event.getLevel().getBlockState(event.getPos()).getBlock().getName().toString());

        // Leave if on client side.
        if (event.getLevel().isClientSide) {
            // LOGGER.info("DEBUG: Skipping if client.");
            return;
        }

        // Find the nearest village.
        Player player = (Player) event.getEntity();


        // TEST 4: findNearestMapStructure code search.
        // REF: https://github.com/NikitaCartes-archive/MinecraftDeobfuscated-Mojang/blob/9b007d733d7cb6df13dce886d05a2dbab1ff5e04/minecraft/src/net/minecraft/server/level/ServerLevel.java#L1076
        // inside ChunkGeneratorWrapper
//            public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> targetStructures, BlockPos pos, int searchRadius, boolean skipKnownStructures) {

//            @Nullable
//            Pair<BlockPos, Holder<Structure>> nearest = super.findNearestMapStructure(level, targetStructures, pos, searchRadius, skipKnownStructures);

        // And another one, similar.

        // Call
//            BlockPos blockPos = serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, player.blockPosition(), 100, false);
        // Calls
//            public BlockPos findNearestMapStructure(TagKey<Structure> tagKey, BlockPos blockPos, int i, boolean bl) {
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

//       TagKey<Structure> tagKey = StructureTags.EYE_OF_ENDER_LOCATED;
        TagKey<Structure> tagKey = StructureTags.VILLAGE;  // hardcoded testing.
//            TagKey<Structure> tagKey = StructureTags.MINESHAFT;  // hardcoded testing.
        LOGGER.info("DEBUG: blockPos = " + blockPos);

        int i = 100; // hardcoded const.
        boolean bl = false; // hardcoded const.

        if (!server.getWorldData().worldGenOptions().generateStructures()) {
            LOGGER.info("DEBUG: generateStructures is empty. ");
            return;
        }

        Optional<HolderSet.Named<Structure>> optional = server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tagKey);
        if (optional.isEmpty()) {
            LOGGER.info("DEBUG: structure registry is empty. ");
            return;
        }


        //locate the structure async in order to not freeze the server
        var async = AsyncLocator.locate(
                serverLevel, optional.get(), blockPos, i, true
        );

        async.thenOnServerThread(e -> {
                    //make sure stuff gets merged to main thread on here
                    LOGGER.info("DEBUG: pair = " + e);
                    Structure structure = e.getSecond().get();
                    BlockPos structurePos = e.getFirst();
                    LOGGER.info("DEBUG: structurePos = " + structurePos);
                    //  DEBUG: pair = (BlockPos{x=-864, y=0, z=-352}, Reference{ResourceKey[minecraft:worldgen/structure / minecraft:village_desert]=net.minecraft.world.level.levelgen.structure.structures.JigsawStructure@4e1acf2e})
                    // TODO: get actual name of village type like desert_village.

                    if (structure == null) {
                        LOGGER.info("DEBUG: structure is null. ");
                        return;
                    }

                    // Get the structure settings
//                  LOGGER.info("DEBUG: structure = " + structure);
                    LOGGER.info("DEBUG: structure.getModifiedStructureSettings() = " + structure.getModifiedStructureSettings());

                    // TODO Get name from registry somehow now.


                    // Cant get bounding box, may need to check all buildings.
                    // Need building count, location and name also.
                    StructureManager structureManager = serverLevel.structureManager();
                    LOGGER.info("DEBUG: structureManager = " + structureManager);
                    List<StructureStart> starts = structureManager.startsForStructure(new ChunkPos(structurePos), structureToCheck -> true);
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
                    // [18:57:31] [Server thread/INFO] [co.mc.tu.Tutorial1Basics/]: DEBUG: blockPos = MutableBlockPos{x=292, y=70, z=73}
                    //18:57:31.891
                    //game
                    //[18:57:31] [Server thread/INFO] [co.mc.tu.Tutorial1Basics/]: DEBUG: pair = (BlockPos{x=272, y=0, z=16},
                    // Reference{ResourceKey[minecraft:worldgen/structure /
                    // ctov:small/village_jungle]=net.minecraft.world.level.levelgen.structure.structures.JigsawStructure@6c539992})
                    // YAY correcto!
                    // now just loop the hell out of this and look for villages?  or as we walk start logging them all?
                    // need to save then to external feed!
                    // TODO how to save to data file.
                }
        );


    }


    // Given a crop stack item, and biomename, check if it is allowed to be planted here.
    public static boolean isCropAllowed(ItemStack stack, String biomeName, PlayerInteractEvent event) {
        List<String> cropsAllowed = getCropsForBiome(biomeName);
//            LOGGER.info("DEBUG: biomeName = " + biomeName);
//            LOGGER.info("DEBUG: cropsAllowed = " + cropsAllowed);

        String seedDescription = stack.getDescriptionId();
        // Add second variation seeditem vs regular veggie, both can plant.
        String seedDescriptionTwo = seedDescription;
        String seedItem = seedDescription;
        if (seedDescription.contains("seeditem")) {
            seedDescriptionTwo = replace(seedDescriptionTwo, "seeditem", "item");
        } else if (seedDescription.contains("pamhc2crops")) {
            // If not a seeditem one, add seeditem onto it, and test again.
            // Adding this cause you can plant with crop item and seed item both.
            seedDescriptionTwo = seedDescriptionTwo.substring(0, seedDescriptionTwo.length() - 4) + "seeditem";
            seedItem = seedDescriptionTwo;
        }
//            LOGGER.info("DEBUG: seedDescriptionTwo = " + seedDescriptionTwo);

        // Check for 'seeditem', and item plain, as both can be planted.
        if (!cropsAllowed.contains(seedDescription) && !cropsAllowed.contains(seedDescriptionTwo)) {
            String biomeNameShow = biomeName.split(":")[1];

            //use translatable to get correct name instead
            String cropItemShow = Component.translatable(seedDescription).toString();
            List<String> cropsAllowedShow = getCropShowNames(cropsAllowed);
//                LOGGER.info("DEBUG: For this biome: " + biomeNameShow + " you cannot plant " + cropItemShow);
//                LOGGER.info("DEBUG: Crops you can plant in " + biomeNameShow + ": " + cropsAllowedShow.toString());

            // Send a message to the player.
            if (event.getLevel().isClientSide()) {
                Player player = event.getEntity();
                // TODO change to reddish for warnings.
                //PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§eYou cannot plant " + cropItemShow + " in " + biomeNameShow + " biome.");
                //player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                MutableComponent component = Component.literal("§eYou cannot plant " + cropItemShow + " in " + biomeNameShow + " biome.");
                player.displayClientMessage(component, false);

                // Send full crop list for this biome.
                //chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§aCrops you can plant in " + biomeNameShow + ": §2" + cropsAllowedShow.toString());
                //player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                component = Component.literal("§aCrops you can plant in " + biomeNameShow + ": §2" + cropsAllowedShow.toString());
                player.displayClientMessage(component, false);

                LOGGER.info("DEBUG: seedItem = " + seedItem);
                List<String> biomesAllowed = cropBiomes.get(seedItem);
                if (biomesAllowed != null) {
                    // Send full list of biomes you can plant it in.
                    //chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§bBiomes you can plant " + cropItemShow + ": §3" + cropBiomes.get(seedItem).toString());
                    //player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));


                    component = Component.literal("§bBiomes you can plant " + cropItemShow + ": §3" + cropBiomes.get(seedItem).toString());
                    player.displayClientMessage(component, false);
                }
            }
            return false;
        }

        // Passed all, allow planting.
        return true;
    }

    public static String getCropShowName(String itemName) {
        // Remove pam stuff and minecraft stuffs...

        //todo use translatable to get correct name for this as well
        itemName = replace(itemName, "item.pamhc2crops.", "");
        itemName = replace(itemName, "item.minecraft.", "");
        itemName = replace(itemName, "seeditem", "");
        itemName = itemName.replaceAll("item$", ""); // last one only.

        return itemName;
    }

    public static List<String> getCropShowNames(List<String> itemNames) {
        List<String> modifiedNames = new ArrayList<>();

        // Remove pam stuff and minecraft stuffs...
        for (String itemName : itemNames) {
            modifiedNames.add(getCropShowName(itemName));
        }

        return modifiedNames;
    }

    // Method to access biomeCrops globally
    public static List<String> getCropsForBiome(String biomeName) {
        return biomeCrops.getOrDefault(biomeName, new ArrayList<>());
    }

    // TODO change to an onLoading event instead.
    // TODO make desert/wastelands have only 10 items.
    public static void setupBiomeCrops(PlayerInteractEvent event) {
        // Check cache now so isnt done twice.
        if (!biomeCrops.isEmpty()) {
//            LOGGER.info("DEBUG: Already have biomeCrops cache, not redoing it now. ");
            return;
        }

        LOGGER.info("DEBUG: Setting up biomeCrops list now... ");

        // STEP 1: get a list of all crops and seeds, harvestCraft crops and vanilla crops.
        List<String> cropNames = getAllCropNames();


        // STEP 2: Get the list of all biomes from the registry
        List<String> biomeNames = getAllBiomeNames(event.getLevel());


        // STEP 3: For each biome randomize seed list and assign a set to them.
        // STEP 4: Save a biome list to each crop type also, for display.
        // Loop over all biomes and save cropList to cache map.
        for (String biomeName : biomeNames) {
//                List<String> selectedCrops = setupRandomCrops(biomeName, cropNames);
            List<String> selectedCrops = new ArrayList<>(setupRandomCrops(biomeName, cropNames));
            biomeCrops.put(biomeName, selectedCrops);
        }

//            LOGGER.info("TEST DEBUG: all biome crops = " + biomeCrops.toString());

        // STEP 5: create reverse cache list also.
        cropBiomes = invertBiomeCrops(biomeCrops);
//            LOGGER.info("TEST DEBUG: all cropBiomes = " + cropBiomes.toString());


        // STEP 6: Save to local data storage. maybe no? so I can read it.
        // LOAD previous ones from storage. maybe no?
    }

    public static Map<String, List<String>> invertBiomeCrops(Map<String, List<String>> biomeCrops) {
        Map<String, List<String>> cropBiomes = new HashMap<>();

        // Loop through each entry in biomeCrops
        for (Map.Entry<String, List<String>> entry : biomeCrops.entrySet()) {
            String biomeName = entry.getKey();
            String biomeNameShow = biomeName.replace("minecraft:", "").replace("biomesoplenty:", "");
            List<String> cropNames = entry.getValue();

            // Loop through cropNames for the current biome
            for (String cropName : cropNames) {
                cropBiomes.computeIfAbsent(cropName, k -> new ArrayList<>()).add(biomeNameShow);
            }
        }

        // STEP 2: Sort each one now also.
        Map<String, List<String>> sortedCropBiomes = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : cropBiomes.entrySet()) {
            List<String> sortedBiomes = new ArrayList<>(entry.getValue());
            Collections.sort(sortedBiomes);
            sortedCropBiomes.put(entry.getKey(), sortedBiomes);
        }

        return sortedCropBiomes;
    }

    public static List<String> getAllCropNames() {
        // Get the list of all items
        Iterable<Item> allItems = ForgeRegistries.ITEMS;

        // Print information about items from the target mod
        List<String> cropNames = new ArrayList<>();
        for (Item item : allItems) {
            // Check if the item belongs to the target mod
            String itemName = item.getDescriptionId();
            if (itemName.contains("pamhc2crops") && itemName.contains("seeditem")) {
                // item.pamhc2crops.bokchoyseeditem
//                    // Save list trim strings up.
//                    itemName = replace(itemName, "item.pamhc2crops.", "");
//                    itemName = replace(itemName, "seeditem", "");
                cropNames.add(itemName);
            }
        }


        // STEP 2: Add in vanilla crops.
        // Careful not sure how we process this later on, hmmmm, need to convert back also.
        // Maybe save all as registry first, then convert only on display?
        cropNames.add("item.minecraft.potato");
        cropNames.add("item.minecraft.carrot");
        cropNames.add("item.minecraft.wheat_seeds");
        cropNames.add("item.minecraft.beetroot_seeds");

        return cropNames;
    }

    public static List<String> getAllBiomeNames(Level level) {
        List<String> biomeNames = new ArrayList<>();
        for (var biomeEntry : level.registryAccess().registryOrThrow(Registries.BIOME).entrySet()) {
//              DEBUG: biomeEntry = ResourceKey[minecraft:worldgen/biome / minecraft:lukewarm_ocean]=net.minecraft.world.level.biome.Biome@54139c70
            ResourceKey<Biome> biomeKey = biomeEntry.getKey();
            // Get the ResourceLocation from the ResourceKey
            ResourceLocation biomeLocation = biomeKey.location();
//              LOGGER.info("biomeLocation: " + biomeLocation);
            biomeNames.add(biomeLocation.toString());
        }
//            LOGGER.info("biomeNames list =" + biomeNames.toString());

        return biomeNames;
    }

    public static List<String> setupRandomCrops(String biomeName, List<String> cropNames) {
        // Rule: No crops plantable in ocean biomes.
        if (biomeName.contains("ocean")) {
            return Collections.emptyList();
        }

        // Randomly shuffle cropNames based on the seed derived from biomeName
        long seed = getSeedFromBiomeName(biomeName);
//            if (biomeName.equals("minecraft:plains") || biomeName.equals("minecraft:river")) {
//                LOGGER.info("DEBUG3: biomeName is " + biomeName);
//                LOGGER.info("DEBUG3: Seed value is " + seed);
//            }

        // TODO move to its own file this.

        // TODO rename tutorial to my name.


//            Collections.shuffle(cropNames, new Random(seed));
        List<String> cropNameCopy = new ArrayList<>(cropNames);  // Create a copy of the original list
        Collections.shuffle(cropNameCopy, new Random(seed));  // Shuffle the copy

        // Grab the first 15 cropNames and sort them.
        List<String> selectedCrops = cropNameCopy.subList(0, Math.min(cropNameCopy.size(), 15));
        Collections.sort(selectedCrops);

        // Print the selected crops
//            if (biomeName.equals("minecraft:plains") || biomeName.equals("minecraft:river")) {
//                LOGGER.info("DEBUG3: Selected Crops for " + biomeName + ": " + selectedCrops);
//            }

        return selectedCrops;
    }

    private static long getSeedFromBiomeName(String biomeName) {
        // You can implement a custom method to derive a seed from the biomeName
        // For simplicity, I'm using hashCode, but you might want a more robust approach
        // TODO add a global shift variable to change them all.
        return biomeName.hashCode();
    }


    public static ResourceLocation nameOfBiome(Level level, Biome biome) {
        return level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
//        return level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
    }

    public static String getBiomeName(String biomeKey) {
        // Replace "minecraft:plains" with the registry name of the target biome
        String targetBiomeRegistryName = "minecraft:plains";

        // Get the biome from the registry
        Biome targetBiome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(targetBiomeRegistryName));
        LOGGER.info(" targetBiome = " + targetBiome.toString());

        if (targetBiome != null) {
            // Get the biome category
//            BiomeCategory category = targetBiome.getBiomeCategory();

            // Check if I18n is available (might not be available in certain environments)
            if (I18n.exists("biome.minecraft.plains")) {
                // Get the localized name of the biome category
                String localizedName = I18n.get("biome.minecraft.plains");

                // Print the localized name
                System.out.println("Localized Name of " + targetBiomeRegistryName + ": " + localizedName);
                return localizedName;
            } else {
                System.out.println("I18n not available for biome : " + targetBiomeRegistryName);
                return targetBiomeRegistryName;
            }
        } else {
            System.out.println("Biome not found: " + targetBiomeRegistryName);
            return targetBiomeRegistryName;
        }
    }


    // TODO move to a player class.
    // Make a player use food faster always!
    // REF: https://minecraft.fandom.com/wiki/Hunger
    // TODO make configurable.
    // but then also how do we handle bonuses for player?  just check with that math?
    // NOTICE: Event methods cannot be static.
    @SubscribeEvent
    public static void hunger(TickEvent.PlayerTickEvent event) {
        // Leave if on client side.
        if (!event.side.isServer()) {
//             LOGGER.info("DEBUG: leaving if not on server side.");
            return;
        }

        Player player = event.player;
        Random random = new Random();

        // 500 will starve in about 40 minutes.
        // Testing at 250, twice as fast.
        if (random.nextInt(500) == 1) {
            Difficulty difficulty = player.getCommandSenderWorld().getDifficulty(); // todo test
            // TODO: skip if easy mode? less for normal more for hard?
//            LOGGER.info("DEBUG: difficulty = " + difficulty.toString());
            // "HARD"

            player.getFoodData().addExhaustion(0.5f);
            LOGGER.info("DEBUG: Adding exhaustion: foodlevel:" +
                    player.getFoodData().getFoodLevel()
                    + " saturation level: " + player.getFoodData().getSaturationLevel()
                    + " exhaustion level: " + player.getFoodData().getExhaustionLevel());
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
//                LOGGER.info("DEBUG: name = " + itemName);
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

        List<String> itemNamesCopy = new ArrayList<>(itemNames);  // Create a copy of the original list
        Collections.shuffle(itemNamesCopy, new Random(worldSeed));  // Shuffle the copy

        // Grab the first 15 items and sort them.
        List<String> selectedItems = itemNamesCopy.subList(0, Math.min(itemNamesCopy.size(), 20));
        Collections.sort(selectedItems);
        LOGGER.info("DEBUG: marketItems = " + selectedItems.size() + " " + selectedItems.toString());

        // TODO SAVE AND LOAD TO DB FILE!

        // TODO May need to grab 20 first, then filter out the tree fruits, up to 5.

        // Need to check actual recipe here now though, filter out some tree fruits.
        for (String itemName : selectedItems) {
            // TODO: Get the recipe for each item and check if it is a tree fruit or things we dont want.

            // Shift name format to resource location.
//            String itemResourceName = selectedItems.get(0).replace("item.", "").replace(".", ":");
            String itemResourceName = itemName.replace("item.", "").replace(".", ":");

            // TODO Get ingredients to double check some things.
            final List<ItemStack> inputs = getIngredients(serverLevel, itemResourceName);

            // TODO do things

            // TODO: oh, what about tags?  how to check for them?

            // Check for any tree fruit
            // test just remove it.
            for (ItemStack stack : inputs) {
//                LOGGER.info("DEBUG3: stack name== " + stack.getDescriptionId());
                if (stack.getDescriptionId().contains("pamhc2trees")) {
                    LOGGER.info("DEBUG3: tree fruit found, removing from list.");
                    selectedItems.remove(itemName);
                    break;
                }
            }

            // NOTICE: item.pamhc2foodextended.schnitzelitem
            // has tofacon as main ingredient, could also be bacon, need to get all recipes probably. or tags?

        }

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

        // todo test a not shapeless recipe?  same? just ordered?  no? hmmm

        // Now get recipe...
        Recipe recipe = serverLevel.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(recipe1 -> recipe1.getResultItem(serverLevel.registryAccess()).getItem() == item)
                .findFirst()
                .orElse(null);

        // And Loop over all ingredients now.
        if (recipe != null) {
//            LOGGER.info("DEBUG: recipe = " + recipe);  // shapeless or shaped recipe.

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


}


