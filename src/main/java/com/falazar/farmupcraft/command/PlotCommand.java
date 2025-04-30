package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.*;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

public class PlotCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlotCommand.class.getSimpleName());
    private static final List<String> VALID_PLOT_TYPES = Arrays.asList("plot", "farm", "nursery", "kitchen", "restaurant", "house");

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
                            builder2.suggest("restaurant");
                            builder2.suggest("house");
                            return builder2.buildFuture();
                        })
                        .executes(context -> {
                            String plotType = StringArgumentType.getString(context, "type");
                            if (VALID_PLOT_TYPES.contains(plotType)) {
                                return buyPlot(context.getSource(), plotType);
                            } else {
                                context.getSource().sendFailure(Component.literal("Invalid plot type. Must be plot, farm, nursery, kitchen, restaurant, house."));
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
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(deleteBuilder);


        // Define ADMIN setvillage chunk to reclaim a single chunk.
        LiteralArgumentBuilder <CommandSourceStack> reclaimBuilder = Commands.literal("reclaim")
                .executes(context -> {
                    reclaimChunk(context.getSource());
                    return 0;
                })
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(reclaimBuilder);


        // Register the main "plot" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showPlotInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

//            Level level = summoner.level();

            ChunkPos chunkPos = new ChunkPos(summoner.blockPosition());
            DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = dataBase.getData(chunkPos.toLong());
            if (chunkData == null) {
                context.getSource().sendFailure(Component.literal("Plot at " + chunkPos + " is not owned."));
                // todo show closest village still though.
                return 0;
            }

            // Pull out plot info and owner and village.
            ServerLevel serverLevel = context.getSource().getLevel();

            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(serverLevel);
            VillageData villageData = villageDataDB.getData(chunkData.getVillageId());

            // BUG here maybe. update playerid to uuid string.
            LOGGER.info("Plot info for " + chunkPos + ": player id = " + chunkData.getPlayerId()
                    + ", village id = " + chunkData.getVillageId() + ", type = " + chunkData.getType());
            LOGGER.info("Player name: " + chunkData.getNameForPlayer(serverLevel));

            // Build a response message
            MutableComponent response = Component.literal("Plot info for " + chunkPos + ": ");
            response = response.append(Component.literal("Owned by: " + chunkData.getNameForPlayer(serverLevel) + ", "));
            response = response.append(Component.literal("Village: " + villageData.getName() + ", "));
            response = response.append(Component.literal("Type: " + chunkData.getType()));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
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
                source.sendFailure(Component.literal("Plot has already been purchased."));
                return 0;
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
            // TODO OR TOUCHING CENTER PLOT!!!
            // TODO test
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


            // STEP 3: Calc cost to buy plot and check players total.
            int cost = calculatePlotCost(village, plotType);
            Level level = playerSource.level();
            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            if (!player.getWallet().hasEnough(bronzeCoin, cost)) {
                source.sendFailure(Component.literal("Player does not have enough money."));
                // Show cost and coins
                source.sendFailure(Component.literal("Cost: " + cost));
                source.sendFailure(Component.literal("Player Coins: " + player.getWallet().get(bronzeCoin)));
                return 0;
            }

            // STEP 4: Buy plot and mark to db, and village.
            chunk.setType(plotType);
            chunkDataDatabase.putData(chunkPos.toLong(), chunk);

            // Extra farm step, force chunk to stay loaded!
            if (plotType.equalsIgnoreCase("farm")) {
                LOGGER.info("DEBUG1: Farming plot for " + chunkPos + ": " + village.getName());
                ForgeChunkManager.forceChunk((ServerLevel) level, MODID, playerSource.getUUID(), chunkPos.x, chunkPos.z, true, true);
            }
            LOGGER.info("Plot bought at " + playerSource.blockPosition().toShortString());

            // STEP 5: Subtract money out of player.  TODO helper method hide this???
            player.getWallet().remove(bronzeCoin, cost);
            playerDatabase.putData(playerSource.getUUID(), player);

            // Build a response message
            MutableComponent response = Component.literal("Plot bought at " + playerSource.blockPosition().toShortString() + " as " + plotType + " for " + cost + " coins.");
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
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

            // Add to village now.
            ChunkData chunk = new ChunkData("village", playerSource.getId(), villageId);
            chunkDatabase.putData(chunkPos.toLong(), chunk);
            village.addClaimedChunk(chunkPos);

            // Build a response message
            MutableComponent response = Component.literal("Chunk added at " + chunkPos);
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
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
        int totalCost = baseCost + 30 * plotCnt;

        return totalCost;
    }
}
