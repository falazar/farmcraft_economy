package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;

/**
 * StructureCommand provides commands for finding and managing structures in the
 * world.
 * It includes commands to find nearby structures and list structures from the
 * database.
 */
public class StructureCommand {
    public static final CustomLogger LOGGER = new CustomLogger(StructureCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "structure"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("structures");

        // Define the "find" sub-command
        LiteralArgumentBuilder<CommandSourceStack> findBuilder = Commands.literal("find")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .then(Commands.argument("filter", StringArgumentType.string())
                        .suggests((context, suggestionBuilder) -> {
                            // Provide suggestions for the argument
                            return net.minecraft.commands.SharedSuggestionProvider
                                    .suggest(new String[] { "upper", "lower", "all" }, suggestionBuilder);
                        })
                        .executes(context -> {
                            // Get the filter argument
                            String filter = StringArgumentType.getString(context, "filter");
                            int defaultRadius = 5;
                            findNearbyStructures(context.getSource(), filter, defaultRadius); // Pass the filter to the
                                                                                              // method with default
                                                                                              // radius
                            return 0;
                        })
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                .executes(context -> {
                                    // Get the filter and radius arguments
                                    String filter = StringArgumentType.getString(context, "filter");
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    findNearbyStructures(context.getSource(), filter, radius); // Pass both filter and
                                                                                               // radius
                                    return 0;
                                })));
        builder.then(findBuilder);

        // Define the "list" sub-command to show structures from database
        LiteralArgumentBuilder<CommandSourceStack> listBuilder = Commands.literal("list")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .executes(context -> {
                    // Default to showing first 20 structures
                    listStructuresFromDatabase(context.getSource(), 20, "all");
                    return 0;
                })
                .then(Commands.argument("filter", StringArgumentType.string())
                        .suggests((context, suggestionBuilder) -> {
                            // Provide suggestions for the filter argument
                            return net.minecraft.commands.SharedSuggestionProvider.suggest(
                                    new String[] { "all", "claimed", "unclaimed", "visited", "unvisited", "village" },
                                    suggestionBuilder);
                        })
                        .executes(context -> {
                            String filter = StringArgumentType.getString(context, "filter");
                            listStructuresFromDatabase(context.getSource(), 20, filter);
                            return 0;
                        })
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> {
                                    String filter = StringArgumentType.getString(context, "filter");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    listStructuresFromDatabase(context.getSource(), count, filter);
                                    return 0;
                                })));
        builder.then(listBuilder);

        // Define the "unmarkvisited" sub-command to unmark structures as visited
        LiteralArgumentBuilder<CommandSourceStack> unmarkBuilder = Commands.literal("unmarkvisited")
                .requires(stack -> stack.hasPermission(2)) // Require permission level 2
                .then(Commands.argument("id", LongArgumentType.longArg())
                        .executes(context -> {
                            long id = LongArgumentType.getLong(context, "id");
                            unmarkStructureAsVisited(context.getSource(), id);
                            return 0;
                        }));
        builder.then(unmarkBuilder);

        // /structure detail <N> [chests] — show Nth nearest structure from the whole DB
        builder.then(Commands.literal("detail")
                .requires(stack -> stack.hasPermission(2))
                .then(Commands.argument("index", IntegerArgumentType.integer(1, 500))
                        .executes(context -> FarmCraftCommand.showNearestStructureDetail(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "index"),
                                false))
                        .then(Commands.literal("chests")
                                .executes(context -> FarmCraftCommand.showNearestStructureDetail(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "index"),
                                        true)))));

        // /structures inside — show details of the structure you're currently standing
        // in
        builder.then(Commands.literal("inside")
                .requires(stack -> stack.hasPermission(2))
                .executes(context -> showInsideStructure(context.getSource())));

        // /structure setupspecialchest — look at a chest while standing in a structure
        builder.then(Commands.literal("setupspecialchest")
                .requires(stack -> stack.hasPermission(2))
                .executes(context -> FarmCraftCommand.setupSpecialChestCommand(context.getSource())));

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    // Simple class to hold structure information
    public static class StructureInfo {
        private final BlockPos position;
        private final String type;
        private final Long id;

        public StructureInfo(BlockPos position, String type, Long id) {
            this.position = position;
            this.type = type;
            this.id = id;
        }

        public BlockPos getPosition() {
            return position;
        }

        public String getType() {
            return type;
        }

        public Long getId() {
            return id;
        }
    }

    /**
     * Shows details (including chests) for the structure the player is standing
     * inside.
     */
    public static int showInsideStructure(CommandSourceStack source) {
        try {
            Entity nullablePlayer = source.getEntity();
            Player playerSource = nullablePlayer instanceof Player p ? p : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Must be run by a player."));
                return 0;
            }
            ServerLevel world = source.getLevel();
            var structureDb = ModEvents.getGameStructureDatabase(world);

            com.falazar.farmupcraft.data.GameStructureData s = com.falazar.farmupcraft.util.StructureUtils
                    .findStructureForPlayer(structureDb, playerSource);
            if (s == null) {
                source.sendFailure(Component.literal(
                        "You are not inside any known structure (no bbox match). Try /structures find all 5 first."));
                return 0;
            }

            // Reuse the detail display from FarmCraftCommand — find the global index so
            // the output header is consistent.
            BlockPos playerPos = playerSource.blockPosition();
            java.util.List<java.util.Map.Entry<Long, com.falazar.farmupcraft.data.GameStructureData>> all = new java.util.ArrayList<>();
            for (Long id : structureDb.getKeys()) {
                com.falazar.farmupcraft.data.GameStructureData sd = structureDb.getData(id);
                if (sd != null)
                    all.add(java.util.Map.entry(id, sd));
            }
            all.sort(java.util.Comparator.comparingDouble(e -> e.getValue().getCenterPos().distSqr(playerPos)));
            int idx = 1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getKey().equals(s.getId())) {
                    idx = i + 1;
                    break;
                }
            }

            return FarmCraftCommand.showNearestStructureDetail(source, idx, true);
        } catch (Exception ex) {
            LOGGER.error("showInsideStructure error: ", ex);
            source.sendFailure(Component.literal("Error — see log."));
            return 0;
        }
    }

    // Find nearby structures command
    public static int findNearbyStructures(CommandSourceStack source, String filter, int radius) {
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
        if (playerSource == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        ServerLevel world = (ServerLevel) source.getLevel();

        VillageData playerVillage = null;
        var playerDatabase = ModEvents.getPlayerDatabase();
        PlayerData playerData = playerDatabase.getData(playerSource.getUUID());
        if (playerData != null && playerData.getHomeVillage() != null) {
            playerVillage = playerData.getHomeVillage();
        }

        List<StructureInfo> sortedStructures = findNearbyStructuresForVillage(
                BlockPos.containing(source.getPosition()), world, playerSource, playerVillage, filter, radius);

        if (sortedStructures.isEmpty()) {
            source.sendFailure(Component.literal("No structures found nearby."));
            return 0;
        }

        Entity entity = source.getEntity();
        boolean isDebug = false;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            isDebug = com.falazar.farmupcraft.ChunkManager.isDebugPlayer(player);
        }
        if (isDebug) {
            source.sendSystemMessage(Component.literal("Found " + sortedStructures.size() + " structures nearby.")
                    .withStyle(ChatFormatting.GOLD));
            printStructureList(source, sortedStructures, playerSource, world);
            scanAndSetupChests(source, sortedStructures, world);
        }

        return 0;
    }

    /**
     * Prints the numbered structure list with claimed/visited status and clickable
     * TP.
     */
    private static void printStructureList(CommandSourceStack source, List<StructureInfo> structures,
            Player playerSource, ServerLevel world) {
        BlockPos origin = BlockPos.containing(source.getPosition());
        var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);
        DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();

        Entity entity = source.getEntity();
        boolean isDebug = false;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            isDebug = com.falazar.farmupcraft.ChunkManager.isDebugPlayer(player);
        }
        if (!isDebug)
            return;
        for (int i = 0; i < structures.size(); i++) {
            StructureInfo si = structures.get(i);
            int dist = (int) Math.sqrt(si.getPosition().distSqr(origin));
            ChunkData chunkData = chunkDatabase.getData(new ChunkPos(si.getPosition()).toLong());
            String claimedStatus = chunkData == null ? " (oo range)"
                    : (!chunkData.getType().equals("village") ? " (claimed)" : "");
            GameStructureData sd = gameStructureDatabase.getData(si.getId());
            String visitedStatus = (sd != null && sd.wasVisited()) ? " (visited)" : "";
            int idx = i + 1;
            final int detailIdx = idx;
            String coords = si.getPosition().getX() + "," + si.getPosition().getY() + "," + si.getPosition().getZ();
            String tpCmd = "/tp " + si.getPosition().getX() + " " + si.getPosition().getY() + " "
                    + si.getPosition().getZ();
            MutableComponent msg = Component.literal(idx + ". ");
            msg.append(Component.literal(getCommonNameForStructure(si.getType()))
                    .withStyle(style -> style.withColor(ChatFormatting.WHITE).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/structures detail " + detailIdx))));
            msg.append(Component.literal(claimedStatus + visitedStatus + " "));
            if (playerSource != null && playerSource.isCreative()) {
                msg.append(Component.literal(coords)
                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd))));
            } else {
                msg.append(Component.literal(coords).withStyle(ChatFormatting.YELLOW));
            }
            msg.append(Component.literal(" d=" + dist));
            source.sendSystemMessage(msg);
        }
    }

    /**
     * Scans each structure's bbox for chests, persists totalChestCount, and
     * auto-plants a special chest if the structure is eligible.
     */
    public static void scanAndSetupChests(CommandSourceStack source, List<StructureInfo> structures,
            ServerLevel world) {
        var structureDb = ModEvents.getGameStructureDatabase(world);
        int chestsScanned = 0;
        int specialChestsPlanted = 0;

        Entity entity = source.getEntity();
        boolean isDebug = false;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            isDebug = com.falazar.farmupcraft.ChunkManager.isDebugPlayer(player);
        }
        if (!isDebug)
            return;
        for (StructureInfo si : structures) {
            GameStructureData sd = structureDb.getData(si.getId());
            if (sd == null) {
                LOGGER.info("scanAndSetupChests: no DB entry for id={}", si.getId());
                continue;
            }
            if (!sd.hasBoundingBox()) {
                LOGGER.info("scanAndSetupChests: no bbox for {} (id={})", sd.getName(), sd.getId());
                continue;
            }
            int chunkCount = sd.hasChunkPositions() ? sd.getChunkPositions().size()
                    : ((sd.getMaxPos().getX() >> 4) - (sd.getMinPos().getX() >> 4) + 1)
                            * ((sd.getMaxPos().getZ() >> 4) - (sd.getMinPos().getZ() >> 4) + 1);
            if (chunkCount >= 80) {
                LOGGER.info("scanAndSetupChests: skipping {} — too large ({} chunks)", sd.getName(), chunkCount);
                source.sendSystemMessage(Component.literal(
                        "  Skipping " + sd.getName() + " — too large (" + chunkCount + " chunks).")
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            List<BlockPos> foundChests = scanBboxForChests(sd, world);
            LOGGER.info("scanAndSetupChests: {} — found {} chest(s), visited={}, hasSpecial={}",
                    sd.getName(), foundChests.size(), sd.wasVisited(), sd.hasSpecialChest());
            boolean plantedSpecial = false;
            if (!foundChests.isEmpty()) {
                chestsScanned += foundChests.size();
                if (sd.getTotalChestCount() != foundChests.size()) {
                    sd.setTotalChestCount(foundChests.size());
                    structureDb.putData(sd.getId(), sd);
                    structureDb.setDirty();
                }
                if (!sd.wasVisited() && !sd.hasSpecialChest()) {
                    if (VillageCommand.setupSpecialChest(world, sd, foundChests, structureDb)) {
                        specialChestsPlanted++;
                        plantedSpecial = true;
                    }
                }
            } else if (!sd.wasVisited() && !sd.hasSpecialChest()) {
                // No chests found — place one at the structure's center on solid ground.
                BlockPos placed = placeChestAtStructureCenter(sd, world);
                if (placed != null) {
                    chestsScanned++;
                    sd.setTotalChestCount(1);
                    structureDb.putData(sd.getId(), sd);
                    structureDb.setDirty();
                    if (VillageCommand.setupSpecialChest(world, sd, java.util.List.of(placed), structureDb)) {
                        specialChestsPlanted++;
                        plantedSpecial = true;
                    }
                    source.sendSystemMessage(Component.literal(
                            "  " + sd.getName() + ": placed chest at " + placed.toShortString())
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    LOGGER.info("scanAndSetupChests: {} — could not find solid ground to place chest", sd.getName());
                }
            }
            String chestMsg = "  " + sd.getName() + ": " + foundChests.size() + " chest(s)";
            if (plantedSpecial)
                chestMsg += " [special chest planted]";
            else if (sd.hasSpecialChest())
                chestMsg += " [has special]";
            else if (sd.wasVisited())
                chestMsg += " [visited]";
            source.sendSystemMessage(Component.literal(chestMsg).withStyle(ChatFormatting.AQUA));
        }

        if (chestsScanned > 0 || specialChestsPlanted > 0) {
            source.sendSystemMessage(Component.literal(
                    "Total: " + chestsScanned + " chest(s), "
                            + specialChestsPlanted + " special chest(s) planted.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Walks a structure's bounding box and returns all chest/barrel positions.
     * Only reads blocks in loaded chunks — unloaded chunks always read as air.
     */
    public static List<BlockPos> scanBboxForChests(GameStructureData sd, ServerLevel world) {
        List<BlockPos> found = new ArrayList<>();
        int unloadedChunks = 0;
        for (int bx = sd.getMinPos().getX(); bx <= sd.getMaxPos().getX(); bx++) {
            for (int bz = sd.getMinPos().getZ(); bz <= sd.getMaxPos().getZ(); bz++) {
                int cx = bx >> 4;
                int cz = bz >> 4;
                if (!world.isLoaded(new BlockPos(bx, 64, bz))) {
                    unloadedChunks++;
                    continue;
                }
                for (int by = sd.getMinPos().getY(); by <= sd.getMaxPos().getY(); by++) {
                    BlockPos bp = new BlockPos(bx, by, bz);
                    if (com.falazar.farmupcraft.StructureManager.isStructureChest(world.getBlockState(bp).getBlock()))
                        found.add(bp.immutable());
                }
            }
        }
        if (unloadedChunks > 0)
            LOGGER.info("scanBboxForChests: {} — skipped {} column(s) in unloaded chunks", sd.getName(),
                    unloadedChunks);
        return found;
    }

    /**
     * Finds solid ground near the structure's center (radius 5, center-first
     * spiral)
     * and places a vanilla chest there. Returns the placed position, or null on
     * failure.
     */
    private static BlockPos placeChestAtStructureCenter(GameStructureData sd, ServerLevel world) {
        BlockPos center = sd.getCenterPos();
        int searchTop = sd.hasBoundingBox() ? sd.getMaxPos().getY() + 2 : center.getY() + 10;
        int searchBot = sd.hasBoundingBox() ? sd.getMinPos().getY() : Math.max(center.getY() - 20, 0);

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                for (int y = searchTop; y >= searchBot; y--) {
                    BlockPos candidate = new BlockPos(cx, y, cz);
                    if (!world.isLoaded(candidate))
                        continue;
                    BlockPos below = candidate.below();
                    net.minecraft.world.level.block.state.BlockState belowState = world.getBlockState(below);
                    net.minecraft.world.level.block.state.BlockState atState = world.getBlockState(candidate);
                    net.minecraft.world.level.block.state.BlockState aboveState = world
                            .getBlockState(candidate.above());
                    if (belowState.isSolidRender(world, below)
                            && (atState.isAir() || atState.canBeReplaced())
                            && (aboveState.isAir() || aboveState.canBeReplaced())) {
                        world.setBlock(candidate, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_ALL);
                        LOGGER.info("placeChestAtStructureCenter: placed chest at {} in {}", candidate, sd.getName());
                        return candidate.immutable();
                    }
                }
            }
        }
        return null;
    }

    // Find nearby structures for a specific village
    public static List<StructureInfo> findNearbyStructuresForVillage(BlockPos centerPos, ServerLevel world,
            Player playerSource, VillageData village, String filter, int radius) {
        final Map<Structure, LongSet> structures = new HashMap<>();

        // STEP 1: Loop nearby area, 10x10 chunk area.
        final ChunkPos start = new ChunkPos(centerPos);
        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                for (final Map.Entry<Structure, LongSet> entry : world.structureManager()
                        .getAllStructuresAt(new BlockPos((start.x + x) << 4, 0, (start.z + z) << 4))
                        .entrySet()) {
                    structures.computeIfAbsent(entry.getKey(), k -> new LongOpenHashSet(entry.getValue()))
                            .addAll(entry.getValue());
                }
            }
        }

        // STEP 2: Loop over all structures found, put in our list.
        LOGGER.info("STEP 2 loop over structures....");
        List<StructureInfo> structureInfos = new ArrayList<>();

        // Get the database for saving structures
        var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);
        int[] newStructuresSaved = { 0 }; // Use array to work around lambda variable capture

        for (Map.Entry<Structure, LongSet> structureEntry : structures.entrySet()) {
            String type = world.registryAccess().registry(Registries.STRUCTURE).get().getKey(structureEntry.getKey())
                    .toString();

            // Loop through each Long ID in the set for this structure type
            for (Long id : structureEntry.getValue()) {
                LongSet singleIdSet = new LongOpenHashSet();
                singleIdSet.add(id);
                world.structureManager().fillStartsForStructure(structureEntry.getKey(), singleIdSet,
                        structureStart -> processStructureStart(structureStart, type, id, structureInfos,
                                gameStructureDatabase, newStructuresSaved, filter, structureEntry, playerSource));
            }
        }

        // STEP 3: Sort list by distance then return it.
        structureInfos.sort(Comparator.comparingDouble(s -> s.getPosition().distSqr(centerPos)));

        // Save the database
        gameStructureDatabase.setDirty();
        LOGGER.info("Found " + structureInfos.size() + " structures, saved " + newStructuresSaved[0]
                + " NEW structures to GameStructureData database");

        // DEBUG: show totals in chat to the triggering player (Bosspanda only).
        if (playerSource instanceof net.minecraft.server.level.ServerPlayer sp
                && com.falazar.farmupcraft.ChunkManager.isDebugPlayer(sp)) {
            int existing = structureInfos.size() - newStructuresSaved[0];
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[DEBUG] Scan: " + structureInfos.size() + " total ("
                            + newStructuresSaved[0] + " new, " + existing + " existing)")
                    .withStyle(ChatFormatting.GRAY));
        }

        return structureInfos;
    }

    /**
     * Processes a single structure start and adds it to the database and results
     * list.
     * 
     * This method handles the complex logic for processing individual structure
     * instances found in the world.
     * It performs several key operations:
     * 
     * 1. **Position Calculation**: Determines the center position of the structure,
     * with special handling
     * for structures that have incorrect Y coordinates (like mineshafts and
     * dungeons)
     * 
     * 2. **Filtering**: Applies height-based filtering based on the provided filter
     * parameter
     * - "upper": Only includes structures above Y=60
     * - "lower": Only includes structures below Y=60
     * - "all": Includes all structures regardless of height
     * 
     * 3. **Database Storage**: Saves the structure to the GameStructureData
     * database if it doesn't
     * already exist, using the structure's unique Long ID as the key
     * 
     * 4. **Results Collection**: Adds the structure to the results list for display
     * to the user
     * 
     * 
     * @param structureStart        The Minecraft structure start object containing
     *                              bounding box and other data
     * @param type                  The structure type identifier (e.g.,
     *                              "mineshaft", "dungeon")
     * @param id                    The unique Long ID for this specific structure
     *                              instance
     * @param structureInfos        The list to add the processed structure to for
     *                              display
     * @param gameStructureDatabase The database to store the structure data in
     * @param newStructuresSaved    Counter array to track how many new structures
     *                              were saved
     * @param filter                The height filter to apply ("upper", "lower", or
     *                              "all")
     * @param structureEntry        The original structure entry containing the
     *                              LongSet for logging
     */
    private static void processStructureStart(StructureStart structureStart,
            String type, Long id, List<StructureInfo> structureInfos,
            DataBase<Long, GameStructureData> gameStructureDatabase,
            int[] newStructuresSaved, String filter,
            Map.Entry<Structure, LongSet> structureEntry, Player playerSource) {
        BlockPos structureCenterPos;

        // Hack for mineshafts and things that are too large a bounding box.
        // If the y min value is less than 50 and the y max is greater than 10, manually
        // set the y value to y min + 50 only!
        if (structureStart.getBoundingBox().minY() <= 60 && structureStart.getBoundingBox().maxY() > 100) {
            LOGGER.info(
                    "HACKING structure at Y=" + structureStart.getBoundingBox().minY() + " because it is below 60.");
            structureCenterPos = new BlockPos(
                    structureStart.getBoundingBox().getCenter().getX(),
                    structureStart.getBoundingBox().minY() + 50, // Set to minY + 50
                    structureStart.getBoundingBox().getCenter().getZ());
            // Hack for mineshaft just add 5 to minY
            if (type.contains("mineshaft")) {
                structureCenterPos = new BlockPos(
                        structureStart.getBoundingBox().getCenter().getX(),
                        structureStart.getBoundingBox().minY() + 5, // Set to minY + 5
                        structureStart.getBoundingBox().getCenter().getZ());
            } else if (type.contains("dungeoncrawl:dungeon")) {
                // Hack for tower dungeon, use minY + 100 (could be red or grey)
                structureCenterPos = new BlockPos(
                        structureStart.getBoundingBox().getCenter().getX(),
                        structureStart.getBoundingBox().minY() + 100, // Set to minY + 100
                        structureStart.getBoundingBox().getCenter().getZ());
            }
        } else {
            structureCenterPos = structureStart.getBoundingBox().getCenter();
        }

        // STEP: Filter out our set if needed.
        if (filter.equals("upper") && structureCenterPos.getY() <= 60) {
            return; // Skip if below 60
        } else if (filter.equals("lower") && structureCenterPos.getY() > 60) {
            return; // Skip if above 60
        }

        // Store each structure with its unique ID
        structureInfos.add(new StructureInfo(structureCenterPos, type, id));

        // Save to GameStructureData database (only if not already saved)
        if (!gameStructureDatabase.containsKey(id)) {
            // Check if this structure is on a claimed chunk
            boolean isOnClaimedPlot = false;
            ChunkPos structureChunk = new ChunkPos(structureCenterPos);
            DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDatabase.getData(structureChunk.toLong());
            if (chunkData != null && !chunkData.getType().equals("village")) {
                isOnClaimedPlot = true;
            }

            // Compute the exact set of chunks covered by each structure piece.
            Set<Long> pieceChunks = new LinkedHashSet<>();
            for (StructurePiece piece : structureStart.getPieces()) {
                BoundingBox pbb = piece.getBoundingBox();
                int minCX = pbb.minX() >> 4;
                int minCZ = pbb.minZ() >> 4;
                int maxCX = pbb.maxX() >> 4;
                int maxCZ = pbb.maxZ() >> 4;
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        pieceChunks.add(new ChunkPos(cx, cz).toLong());
                    }
                }
            }

            GameStructureData structureData = new GameStructureData(
                    id, // Use the Long ID directly
                    getCommonNameForStructure(type), // Use common name for display
                    structureCenterPos, // Center position
                    type, // Original type string
                    isOnClaimedPlot, // Whether on claimed plot
                    false, // Not visited yet
                    new BlockPos(structureStart.getBoundingBox().minX(), structureStart.getBoundingBox().minY(),
                            structureStart.getBoundingBox().minZ()),
                    new BlockPos(structureStart.getBoundingBox().maxX(), structureStart.getBoundingBox().maxY(),
                            structureStart.getBoundingBox().maxZ()),
                    new ArrayList<>(pieceChunks));
            gameStructureDatabase.putData(id, structureData);
            gameStructureDatabase.setDirty();
            newStructuresSaved[0]++;
            LOGGER.info("Saved NEW structure to database: " + type + " (ID: " + id + ") at " + structureCenterPos
                    + " claimed: " + isOnClaimedPlot);
            // DEBUG: broadcast to all players so we can see new discoveries in-game.
            String newStructName = getCommonNameForStructure(type);
            String newStructCoords = structureCenterPos.getX() + "," + structureCenterPos.getY() + ","
                    + structureCenterPos.getZ();
            if (playerSource instanceof net.minecraft.server.level.ServerPlayer sp) {
                for (net.minecraft.server.level.ServerPlayer online : sp.getServer().getPlayerList().getPlayers()) {
                    if (com.falazar.farmupcraft.ChunkManager.isDebugPlayer(online)) {
                        online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[DEBUG] New structure found: " + newStructName + " at " + newStructCoords)
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
            }
        } else {
            // Update claimed status and backfill bbox/chunkPositions for existing
            // structures if missing.
            GameStructureData existingData = gameStructureDatabase.getData(id);
            if (existingData != null) {
                boolean changed = false;

                // Only update claimed status if the structure has been visited —
                // unvisited structures should not be marked claimed by a scan.
                if (existingData.wasVisited()) {
                    ChunkPos structureChunk = new ChunkPos(structureCenterPos);
                    DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
                    ChunkData chunkData = chunkDatabase.getData(structureChunk.toLong());
                    boolean isOnClaimedPlot = (chunkData != null && !chunkData.getType().equals("village"));
                    if (existingData.isOnClaimedPlot() != isOnClaimedPlot) {
                        existingData.setOnClaimedPlot(isOnClaimedPlot);
                        changed = true;
                        LOGGER.info("Updated structure claimed status: " + type + " (ID: " + id + ") claimed: "
                                + isOnClaimedPlot);
                    }
                }

                // Backfill bounding box if not stored yet.
                if (!existingData.hasBoundingBox()) {
                    existingData.setMinPos(new BlockPos(structureStart.getBoundingBox().minX(),
                            structureStart.getBoundingBox().minY(), structureStart.getBoundingBox().minZ()));
                    existingData.setMaxPos(new BlockPos(structureStart.getBoundingBox().maxX(),
                            structureStart.getBoundingBox().maxY(), structureStart.getBoundingBox().maxZ()));
                    LOGGER.info("Backfilled bbox for: " + type + " (ID: " + id + ") min=" + existingData.getMinPos()
                            + " max=" + existingData.getMaxPos());
                    changed = true;
                }

                // Backfill chunk positions from pieces if not stored yet.
                if (!existingData.hasChunkPositions()) {
                    Set<Long> pieceChunks = new LinkedHashSet<>();
                    for (StructurePiece piece : structureStart.getPieces()) {
                        BoundingBox pbb = piece.getBoundingBox();
                        for (int cx = pbb.minX() >> 4; cx <= pbb.maxX() >> 4; cx++) {
                            for (int cz = pbb.minZ() >> 4; cz <= pbb.maxZ() >> 4; cz++) {
                                pieceChunks.add(new ChunkPos(cx, cz).toLong());
                            }
                        }
                    }
                    existingData.setChunkPositions(new ArrayList<>(pieceChunks));
                    LOGGER.info("Backfilled " + pieceChunks.size() + " chunk(s) for: " + type + " (ID: " + id + ")");
                    changed = true;
                }

                if (changed) {
                    gameStructureDatabase.putData(id, existingData);
                    gameStructureDatabase.setDirty();
                } else {
                    LOGGER.info("Structure already fully populated, skipping: " + type + " (ID: " + id + ")");
                }
            }
        }

        // This seems to indicate we have bounding box and such on things? hmmm
        // How do we save a single structure?
        // by type and longset.
        LOGGER.info("Type = " + type);
        LOGGER.info("key = " + structureEntry.getKey()
                + " longset=" + structureEntry.getValue().toString());
        LOGGER.info("Bounds = " + structureStart.getBoundingBox().toString());
        LOGGER.info("");
    }

    // Function to get common name for a structure based on the type.
    public static String getCommonNameForStructure(String type) {
        switch (type) {
            case "dungeoncrawl:dungeon":
                return "tower dungeon"; // could be red or grey, wish we could find out which?
            case "structory:graveyard":
                return "cemetery";
            case "valhelsia_structures:forge":
                return "armorers cabin";
            default:
                // use second half of type string.
                String[] parts = type.split(":");
                if (parts.length > 1) {
                    // Return the second part of the type as the common name
                    return parts[1].replace("_", " ").replace("-", " ");
                } else {
                    // If no common name found, return the type itself
                    return type;
                }
        }
    }

    /**
     * Returns true if any ruined_portal structure exists in the given chunk.
     * Uses the world's structure manager directly so no prior scan is needed.
     */
    public static boolean chunkHasRuinedPortal(ChunkPos chunkPos, ServerLevel world) {
        BlockPos checkPos = new BlockPos(chunkPos.x * 16, 64, chunkPos.z * 16);
        for (Map.Entry<Structure, LongSet> entry : world.structureManager().getAllStructuresAt(checkPos).entrySet()) {
            String type = world.registryAccess().registry(Registries.STRUCTURE).get().getKey(entry.getKey()).toString();
            if (type.contains("ruined_portal")) {
                return true;
            }
        }
        return false;
    }

    // todo chekc water tower, not showing up.

    // List structures from database
    public static int listStructuresFromDatabase(CommandSourceStack source, int count, String filter) {
        ServerLevel world = (ServerLevel) source.getLevel();
        var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);

        if (gameStructureDatabase.getSize() == 0) {
            source.sendSystemMessage(
                    Component.literal("No structures found in database.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        // Get player position for distance calculation
        BlockPos playerPos = BlockPos.containing(source.getPosition());

        // Create a list of structures with their data for sorting
        List<Map.Entry<Long, GameStructureData>> structuresList = new ArrayList<>();
        for (Long structureId : gameStructureDatabase.getKeys()) {
            GameStructureData structureData = gameStructureDatabase.getData(structureId);
            if (structureData != null) {

                // Apply filter
                boolean includeStructure = false;
                switch (filter.toLowerCase()) {
                    case "all":
                        includeStructure = true;
                        break;
                    case "claimed":
                        includeStructure = structureData.isOnClaimedPlot();
                        break;
                    case "unclaimed":
                        includeStructure = !structureData.isOnClaimedPlot();
                        break;
                    case "visited":
                        includeStructure = structureData.wasVisited();
                        break;
                    case "unvisited":
                        includeStructure = !structureData.wasVisited();
                        break;
                    case "village":
                        // Skip underground structures (below Y=60) unless they have been visited
                        if (structureData.getCenterPos().getY() < 60 && !structureData.wasVisited()) {
                            continue;
                        }

                        // Check if chunk data exists (structure is in a village area)
                        ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
                        DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
                        ChunkData chunkData = chunkDatabase.getData(structureChunk.toLong());
                        includeStructure = (chunkData != null);
                        break;
                    default:
                        source.sendFailure(Component.literal("Invalid filter: " + filter
                                + ". Use: all, claimed, unclaimed, visited, unvisited, village"));
                        return 0;
                }

                if (includeStructure) {
                    structuresList.add(new AbstractMap.SimpleEntry<>(structureId, structureData));
                }
            }
        }

        if (structuresList.isEmpty()) {
            source.sendSystemMessage(Component.literal("No structures found matching filter: " + filter)
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        // Sort by distance from player
        structuresList.sort(Comparator.comparingDouble(entry -> entry.getValue().getCenterPos().distSqr(playerPos)));

        source.sendSystemMessage(Component
                .literal("Structures in database (showing first " + count + " by distance, filter: " + filter + "):")
                .withStyle(ChatFormatting.GOLD));

        int displayed = 0;
        for (Map.Entry<Long, GameStructureData> entry : structuresList) {
            if (displayed >= count)
                break;

            Long structureId = entry.getKey();
            GameStructureData structureData = entry.getValue();

            // Calculate distance for display
            int distance = (int) Math.sqrt(structureData.getCenterPos().distSqr(playerPos));

            String status = "";
            if (structureData.isOnClaimedPlot())
                status += " [CLAIMED]";
            if (structureData.wasVisited())
                status += " [VISITED]";

            // Check if structure is out of range (no chunk data means far from village
            // areas)
            if (!structureData.isOnClaimedPlot()) {
                ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
                DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
                ChunkData chunkData = chunkDatabase.getData(structureChunk.toLong());
                if (chunkData == null) {
                    status += " [OUT OF RANGE]";
                }
            }

            MutableComponent message = Component.literal(
                    (displayed + 1) + ". " + structureData.getName() +
                            " at " + structureData.getCenterPos().toShortString() +
                            " (d=" + distance + ")")
                    .withStyle(ChatFormatting.WHITE);

            // Add status with conditional color
            if (!status.isEmpty()) {
                ChatFormatting statusColor;
                if (structureData.isOnClaimedPlot() && structureData.wasVisited()) {
                    statusColor = ChatFormatting.GREEN; // Both claimed and visited
                } else if (structureData.isOnClaimedPlot() || structureData.wasVisited()) {
                    statusColor = ChatFormatting.YELLOW; // Either claimed or visited
                } else {
                    statusColor = ChatFormatting.WHITE; // Neither claimed nor visited
                }
                message.append(Component.literal(status).withStyle(statusColor));
            }

            // Log all fields with id and type
            LOGGER.info("StructureData: " + structureData.getName() + " (" + structureData.getType() + ") at "
                    + structureData.getCenterPos().toShortString() + " ID: " + structureId + " d=" + distance + status);

            source.sendSystemMessage(message);
            displayed++;
        }

        source.sendSystemMessage(Component.literal("Total structures matching filter '" + filter + "': " +
                structuresList.size() + " (of " + gameStructureDatabase.getSize() + " total)")
                .withStyle(ChatFormatting.GREEN));

        return 0;
    }

    /**
     * Updates the claimed status of all structures in a given chunk.
     * This should be called whenever a chunk gets claimed or unclaimed by a
     * village.
     * 
     * @param chunkPos  The chunk position that was claimed/unclaimed
     * @param isClaimed Whether the chunk is now claimed (true) or unclaimed (false)
     * @param world     The server level for database access
     */
    public static void updateStructuresInChunk(ChunkPos chunkPos, boolean isClaimed, ServerLevel world) {
        try {
            var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);

            for (Long structureId : gameStructureDatabase.getKeys()) {
                GameStructureData structureData = gameStructureDatabase.getData(structureId);
                if (structureData == null) {
                    continue;
                }

                // Check if this structure is in the specified chunk
                ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
                if (!structureChunk.equals(chunkPos)) {
                    continue;
                }

                // Only update claimed status if the structure has been visited first.
                // Unvisited structures should not be marked claimed just because a plot was
                // bought nearby.
                if (structureData.wasVisited() && structureData.isOnClaimedPlot() != isClaimed) {
                    structureData.setOnClaimedPlot(isClaimed);
                    gameStructureDatabase.putData(structureId, structureData);
                    gameStructureDatabase.setDirty();
                    LOGGER.info("Updated structure claimed status: " + structureData.getName() +
                            " (ID: " + structureId + ") in chunk " + chunkPos + " claimed: " + isClaimed);
                }
            }

        } catch (Exception ex) {
            LOGGER.error("Error updating structures in chunk " + chunkPos + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Unmarks a structure as visited by its ID.
     * 
     * @param source      The command source
     * @param structureId The ID of the structure to unmark
     */
    public static int unmarkStructureAsVisited(CommandSourceStack source, long structureId) {
        try {
            ServerLevel world = (ServerLevel) source.getLevel();
            var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);

            GameStructureData structureData = gameStructureDatabase.getData(structureId);
            if (structureData == null) {
                source.sendFailure(Component.literal("Structure with ID " + structureId + " not found in database."));
                return 0;
            }

            if (!structureData.wasVisited()) {
                source.sendFailure(Component.literal("Structure " + structureData.getName() + " (ID: " + structureId
                        + ") is not marked as visited."));
                return 0;
            }

            structureData.setWasVisited(false);
            gameStructureDatabase.putData(structureId, structureData);
            gameStructureDatabase.setDirty();

            source.sendSystemMessage(Component
                    .literal("Unmarked structure as visited: " + structureData.getName() + " (ID: " + structureId + ")")
                    .withStyle(ChatFormatting.GREEN));
            LOGGER.info("Unmarked structure as visited: " + structureData.getName() + " (ID: " + structureId + ")");

            return 1;

        } catch (Exception ex) {
            LOGGER.error("Error unmarking structure as visited: " + ex.getMessage());
            ex.printStackTrace();
            source.sendFailure(Component.literal("Error unmarking structure: " + ex.getMessage()));
            return 0;
        }
    }

    // TODO make a village method now that locates all structures near a village
    // area.
    // then logs them to the village, and to the DB.
    // then we need to know if they have been visited somehow, by a player or a
    // village, and save that.
    // then we need a flag to see if they are claimed or not by a village.... phew.
    // TODO TODO

    /*
     * example
     * // NOTICE: mineshafts and at least one dungeon have INCORRECT y value, need
     * to dig deeper, they show at like 128 or something instead of underground,
     * // annoying.
     * [07:55:14] [TesterCommand:171] Type = bettermineshafts:mineshaft_jungle
     * [07:55:14] [TesterCommand:172] key =
     * com.yungnickyoung.minecraft.bettermineshafts.world.BetterMineshaftStructure@
     * 37e152ff longset={-412316860221, -390842023739}
     * [07:55:14] [TesterCommand:174] Bounds = BoundingBox{minX=3085, minY=-26,
     * minZ=-1537, maxX=3214, maxY=320, maxZ=-1429}
     * 
     * [10:52:56] [Server thread/INFO] [co.fa.fa.ut.CustomLogger/]: [INFO]
     * [farmupcraft] [StructureCommand:273] Type =
     * valhelsia_structures:deep_spawner_room
     * 10:52:56.540
     * game
     * [10:52:56] [Server thread/INFO] [co.fa.fa.ut.CustomLogger/]: [INFO]
     * [farmupcraft] [StructureCommand:274] key =
     * com.stal111.valhelsia_structures.common.world.structures.
     * ValhelsiaJigsawStructure@42137192
     * longset={214748364708, 210453397420}
     * there are two of them? confusing a bit here....
     * yah not saving the dupes...
     * 
     */

    /*
     * test data 6-18 in Faewild
     * 
     * 2 found from town center, that should NOT be on the surface y.
     * 
     * [17:17:46] [TesterCommand:291] Type = bettermineshafts:mineshaft_lush
     * [17:17:46] [TesterCommand:292] key =
     * com.yungnickyoung.minecraft.bettermineshafts.world.BetterMineshaftStructure@
     * 65223073 longset={270582939573}
     * [17:17:46] [TesterCommand:294] Bounds = BoundingBox{minX=-1249, minY=-45,
     * minZ=866, maxX=-1138, maxY=320, maxZ=1019}
     * 
     * [17:17:46] [TesterCommand:291] Type = dungeoncrawl:dungeon
     * [17:17:46] [TesterCommand:292] key =
     * xiroc.dungeoncrawl.dungeon.Dungeon@56327917 longset={219043332008}
     * [17:17:46] [TesterCommand:294] Bounds = BoundingBox{minX=-1470, minY=-7,
     * minZ=720, maxX=-1328, maxY=512, maxZ=878}
     * grey tower...does go into sky, to 105
     * // TODO add a Common Name to all these also, map them.
     * 
     * [17:17:46] [CHAT] Structures nearby:
     * [17:17:46] [CHAT] Found 11 structures nearby.
     * [17:17:46] [CHAT] 1. towns_and_towers:village_birch_forest a. -1302, 74, 916
     * d=17
     * [17:17:46] [CHAT] 9. bettermineshafts:mineshaft_lush a. -1193, 138, 943 d=146
     * BAD
     * [17:17:46] [CHAT] 11. dungeoncrawl:dungeon a. -1399, 253, 799 d=230 BAD
     * 
     * after hack fix
     * [System] [CHAT] Found 11 structures nearby.
     * [17:49:09] 2. alexscaves:underground_cabin a. -1267, 28, 916 d=63
     * [17:49:09] 3. valhelsia_structures:spawner_room a. -1323, 27, 981 d=84
     * [17:49:09] 4. betterdungeons:small_dungeon a. -1280, 10, 960 d=85
     * [17:49:09] 5. alexscaves:underground_cabin a. -1372, 23, 964 d=91
     * [17:49:09] 6. philipsruins:ancient_dungeon a. -1348, -26, 924 d=100
     * [17:49:09] 7. philipsruins:ancient_dungeon a. -1283, -28, 900 d=101
     * [17:49:09] 8. alexscaves:underground_cabin a. -1340, -18, 852 d=105
     * [17:49:09] 9. dungeoncrawl:dungeon a. -1399, 43, 799 d=139 BETTER
     * testing: grey tower
     * [17:49:09] 10. bettermineshafts:mineshaft_lush a. -1193, 5, 943 d=142 BETTER
     * testing: too high here... -40 is real one?
     * [17:49:09] 11. philipsruins:ancient_dungeon a. -1260, -28, 805 d=151
     * 
     * todo test armorers house
     * what other important ones?
     * red tower
     * 
     */

}