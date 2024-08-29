package com.falazar.farmupcraft;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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

//import java.awt.*;
import java.util.*;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.replace;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Main.MODID)
public class Main {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "farmupcraft";
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

    public Main() {
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

        // Register our new classes.
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


    // TODO MOVE OUTSIDE OF MAIN CLASS.
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class BiomeCropsEventsHandler {
        // Class-level variable to store biome information
        private static Map<String, List<String>> biomeCrops = new HashMap<>();
        private static Map<String, List<String>> cropBiomes = new HashMap<>();

        // Looking for village.
        public void findNearestVillage(PlayerInteractEvent.RightClickBlock event) {
            LOGGER.info("DEBUG: testDebugMethod: this target block is " + event.getLevel().getBlockState(event.getPos()).getBlock().getName().toString());


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


        @SubscribeEvent
        // Main Method here:
        // When trying to plant crops, check our biome rules to see what crops are allowed there.
        public void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

            // Step 1: If in creative mode, skip all rules and allow planting all.
            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                if (player.isCreative()) {
                    LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
                    return;
                }
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
            LOGGER.info("DEBUG: item held getDescriptionId is " + stack.getDescriptionId());
            if (!stack.getDescriptionId().contains("pamhc2crops") && !isVanillaCrop(stack)) {
                // Is not a crop type... leave..
//                LOGGER.info("DEBUG: not a crop, leaving rules now.");
                return;
            }

            // DEBUG ZONE:
            findNearestVillage(event);

            // Setup all of our biomes and crops rules allowed, saves to cache.
            setupBiomeCrops(event);  // TEMP TESTER AREA. TODO move me, call one time only at start.

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

        // Given a stack item, and biomename, check if it is allowed to be planted here.
        public boolean isCropAllowed(ItemStack stack, String biomeName, PlayerInteractEvent event) {
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

                // TODO Send a message to the player - todo sending AS player I think, fix me later.
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

TODO:
Command to show nearby biomes?
Command to show all biomes in this chuck?
Chunk Biome setting or command?
Command to show /biomecrop strawberry
    /biomecrop garlic onion chilepepper  (combo list of up to 3) shows where can plant both.
    /cropbiome swamp

 */
