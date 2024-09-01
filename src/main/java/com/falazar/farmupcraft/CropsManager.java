package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.AsyncLocator;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
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
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
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
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.replace;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CropsManager {
    public static final CustomLogger LOGGER = new CustomLogger(CropsManager.class.getSimpleName());

    // Class-level variable to store biome information
    private static Map<String, List<String>> biomeCrops = new HashMap<>();
    private static Map<String, List<String>> cropBiomes = new HashMap<>();

    @SubscribeEvent
    // Main Method here:
    // When trying to plant crops, check our biome rules to see what crops are allowed there.
    public static void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {

        // Step 1: If in creative mode, skip all rules and allow planting all.
        Player player = (Player) event.getEntity();
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
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
        if (!stack.is(FUCTags.MODDED_CROPS) && !stack.is(FUCTags.VANILLA_CROPS) && !stack.is(FUCTags.MODDED_SEEDS)) {
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

        //The biome has rules defined for what can happen in it or not!
        BiomeRulesManager manager = BiomeRulesManager.get(event.getLevel());
        if (manager == null || !manager.hasRules()) return;

        BiomeRulesInstance instance = manager.getBiomeRules(biome);
        if (instance == null) return;

        //check if the crop is allowed in the biome
        if (!isCropAllowed(manager, instance, stack, biome, event)) {
            // Cancel event and return now.
            event.setCanceled(true);
            return;
        }
    }


    // NOTE: Is about 3 hours now with 400 growth.
    // Now based on actual growth stat instead?  onGrow event
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
            //LOGGER.info("DEBUG: NON CROP BLOCK slowCropsEvent:  allowed, this target block is " + block.getName().toString());
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

//        LOGGER.info("DEBUG: slowCropsEvent: " + randomNum + " allowed, this target block is " + block.getName().toString());
    }


    // DEBUG: Testing, Looking for village.
    public static void findNearestVillage(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.info("DEBUG: testDebugMethod: this target block is " + event.getLevel().getBlockState(event.getPos()).getBlock().getName().toString());
        if (true) return;
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
                    if (structurePos == null) {
                        LOGGER.info("DEBUG: structurepos is null. ");
                        return;
                    }


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

    private static final ResourceKey<Biome> UKNOWN_RK = ResourceKey.create(Registries.BIOME, new ResourceLocation("unkown"));

    // Given a crop stack item, and biome, check if it is allowed to be planted here.
    public static boolean isCropAllowed(BiomeRulesManager manager, BiomeRulesInstance instance, ItemStack stack, Holder<Biome> biome, PlayerInteractEvent event) {
        // This crop is allowed here in this biome.
        if (instance.biomeHasCrops(stack)) {
            return true;
        }

        // Get the name of the crop item
        String cropItemShow = stack.getHoverName().getString();

        // Get the biome name
        ResourceKey<Biome> rl = biome.unwrapKey().orElse(UKNOWN_RK);
        Component biomeNameShow = Component.translatable(getBiomeLangKey(rl.location())).withStyle(ChatFormatting.AQUA);

        Player player = event.getEntity();
        if (!event.getLevel().isClientSide) {
            // Display message that this crop cannot be planted in this biome
            MutableComponent component = Component.literal("§eYou cannot plant " + cropItemShow + " in ").append(biomeNameShow);
            player.displayClientMessage(component, false);

            // List the crops allowed in the current biome
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
                    // Filter out the remaining names that still include "Seed" or "Seeds" but not the special cases
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


            player.displayClientMessage(component, false);

            // List the biomes where this crop can be planted
            if (manager.hasItems()) {
                // Get the translated biome names, sort them, ensure they are unique, and combine them into a single component
                Component biomesListShow = manager.getBiomesForItem(stack.getItem()).stream()
                        .map(b -> Component.translatable(getBiomeLangKey(b.unwrapKey().get().location())).withStyle(ChatFormatting.AQUA))
                        .map(Component::getString) // Convert to plain text for uniqueness check
                        .distinct() // Ensure each biome is unique
                        .sorted() // Sort the biomes alphabetically
                        .map(name -> Component.literal(name)) // Convert back to Component
                        .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
                        .orElse(Component.literal("None"));

                // Create the final message component
                component = Component.literal("§bBiomes you can plant " + cropItemShow + " in §3").append(biomesListShow);
                player.displayClientMessage(component, false);
            }
        }
        return false;
    }

    private static String getBiomeLangKey(ResourceLocation location) {
        String name = location.getPath();
        String id = location.getNamespace();
        return "biome." + id + "." + name;
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

    // TODO: move to stone? manager class.
    // On breaking stone, sometimes it will fail and you will not get back any items.
    // You can increase the rate with skills and special items...
    // cobblestone and deepslate drop rate here.
    // NOTICE: Event methods cannot be static.
    @SubscribeEvent
    public static void onBreakStone(BlockEvent.BreakEvent event) {
//        Player player = event.getPlayer();
//        Player player = Player.getByName(player.getScoreboardName());
//        if (player == null) {
//            return;
//        }
        String string = "";
        //StringBuilder builder = new StringBuilder();
//
        //Iterable<Item> allItems = ForgeRegistries.ITEMS;
//
        //// Filter information about items from the target mod
        //List<String> itemNames = new ArrayList<>();
        //for (Item item : allItems) {
        //    // TODO add in basic foods and tree foods.
//
        //    // Check if the item belongs to the target mod
        //    String itemName = item.getDescriptionId();
        //    // Example: 'pamhc2foodcore:baconcheeseburgeritem'
        //    // rolleritem bad ones, filter out.
        //    // Look for edible as well.
        //    if ((itemName.contains("pamhc2foodcore") || itemName.contains("pamhc2foodextended"))
        //            && itemName.contains("item")
        //            && item.isEdible()) {
//      //          LOGGER.info("DEBUG: name = " + itemName);
        //        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        //        builder.append(".tag(new ResourceLocation(\"" + rl + "\"))");
        //    }
        //}
        //builder.append(";");
        //LOGGER.info("Final string = " + builder.toString());



        final BlockState blockState = event.getLevel().getBlockState(event.getPos());
        MutableComponent component = Component.translatable(blockState.getBlock().getDescriptionId());
        String s = component.toString();
        LOGGER.info("DEBUG1: " + s + " all tags = " + blockState.getTags().map(itemTagKey -> itemTagKey.toString()).collect(Collectors.toList()));


        // Only do rule if base stone or dirt.
        if (!blockState.is(BlockTags.BASE_STONE_OVERWORLD) && !blockState.is(BlockTags.DIRT)) {
            return;
        }

        LOGGER.info("DEBUG2: Testing2 here we found base stone/dirt");

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
        LOGGER.info("DEBUG3: Random Num = " + randomNum);
        if (randomNum >= successRate) {
            LOGGER.info("DEBUG: DESTROYING stone block, no drops...");
            event.getLevel().destroyBlock(event.getPos(), false);
            event.setCanceled(true);  // this works fine, prevents drops, must have or it replaces it!
            return;
        }
        LOGGER.info("DEBUG3: ALLOWING stone block drops...");

        // SECOND ABILITY (only if above worked)
        // trencher
//            if (player.hasSkill("trencher")) {
//                attemptTrenchBreak(player, event.getPos());
//            }


    }

}


