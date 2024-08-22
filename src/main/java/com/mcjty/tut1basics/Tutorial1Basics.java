package com.mcjty.tut1basics;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

//import net.minecraftforge.event.world.BlockEvent;

import org.slf4j.Logger;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.replace;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Tutorial1Basics.MODID)
public class Tutorial1Basics {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "tut1basics";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Create a Deferred Register to hold Blocks which will all be registered under the "tut1basics" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "tut1basics" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Creates a new Block with the id "tut1basics:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "tut1basics:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    public Tutorial1Basics() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Registers to the mod event bus so blocks and items get registered
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our new methods.
        MinecraftForge.EVENT_BUS.register(new BiomeCropsEventsHandler());


    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

    }


    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class BiomeCropsEventsHandler {

        // Class-level variable to store biome information
        private static Map<String, List<String>> biomeCrops = new HashMap<>();
        private static Map<String, List<String>> cropBiomes = new HashMap<>();

        @SubscribeEvent
        // When trying to plant crops, check our biome rules to allow.
        public void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

            // TODO FIX ME!
            // rules ignored if this one is on.
//            if (!event.getLevel().isClientSide()) {
//                // Do nothing on server side.
////                LOGGER.info("DEBUG: Right click on server side. ");
//                return;
//            }
////             Flicker if this one is on only.
//            if (event.getLevel().isClientSide()) {
////                LOGGER.info("DEBUG: Right click on client side. ");
//                return;
//            }

            // TODO test if in creative mode, skip all rules!

            LOGGER.info("###################################### ");
//            LOGGER.info("DEBUG: Right click on client side. ");

            ItemStack stack = event.getItemStack();
//            BlockPos pos = event.getPos();
//            LOGGER.info("DEBUG: Right click event happened. ");
//            LOGGER.info("DEBUG: pos happened is " + pos.toShortString());
            LOGGER.info("DEBUG: this item getDescriptionId is " + stack.getDescriptionId());



            // Test Other things now, like, get current biome.
            Biome biome = event.getLevel().getBiome(event.getPos()).value();  // requires Holder odd
            String biomeName = nameOfBiome(event.getLevel(), biome).toString();
            LOGGER.info("DEBUG: this biome is " + nameOfBiome(event.getLevel(), biome));



            // STEP 1: Test if target block is farmland, if not leave.
            final BlockState blockState = event.getLevel().getBlockState(event.getPos());
//            LOGGER.info("DEBUG: 1 this target block is " + blockState.getBlock().getName().toString());  // not display name
            if (blockState.getBlock() != Blocks.FARMLAND) {
//                LOGGER.info("DEBUG: Not farmland leaving now. ");
                return;
            }


            // STEP 2: Test if holding a vanilla or harvestcraft item, if not leave.
            if (!stack.getDescriptionId().contains("pamhc2crops") && !isVanillaCrop(stack)) {
                // Is not a crop type... leave..
//                LOGGER.info("DEBUG: not a crop, leaving rules now.");

                return;
            }

            // Setup all of our biomes and crops rules allowed, saves to cache.
            setupBiomeCrops(event);  // TEMP TESTER AREA. TODO move me.


            // STEP 3: Implement biome rules now, if fail, cancel plant and leave.
            // Is this an allowed plant for this biome?  if not cancel event.
            List<String> cropsAllowed = getCropsForBiome(biomeName);
//            LOGGER.info("DEBUG: biomeName = " + biomeName);
//            LOGGER.info("DEBUG: cropsAllowed = " + cropsAllowed);

            String seedDescription = stack.getDescriptionId();
            String seedDescriptionTwo = seedDescription;  // Add second variation seeditem vs regular veggie, both can plant.
            String seedItem = seedDescription;
            if (seedDescription.contains("seeditem")) {
                seedDescriptionTwo = replace(seedDescriptionTwo, "seeditem", "item");
            } else if (seedDescription.contains("pamhc2crops")) {
                // If not a seeditem one, add seeditem onto it, and test again.
                seedDescriptionTwo = seedDescriptionTwo.substring(0, seedDescriptionTwo.length() - 4) + "seeditem";
                seedItem = seedDescriptionTwo;
            }

//            LOGGER.info("DEBUG: seedDescriptionTwo = " + seedDescriptionTwo);

            // Check for seeditem, and item plain, as both can be planted.
            if (!cropsAllowed.contains(seedDescription) && !cropsAllowed.contains(seedDescriptionTwo)) {
                String biomeNameShow = biomeName.split(":")[1];
                String cropItemShow = getCropShowName(seedDescription);
                List<String> cropsAllowedShow = getCropShowNames(cropsAllowed);
                LOGGER.info("DEBUG: For this biome: " + biomeNameShow + " you cannot plant " + cropItemShow);
                LOGGER.info("DEBUG: Crops you can plant: " + cropsAllowedShow.toString());

                // TODO Send a message to the player - todo sending AS player I think, fix me later.
                if (event.getLevel().isClientSide()) {
                    Player player = event.getEntity();
                    PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "You cannot plant " + cropItemShow + " in " + biomeNameShow + " biome.");
                    player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                    chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "Crops you can plant: " + cropsAllowedShow.toString());
                    player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));

                    LOGGER.info("DEBUG seedItem = "+seedItem);
                    List<String> biomesAllowed = cropBiomes.get(seedItem);
                    if (biomesAllowed != null) {
                        chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "Biomes you can plant " + cropItemShow + ": " + cropBiomes.get(seedItem).toString());
                        player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));
                    }
                }

                // Cancel event and return now.
                event.setCanceled(true);  // Must be done CLIENT side, so it doesnt eat it.  true?
                return;
            }


            // TODO figure out how to use.
//            BlockEvent.BreakEvent
//            BreakEvent(World world, BlockPos pos, BlockState state, PlayerEntity player)
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
        // TODO remove ocean biomes...!!!
        // TODO make desert have only 10 items.
        public static void setupBiomeCrops(PlayerInteractEvent event) {
            // TODO: Cache all this one time or load from memory.
            // Check cache now so isnt done twice.
            if (!biomeCrops.isEmpty()) {
//                LOGGER.info("Already have biomeCrops cache, not redoing it now. ");
                return;
            }


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


            // STEP 6: Save to local data storage. maybe no?
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
            if (biomeName.contains("ocean")) {
                return Collections.emptyList();
            }

            // Randomly shuffle cropNames based on the seed derived from biomeName
            long seed = getSeedFromBiomeName(biomeName);
            if (biomeName.equals("minecraft:plains") || biomeName.equals("minecraft:river")) {
                LOGGER.info("DEBUG3: biomeName is " + biomeName);
                LOGGER.info("DEBUG3: Seed value is " + seed);
            }
            
            // TODO move to its own file this.

            // TODO rename tutorial to mine.



//            Collections.shuffle(cropNames, new Random(seed));
            List<String> cropNameCopy = new ArrayList<>(cropNames);  // Create a copy of the original list
            Collections.shuffle(cropNameCopy, new Random(seed));  // Shuffle the copy


            // Grab the first 15 cropNames and sort them.
            List<String> selectedCrops = cropNameCopy.subList(0, Math.min(cropNameCopy.size(), 15));
            Collections.sort(selectedCrops);

            // Print the selected crops
            if (biomeName.equals("minecraft:plains") || biomeName.equals("minecraft:river")) {
                LOGGER.info("DEBUG3: Selected Crops for " + biomeName + ": " + selectedCrops);
            }

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
            if (item == Items.POTATO ||
                    item == Items.CARROT ||
                    item == Items.WHEAT_SEEDS ||
                    item == Items.BEETROOT_SEEDS) {
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

    }


//    @SubscribeEvent
//    public void onBlockDropItems(BlockEvent.HarvestDropsEvent event) {
//        if (event.block == Blocks.mob_spawner) {
//            event.drops.add(new ItemStack(Items.experience_bottle));
//        }
//    }


    // TODO figure out how to use.
//            BlockEvent.BreakEvent
//            BreakEvent(World world, BlockPos pos, BlockState state, PlayerEntity player)


}

/*
NOTES AREA:


12:34:53.362
game
[12:34:53] [Server thread/WARN] [citadel/]: biome config: BIOME_DICT and BIOME_CATEGORY are no longer valid in 1.19+. Please use BIOME_TAG instead.
12:34:53.362
game
[12:34:53] [Server thread/WARN] [citadel/]: biome config: BIOME_DICT and BIOME_CATEGORY are no longer valid in 1.19+. Please use BIOME_TAG instead.
Ton of errors for this one, is there an update?
Who uses it?


Lots for this one:
[12:35:09] [Server thread/WARN] [un.UntamedWilds/]: There's no species provided for the EntityType

Remove both those and retest for lag?

Close logs and test for lag.


DEBUG seedItem = item.pamhc2crops.elderberryseeditem

TEST DEBUG: all cropBiomes = {item.
pamhc2crops.scallionseeditem=[clover_patch, crag, field, jagged_peaks, old_growth_spruce_taiga, prairie, rainforest, rocky_shrubland, scrubland, seasonal_forest, small_end_islands, soul_sand_valley, stony_shore, wasteland, wetland, woodland], item.
pamhc2crops.cucumberseeditem=[bamboo_jungle, dark_forest, end_barrens, end_highlands, grove, ice_spikes, lavender_field, lush_caves, river, seasonal_forest, snowy_coniferous_forest, taiga, the_void, tropics, visceral_heap, wooded_badlands], item.
pamhc2crops.tomatoseeditem=[auroral_garden, basalt_deltas, birch_forest, cold_desert, deep_dark, glowing_grotto, grassland, mangrove_swamp, moor, old_growth_birch_forest, orchard, river, rocky_rainforest, scrubland, snowy_coniferous_forest, snowy_maple_woods, snowy_slopes, sparse_jungle, spider_nest, stony_shore, taiga, the_end, tropics, volcanic_plains, volcano, wasteland, withered_abyss], item.
pamhc2crops.beanseeditem=[clover_patch, coniferous_forest, eroded_badlands, field, frozen_peaks, frozen_river, highland, jagged_peaks, old_growth_pine_taiga, old_growth_woodland, rocky_shrubland, savanna_plateau, seasonal_forest, seasonal_orchard, small_end_islands, snowblossom_grove, snowy_beach, snowy_plains, snowy_taiga, the_end, warped_forest, wetland, windswept_forest, windswept_savanna, wooded_badlands], item.
pamhc2crops.calabashseeditem=[bayou, beach, birch_forest, dune_beach, erupting_inferno, floodplain, flower_forest, ice_spikes, lavender_field, meadow, moor, old_growth_pine_taiga, orchard, pumpkin_patch, seasonal_forest, snowy_fir_clearing, stony_peaks, stony_shore, visceral_heap, withered_abyss], item.
pamhc2crops.cattailseeditem=[badlands, cherry_grove, dark_forest, dune_beach, ice_spikes, jade_cliffs, jagged_peaks, lavender_field, mushroom_fields, mystic_grove, origin_valley, rainforest, river, savanna, seasonal_forest, undergrowth, volcanic_plains], item.
pamhc2crops.canolaseeditem=[birch_forest, cherry_grove, dryland, floodplain, frozen_peaks, lush_caves, lush_savanna, nether_wastes, rocky_rainforest, rocky_shrubland, savanna_plateau, stony_shore, taiga, visceral_heap, wooded_badlands], item.
pamhc2crops.coffeebeanseeditem=[badlands, cherry_grove, coniferous_forest, end_highlands, field, floodplain, grassland, ice_spikes, nether_wastes, old_growth_dead_forest, ominous_woods, rocky_rainforest, seasonal_forest, snowy_beach, snowy_coniferous_forest, snowy_taiga, spider_nest, windswept_gravelly_hills, woodland], item.
pamhc2crops.leekseeditem=[crimson_forest, field, flower_forest, frozen_peaks, lush_caves, maple_woods, mediterranean_forest, mushroom_fields, muskeg, old_growth_woodland, plains, redwood_forest, rocky_rainforest, savanna, scrubland, sunflower_plains, warped_forest, windswept_hills], item.
pamhc2crops.flaxseeditem=[coniferous_forest, crystalline_chasm, dead_forest, dripstone_caves, forested_field, fungal_jungle, lush_savanna, meadow, mediterranean_forest, old_growth_dead_forest, spider_nest, tundra, volcanic_plains, windswept_forest, windswept_savanna, woodland], item.
pamhc2crops.cabbageseeditem=[auroral_garden, clover_patch, forested_field, grassland, highland, jade_cliffs, maple_woods, prairie, savanna_plateau, scrubland, snowy_fir_clearing, stony_shore, sunflower_plains, visceral_heap, wasteland], item.
pamhc2crops.sweetpotatoseeditem=[birch_forest, cold_desert, crag, desert, end_midlands, ominous_woods, pasture, savanna_plateau, the_void, wasteland_steppe, windswept_forest], item.
pamhc2crops.amaranthseeditem=[bamboo_jungle, bog, crimson_forest, dune_beach, fungal_jungle, highland, lush_desert, mushroom_fields, muskeg, orchard, plains, prairie, snowy_coniferous_forest, snowy_maple_woods, snowy_slopes, soul_sand_valley, spider_nest, stony_shore, sunflower_plains], item.
pamhc2crops.rhubarbseeditem=[auroral_garden, bamboo_jungle, cherry_grove, cold_desert, coniferous_forest, dark_forest, eroded_badlands, field, floodplain, jagged_peaks, mangrove_swamp, meadow, mystic_grove, old_growth_dead_forest, origin_valley, shrubland, snowy_beach, sparse_jungle, spider_nest, taiga, terrablender:deferred_placeholder, visceral_heap, windswept_forest, windswept_hills, withered_abyss], item.
pamhc2crops.soybeanseeditem=[bamboo_jungle, crystalline_chasm, grove, ice_spikes, lavender_forest, marsh, meadow, ominous_woods, snowy_beach, snowy_plains, soul_sand_valley, sunflower_plains, terrablender:deferred_placeholder, warped_forest, wetland], item.
pamhc2crops.brusselsproutseeditem=[bamboo_jungle, bayou, bog, coniferous_forest, crag, crystalline_chasm, dead_forest, forested_field, marsh, moor, mushroom_fields, plains, snowy_slopes, swamp, terrablender:deferred_placeholder, woodland], item.
pamhc2crops.tomatilloseeditem=[crystalline_chasm, desert, dripstone_caves, erupting_inferno, highland, lavender_field, lavender_forest, muskeg, rainforest, tundra], item.
pamhc2crops.sorghumseeditem=[bog, clover_patch, crag, deep_dark, fir_clearing, forested_field, moor, nether_wastes, redwood_forest, seasonal_orchard, small_end_islands, the_end, tundra, undergrowth, windswept_forest, windswept_gravelly_hills, wooded_badlands, woodland], item.
pamhc2crops.blueberryseeditem=[crimson_forest, deep_dark, desert, dripstone_caves, dryland, end_barrens, fir_clearing, floodplain, jungle, lush_savanna, mangrove_swamp, muskeg, nether_wastes, seasonal_orchard, spider_nest, stony_shore, taiga, the_void, volcano, warped_forest], item.
pamhc2crops.cranberryseeditem=[dead_forest, frozen_peaks, redwood_forest, snowy_fir_clearing, terrablender:deferred_placeholder, wasteland, wasteland_steppe, windswept_forest], item.
pamhc2crops.strawberryseeditem=[bamboo_jungle, clover_patch, crimson_forest, desert, eroded_badlands, erupting_inferno, fir_clearing, mushroom_fields, origin_valley, rocky_shrubland, stony_peaks, sunflower_plains, tropics, tundra, wasteland_steppe, windswept_savanna], item.
pamhc2crops.raspberryseeditem=[birch_forest, coniferous_forest, desert, end_barrens, flower_forest, lavender_field, lush_savanna, mangrove_swamp, old_growth_spruce_taiga, plains, rainforest, swamp, terrablender:deferred_placeholder, tundra, volcano, wooded_badlands], item.
pamhc2crops.spinachseeditem=[auroral_garden, bayou, dark_forest, dryland, glowing_grotto, grassland, lavender_field, lavender_forest, lush_caves, marsh, muskeg, old_growth_spruce_taiga, origin_valley, redwood_forest, small_end_islands, snowblossom_grove, snowy_beach, visceral_heap, windswept_gravelly_hills, withered_abyss], item.
pamhc2crops.barleyseeditem=[bamboo_jungle, basalt_deltas, dead_forest, dune_beach, flower_forest, forest, grove, ice_spikes, lush_caves, old_growth_spruce_taiga, river, rocky_rainforest, seasonal_orchard, snowy_slopes, soul_sand_valley, undergrowth, volcanic_plains], item.
pamhc2crops.asparagusseeditem=[coniferous_forest, dark_forest, dripstone_caves, erupting_inferno, floodplain, grassland, lavender_forest, old_growth_birch_forest, pasture, savanna_plateau, seasonal_forest, seasonal_orchard, snowblossom_grove, snowy_maple_woods, snowy_plains, snowy_slopes, snowy_taiga, the_void, volcanic_plains], item.
pamhc2crops.quinoaseeditem=[basalt_deltas, dark_forest, end_highlands, end_midlands, fir_clearing, floodplain, frozen_river, grove, jade_cliffs, lush_desert, mediterranean_forest, nether_wastes, old_growth_birch_forest, old_growth_pine_taiga, rocky_rainforest, rocky_shrubland, savanna, shrubland, snowy_plains, volcanic_plains, volcano, warped_forest], item.
pamhc2crops.celeryseeditem=[coniferous_forest, deep_dark, dripstone_caves, fungal_jungle, grove, mangrove_swamp, marsh, meadow, old_growth_spruce_taiga, rocky_shrubland, sunflower_plains, windswept_hills], item.
pamhc2crops.gingerseeditem=[bog, crimson_forest, dryland, end_highlands, lavender_forest, mushroom_fields, muskeg, old_growth_birch_forest, pumpkin_patch, sparse_jungle, the_end, tropics, volcanic_plains, wetland, windswept_gravelly_hills, windswept_hills], item.
pamhc2crops.wintersquashseeditem=[bog, dripstone_caves, grassland, marsh, meadow, muskeg, old_growth_birch_forest, old_growth_dead_forest, old_growth_pine_taiga, old_growth_spruce_taiga, pasture, savanna, snowy_slopes, sunflower_plains, the_end, visceral_heap, wasteland], item.
pamhc2crops.grapeseeditem=[beach, field, forested_field, glowing_grotto, grassland, jagged_peaks, marsh, moor, muskeg, old_growth_dead_forest, rainforest, scrubland, seasonal_orchard, shrubland, small_end_islands, stony_peaks, visceral_heap, warped_forest, withered_abyss], item.
pamhc2crops.peasseeditem=[basalt_deltas, bayou, cold_desert, dead_forest, frozen_river, highland, pasture, plains, rocky_rainforest, rocky_shrubland, shrubland, snowy_maple_woods, snowy_taiga, stony_shore, taiga, undergrowth, visceral_heap, wetland, windswept_hills], item.
pamhc2crops.lentilseeditem=[basalt_deltas, beach, forest, fungal_jungle, highland, lavender_field, orchard, prairie, seasonal_orchard, snowy_plains, terrablender:deferred_placeholder, the_end, visceral_heap], item.
pamhc2crops.bellpepperseeditem=[cherry_grove, coniferous_forest, end_midlands, forest, jagged_peaks, jungle, lush_desert, origin_valley, rocky_rainforest, savanna_plateau, scrubland, seasonal_orchard, soul_sand_valley, spider_nest, sunflower_plains, wasteland, wasteland_steppe], item.
pamhc2crops.peanutseeditem=[basalt_deltas, clover_patch, crimson_forest, crystalline_chasm, dead_forest, erupting_inferno, mediterranean_forest, old_growth_dead_forest, old_growth_spruce_taiga, origin_valley, scrubland, snowy_taiga, sparse_jungle, swamp, the_void, tropics], item.
pamhc2crops.ryeseeditem=[auroral_garden, beach, dryland, end_barrens, end_midlands, frozen_river, fungal_jungle, moor, ominous_woods, plains, rocky_rainforest, savanna, snowy_beach, snowy_fir_clearing, soul_sand_valley, sparse_jungle, spider_nest, stony_shore, swamp, the_end, tropics, wooded_badlands], item.
pamhc2crops.cauliflowerseeditem=[auroral_garden, badlands, bamboo_jungle, birch_forest, bog, dune_beach, erupting_inferno, forest, grove, lush_savanna, nether_wastes, pasture, pumpkin_patch, shrubland, snowblossom_grove, snowy_maple_woods, wasteland], item.
pamhc2crops.mustardseedsseeditem=[basalt_deltas, birch_forest, dripstone_caves, lavender_forest, mystic_grove, old_growth_pine_taiga, old_growth_woodland, savanna_plateau, snowblossom_grove], item.
pamhc2crops.sisalseeditem=[deep_dark, end_barrens, erupting_inferno, grove, mushroom_fields, muskeg, mystic_grove, nether_wastes, pumpkin_patch, river, snowy_taiga, spider_nest, volcanic_plains, windswept_hills], item.
pamhc2crops.nopalesseeditem=[auroral_garden, beach, birch_forest, crimson_forest, crystalline_chasm, forest, forested_field, jungle, mystic_grove, old_growth_spruce_taiga, savanna_plateau, shrubland, small_end_islands, sparse_jungle, spider_nest, sunflower_plains, undergrowth, windswept_savanna, wooded_badlands], item.
pamhc2crops.broccoliseeditem=[cherry_grove, dryland, end_barrens, forest, frozen_peaks, highland, lavender_field, lush_caves, pumpkin_patch, rocky_rainforest, savanna_plateau, snowblossom_grove, snowy_fir_clearing, volcanic_plains, wasteland], item.
pamhc2crops.waterchestnutseeditem=[auroral_garden, crag, fir_clearing, grove, mediterranean_forest, mushroom_fields, old_growth_woodland, ominous_woods, orchard, pasture, pumpkin_patch, stony_peaks, the_void, undergrowth, windswept_hills], item.
pamhc2crops.arrowrootseeditem=[auroral_garden, badlands, beach, dryland, eroded_badlands, erupting_inferno, frozen_river, glowing_grotto, lavender_field, mediterranean_forest, mystic_grove, pasture, prairie, small_end_islands, taiga, tundra, wasteland_steppe, woodland], item.
pamhc2crops.elderberryseeditem=[birch_forest, crystalline_chasm, dead_forest, end_barrens, floodplain, nether_wastes, pumpkin_patch, redwood_forest, rocky_shrubland, scrubland, small_end_islands, snowy_beach, tundra, undergrowth, visceral_heap, windswept_gravelly_hills, windswept_savanna, woodland], item.
pamhc2crops.cassavaseeditem=[badlands, bayou, crag, erupting_inferno, fir_clearing, floodplain, flower_forest, forested_field, fungal_jungle, jade_cliffs, jungle, maple_woods, marsh, muskeg, ominous_woods, sunflower_plains, the_end, tropics, tundra, warped_forest, wasteland_steppe, wetland, wooded_badlands], item.
pamhc2crops.candleberryseeditem=[auroral_garden, desert, field, glowing_grotto, jagged_peaks, maple_woods, marsh, meadow, muskeg, mystic_grove, snowy_maple_woods, snowy_taiga, spider_nest, terrablender:deferred_placeholder, visceral_heap, wasteland], item.
pamhc2crops.radishseeditem=[badlands, bayou, cherry_grove, crystalline_chasm, dark_forest, desert, field, flower_forest, forest, frozen_river, jagged_peaks, jungle, muskeg, prairie, rainforest, snowblossom_grove, snowy_fir_clearing, warped_forest, wetland, withered_abyss, wooded_badlands], item.
pamhc2crops.sesameseedsseeditem=[dead_forest, desert, flower_forest, frozen_river, grassland, jungle, moor, orchard, origin_valley, pumpkin_patch, snowy_maple_woods, soul_sand_valley, stony_peaks, taiga, terrablender:deferred_placeholder, the_void, windswept_hills], item.
pamhc2crops.lotusseeditem=[beach, cold_desert, crag, dead_forest, fir_clearing, frozen_river, glowing_grotto, jagged_peaks, lush_caves, old_growth_birch_forest, old_growth_pine_taiga, ominous_woods, seasonal_forest, snowy_beach, snowy_coniferous_forest, volcano], item.
pamhc2crops.blackberryseeditem=[clover_patch, crimson_forest, deep_dark, dryland, frozen_river, jagged_peaks, lush_caves, maple_woods, pumpkin_patch, savanna, seasonal_orchard, snowy_beach, snowy_coniferous_forest, snowy_slopes, spider_nest, terrablender:deferred_placeholder, tropics, warped_forest, windswept_hills], item.
pamhc2crops.onionseeditem=[basalt_deltas, cherry_grove, dune_beach, erupting_inferno, forest, grassland, highland, jagged_peaks, plains, volcano], item.
pamhc2crops.oatsseeditem=[auroral_garden, basalt_deltas, bayou, dead_forest, dryland, dune_beach, fir_clearing, frozen_peaks, ice_spikes, jade_cliffs, meadow, nether_wastes, orchard, redwood_forest, seasonal_orchard, snowblossom_grove, snowy_coniferous_forest, snowy_taiga, terrablender:deferred_placeholder, volcano, wetland, windswept_savanna], item.
pamhc2crops.garlicseeditem=[bamboo_jungle, beach, bog, end_midlands, frozen_peaks, frozen_river, highland, lavender_forest, moor, old_growth_birch_forest, old_growth_dead_forest, old_growth_spruce_taiga, old_growth_woodland, ominous_woods, savanna, snowy_maple_woods, snowy_taiga, wasteland, windswept_hills], item.
pamhc2crops.cottonseeditem=[bamboo_jungle, basalt_deltas, beach, bog, end_highlands, flower_forest, forested_field, ice_spikes, orchard, redwood_forest, rocky_shrubland, savanna, snowy_fir_clearing, snowy_slopes, sparse_jungle, stony_peaks, volcano, windswept_forest, windswept_gravelly_hills, wooded_badlands, woodland], item.
pamhc2crops.sunchokeseeditem=[cherry_grove, clover_patch, coniferous_forest, dripstone_caves, dune_beach, eroded_badlands, frozen_river, lush_savanna, maple_woods, old_growth_birch_forest, river, snowblossom_grove, stony_peaks], item.
minecraft.potato=[beach, cold_desert, deep_dark, forested_field, grove, lush_savanna, old_growth_birch_forest, old_growth_pine_taiga, savanna, snowy_fir_clearing, stony_shore, tundra, warped_forest, wetland, windswept_gravelly_hills, windswept_hills], item.
minecraft.wheat_seeds=[cherry_grove, crimson_forest, deep_dark, dripstone_caves, fir_clearing, forested_field, lush_desert, lush_savanna, meadow, origin_valley, river, seasonal_forest, shrubland, small_end_islands, snowy_beach, snowy_maple_woods, snowy_slopes, windswept_forest, windswept_gravelly_hills, woodland], item.
pamhc2crops.juniperberryseeditem=[badlands, basalt_deltas, birch_forest, dark_forest, desert, end_highlands, ice_spikes, maple_woods, mediterranean_forest, muskeg, pasture, river, savanna_plateau, snowblossom_grove, snowy_coniferous_forest, taiga, the_end, wasteland_steppe], item.
pamhc2crops.yuccaseeditem=[crystalline_chasm, dryland, flower_forest, grove, mushroom_fields, ominous_woods, prairie, savanna_plateau, scrubland, snowy_coniferous_forest, snowy_plains, snowy_slopes, stony_peaks, swamp, taiga, undergrowth, wasteland_steppe, windswept_savanna], item.
pamhc2crops.guaranaseeditem=[badlands, bayou, beach, coniferous_forest, crystalline_chasm, dark_forest, dryland, fungal_jungle, mangrove_swamp, maple_woods, mushroom_fields, nether_wastes, old_growth_dead_forest, seasonal_forest, seasonal_orchard, snowy_beach, snowy_slopes, spider_nest, terrablender:deferred_placeholder, the_void, tropics, tundra, volcanic_plains, wetland, windswept_forest], item.
pamhc2crops.wolfberryseeditem=[bamboo_jungle, birch_forest, deep_dark, end_highlands, flower_forest, frozen_peaks, ice_spikes, jade_cliffs, lush_caves, old_growth_pine_taiga, orchard, snowy_slopes, stony_peaks, taiga], item.
pamhc2crops.juteseeditem=[cherry_grove, clover_patch, crag, dark_forest, dead_forest, dune_beach, eroded_badlands, erupting_inferno, grove, lush_caves, old_growth_dead_forest, river, wetland, windswept_forest, windswept_gravelly_hills], item.
pamhc2crops.aloeseeditem=[deep_dark, end_barrens, jade_cliffs, lush_desert, mangrove_swamp, marsh, meadow, moor, mystic_grove, rocky_shrubland, sparse_jungle, swamp, tundra], item.
pamhc2crops.greengrapeseeditem=[badlands, coniferous_forest, dark_forest, dune_beach, floodplain, glowing_grotto, jade_cliffs, jungle, lavender_field, mangrove_swamp, old_growth_pine_taiga, origin_valley, pasture, rocky_rainforest, savanna, small_end_islands, terrablender:deferred_placeholder, volcanic_plains, woodland], item.
pamhc2crops.cantaloupeseeditem=[bamboo_jungle, clover_patch, cold_desert, dark_forest, dead_forest, end_barrens, flower_forest, lush_caves, lush_desert, moor, old_growth_dead_forest, orchard, origin_valley, rocky_rainforest, savanna, seasonal_orchard, snowy_coniferous_forest, snowy_maple_woods, snowy_slopes, soul_sand_valley, stony_shore, volcano], item.
pamhc2crops.cornseeditem=[bayou, cold_desert, dryland, dune_beach, end_highlands, end_midlands, field, floodplain, lush_savanna, mediterranean_forest, old_growth_woodland, rainforest, rocky_rainforest, snowy_coniferous_forest, wooded_badlands], item.
pamhc2crops.zucchiniseeditem=[badlands, bamboo_jungle, crag, crimson_forest, dead_forest, erupting_inferno, field, floodplain, fungal_jungle, grassland, jagged_peaks, lavender_forest, old_growth_woodland, pasture, prairie, scrubland, snowy_fir_clearing, sunflower_plains, wasteland_steppe, windswept_gravelly_hills], item.
pamhc2crops.papyrusseeditem=[bayou, cherry_grove, cold_desert, dripstone_caves, dune_beach, end_midlands, fir_clearing, frozen_peaks, lush_desert, moor, old_growth_pine_taiga, old_growth_woodland, snowblossom_grove, sparse_jungle, stony_peaks, terrablender:deferred_placeholder, visceral_heap, wasteland_steppe, windswept_gravelly_hills, windswept_savanna, wooded_badlands], item.
pamhc2crops.parsnipseeditem=[dark_forest, fir_clearing, forested_field, moor, nether_wastes, pasture, savanna, savanna_plateau, seasonal_orchard, undergrowth, wasteland_steppe, windswept_gravelly_hills], item.
minecraft.beetroot_seeds=[bog, clover_patch, end_barrens, flower_forest, forested_field, fungal_jungle, grove, jagged_peaks, old_growth_spruce_taiga, origin_valley, pumpkin_patch, river, sparse_jungle, the_end, tropics], item.
pamhc2crops.bokchoyseeditem=[badlands, highland, old_growth_woodland, origin_valley, prairie, scrubland, seasonal_forest, snowblossom_grove, soul_sand_valley, sunflower_plains, warped_forest, wasteland, withered_abyss], item.
pamhc2crops.okraseeditem=[bayou, end_highlands, end_midlands, field, flower_forest, maple_woods, old_growth_spruce_taiga, old_growth_woodland, rainforest, river, small_end_islands, snowy_beach, the_void, wasteland], item.
pamhc2crops.whitemushroomseeditem=[auroral_garden, cherry_grove, crag, crystalline_chasm, dripstone_caves, eroded_badlands, glowing_grotto, jungle, lush_desert, mangrove_swamp, mediterranean_forest, savanna_plateau, the_end, the_void, tropics, warped_forest, withered_abyss], item.
pamhc2crops.cactusfruitseeditem=[bayou, birch_forest, cold_desert, crimson_forest, end_highlands, forested_field, frozen_peaks, fungal_jungle, grassland, jungle, lavender_forest, moor, ominous_woods, orchard, rainforest, small_end_islands, snowy_maple_woods, snowy_plains, snowy_taiga, sparse_jungle, stony_peaks, the_void, volcano, wetland], item.
pamhc2crops.barrelcactusseeditem=[auroral_garden, bog, cherry_grove, crimson_forest, end_barrens, end_highlands, highland, jade_cliffs, lavender_forest, marsh, mediterranean_forest, nether_wastes, old_growth_pine_taiga, orchard, prairie, scrubland, snowy_beach, snowy_fir_clearing, soul_sand_valley, windswept_gravelly_hills], item.
pamhc2crops.eggplantseeditem=[basalt_deltas, beach, clover_patch, dripstone_caves, end_highlands, forest, glowing_grotto, grove, jade_cliffs, lavender_forest, old_growth_birch_forest, old_growth_dead_forest, prairie, seasonal_orchard, snowy_fir_clearing, snowy_taiga, taiga, terrablender:deferred_placeholder, wasteland, windswept_savanna, withered_abyss], item.
pamhc2crops.chilipepperseeditem=[bog, crystalline_chasm, eroded_badlands, frozen_peaks, frozen_river, grassland, lavender_forest, old_growth_pine_taiga, prairie, rainforest, savanna_plateau, snowy_beach, snowy_coniferous_forest, snowy_fir_clearing, snowy_plains, snowy_taiga, spider_nest, swamp], item.
pamhc2crops.kohlrabiseeditem=[auroral_garden, bayou, bog, clover_patch, coniferous_forest, crimson_forest, end_highlands, flower_forest, glowing_grotto, old_growth_woodland, orchard, redwood_forest, rocky_shrubland, scrubland, small_end_islands, snowy_fir_clearing, snowy_plains, stony_shore, windswept_forest, windswept_hills, wooded_badlands], item.
pamhc2crops.milletseeditem=[badlands, dripstone_caves, eroded_badlands, erupting_inferno, flower_forest, forested_field, lavender_forest, mangrove_swamp, old_growth_spruce_taiga, river, snowy_taiga, swamp, visceral_heap, windswept_savanna], item.
pamhc2crops.huckleberryseeditem=[basalt_deltas, crag, crystalline_chasm, dune_beach, end_barrens, eroded_badlands, field, fir_clearing, forest, jade_cliffs, jungle, lavender_field, mangrove_swamp, maple_woods, marsh, mystic_grove, old_growth_spruce_taiga, pasture, rainforest, snowy_maple_woods, the_void, wasteland], item.
pamhc2crops.kenafseeditem=[badlands, bamboo_jungle, crag, forest, glowing_grotto, ice_spikes, jungle, lavender_field, lush_caves, maple_woods, mediterranean_forest, old_growth_pine_taiga, rainforest, redwood_forest, savanna, savanna_plateau, shrubland, snowy_beach, snowy_plains, sparse_jungle, stony_peaks, wetland, windswept_savanna, woodland], item.
pamhc2crops.truffleseeditem=[highland, jade_cliffs, lavender_forest, lush_desert, lush_savanna, maple_woods, mediterranean_forest, nether_wastes, ominous_woods, snowblossom_grove, snowy_coniferous_forest, stony_peaks, taiga, the_end, undergrowth, wetland], item.
pamhc2crops.kiwiseeditem=[birch_forest, bog, crag, deep_dark, floodplain, mushroom_fields, muskeg, rainforest, redwood_forest, savanna, stony_shore, swamp, the_void, withered_abyss, wooded_badlands], item.
pamhc2crops.spiceleafseeditem=[dune_beach, end_midlands, frozen_peaks, grassland, lush_desert, meadow, old_growth_birch_forest, old_growth_dead_forest, plains, seasonal_forest, shrubland, the_end, undergrowth], item.
pamhc2crops.kaleseeditem=[auroral_garden, desert, dryland, end_highlands, end_midlands, erupting_inferno, fir_clearing, forest, frozen_peaks, lush_caves, lush_desert, maple_woods, mystic_grove, old_growth_dead_forest, old_growth_woodland, ominous_woods, plains, redwood_forest, river, shrubland, snowy_coniferous_forest, snowy_fir_clearing, tropics], item.
pamhc2crops.pineappleseeditem=[beach, birch_forest, bog, desert, dryland, end_barrens, fir_clearing, frozen_peaks, highland, nether_wastes, old_growth_birch_forest, old_growth_spruce_taiga, scrubland, seasonal_orchard, soul_sand_valley, stony_shore, warped_forest, wasteland_steppe, windswept_gravelly_hills], item.
pamhc2crops.agaveseeditem=[basalt_deltas, crystalline_chasm, dead_forest, deep_dark, end_highlands, fungal_jungle, glowing_grotto, ice_spikes, jade_cliffs, mediterranean_forest, muskeg, origin_valley, prairie, rocky_rainforest, rocky_shrubland, shrubland, soul_sand_valley, stony_peaks, undergrowth, volcano], item.
pamhc2crops.chickpeaseeditem=[basalt_deltas, cherry_grove, cold_desert, deep_dark, dryland, eroded_badlands, floodplain, frozen_peaks, frozen_river, ice_spikes, jagged_peaks, old_growth_birch_forest, origin_valley, pasture, pumpkin_patch, seasonal_forest, sparse_jungle, sunflower_plains, the_void, volcanic_plains, volcano, wasteland], item.
pamhc2crops.mulberryseeditem=[beach, desert, dune_beach, end_midlands, frozen_river, grassland, lush_desert, marsh, old_growth_woodland, plains, prairie, rocky_shrubland, shrubland, small_end_islands, snowy_taiga, stony_shore, swamp, taiga, tropics, wasteland_steppe, windswept_savanna], item.
pamhc2crops.riceseeditem=[birch_forest, crimson_forest, desert, floodplain, grove, highland, lavender_field, mangrove_swamp, mystic_grove, redwood_forest, rocky_shrubland, scrubland, snowy_plains, swamp, undergrowth, windswept_forest, windswept_savanna], item.
minecraft.carrot=[badlands, bayou, beach, clover_patch, cold_desert, crag, end_midlands, eroded_badlands, erupting_inferno, forest, forested_field, fungal_jungle, grove, jungle, lush_savanna, mangrove_swamp, marsh, meadow, moor, nether_wastes, old_growth_pine_taiga, rainforest, river, rocky_shrubland, seasonal_forest, snowy_maple_woods, snowy_plains, the_void, volcanic_plains, windswept_hills, woodland], item.
pamhc2crops.artichokeseeditem=[field, fir_clearing, ice_spikes, jungle, lush_savanna, maple_woods, mediterranean_forest, old_growth_spruce_taiga, old_growth_woodland, origin_valley, plains, pumpkin_patch, sparse_jungle, spider_nest, stony_peaks, volcanic_plains, volcano, windswept_forest, windswept_gravelly_hills, windswept_hills, withered_abyss], item.
pamhc2crops.lettuceseeditem=[deep_dark, desert, eroded_badlands, field, glowing_grotto, meadow, moor, old_growth_woodland, plains, redwood_forest, shrubland, snowblossom_grove, snowy_maple_woods, tropics, warped_forest, wetland, windswept_savanna, woodland], item.
pamhc2crops.nettlesseeditem=[clover_patch, dark_forest, fungal_jungle, lavender_forest, mystic_grove, plains, pumpkin_patch, rainforest, shrubland, swamp, undergrowth], item.
pamhc2crops.alfalfaseeditem=[badlands, cold_desert, deep_dark, end_barrens, eroded_badlands, glowing_grotto, ice_spikes, maple_woods, marsh, mediterranean_forest, orchard, pasture, seasonal_forest, soul_sand_valley, sunflower_plains, tundra, visceral_heap], item.
pamhc2crops.turnipseeditem=[bayou, crimson_forest, jungle, lavender_field, lush_desert, mushroom_fields, mystic_grove, prairie, snowy_slopes, taiga, tundra, withered_abyss, woodland], item.
pamhc2crops.tealeafseeditem=[bog, dead_forest, end_midlands, eroded_badlands, fungal_jungle, lush_savanna, meadow, mystic_grove, ominous_woods, redwood_forest, river, snowblossom_grove, snowy_taiga, swamp, volcanic_plains, volcano, wasteland_steppe, windswept_forest, windswept_hills, windswept_savanna, withered_abyss], item.
pamhc2crops.chiaseeditem=[crystalline_chasm, desert, dripstone_caves, end_barrens, forest, fungal_jungle, grassland, jungle, lush_caves, lush_desert, mangrove_swamp, mushroom_fields, ominous_woods, plains, pumpkin_patch, small_end_islands, sparse_jungle, tundra, withered_abyss, woodland], item.
pamhc2crops.jicamaseeditem=[bamboo_jungle, coniferous_forest, dark_forest, glowing_grotto, jade_cliffs, jagged_peaks, lush_desert, mangrove_swamp, mushroom_fields, old_growth_birch_forest, old_growth_dead_forest, old_growth_pine_taiga, ominous_woods, pumpkin_patch, redwood_forest, snowy_plains, soul_sand_valley, swamp, terrablender:deferred_placeholder, the_end, undergrowth, windswept_forest, withered_abyss], item.
pamhc2crops.taroseeditem=[cold_desert, crag, dripstone_caves, end_midlands, lush_caves, lush_savanna, mystic_grove, old_growth_birch_forest, pasture, plains, shrubland, snowy_coniferous_forest, snowy_fir_clearing, snowy_plains, sunflower_plains, volcano], item.
pamhc2crops.cloudberryseeditem=[cold_desert, coniferous_forest, end_midlands, field, forest, frozen_river, highland, lavender_field, old_growth_dead_forest, orchard, savanna, snowy_slopes, swamp, the_end, tropics, tundra, wasteland_steppe], item.
pamhc2crops.rutabagaseeditem=[jade_cliffs, lush_savanna, marsh, meadow, mushroom_fields, rocky_rainforest, snowy_maple_woods, snowy_plains, soul_sand_valley, warped_forest, wooded_badlands]}


pamhc2crops.garlicseeditem=[bamboo_jungle,
 beach,
 bog,
 end_midlands,
 frozen_peaks,
 frozen_river,  this one?
 highland,
 lavender_forest,
 moor,
 old_growth_birch_forest,
 old_growth_dead_forest,
 old_growth_spruce_taiga,
 old_growth_woodland,
 ominous_woods,
 savanna,
 snowy_maple_woods,
 snowy_taiga,
 wasteland,
 windswept_hills],


new city either in bog or frozen areas?


TODO:
Command to show nearby biomes?
Command to show all biomes in this chuck?
Chunk Biome setting or command?
Command to show /biomecrop strawberry
    /biomecrop garlic onion chilepepper  (combo list of up to 3) shows where can plant both.
    /cropbiome swamp

 */
