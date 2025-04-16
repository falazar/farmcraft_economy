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

        // Define the delete sub-command. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> deleteBuilder = Commands.literal("delete")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return deleteVillage(context.getSource(), villageName);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(deleteBuilder);

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
            DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
            ;
            ChunkData data = dataBase.getData(chunkPos.toLong());
            // TODO can we hide all this inside???
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(player.getUUID());
            if (playerData == null) {
                source.sendFailure(Component.literal("Player data not found."));
                return 0;
            }
            LOGGER.info("DEBUG TODO PlayerData: " + playerData.getId() + ", " + playerData.getHomeVillageUUID());

//            Wallet wallet =  playerData.getWallet();
//            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
//            Coin coin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
//            CurrencyCost currencyCost = new CurrencyCost(coin, 10);


            // Step 1: Check if chunk is owned. (Inside another village)
            // TEMP REMOVE FOR TESTING.  TODO add in, make method.
//            if (data != null) {
//                source.sendFailure(Component.literal("Chunk is already owned."));
//                return 0;
//            }

            // Step 2: Check if too close to nearest village
            VillageData closestVillage = getClosestVillage(player.blockPosition());
            if (closestVillage != null) {
                BlockPos closestVillagePos = closestVillage.getPosition().getWorldPosition();
                int distance = Math.abs(closestVillagePos.getX() - player.blockPosition().getX()) +
                        Math.abs(closestVillagePos.getZ() - player.blockPosition().getZ());
                if (distance < 100) { // TODO make this a config value.
                    source.sendFailure(Component.literal("Too close to another village: " + closestVillage.getName() + " distance = " + distance));
                    return 0;
                }
            }

            // Step 3: Check if village name is unique.
            if (!isVillageNameUnique(villageName)) {
                source.sendFailure(Component.literal("Village name " + villageName + " is not unique, please choose another."));
                return 0;
            }


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
            UUID homeVillageId = playerData.getHomeVillageUUID();
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            if (homeVillageId != null) {
                VillageData homeVillage = villageDatabase.getData(homeVillageId);
                if (homeVillage != null) {
                    source.sendFailure(Component.literal("Player is already in a village: " + homeVillage.getName()));
                    return 0;
                }
            }


            // Step 7: Generate all chunk positions in the radius
            int radius = 5;
            ChunkPos centerChunk = new ChunkPos(player.blockPosition());
            List<ChunkPos> villageChunks = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    villageChunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
                }
            }
            // TODO add chunk count somewhere?

            // Step 8: Create Village object and save it
            UUID villageId = UUID.randomUUID();
            VillageData villageData = new VillageData(villageId, villageName, player.chunkPosition(), 1, villageChunks, true);
            villageDatabase.putData(villageId, villageData);
            LOGGER.info("Village " + villageName + " created with id " + villageId +
                    " saved with " + villageChunks.size() + " chunks around " + player.blockPosition());

            // STEP 9: Buy plot and mark to db.
            // TODO1 this doesnt buy the plot does it?
            // TODO1 THESE villageId TO USE UUIDS
//            ChunkData newPlot = new ChunkData("village", player.getId(), villageId); // hack test.
            for(ChunkPos pos : villageChunks) {
                ChunkData newPlot = new ChunkData("village", player.getId(), villageId); // hack test.
                dataBase.putData(pos.toLong(), newPlot);
            }

            LOGGER.info("Plot bought at " + chunkPos);
            // TODO1 call a set plot method, separate this out.
            // TODO1 add plot to city.


            // STEP 10: Add player to village list.
            // TODO: Implement

            // STEP 11: Add village to player.
            playerData.setHomeVillageId(villageId);

            //We have to put it back in there otherwise it wont sync to client
            playerDataDataBase.putData(player.getUUID(), playerData);

            // STEP 12: Build a response message and send.
            MutableComponent response = Component.literal("Village bought at " + chunkPos);
            response = response.append(Component.literal(" and created with name " + villageName));
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

            DataBase<UUID,PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(summoner.getUUID());
            UUID villageId = playerData.getHomeVillageUUID();
            DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
            VillageData villageData = dataBase.getData(villageId);
            if (villageData == null) {
                // TODO
                context.getSource().sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Pull out plot info and owner and village.
            LOGGER.info("DEBUG TODO Village info for: player id = xxx ");
            //+ ", village id = " + data.getVillageId() + ", type = " + data.getType());
//            LOGGER.info("Player name: " + data.getNameForPlayer(serverLevel));

            // Build a response message
            MutableComponent response = Component.literal("Village info for VILLAGE NAME: " + villageData.getName()
                    + " at " + villageData.getClaimedChunks().stream().findFirst().toString() + " with id = " + villageData.getUUID().toString());
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
            DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
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

    public static VillageData findVillageByName(String villageName) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // Loop and find the village.
        for (VillageData data : dataList) {
            if (data.getName().equalsIgnoreCase(villageName)) {
                return data;
            }
        }
        return null;
    }

    // Delete a village from db.
    public static int deleteVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (player == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
//            Level level = player.level();

            DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
            VillageData villageData = findVillageByName(villageName);
            if (villageData == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // TODO remove from player datas.
            // TODO remove chunk data.

//            villageData.delete(); todo scout.

            // Build a response message
            MutableComponent response = Component.literal("Village deleted: " + villageData.getName());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // TODO MOVE these over to a manager.
    // Get closest village to location.
    public static VillageData getClosestVillage(BlockPos pos) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // Loop and find the closest village.
        VillageData closestVillage = null;
        int closestDistance = Integer.MAX_VALUE;
        for (VillageData data : dataList) {
            closestVillage = data;
            // Use abs manhattan distance formula
            int distance = Math.abs(data.getPosition().x - pos.getX()) +
                    Math.abs(data.getPosition().z - pos.getZ());
            if (distance < closestDistance) {
                closestDistance = distance;
            }
        }

        return closestVillage;
    }

    // Check if village name is unique.


    //todo maybe check UUIDS instead of village names
    public static boolean isVillageNameUnique(String villageName) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return true;
        }

        // Loop and check all village names.
        for (VillageData data : dataList) {
            if (data.getName().equalsIgnoreCase(villageName)) {
                return false;
            }
        }
        return true;
    }
}
