package com.falazar.farmupcraft;

import com.mojang.datafixers.util.Pair;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.CraftingRecipeBuilder;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.replace;

public class CropsManager {
    private static final Logger LOGGER = LogManager.getLogger();

    // Class-level variable to store biome information
    private static Map<String, List<String>> biomeCrops = new HashMap<>();
    private static Map<String, List<String>> cropBiomes = new HashMap<>();

    @SubscribeEvent
    // Main Method here:
    // When trying to plant crops, check our biome rules to see what crops are allowed there.
    public void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player player = null;
        if (event.getEntity() instanceof Player) {
            player = (Player) event.getEntity();
            if (player.isCreative()) {
                LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
                return;
            }
        } else {
            LOGGER.info("DEBUG: Not a player, leaving now.");
            return;
        }

        // STEP 2: Test if target block is farmland, if not leave.
        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
//            LOGGER.info("DEBUG: 1 this target block is " + blockState.getBlock().getName().toString());  // not display name
        if (blockState.getBlock() != Blocks.FARMLAND) {
//                LOGGER.info("DEBUG: Not farmland leaving now. ");
            return;
        }

        // TODO sugarcane and sweetberries.

        // STEP 3: Test if holding a vanilla or harvestcraft item, if not leave.
        LOGGER.info("###################################### ");
        ItemStack stack = event.getItemStack();
//        LOGGER.info("DEBUG: item held getDescriptionId is " + stack.getDescriptionId());
        if (!stack.getDescriptionId().contains("pamhc2crops") && !isVanillaCrop(stack)) {
            // Is not a crop type... leave..
//                LOGGER.info("DEBUG: not a crop, leaving rules now.");
            return;
        }

        // DEBUG ZONE:
        if (stack.getDescriptionId().contains("wheat")) {
            LOGGER.info("DEBUG: is a wheat seeds crop, running DEBUG METHOD.");
            findNearestVillage(event);

            // Only run on server side.
            if (event.getSide().isServer()) {
                ServerLevel serverLevel = player.getServer().getLevel(player.getCommandSenderWorld().dimension());
                long worldSeed = player.getServer().getLevel(player.getCommandSenderWorld().dimension()).getSeed();
//                ServerLevel serverLevel = event.getLevel());
//                ServerLevel serverLevel = server.getLevel(level.dimension());

                getAllFoods(serverLevel, worldSeed);
            }
        }

        // Setup all of our biomes and crops rules allowed, saves to cache.
        // TODO move me, call one time only at start.
        setupBiomeCrops(event);  // TEMP TESTER AREA.

        // STEP 4: Get current biome the block is in.
        Biome biome = event.getLevel().getBiome(event.getPos()).value();
        String biomeName = nameOfBiome(event.getLevel(), biome).toString();
        LOGGER.info("DEBUG: this biome is " + nameOfBiome(event.getLevel(), biome));


        // STEP 5: Implement biome rules now, if fail, cancel plant and leave.
        // Is this an allowed plant for this biome?
        if (!isCropAllowed(stack, biomeName, event)) {
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
    public void slowCropsEvent(BlockEvent.CropGrowEvent.Pre event) {
        BlockPos blockPos = event.getPos();
        BlockState blockState = event.getLevel().getBlockState(blockPos);
        Block block = blockState.getBlock();

        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99

        // TODO testing with this. slow min.
        int baseSuccessRate = 10;

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
        if (!event.getSide().isServer()) {
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

        Level level = event.getEntity().getCommandSenderWorld();
        // Get the block position
        BlockPos blockPos = event.getPos();
        // Get serverLevel
        ServerLevel serverLevel = server.getLevel(level.dimension());
        LOGGER.info("DEBUG: serverLevel value gotten fine. ");

//            TagKey<Structure> tagKey = StructureTags.EYE_OF_ENDER_LOCATED;
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
        // Main call here to find the nearest structure.
        Pair<BlockPos, Holder<Structure>> pair = serverLevel.getChunkSource()
                .getGenerator()
                .findNearestMapStructure(serverLevel, (HolderSet<Structure>) optional.get(), blockPos, i, bl);
        LOGGER.info("DEBUG: pair = " + pair);
        Structure structure = pair.getSecond().get();
        BlockPos structurePos = pair.getFirst();
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
            String cropItemShow = getCropShowName(seedDescription);
            List<String> cropsAllowedShow = getCropShowNames(cropsAllowed);
//                LOGGER.info("DEBUG: For this biome: " + biomeNameShow + " you cannot plant " + cropItemShow);
//                LOGGER.info("DEBUG: Crops you can plant in " + biomeNameShow + ": " + cropsAllowedShow.toString());

            // Send a message to the player.
            if (event.getLevel().isClientSide()) {
                Player player = event.getEntity();
                // TODO change to reddish for warnings.
                PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§eYou cannot plant " + cropItemShow + " in " + biomeNameShow + " biome.");
                player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                // Send full crop list for this biome.
                chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§aCrops you can plant in " + biomeNameShow + ": §2" + cropsAllowedShow.toString());
                player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                LOGGER.info("DEBUG: seedItem = " + seedItem);
                List<String> biomesAllowed = cropBiomes.get(seedItem);
                if (biomesAllowed != null) {
                    // Send full list of biomes you can plant it in.
                    chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "§bBiomes you can plant " + cropItemShow + ": §3" + cropBiomes.get(seedItem).toString());
                    player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));
                }
            }
            return false;
        }

        // Passed all, allow planting.
        return true;
    }

    public static String getCropShowName(String itemName) {
        // Remove pam stuff and minecraft stuffs...
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

    public static boolean isVanillaCrop(ItemStack stack) {
        // Hardcoded for now.
        Item item = stack.getItem();
        if (item == Items.POTATO || item == Items.CARROT || item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS) {
            LOGGER.info("DEBUG: is a vanilla crop.");
            return true;
        }

        return false;
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
    public void hunger(TickEvent.PlayerTickEvent event) {
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
//            LOGGER.info("DEBUG: Adding exhaustion: foodlevel:" +
//                    player.getFoodData().getFoodLevel()
//                    + " saturation level: " + player.getFoodData().getSaturationLevel()
//                    + " exhaustion level: " + player.getFoodData().getExhaustionLevel());
        }

    }


    // TODO cooking stuffs testing next.
    // Get a list of all cooked items for market!
    // Generic method to get a list of items...
    // notice was static
    public List<String> getAllFoods(ServerLevel serverLevel, long worldSeed) {
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
        // has tofacon as main ingredient, could also be bacon, need to get all recipes probably. or tags?
        // egg tart also fails on avocadoa substitute, we need to look at tags.
        // .pamhc2foodextended.eggtartitem

        // Trim it down to 15 now.
        selectedItems = selectedItems.subList(0, Math.min(selectedItems.size(), 15));
        LOGGER.info("DEBUG4: final marketItems = " + selectedItems.size() + " " + selectedItems.toString());

        return selectedItems;
    }


    public List<ItemStack> getIngredients(ServerLevel serverLevel, String itemResourceName) {
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

    // TODO: move to mining manager class.
    // On breaking stone, sometimes it will fail and you will not get back any items.
    // You can increase the rate with skills and special items...
    // cobblestone and deepslate drop rate here.
    // NOTICE: Event methods cannot be static.
    @SubscribeEvent
    public void onBreakStone(BlockEvent.BreakEvent event) {
//        Player player = event.getPlayer();
//        Player player = Player.getByName(player.getScoreboardName());
//        if (player == null) {
//            return;
//        }


        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
        Item item = blockState.getBlock().asItem();
        ItemStack itemStack = new ItemStack(item);

        // TODO TEST LINE
        LOGGER.info("DEBUG1: all tags = " + itemStack.getTags().map(itemTagKey -> itemTagKey.toString()).collect(Collectors.toList()));
        // Now instead lets have it trigger a destroy block and maybe not give rewards.
//        if (itemStack.getTags().contains(new ResourceLocation("minecraft", "base_stone_overworld"))
//                || blockState.getBlock().getTags().contains(new ResourceLocation("forge", "dirt"))) {
        // https://mcreator.net/wiki/minecraft-block-tags-list
        // base_stone_overworld = stone, granite, diorite, andesite, tuff, deepslate
        // dirt = dirt, grass_block, podzol, coarse_dirt, mycelium, rooted_dirt, moss_block, mud, muddy_mangrove_roots
        // TODO whats forge:dirt then???
        // todo make mini method. do all for above and here. multiple tags passed in.
        if (!(itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.toString().contains("base_stone_overworld")
                || itemTagKey.toString().contains("dirt")
                || itemTagKey.toString().contains("base_stone_nether")))) {
            // Not a block we care about.
            return;
        }


//                || itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.toString().contains("dirt"))) ||
//        itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.toString().contains("base_stone_overworld")) {
        LOGGER.info("DEBUG: Testing2 here we found base stone/dirt");

        // STEP 1: Get SuccessRate
        int baseSuccessRate = 30;  // 50% chance to fail loot at start.
        // change to 30% start?

        // Make generics.
//            // CHECK 1: Add skill percent now.
//            if (player.hasSkill("moreStoneDrops")) {
//                baseSuccessRate += player.getRoleLevel("miner") * 4;
//            }
//            // CHECK 2: Add basic smaller skill percent now for non miners.
//            else {
//                baseSuccessRate += player.getLevel() * 2;
//            }
        int successRate = baseSuccessRate;

        // STEP 2: Roll and check for success.
        // TODO make roll a mini method.
        Random rand = new Random();
        int randomNum = rand.nextInt(100); // 100% 0-99
        LOGGER.info("DEBUG: Random Num = " + randomNum);
        if (randomNum >= successRate) {
            LOGGER.info("DEBUG: DESTROYING stone block, no drops...");
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);  // this works fine, prevents drops, must have or it replaces it!
            return;
        }
        LOGGER.info("DEBUG: ALLOWING stone block drops...");

        // SECOND ABILITY (only if above worked)
        // trencher
//            if (player.hasSkill("trencher")) {
//                attemptTrenchBreak(player, event.getPos());
//            }


    }

}


