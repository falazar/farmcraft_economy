package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.CurrencyCost;
import com.falazar.farmupcraft.currency.Wallet;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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

        // Define the "plots" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(PlotCommand::showPlotInfo);

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


        // Define the "delete" sub-command - For ADMIN only!
        LiteralArgumentBuilder<CommandSourceStack> deleteBuilder = Commands.literal("delete")
                .executes(context -> {
                    deletePlot(context.getSource());
                    return 0;
                })
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(deleteBuilder);

//        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
//                .executes(c -> buyPlot(c))
//                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Add the sub-commands to the "plot" command
        builder.then(infoBuilder);
        builder.then(buyBuilder);

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
            ;
            ChunkData chunk = chunkDataDatabase.getData(chunkPos.toLong());
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(playerSource.getUUID());

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
            // TODO TEST
            if (!chunk.getVillageId().equals(playerData.getHomeVillageUUID())) {
                source.sendFailure(Component.literal("Plot is not in your village."));
                return 0;
            }
            // TODO BUG can buy plots OUTSIDE of village.

            // STEP 2.5: Check if it is touching another plot (that isn't marked village).
            // TODO make a method.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
            VillageData village = villageDataDB.getData(playerData.getHomeVillageUUID());
            // TODO make optional rule maybe, for scout.
            // Get all four adjacent chunks.
            // TODO TEST
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
            int cost = calculatePlotCost(playerData, village, plotType);
            Level level = playerSource.level();
            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            if (!playerData.getWallet().hasEnough(bronzeCoin, cost)) {
                source.sendFailure(Component.literal("Player does not have enough money."));
                // Show cost and coins
                source.sendFailure(Component.literal("Cost: " + cost));
                source.sendFailure(Component.literal("Player Coins: " + playerData.getWallet().get(bronzeCoin)));
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
            LOGGER.info("Plot bought at " + chunkPos);

            // STEP 5: Subtract money out of player.
            playerData.getWallet().remove(bronzeCoin, cost);

            // Build a response message
            MutableComponent response = Component.literal("Plot bought at " + chunkPos + " as " + plotType);
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Delete a plot from DB right now, admin method.
    public static void deletePlot(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (player == null) {
                source.sendFailure(Component.literal("Player not found."));
                return;
            }

//            Level level = player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
            ChunkData data = dataBase.getData(chunkPos.toLong());
            if (data == null) {
                source.sendFailure(Component.literal("Plot at " + chunkPos + " is not owned."));
                return;
            }

            // TODO remove from DB.
//            dataBase.removeData(chunkPos);
            LOGGER.info("Plot deleted at " + chunkPos);

            // TODO MAYBE REMOVE FROM VILLAGE LIST.

            // Build a response message
            MutableComponent response = Component.literal("Plot deleted at " + chunkPos);
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    private static int calculatePlotCost(PlayerData playerData, VillageData villageData, String plotType) {
        // TODO implement cost calculation logic here.
        int baseCost = 100;
        // 100 + 100 for each plot.... whatevers.

        // TODO count plots existing scouter

        int plotCount = 1; // TODO get from village Object.
        int totalCost = baseCost + (plotCount - 1) * 100;

        LOGGER.info("DEBUG: Plot cost for " + plotType + ": " + totalCost + " plotCount = " + plotCount);
        return totalCost;
    }
}
