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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlotCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlotCommand.class.getSimpleName());
    private static final List<String> VALID_PLOT_TYPES = Arrays.asList("plot", "farm", "nursery");

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
                            return builder2.buildFuture();
                        })
                        .executes(context -> {
                            String plotType = StringArgumentType.getString(context, "type");
                            if ("farm".equals(plotType) || "plot".equals(plotType)) {
                                return buyPlot(context.getSource(), plotType);
                            } else {
                                context.getSource().sendFailure(Component.literal("Invalid plot type. Must be 'farm' or 'plot'."));
                                return 0;
                            }
                        }));


        // Define the "delete" sub-command - For admins only!
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

            Level level = summoner.level();

            ChunkPos chunkPos = new ChunkPos(summoner.blockPosition());
            DataBase<ChunkPos, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
            ChunkData data = dataBase.getData(chunkPos);
            if (data == null) {
                context.getSource().sendFailure(Component.literal("Plot at " + chunkPos + " is not owned."));
                // todo show closest village still though.
                return 0;
            }

            // Pull out plot info and owner and village.
            ServerLevel serverLevel = context.getSource().getLevel();

            // BUG here maybe. update playerid to uuid string.
            LOGGER.info("Plot info for " + chunkPos + ": player id = " + data.getPlayerId() + ", village id = " + data.getVillageId() + ", type = " + data.getType());
            LOGGER.info("Player name: " + data.getNameForPlayer(serverLevel));

            // Build a response message
            MutableComponent response = Component.literal("Plot info for " + chunkPos + ": ");
            response = response.append(Component.literal("Owned by: " + data.getNameForPlayer(serverLevel) + ", "));
            response = response.append(Component.literal("Village: " + data.getVillageId() + ", "));
            response = response.append(Component.literal("Type: " + data.getType()));
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
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            if (!VALID_PLOT_TYPES.contains(plotType)) {
                source.sendFailure(Component.literal("Invalid plot type. Must be plot, farm, or nursery."));
                return 0;
            }

            Level level = summoner.level();
            ChunkPos chunkPos = new ChunkPos(summoner.blockPosition());
            DataBase<ChunkPos, ChunkData> dataBase = ModEvents.getChunkDataDatabase();;
            ChunkData data = dataBase.getData(chunkPos);

            DataBase<Integer, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(1);
//            Wallet wallet =  playerData.getWallet();
//            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
//            Coin coin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
//            CurrencyCost currencyCost = new CurrencyCost(coin, 10);

//             if (currencyCost.canAfford(wallet)) {
//                 //do something
//             }

            // TEMP REMOVE FOR TESTING.
//            if (data != null) {
//                source.sendFailure(Component.literal("Plot is already owned."));
//                return 0;
//            }

            // TODO implement plot buying logic here.
            // Step 1: Check who owns, if already owned, just show info.

            // TODO get player village id
            String village = "testobj";

            // STEP 3: Calc cost to buy plot.
            String player = "testobj";  // TODO not needed?
            int cost = calculatePlotCost(village, player, plotType);
//            if (player.checkPlayerMoney(cost)) {
//                context.getSource().sendFailure(Component.literal("Player does not have enough money."));
//                return 0;
//            }

            // STEP 4: Buy plot and mark to db.
//            data.setPlayerId(summoner.getId());
//            data.setType("farm"); // TODO set to type.
            // TODO get village id.
            ChunkData newPlot = new ChunkData(plotType, summoner.getId(), 1234); // hack test.
            dataBase.putData(chunkPos, newPlot);
            LOGGER.info("Plot bought at " + chunkPos);

            // STEP 5: Subtract money out of player.
//            player.subtractMoney(cost);

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


    private static int calculatePlotCost(String village, String player, String plotType) {
        // TODO implement cost calculation logic here.
        int baseCost = 100;
        // 100 + 100 for each plot.... whatevers.

        int plotCount = 1; // TODO get from village Object.
        int totalCost = baseCost + (plotCount-1) * 100;

        return totalCost;
    }
}
