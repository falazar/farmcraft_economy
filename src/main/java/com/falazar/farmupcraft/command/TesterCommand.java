package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * TestCommand is a command class that provides various test commands for debugging and development purposes.
 * It includes commands to show plot information, player data, and other test functionalities.
 */
public class TesterCommand {
    public static final CustomLogger LOGGER = new CustomLogger(TesterCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "test"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("tester");

        // Some test commands looking for....

        // MOSTLY DONE - Find islands/lakes nearby
        // EASY - Show biomes for single chunk (can save to plot and farm)
        // DONE - Show all biomes in a village (move to that command)
        // TODO Find all villagers ina village (or near area?)
        // -- See if we can get a villagers name.
        // Find all nearby Structures - ruins and such.
        // Find all nearby Open caves, at surface.
        // Find all below ground caves.

        // Define the "islandsAndLakes" sub-command
        LiteralArgumentBuilder<CommandSourceStack> islandsBuilder = Commands.literal("islandsAndLakes")
                .executes(context -> {
                    // Default distance of 10 if no argument is provided
                    return showIslandsAndLakes(context.getSource(), 10);
                })
                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 100)) // Add a distance argument with a range
                        .executes(context -> {
                            int distance = IntegerArgumentType.getInteger(context, "distance"); // Retrieve the distance value
                            return showIslandsAndLakes(context.getSource(), distance); // Pass the distance to the method
                        })
                );
        builder.then(islandsBuilder);

        // Define the "findvillagers" sub-command
        LiteralArgumentBuilder<CommandSourceStack> findVillagersBuilder = Commands.literal("findvillagers")
                .executes(context -> {
                    findNearVillagers(context.getSource());
                    return 0;
                });
        builder.then(findVillagersBuilder);

        // Define the "renamevillager" sub-command, ADMIN only!
        // Add two inputs oldName and newName strings, required
        LiteralArgumentBuilder<CommandSourceStack> renameVillagerBuilder = Commands.literal("renamevillager")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .then(Commands.argument("oldName", StringArgumentType.string())
                        .then(Commands.argument("newName", StringArgumentType.string())
                                .executes(context -> {
                                    // Get the old and new names from the command arguments
                                    String oldName = StringArgumentType.getString(context, "oldName");
                                    String newName = StringArgumentType.getString(context, "newName");
                                    // Call the renameVillager method with the old and new names
                                    return renameVillager(context.getSource(), oldName, newName);
                                })
                        )
                );
        builder.then(renameVillagerBuilder);

        // Define the "findneareststructure" sub-command, ADMIN permissions only!
        LiteralArgumentBuilder<CommandSourceStack> findNearestStructureBuilder = Commands.literal("findneareststructure")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .executes(context -> {
                    // Call the method to find the nearest structure
                    findNearestStructureCom(context.getSource());
                    return 0;
                });
        builder.then(findNearestStructureBuilder);

        // Define the "testcloseststructure" sub-command, ADMIN permissions only!
        LiteralArgumentBuilder<CommandSourceStack> testClosestStructureBuilder = Commands.literal("testcloseststructure")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .executes(context -> {
                    // Call the method to find the closest structure
                    testClosestStructure(context.getSource());
                    return 0;
                });
        builder.then(testClosestStructureBuilder);

        // Find all chests in nearest structure.
        // Define the findchests subcommand. ADMIN perms only.
        LiteralArgumentBuilder<CommandSourceStack> findChestsBuilder = Commands.literal("findchests")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .executes(context -> {
                    // Call the method to find chests in the nearest structure
                    findChestsInStructure(context.getSource(), "structory:graveyard", 270582939571L);
                    return 0;
                });
        builder.then(findChestsBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    // Find the closest structure to the player.
    // Add a written book to a chest.
    public static int testClosestStructure(CommandSourceStack source) {
        try {
            // Get the player's current position
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            BlockPos playerPos = playerSource.blockPosition();

            // STEP 1: Find the nearest structure
            Map.Entry<String, Long> nearestStructure = findNearestStructure(playerPos, (ServerLevel) source.getLevel(), source);
            LOGGER.info("Nearest structure key(type?): " + nearestStructure.getKey());
            LOGGER.info("Nearest structure value: " + nearestStructure.getValue());

            // Load structure and show it.
//            BoundingBox boundingBox = loadStructure(source, nearestStructure.getKey(), nearestStructure.getValue());
//            LOGGER.info("Bounding box: " + boundingBox);

            // STEP 2: Find all chests now.
            //findChestsInStructure(source, nearestStructure.getKey(), nearestStructure.getValue());

            // STEP 2: Find closest chest now, also does barrels.
            BlockPos nearestContainer = findNearestChestInStructure(source, nearestStructure.getKey(), nearestStructure.getValue());
            LOGGER.info("Nearest chest found at " + nearestContainer);
            if (nearestContainer.equals(null)) {
                source.sendSystemMessage(Component.literal("No chests found in the structure.").withStyle(ChatFormatting.RED));
                return 0;
            }

            // STEP 4: Create a book with writing in it.
            // Create a book item and put it in the chest.
//            Item bookItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("book"));
//            ItemStack bookStack = new ItemStack(bookItem);
            Item bookItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:written_book"));
            ItemStack bookStack = new ItemStack(bookItem);
            // Set the book's title and author
            bookStack.getOrCreateTag().putString("title", "Family History");
//            bookStack.getOrCreateTag().putString("author", playerSource.getName().getString());
            bookStack.getOrCreateTag().putString("author", "Corbin Eldrin");

            // Add some pages to the book
//            List<Component> pages = new ArrayList<>();
//            pages.add(Component.literal("This is a test book."));
//            pages.add(Component.literal("Page 2: More test content."));
//            pages.add(Component.literal("Page 3: Even more test content."));

            // Add some pages to the book
            ListTag pages = new ListTag();
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("We, the Eldrin Family, carved this life from the jade cliffs, yet the stone remembers the sacrifices made to appease its hunger."))));
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("The mystic lake offers solace, but its depths hold reflections of horrors we dared not speak, only to pass them down in our blood."))));
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("This book is a testament not of glory, but of the burden carried by each Eldrin, a legacy entwined with the shadowed fate of this mountain."))));

            // Attach the pages to the book
            bookStack.getOrCreateTag().put("pages", pages);

            // STEP 5: Put an item in the chest.
            // Get the block entity at the chest position
            BlockEntity blockEntity = source.getLevel().getBlockEntity(nearestContainer);
            if (!(blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity)) {
                LOGGER.error("Block entity at " + nearestContainer + " is not a chest.");
                // Say what it is.
                LOGGER.info("Block entity at " + nearestContainer + " is a " + blockEntity.getType().toString());
                return 0;
            }
            // Cast to ChestBlockEntity or BarrelBlockEntity and insert.

            RandomizableContainerBlockEntity containerEntity = (RandomizableContainerBlockEntity) blockEntity;
            // TODO make helper method.
            // Add the book to the chest inventory
            for (int i = 0; i < containerEntity.getContainerSize(); i++) {
                if (containerEntity.getItem(i).isEmpty()) {
                    containerEntity.setItem(i, bookStack);
                    LOGGER.info("Book added to container at " + nearestContainer);
                    break;
                }
            }
            // Mark the chest as updated
            containerEntity.setChanged();

            // Message chat to say location and that book was added.
            source.sendSystemMessage(Component.literal("Added book to chest at " + nearestContainer.toShortString()).withStyle(ChatFormatting.GOLD));
        } catch (Exception e) {
            LOGGER.error("Error finding closest structure: " + e.getMessage());
            source.sendSystemMessage(Component.literal("Error finding closest structure: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }

        return 0;
    }

    // Find nearby structure from command, call method and write to chat.
    public static int findNearestStructureCom(CommandSourceStack source) {
        // Get the player's current position
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
        BlockPos playerPos = playerSource.blockPosition();

        // Find the nearest structure
        Map.Entry<String, Long> nearestStructure = findNearestStructure(playerPos, (ServerLevel) source.getLevel(), source);
        LOGGER.info("Nearest structure key(type?): " + nearestStructure.getKey());
        LOGGER.info("Nearest structure value: " + nearestStructure.getValue());

        // Show the result in chat
        source.sendSystemMessage(Component.literal("Nearest structure: " + nearestStructure.getKey() + " ID: " + nearestStructure.getValue()).withStyle(ChatFormatting.GOLD));

        return 0;
    }

    public static Map.Entry<String, Long> findNearestStructure(BlockPos blockPos, ServerLevel world, CommandSourceStack source) {
        final Map<Structure, LongSet> structures = new HashMap<>();
        final ChunkPos start = new ChunkPos(BlockPos.containing(source.getPosition()));

        // STEP 1: Collect structures in a 10x10 chunk area
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                for (final Map.Entry<Structure, LongSet> entry : world.structureManager()
                        .getAllStructuresAt(new BlockPos((start.x + x) << 4, 0, (start.z + z) << 4))
                        .entrySet()) {
                    structures.computeIfAbsent(entry.getKey(), k -> new LongOpenHashSet(entry.getValue())).addAll(entry.getValue());
                }
            }
        }

        // STEP 2: Collect structure positions with their type and Long id
        Map<BlockPos, Map.Entry<String, Long>> structurePositions = new HashMap<>();
        for (Map.Entry<Structure, LongSet> structureEntry : structures.entrySet()) {
            world.structureManager().fillStartsForStructure(structureEntry.getKey(), structureEntry.getValue(),
                    structureStart -> {
                        String type = source.registryAccess().registry(Registries.STRUCTURE).get().getKey(structureEntry.getKey()).toString();
                        for (long id : structureEntry.getValue()) {
                            structurePositions.put(structureStart.getBoundingBox().getCenter(), new AbstractMap.SimpleEntry<>(type, id));
                        }
                    }
            );
        }

        // STEP 3: Find the nearest structure
        return structurePositions.entrySet().stream()
                .min(Comparator.comparingDouble(entry -> entry.getKey().distSqr(blockPos)))
                .map(Map.Entry::getValue) // Extract the Map.Entry<String, Long>
                .orElse(null); // Return null if no structures are found
    }

    // TODO make a method for chunk load entering the structure we know
    // send chat message for now to test. any chunk.


    // TODO call this with /tester chest
    public static BoundingBox loadStructure(CommandSourceStack source, String structureType, Long structureLong) {
        // TODO phase out source to make generic.
        // TODO phase out source to make generic.
        // TODO phase out source to make generic.
        // TODO phase out source to make generic.

        // Define the ResourceLocation for the structure
        ResourceLocation structureKey = new ResourceLocation(structureType);
        // Retrieve the Structure object from the registry
        Optional<Structure> structureOptional = source.registryAccess()
                .registry(Registries.STRUCTURE)
                .flatMap(registry -> registry.getOptional(structureKey));
        if (structureOptional.isEmpty()) {
            LOGGER.error("Structure not found: " + structureKey);
            return null;
        }
        Structure structure = structureOptional.get();
        // Create a LongSet and add the chunk position
        LongSet longSet = new LongOpenHashSet();
        longSet.add(structureLong);

        final ServerLevel world = source.getLevel();

        // Log the retrieved objects
        LOGGER.info("Load: TEST 2 Structure: " + structure);
        LOGGER.info("Load: TEST 2 LongSet: " + longSet);
        BoundingBox[] boundingBoxHolder = new BoundingBox[1];
        world.structureManager().fillStartsForStructure(structure, longSet, structureStart -> {
            // Access the StructureStart here
            boundingBoxHolder[0] = structureStart.getBoundingBox();
            BlockPos center = boundingBoxHolder[0].getCenter();
            LOGGER.info("Load: StructureStart found: " + structureStart);
            LOGGER.info("Load: BoundingBox: " + boundingBoxHolder[0]);
            LOGGER.info("Load: Center: " + center);

            // Center chunk
            ChunkPos centerChunk = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
            LOGGER.info("Load: Center Chunk: " + centerChunk);
        });

        // Retrieve the bounding box value after the lambda
        BoundingBox boundingBox = boundingBoxHolder[0];
        LOGGER.info("Load: Retrieved BoundingBox: " + boundingBox);
        return boundingBox;

        /* notes

        [10:52:58]   [TesterCommand:181]
        TEST 2: Find structure.
10:52:58.267
game
TEST 2 Structure: net.minecraft.world.level.levelgen.structure.structures.JigsawStructure@5b9eafd8
TEST 2 LongSet: {270582939571}
StructureStart found: net.minecraft.world.level.levelgen.structure.StructureStart@3a23a503
BoundingBox: BoundingBox{minX=-1244, minY=64, minZ=980, maxX=-1198, maxY=93, maxZ=1025}
Center: BlockPos{x=-1221, y=79, z=1003}
WORKS!
Stretches out to chunk areas.
         */

    }

    public static int findChestsInStructure(CommandSourceStack source, String type, Long id) {
        // TODO find all chests in the nearest structure.
        // TODO find all chests in a structure.
        // TODO find all chests in a chunk.
        LOGGER.info("DEBUG findChestsInNearestStructure");

        // Hardcoded 2 ids from earlier.
        // Graveyard near Faewild.
//        loadStructure(source, "structory:graveyard", 270582939571L);
//        BoundingBox boundingBox = loadStructure(source, "structory:graveyard", 270582939571L);
        BoundingBox boundingBox = loadStructure(source, type, id);

        // Loop over all blocks here and look for a chest, doublechest, or barrel
        for (int y = boundingBox.minY(); y < boundingBox.maxY(); y++) {
            for (int x = boundingBox.minX(); x < boundingBox.maxX(); x++) {
                for (int z = boundingBox.minZ(); z < boundingBox.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = source.getLevel().getBlockState(pos).getBlock();
                    if (block == Blocks.CHEST || block == Blocks.BARREL) {
                        LOGGER.info("Found chest at " + pos);
                        // TODO add to our list of chests. do other things.

                    }
                }
            }
        }

        // TODO find all chests in a structure.
        // TODO find all chests in a plot.

        return 0;
    }

    public static BlockPos findNearestChestInStructure(CommandSourceStack source, String type, Long id) {
        LOGGER.info("DEBUG findNearestChestInStructure");

        // Load the structure and get its bounding box
        BoundingBox boundingBox = loadStructure(source, type, id);
        if (boundingBox == null) {
            LOGGER.error("BoundingBox is null for structure type: " + type + " and id: " + id);
            return null;
        }

        // Get the player's position
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
        if (playerSource == null) {
            LOGGER.error("Player source is null.");
            return null;
        }
        BlockPos playerPos = playerSource.blockPosition();

        // Variables to track the nearest chest
        BlockPos nearestChest = null;
        double nearestDistance = Double.MAX_VALUE;

        // Loop through the bounding box to find chests or barrels
        for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = source.getLevel().getBlockState(pos).getBlock();

                    // Check if the block is a chest or barrel
                    if (block == Blocks.CHEST || block == Blocks.BARREL) {
                        double distance = pos.distSqr(playerPos);

                        // Update the nearest chest if this one is closer
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestChest = pos;
                        }
                    }
                }
            }
        }

        if (nearestChest != null) {
            LOGGER.info("Nearest chest found at " + nearestChest + " with distance " + Math.sqrt(nearestDistance));
        } else {
            LOGGER.info("No chests found in the structure.");
        }

        return nearestChest;
    }

    // Find all villagers in a village, or near the player.
    // For now just show the player name and profession and UUID.
    public static int findNearVillagers(CommandSourceStack source) {
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

        // Get player location
        BlockPos playerPos = playerSource.blockPosition();
        Level level = source.getLevel();

        // STEP 1: Scan for any villager in 10 chunk radius.
        TargetingConditions playersTarget = TargetingConditions.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight();
        List<? extends LivingEntity> list = level.getNearbyEntities(
                Villager.class,
                playersTarget,
                playerSource,
                new net.minecraft.world.phys.AABB(playerPos).inflate(160) // ten chunk default around.
        );
        int count = list.size();
        MutableComponent response = Component.literal("Found " + count + " villagers nearby.");
        MutableComponent finalResponse = response;
        source.sendSuccess(() -> finalResponse, false);

        // STEP 2: Sort villagers by distance.
        List<LivingEntity> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparingDouble(v -> v.distanceToSqr(playerSource)));


        // STEP 3: Show, Loop over all villagers, give name and UUID and position.
        for (LivingEntity v : sortedList) {
            LOGGER.info("\nLIST Villager found: ");
//            LOGGER.info("Villager UUID: " + v.getUUID());
//            LOGGER.info("Villagers found: " + v.getScoreboardName()); uuid
            LOGGER.info("Villager name: *" + v.getName().getString() + "*"); // Gives us their proper custom name! woot
            LOGGER.info("Villager position: " + v.blockPosition());
//            LOGGER.info("Villager type: " + v.getType().toString()); villager

            // STEP 4: Grab villager and extra info we want.
            Villager villager = (Villager) v;
            VillagerData d = villager.getVillagerData();
            String profession = d.getProfession().toString();
            String type = d.getType().toString();
//            LOGGER.info("Villager profession: " + profession);
            LOGGER.info("Villager type: " + type);  // plains and ? regular people?
            // Find distance from me, approx, use manhattan distance.
            BlockPos villagerPos = v.blockPosition();
            int distance = Math.abs(villagerPos.getX() - playerPos.getX()) + Math.abs(villagerPos.getY() - playerPos.getY()) + Math.abs(villagerPos.getZ() - playerPos.getZ());

            // Send to chat now name and profession.
            response = Component.literal(" - " + v.getName().getString() + " (" + profession + ") d=" + distance);
            MutableComponent finalResponse1 = response;
            source.sendSuccess(() -> finalResponse1, false);
            // TODO say direction too?
            // todo optional dist input
            // todo show position.
            // Todo can we show what village they are in, reg minecraft, then ours?

            // can we change the name manually and give them a last name or something?
            // NOTE: Name doesnt match exactly?  space or hidden char???
            String villagerName = v.getName().getString().trim(); // Get the name and trim whitespace
            if (villagerName.equals("Alaina Fae")) {
                LOGGER.info("DEBUG Found exact string Alaina Fae, Success!");
                source.sendSystemMessage(Component.literal("DEBUG Found exact string Alaina Fae, Success!"));
            }

//            if (v.getName().toString().contains("Alaina") || v.getName().toString().contains("Liberty")) {
//                LOGGER.info("DEBUG Found Alaina, changing name to TEST NAME");
//                villager.setCustomName(Component.literal("Alaina Fae"));
//                LOGGER.info("DEBUG Found her new name = " + villager.getName().getString());
//                source.sendSystemMessage( Component.literal("DEBUG Found Alaina, changing name to TEST NAME"));
//            }

            // TODO what event triggers when we are near a villager?
            // none create our own.
        }

        return 0;
    }

    // Rename a villager if they are nearby.
    public static int renameVillager(CommandSourceStack source, String oldName, String newName) {
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

        // Get player location
        BlockPos playerPos = playerSource.blockPosition();
        Level level = source.getLevel();

        // Scan for any villager in 20 chunk radius
        TargetingConditions playersTarget = TargetingConditions.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight();
        List<? extends LivingEntity> list = level.getNearbyEntities(
                Villager.class,
                playersTarget,
                playerSource,
                new net.minecraft.world.phys.AABB(playerPos).inflate(320)
        );

        int count = list.size();
        LOGGER.info(" DEBUG: Found " + count + " villagers nearby.");
        MutableComponent response = Component.literal("Found " + count + " villagers nearby.");
        MutableComponent finalResponse = response;
        source.sendSuccess(() -> finalResponse, false);

        boolean found = false;
        // Loop over all villagers, give name and UUID and position.
        for (LivingEntity v : list) {
            LOGGER.info("\nLIST Villager found: ");
            LOGGER.info("Villager name: *" + v.getName().getString() + "*"); // Gives us their proper custom name! woot

            Villager villager = (Villager) v;
            VillagerData d = villager.getVillagerData();
            String profession = d.getProfession().toString();
            LOGGER.info("Villager profession: " + profession);

            // Rename npc if found.
            String villagerName = v.getName().getString().trim(); // Get the name and trim whitespace
            if (villagerName.equals(oldName)) {
                LOGGER.info("DEBUG Found villager " + oldName + ", Success!");
                response = Component.literal("Found villager " + oldName + ", renamed them to " + newName);
                MutableComponent finalResponse1 = response;
                source.sendSuccess(() -> finalResponse1, false);

                // Set the new name
                villager.setCustomName(Component.literal(newName));

                found = true;
            }
        }
        if (!found) {
            LOGGER.info("DEBUG Villager " + oldName + " not found.");
            response = Component.literal("Villager " + oldName + " not found.");
            MutableComponent finalResponse1 = response;
            source.sendSuccess(() -> finalResponse1, false);
        }

        return 0;
    }

    public static int showIslandsAndLakes(CommandSourceStack source, int distance) {
        // STEP 1: Get current location of the player.
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

        ChunkPos chunkPos = playerSource.chunkPosition();
        LOGGER.info("DEBUG ChunkPos for player is " + chunkPos);

        // Create a double array of ints variable size
        // This will be a grid of 1s and 0s for land and water.
        // The size of the grid is 2 * distance + 1
        int[][] grid = new int[2 * distance + 1][2 * distance + 1];

        // STEP 2: Loop in a square around the player X distance.
        for (int z = -distance; z <= distance; z++) {
            LOGGER.info("\nDEBUG z=" + z);
            for (int x = -distance; x <= distance; x++) {
                // Get the chunk position.
                ChunkPos chunk = new ChunkPos(chunkPos.x + x, chunkPos.z + z);
//                LOGGER.info("DEBUG ChunkPos for player is " + chunk);
                // Get the chunk data.
                ServerLevel level = (ServerLevel) source.getLevel();

                // STEP 3: Get the biome for each chunk.
                // Get the biome for the chunk center.
                BlockPos blockPos = chunk.getMiddleBlockPosition(64); // default height notice.

                // Get height at that position.
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ()) - 1;
                blockPos = new BlockPos(blockPos.getX(), height, blockPos.getZ());

                Biome biome = level.getBiome(blockPos).value();
                ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
                if (biomeName == null) {
                    LOGGER.info("Error: Biome name is null for chunk " + chunk);
                    continue;
                }
                LOGGER.info("DEBUG Biome name for chunk " + chunk + " is " + biomeName);
                // TODO Can map show chunks???

                // STEP 4: Mark grid data 1 for land types and 0 for water types.
                boolean isWater = biomeName.getPath().contains("ocean") || biomeName.getPath().contains("river");

                // Check for actual water block also.
                if (!isWater) {
                    // If water or ice block, it is water.
                    final BlockState blockState = level.getBlockState(blockPos);
//                    LOGGER.info("DEBUG BlockState for blockPos " + blockPos.toShortString() + " is " + blockState);

                    if (blockState.is(Blocks.WATER) || blockState.is(Blocks.ICE)) {
//                        LOGGER.info("DEBUG BlockState for chunk " + chunk.toString() + " is WATER WE FOUND = " + blockState);
                        isWater = true;
                    }
                }

                if (isWater) {
                    grid[x + distance][z + distance] = 0; // set water
                } else {
                    // mark 2 for plains
                    // mark 3 for forest
                    // mark 1 for rest
                    if (biomeName.getPath().contains("plains")) {
                        grid[x + distance][z + distance] = 2; // plains
                    } else if (biomeName.getPath().contains("forest")) {
                        grid[x + distance][z + distance] = 3; // forest
                    } else {
                        // mark 1 for all other land types
                        grid[x + distance][z + distance] = 1; // land
                    }
                }

            }
        }

        // STEP 5: Then show the grid (for debugging)
        showGrid(grid, chunkPos, distance);


        // STEP 6: Algorithm to look for lakes and islands with this grid data.
        // floodfill fun!
        // Loop over the grid and find lakes and islands.
        // A lake is a 0 surrounded by 1s.
        // An island is a 1 surrounded by 0s.
        String answer = findLakesAndIslands(grid, chunkPos, distance);


        // STEP 7: Show the list of lakes and islands found.
        // TODO later save these into the DB and allow naming.
        MutableComponent response = Component.literal(answer);
        source.sendSuccess(() -> response, false);

        return 0;
    }

    private static void showGrid(int[][] grid, ChunkPos chunkPos, int distance) {

        LOGGER.info("DEBUG Grid data for islands and lakes:");
        // Print the grid with column numbers (x values)
        StringBuilder header = new StringBuilder("   "); // Padding for row numbers
        for (int j = 0; j < grid[0].length; j++) {
            header.append(j - distance + chunkPos.x).append(" "); // Add column number
        }
        LOGGER.info(header.toString());

        // Print the grid with row numbers (z values)
        for (int i = 0; i < grid.length; i++) {
            StringBuilder row = new StringBuilder();
            row.append(i - distance + chunkPos.z).append(": "); // Add row number at the start
            for (int j = 0; j < grid[i].length; j++) {
                row.append(grid[j][i]).append(" "); // Access transposed grid values
            }
            LOGGER.info(row.toString());
        }
    }


    // Helper class to store flood-fill results
    private static class FloodFillResult {
        boolean touchesEdge;
        int size;

        FloodFillResult(boolean touchesEdge, int size) {
            this.touchesEdge = touchesEdge;
            this.size = size;
        }
    }

    private static int[][] copyGrid(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] gridCopy = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(grid[i], 0, gridCopy[i], 0, cols);
        }
        return gridCopy;
    }

    // NOTICE may not work perfectly since a lot of "lakes" are water in an actual plains biome!!!  may need to check water blocks instead????

    // Helper method to perform flood-fill and return the result
    private static FloodFillResult floodFillWithEdgeAndSize(int[][] grid, boolean[][] visited, int x, int z, int targetValue, int distance) {
        // Create a copy of the grid
        int[][] gridCopy = copyGrid(grid);

        int rows = grid.length;
        int cols = grid[0].length;
        boolean touchesEdge = false;
        int size = 0;

        // Use a stack for iterative flood-fill
        Stack<int[]> stack = new Stack<>();
        stack.push(new int[]{x, z});

        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            int cx = cell[0];
            int cz = cell[1];
//            LOGGER.info("DEBUG FloodFillWithEdgeAndSize processing cell at cx=" + cx + ", cy=" + cy);

            // Skip if out of bounds, already visited, or not matching the target value
            if (cx < 0 || cz < 0 || cx >= rows || cz >= cols) {
                touchesEdge = true;
                continue;
            }

            // If we are looking for water and hit any land
            if (targetValue == 0 && gridCopy[cx][cz] != 0) {
                continue;
            }
            // If we are looking for land and hit any water
            if (targetValue != 0 && gridCopy[cx][cz] == 0) {
                continue;
            }

            // If already done
            if (gridCopy[cx][cz] == -1) {
                continue;
            }

            // Mark as visited.
            visited[cx][cz] = true;

            // Mark the cell as processed by changing its value (optional)
            gridCopy[cx][cz] = -1; // Mark as processed (use a special value)

            // Increment the size of the region
            size++;

            // Add neighbors to the stack
            stack.push(new int[]{cx + 1, cz});
            stack.push(new int[]{cx - 1, cz});
            stack.push(new int[]{cx, cz + 1});
            stack.push(new int[]{cx, cz - 1});
        }

        // DEBUG show grid!
        // Uncomment this to see the text output of the grid to debug easily.
//        if (!touchesEdge) {
//            ChunkPos centerChunk = new ChunkPos(x, z);
//            showGrid(gridCopy, centerChunk, distance);
//        }

        // TODO Save and return all chunks on the island.
        // TODO get and add center of island blockpos.
        // TODO get and add biome list strings.
        // Note: Diagonals not exactly perfect, thin lands.
        // probbly count diagonals as well.

        return new FloodFillResult(touchesEdge, size);
    }

    public static String findLakesAndIslands(int[][] grid, ChunkPos centerChunk, int distance) {
        int rows = grid.length;
        int cols = grid[0].length;

        boolean[][] visited = new boolean[rows][cols];
        int lakeCount = 0;
        int islandCount = 0;

        LOGGER.info("DEBUG starting flood fill: ");

        for (int j = 0; j < cols; j++) {
            // Loop through the grid
            LOGGER.info("DEBUG flood fill at j=" + j);

            for (int i = 0; i < rows; i++) {
                // If the cell is not visited, start a flood-fill
                if (visited[i][j]) {
                    continue;
                }

                int targetValue = grid[i][j];
                int waterOrLand = targetValue == 0 ? 0 : 1; // 1 for land, 0 for water
                FloodFillResult result = floodFillWithEdgeAndSize(grid, visited, i, j, waterOrLand, distance);
                // TODO record each object here for later use

                if (!result.touchesEdge) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + (i - distance), centerChunk.z + (j - distance));
                    if (targetValue == 0) {
                        lakeCount++;
                        LOGGER.info("Lake #" + lakeCount + " found at ChunkPos " + chunkPos + " at x,z = " + chunkPos.getMiddleBlockPosition(64).toShortString() +
                                " with size " + result.size);
                    } else {
                        islandCount++;
                        LOGGER.info("Island #" + islandCount + " found at ChunkPos " + chunkPos + " at x,z = " + chunkPos.getMiddleBlockPosition(64).toShortString() +
                                " with size " + result.size);
                    }
                }

            }
        }

        // Output the results
        LOGGER.info("Number of lakes: " + lakeCount);
        LOGGER.info("Number of islands: " + islandCount);

        // Return that text to show to player.
        return "Number of lakes: " + lakeCount + "\n" +
                "Number of islands: " + islandCount;
    }

}
