package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.command.StructureCommand;
import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.*;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

public class PlotCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlotCommand.class.getSimpleName());
    private static final List<String> VALID_PLOT_TYPES = Arrays.asList("plot", "farm", "nursery", "kitchen",
            "restaurant", "house", "trainstation", "graveyard", "pasture", "refinery");

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "show"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("plot");

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(PlotCommand::showPlotInfo);
        builder.then(infoBuilder);

        // Define the "buy" sub-command
        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((context, builder2) -> {
                            builder2.suggest("farm");
                            builder2.suggest("plot");
                            builder2.suggest("nursery");
                            builder2.suggest("kitchen");
                            builder2.suggest("house");
                            builder2.suggest("restaurant");
                            builder2.suggest("trainstation");
                            builder2.suggest("graveyard");
                            builder2.suggest("pasture");
                            return builder2.buildFuture();
                        })
                        .executes(context -> {
                            String plotType = StringArgumentType.getString(context, "type");
                            if (VALID_PLOT_TYPES.contains(plotType)) {
                                return buyPlot(context.getSource(), plotType);
                            } else {
                                context.getSource().sendFailure(Component.literal(
                                        "Invalid plot type. Must be plot, farm, nursery, kitchen, restaurant, house."));
                                return 0;
                            }
                        }));
        builder.then(buyBuilder);

        // Define the "delete" sub-command - For ADMIN only!
        // removes chunk from database, and village
        LiteralArgumentBuilder<CommandSourceStack> deleteBuilder = Commands.literal("delete")
                .executes(context -> {
                    deleteChunk(context.getSource());
                    return 0;
                })
                .requires(s -> s.hasPermission(2)); // Adjust permission as needed
        builder.then(deleteBuilder);

        // Define ADMIN setvillage chunk to reclaim a single chunk.
        LiteralArgumentBuilder<CommandSourceStack> reclaimBuilder = Commands.literal("reclaim")
                .executes(context -> {
                    reclaimChunk(context.getSource());
                    return 0;
                })
                .requires(s -> s.hasPermission(2)); // Adjust permission as needed
        builder.then(reclaimBuilder);

        // Define the "visitor" sub-command — add or remove allowed visitors on a plot.
        LiteralArgumentBuilder<CommandSourceStack> visitorBuilder = Commands.literal("visitor")
                .then(Commands.literal("add")
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "playerName");
                                    return setPlotVisitor(context.getSource(), name, true);
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("playerName", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "playerName");
                                    return setPlotVisitor(context.getSource(), name, false);
                                })));
        builder.then(visitorBuilder);

        // TODO do a /plot biomes command also!

        // Register the main "plot" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showPlotInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(playerSource.blockPosition());
            // Get all biomes for all blocks in this chunk.
            ServerLevel serverLevel = context.getSource().getLevel();
            Map<String, Integer> biomes = getChunkBiomes(playerSource.blockPosition(), serverLevel);
            DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = dataBase.getData(chunkPos.toLong());

            // If no data, then not owned by a village.
            if (chunkData == null) {
                context.getSource().sendFailure(Component.literal("Plot at " + chunkPos + " is not owned."));
                if (!biomes.isEmpty()) {
                    String biomeStr = biomes.entrySet().stream()
                            .map(e -> e.getKey() + " (" + e.getValue() + ")")
                            .collect(java.util.stream.Collectors.joining(", "));
                    context.getSource().sendSuccess(() -> Component.literal(", Biomes: " + biomeStr), false);
                } else {
                    context.getSource().sendSuccess(() -> Component.literal(", No biomes found."), false);
                }
                return 0;
            }

            // Pull out plot info and owner and village.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(serverLevel);
            VillageData villageData = villageDataDB.getData(chunkData.getVillageId());
            // village data is null in one chunk in vilalge.... not claimed properly or
            // whats?

            // add some loggin aboiut chunk data
            LOGGER.info("DEBUG: Chunk data: type=" + chunkData.getType() +
                    ", playerId=" + chunkData.getPlayerId() +
                    ", villageId=" + chunkData.getVillageId());

            // BUG here maybe. update playerid to uuid string.
            LOGGER.info("Plot info for " + chunkPos + ": player id = " + chunkData.getPlayerId()
                    + ", village id = " + chunkData.getVillageId() + ", type = " + chunkData.getType());
            LOGGER.info("Player name: " + chunkData.getNameForPlayer(serverLevel));

            // Debug force-load state when checking plot info.
            boolean vanillaForcedThisChunk = serverLevel.getForcedChunks().contains(chunkPos.toLong());
            boolean forgeForcedThisChunk = isForgeForcedChunk(serverLevel, chunkPos);
            boolean hasAnyForcedChunksInLevel = ForgeChunkManager.hasForcedChunks(serverLevel);
            LOGGER.info("DEBUG: Force-load status for " + chunkPos
                    + ": vanillaForcedThisChunk=" + vanillaForcedThisChunk
                    + ", forgeForcedThisChunk=" + forgeForcedThisChunk
                    + ", hasAnyForcedChunksInLevel=" + hasAnyForcedChunksInLevel);

            // Build a response message
            MutableComponent response = Component.literal("---------- Plot info for " + chunkPos + ": ----------\n")
                    .withStyle(ChatFormatting.YELLOW)
                    // .append(Component.literal("Owned by: " +
                    // chunkData.getNameForPlayer(serverLevel) + ", "))
                    .append(Component.literal("Village: " + villageData.getName() + "\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("Type: " + chunkData.getType() + "\n").withStyle(ChatFormatting.WHITE));
            // todo if village show village unclaimed...

            // TODO get counts of biomes also.
            if (!biomes.isEmpty()) {
                String biomeStr = biomes.entrySet().stream()
                        .map(e -> e.getKey() + " (" + e.getValue() + ")")
                        .collect(java.util.stream.Collectors.joining(", "));
                response.append(Component.literal("Biomes: " + biomeStr + "\n")
                        .withStyle(ChatFormatting.WHITE));
            } else {
                response.append(Component.literal("No biomes found.\n").withStyle(ChatFormatting.WHITE));
            }

            // If farm plot show all crops planted.
            if (chunkData.getType().equalsIgnoreCase("farm")) {
                // Scan a Y range around the player to reliably find crops.
                response.append(Component
                        .literal("Farm plot with crops planted: "
                                + getCropsPlanted(playerSource.blockPosition(), serverLevel) + "\n")
                        .withStyle(ChatFormatting.GREEN));
            }

            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Get all crops planted in the chunk at this position.
    // Scans Y-1 to Y+2 around the given position to reliably detect crops
    // regardless of exact player height.
    public static String getCropsPlanted(BlockPos blockPos, ServerLevel serverLevel) {
        LOGGER.info("DEBUGGER Crops planted at " + blockPos);

        Map<String, Integer> cropsCounts = new HashMap<>();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int baseY = blockPos.getY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.x * 16 + x;
                int worldZ = chunkPos.z * 16 + z;
                // Scan a small vertical range so we catch crops regardless of player Y offset.
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos blockPos2 = new BlockPos(worldX, baseY + dy, worldZ);
                    ItemStack itemStack = serverLevel.getBlockState(blockPos2).getBlock().asItem().getDefaultInstance();
                    if (itemStack.is(FUCTags.VANILLA_AND_MODDED_CROPS)) {
                        String cropName = itemStack.getDescriptionId();
                        cropName = cropName.replaceFirst("^[^.]+\\.[^.]+\\.", "").replaceAll("seeditem$", "");
                        cropsCounts.merge(cropName, 1, Integer::sum);
                    }
                }
            }
        }

        // Alphabetize hashmap and keep counts.
        StringBuilder cropsString = new StringBuilder();
        List<Map.Entry<String, Integer>> sortedCrops = new ArrayList<>(cropsCounts.entrySet());
        // Create a string from the sorted crops.
        sortedCrops.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, Integer> entry : sortedCrops) {
            String cropName = entry.getKey();
            int count = entry.getValue();
            if (cropsString.length() > 0) {
                cropsString.append(", ");
            }
            cropsString.append(cropName).append(" (").append(count).append(")");
        }

        // Return the crops string.
        if (cropsString.length() == 0) {
            return "No crops planted.";
        } else {
            return cropsString.toString();
        }
    }

    // Given current block position, get all biomes in the chunk at this y level
    // with counts.
    public static Map<String, Integer> getChunkBiomes(BlockPos blockPos, ServerLevel serverLevel) {
        ChunkPos chunkPos = new ChunkPos(blockPos);

        Map<String, Integer> biomeCounts = new LinkedHashMap<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int biomeX = chunkPos.x * 16 + x;
                int biomeZ = chunkPos.z * 16 + z;
                BlockPos blockPos2 = new BlockPos(biomeX, 100, biomeZ);
                Biome biome = serverLevel.getBiome(blockPos2).value();
                ResourceLocation biomeRes = serverLevel.registryAccess().registryOrThrow(Registries.BIOME)
                        .getKey(biome);
                String biomeName = biomeRes.toString().replaceAll("^[^:]+:", "");
                biomeCounts.merge(biomeName, 1, Integer::sum);
            }
        }

        return biomeCounts;
    }

    // Buy with an optional type, farm, village, etc.
    public static int buyPlot(CommandSourceStack source, String plotType) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            if (!VALID_PLOT_TYPES.contains(plotType)) {
                source.sendFailure(Component.literal("Invalid plot type. Must be plot, farm, or nursery."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(playerSource.blockPosition());
            DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();

            ChunkData chunk = chunkDataDatabase.getData(chunkPos.toLong());
            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            PlayerData player = playerDatabase.getData(playerSource.getUUID());

            // STEP 1: Check if it is in a village and not already bought.
            // TODO TEST
            if (chunk == null) {
                source.sendFailure(Component.literal("Plot is not in a village."));
                return 0;
            }
            // TODO TEST
            if (!Objects.equals(chunk.getType(), "village") && !Objects.equals(chunk.getType(), "plot")) {
                if (!playerSource.isCreative()) {
                    source.sendFailure(Component.literal("Plot has already been purchased."));
                    return 0;
                }
            }

            // STEP 2: Check if the player is in the village that matches the chunk.
            if (!chunk.getVillageId().equals(player.getHomeVillageUUID())) {
                source.sendFailure(Component.literal("Plot is not in your village."));
                return 0;
            }

            // STEP 2.5: Check if it is touching another plot (that isn't marked village).
            // TODO make optional rule maybe, for scout.
            // TODO make a method.
            // TODO make a method.
            // TODO make a method.
            // TODO make a method.
            // TODO make a method.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
            VillageData village = villageDataDB.getData(player.getHomeVillageUUID());
            // Get all four adjacent chunks.
            ChunkPos[] adjacentChunks = {
                    new ChunkPos(chunkPos.x + 1, chunkPos.z),
                    new ChunkPos(chunkPos.x - 1, chunkPos.z),
                    new ChunkPos(chunkPos.x, chunkPos.z + 1),
                    new ChunkPos(chunkPos.x, chunkPos.z - 1)
            };
            boolean passed = false;
            for (ChunkPos adjacentChunk : adjacentChunks) {
                ChunkData adjacentChunkData = chunkDataDatabase.getData(adjacentChunk.toLong());
                if (adjacentChunkData != null && !Objects.equals(adjacentChunkData.getType(), "village")) {
                    passed = true;
                }
            }
            // after village center, we shiould not need this!!
            if (!passed) {
                // Check if the chunk is touching the center plot.
                // compare chunk to village position
                ChunkPos villagePos = village.getPosition();
                if (chunkPos.x == villagePos.x + 1 && chunkPos.z == villagePos.z) {
                    passed = true;
                } else if (chunkPos.x == villagePos.x - 1 && chunkPos.z == villagePos.z) {
                    passed = true;
                } else if (chunkPos.x == villagePos.x && chunkPos.z == villagePos.z + 1) {
                    passed = true;
                } else if (chunkPos.x == villagePos.x && chunkPos.z == villagePos.z - 1) {
                    passed = true;
                }
            }
            if (!passed) {
                source.sendFailure(Component.literal("Plot is not touching another plot."));
                return 0;
            }

            // STEP 2.6: Enforce per-type limits: "plot" = unlimited, "house" = 2 per level,
            // all others = 1 per level. Creative players bypass.
            if (!playerSource.isCreative()) {
                if (!plotType.equalsIgnoreCase("plot")) {
                    int existing = countPlotType(village, plotType);
                    int limit = plotType.equalsIgnoreCase("house") ? village.getLevel() * 2 : village.getLevel();
                    if (existing >= limit) {
                        source.sendFailure(Component.literal(
                                "Village limit reached for type '" + plotType + "': "
                                + existing + "/" + limit + " (village level " + village.getLevel() + ")."));
                        return 0;
                    }
                }
            }
            int cost = calculatePlotCost(village, plotType);
            Level level = playerSource.level();
            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            if (!playerSource.isCreative() && !player.getWallet().hasEnough(bronzeCoin, cost)) {
                source.sendFailure(Component.literal("Player does not have enough money."));
                // Show cost and coins
                source.sendFailure(Component.literal("Cost: " + cost));
                source.sendFailure(Component.literal("Player Coins: " + player.getWallet().get(bronzeCoin)));
                return 0;
            }

            // STEP 4: Buy plot and mark to db, and village.
            chunk.setType(plotType);
            // For house plots, record the owner UUID so access can be restricted.
            if (plotType.equalsIgnoreCase("house")) {
                chunk.setOwnerUUID(playerSource.getUUID());
            }
            chunkDataDatabase.putData(chunkPos.toLong(), chunk);

            // Update structure claim flags for this chunk
            if (level instanceof ServerLevel serverLevel) {
                StructureCommand.updateStructuresInChunk(chunkPos, true, serverLevel);
            }

            // Extra farm step, force chunk to stay loaded!
            if (plotType.equalsIgnoreCase("farm")) {
                LOGGER.info("DEBUG1: Farming plot for " + chunkPos + ": " + village.getName());
                boolean chunkForced = ForgeChunkManager.forceChunk((ServerLevel) level, MODID, playerSource.getUUID(),
                        chunkPos.x, chunkPos.z, true, true);
                LOGGER.info("DEBUG1: forceChunk result for " + chunkPos + " = " + chunkForced);
                if (!chunkForced) {
                    source.sendFailure(Component.literal(
                            "Farm chunk ticket was not added (already added or rejected). Check server log for DEBUG1 forceChunk result."));
                }
            }
            LOGGER.info("Plot bought at " + playerSource.blockPosition().toShortString());

            // STEP 5: Subtract money out of player. TODO helper method hide this???
            if (!playerSource.isCreative())
                player.getWallet().remove(bronzeCoin, cost);
            playerDatabase.putData(playerSource.getUUID(), player);

            // Build a response message
            MutableComponent response = Component.literal("Plot bought at "
                    + playerSource.blockPosition().toShortString() + " as " + plotType + " for " + cost + " coins.");
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Add or remove a visitor on the current house plot. Only the plot owner can do
    // this.
    public static int setPlotVisitor(CommandSourceStack source, String visitorName, boolean add) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(playerSource.blockPosition());
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunk = chunkDb.getData(chunkPos.toLong());
            if (chunk == null) {
                source.sendFailure(Component.literal("You are not standing on a claimed plot."));
                return 0;
            }
            if (!chunk.getType().equalsIgnoreCase("house")) {
                source.sendFailure(Component.literal("You must be standing on a house plot to manage visitors."));
                return 0;
            }

            // Only the owner (or creative/admin) can manage the visitor list.
            if (!playerSource.isCreative()) {
                if (chunk.getOwnerUUID() == null || !chunk.getOwnerUUID().equals(playerSource.getUUID())) {
                    source.sendFailure(Component.literal("Only the plot owner can manage visitors."));
                    return 0;
                }
            }

            if (add) {
                chunk.addVisitor(visitorName);
                chunkDb.putData(chunkPos.toLong(), chunk);
                source.sendSuccess(() -> Component.literal(visitorName + " added as a visitor to this plot."), false);
            } else {
                chunk.removeVisitor(visitorName);
                chunkDb.putData(chunkPos.toLong(), chunk);
                source.sendSuccess(() -> Component.literal(visitorName + " removed from visitors of this plot."),
                        false);
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Delete a chunk sfrom DB right now, admin method.
    public static void deleteChunk(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDatabase.getData(chunkPos.toLong());
            if (chunkData == null) {
                source.sendFailure(Component.literal("Chunk at " + chunkPos + " is not owned."));
                return;
            }

            // TODO remove from DB.
            // TODO TEST
            chunkDatabase.removeDataAsync(chunkPos.toLong(), null);
            chunkDatabase.setDirty(); // TODO DOES THIS WORK.
            LOGGER.info("Plot deleted at " + chunkPos);

            // TODO REMOVE FROM VILLAGE LIST.

            // Build a response message
            MutableComponent response = Component.literal("Plot deleted at " + chunkPos);
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    public static void reclaimChunk(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            UUID villageId = player.getHomeVillageUUID();
            VillageData village = ModEvents.getVillageDatabase().getData(villageId);

            // Create Chunk.
            ChunkPos chunkPos = new ChunkPos(playerSource.blockPosition());
            DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();

            // Remove any previous chunk.
            chunkDatabase.removeDataAsync(chunkPos.toLong(), null);
            chunkDatabase.setDirty(); // TODO TEST

            // Add to village now.
            ChunkData chunk = new ChunkData("village", playerSource.getId(), villageId);
            chunkDatabase.putData(chunkPos.toLong(), chunk);
            village.addClaimedChunk(chunkPos);

            // Update structure claim flags for this chunk
            if (playerSource.level() instanceof ServerLevel serverLevel) {
                StructureCommand.updateStructuresInChunk(chunkPos, true, serverLevel);
            }

            // Build a response message
            MutableComponent response = Component.literal("Chunk added at " + chunkPos);
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    /** Counts how many chunks in the village already have the given plot type. */
    public static int countPlotType(VillageData villageData, String plotType) {
        int count = 0;
        for (ChunkPos pos : villageData.getClaimedChunks()) {
            ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
            if (chunkData != null && chunkData.getType().equalsIgnoreCase(plotType)) {
                count++;
            }
        }
        return count;
    }

    public static int calculatePlotCost(VillageData villageData, String plotType) {
        // TODO get from village Object.
        // TODO MAKE METHOD
        // Loop over all plots and count them, and farms.
        int plotCnt = 0;
        for (ChunkPos pos : villageData.getClaimedChunks()) {
            ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
            if (chunkData != null) {
                if (!chunkData.getType().equalsIgnoreCase("village")) {
                    plotCnt++;
                }
            }
        }

        int baseCost = 100;

        // Plot Cost: 100 + 100 * plots TODO test
        // Plot Cost: 100 + 30 * plots TODO testing lower cost.
        // Lowering from 30 to 25 Cost at our level was 730 a plot
        // 730 / 30 = 24 plots
        int totalCost = baseCost + 25 * plotCnt;
        // make farm and some cost extra,

        return totalCost;
    }

    // Check if this exact chunk has a Forge ticket (block or entity, ticking or
    // non-ticking).
    private static boolean isForgeForcedChunk(ServerLevel level, ChunkPos chunkPos) {
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
        if (data == null) {
            return false;
        }

        long chunkLong = chunkPos.toLong();

        for (var chunks : data.getBlockForcedChunks().getChunks().values()) {
            if (chunks.contains(chunkLong)) {
                return true;
            }
        }
        for (var chunks : data.getBlockForcedChunks().getTickingChunks().values()) {
            if (chunks.contains(chunkLong)) {
                return true;
            }
        }
        for (var chunks : data.getEntityForcedChunks().getChunks().values()) {
            if (chunks.contains(chunkLong)) {
                return true;
            }
        }
        for (var chunks : data.getEntityForcedChunks().getTickingChunks().values()) {
            if (chunks.contains(chunkLong)) {
                return true;
            }
        }

        return false;
    }
}
