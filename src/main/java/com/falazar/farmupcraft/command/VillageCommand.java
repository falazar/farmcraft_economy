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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class VillageCommand {
    public static final CustomLogger LOGGER = new CustomLogger(VillageCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command for "village"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("village");

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(VillageCommand::showVillageInfo);
        builder.then(infoBuilder);

        // Define the list subcommand:
        LiteralArgumentBuilder<CommandSourceStack> listBuilder = Commands.literal("list")
                .executes(VillageCommand::listVillages);
        builder.then(listBuilder);

        // Define the "buy" sub-command
        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return buyVillage(context.getSource(), villageName);
                        }));
        builder.then(buyBuilder);

        // TODO Add subcommand for nearest village
        // /village info nearest

        // TODO And per village name:
        // /village info <village name>

        // Register the main "village" command with the dispatcher
        pDispatcher.register(builder);
    }

    // Given a player command, villageName and their location, buy the village and mark chunks as owned.
    public static int buyVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullablePlayer = source.getEntity();
            Player player = nullablePlayer instanceof Player ? (Player) nullablePlayer : null;
            if (player == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            if (villageName == null || villageName.isEmpty()) {
                source.sendFailure(Component.literal("Village name is required for village plot type."));
                return 0;
            }

            Level level = player.level();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            DataBase<ChunkPos, ChunkData> dataBase = ModEvents.getChunkDataDatabase();;
            ChunkData data = dataBase.getData(chunkPos);
            DataBase<Integer, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            // TODO can we hide all this inside???
            PlayerData playerData = playerDataDataBase.getData(1);  // is this the id???
            if (playerData == null) {
                source.sendFailure(Component.literal("Player data not found."));
                return 0;
            }
            LOGGER.info("DEBUG TODO PlayerData: " + playerData.getId() + ", " + playerData.getHomeVillageId());

//            Wallet wallet =  playerData.getWallet();
//            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
//            Coin coin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
//            CurrencyCost currencyCost = new CurrencyCost(coin, 10);



            // Step 1: Check if chunk is owned. (Inside another village)
            // TEMP REMOVE FOR TESTING.  TODO add in, make method.
//            if (data != null) {
//                source.sendFailure(Component.literal("Chunnk is already owned."));
//                return 0;
//            }

            // Step 2: TODO Check if too close to nearest village
            // TODO make method.

            // Step 3: TODO Check if village name is unique.
            DataBase<String, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            //if (villageDatabase.containsKey(villageId)) {
            //    LOGGER.warn("Village ID already exists: " + villageId);
            //    return -1;
            //}


            // STEP 4: Calc cost to buy village.
//            int cost = calculateVillageCost(village, player, plotType);
//            if (player.checkPlayerMoney(cost)) {
//                context.getSource().sendFailure(Component.literal("Player does not have enough money."));
//                return 0;
//            }
            int cost = 500; // TODO remove this and use calc cost.
//            if (currencyCost.canAfford(wallet)) {
//                do something
//            }

            // STEP 5: TODO Subtract money out of player.
//            player.subtractMoney(cost);


            // Step 6: TODO Check if player is in another village right now.


            // Step 7: Generate all chunk positions in the radius
            int radius = 5;
            ChunkPos centerChunk = new ChunkPos(player.blockPosition());
            List<ChunkPos> villageChunks = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    villageChunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
                }
            }

            // Step 8: Create Village object and save it
            String villageId = UUID.randomUUID().toString();
            VillageData villageData = new VillageData(villageId, villageName, player.chunkPosition(), 1, villageChunks, true);
            villageDatabase.putData(villageId, villageData);
            LOGGER.info("Village " + villageName + " created with id " + villageId +
                    " saved with " + villageChunks.size() + " chunks around " +  player.blockPosition());

            // STEP 9: Buy plot and mark to db.
            // TODO1 this doesnt buy the plot does it?
            // TODO1 THESE villageId TO USE UUIDS
//            ChunkData newPlot = new ChunkData("village", player.getId(), villageId); // hack test.
            ChunkData newPlot = new ChunkData("village", player.getId(), 1234); // hack test.
            dataBase.putData(chunkPos, newPlot);
            LOGGER.info("Plot bought at " + chunkPos);
            // TODO1 call a set plot method, separate this out.
            // TODO1 add plot to city.


            // STEP 10: Add player to village.
            // TODO: Implement


            // STEP 11: Add village to player.
//            playerData.setHomeVillageId(villageId);


            // STEP 12: Build a response message and send.
            MutableComponent response = Component.literal("Village bought at " + chunkPos);
            response = response.append(Component.literal(" and created village " + villageName));
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Show the village that current player is in.
    public static int showVillageInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            Level level = summoner.level();

            // TODO check player for their village.

            // TODO MAKE METHOD.
            // TODO load village from db.
            String villageId = "TEST12345"; // TODO get from player data.

            DataBase<String, VillageData> dataBase = ModEvents.getVillageDatabase();;
            VillageData villageData = dataBase.getData(villageId);
            if (villageData == null) {
                // TODO
                context.getSource().sendFailure(Component.literal("No village data found."));
                // todo show closest village still though.
                return 0;
            }

            // Pull out plot info and owner and village.
            LOGGER.info("DEBUG TODO Village info for: player id = xxx ");
            //+ ", village id = " + data.getVillageId() + ", type = " + data.getType());
//            LOGGER.info("Player name: " + data.getNameForPlayer(serverLevel));

            // Build a response message
            MutableComponent response = Component.literal("Village info for VILLAGE NAME: " + villageData.getName()
                    + " at " + villageData.getClaimedChunks() + " with id = " + villageData.getId());
//            response = response.append(Component.literal("Owned by: " + data.getNameForPlayer(serverLevel) + ", "));
//            response = response.append(Component.literal("Village: " + data.getVillageId() + ", "));
//            response = response.append(Component.literal("Type: " + data.getType()));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // List all villages in the world.
    public static int listVillages(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }
            Level level = summoner.level();

            // TODO load all village from db.
            // TODO MAKE METHOD.
            DataBase<String, VillageData> dataBase = ModEvents.getVillageDatabase();
            Collection<VillageData> dataList = dataBase.getValues();
            if (dataList == null || dataList.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No villages data found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Village list: ");
            int index = 1;
            for (VillageData data : dataList) {
                response = response.append(Component.literal(index++ + ". " + data.getName() +
                        " at " + data.getClaimedChunks().stream().findFirst().toString() + ", \n"));
            }
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }
}
