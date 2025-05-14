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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.text.NumberFormat;
import java.util.*;

import static com.falazar.farmupcraft.command.PlotCommand.calculatePlotCost;

public class VillageCommand {
    public static final CustomLogger LOGGER = new CustomLogger(VillageCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command for "village"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("village");

        // Define the "info" sub-commands
        // If user doesnt add a name, then show their village, else show the named village.
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(context -> {
                    // Default behavior when no villageName is provided
                    return showVillageInfo(context.getSource(), null);
                })
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            // Behavior when villageName is provided
                            return showVillageInfo(context.getSource(), villageName);
                        }));
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

        // Define the "rename" sub-command
        LiteralArgumentBuilder<CommandSourceStack> renameBuilder = Commands.literal("rename")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return renameVillage(context.getSource(), villageName);
                        }));
        builder.then(renameBuilder);

        // Define the delete sub-command. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> deleteBuilder = Commands.literal("delete")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return deleteVillage(context.getSource(), villageName);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(deleteBuilder);

        // Define the "levelup" sub-command for players current village.
        LiteralArgumentBuilder<CommandSourceStack> levelUpBuilder = Commands.literal("levelup")
                .executes(context -> {
                    return levelUpVillage(context.getSource());
                });
        builder.then(levelUpBuilder);

        // Define the "setlevel" sub-command for players current village. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> setLevelBuilder = Commands.literal("setlevel")
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(context -> {
                            Integer level = IntegerArgumentType.getInteger(context, "level");
                            return setVillageLevel(context.getSource(), level);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(setLevelBuilder);

        // Define the "rundailyupkeep" sub-command for players current village. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> runDailyUpkeepBuilder = Commands.literal("rundailyupkeep")
                .executes(context -> {
                    return runVillageDailyUpkeep(context.getSource());
                })
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(runDailyUpkeepBuilder);

        // Define the "biomes" sub-command
        LiteralArgumentBuilder<CommandSourceStack> villageBiomesBuilder = Commands.literal("biomes")
                .executes(context -> {
                    return showVillageBiomes(context.getSource());
                });
        builder.then(villageBiomesBuilder);


        // Register the main "village" command with the dispatcher
        pDispatcher.register(builder);
    }

    // Given a player command, villageName and their location, buy the village and mark chunks as owned.
    public static int buyVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullablePlayer = source.getEntity();
            Player player = nullablePlayer instanceof Player ? (Player) nullablePlayer : null;
            if (villageName == null || villageName.isEmpty()) {
                source.sendFailure(Component.literal("Village name is required for village plot type."));
                return 0;
            }

//            Level level = player.level();

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDataDatabase.getData(chunkPos.toLong());
            // TODO can we hide all this inside???
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(player.getUUID());
            LOGGER.info("DEBUG TODO PlayerData: " + playerData.getId() + ", " + playerData.getHomeVillageUUID());

            // Step 1: Check if chunk is owned. (Inside another village)
            // TODO add in, make method.
            if (chunkData != null) {
                source.sendFailure(Component.literal("Chunk is already owned."));
                return 0;
            }

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
                //  VillageData homeVillage = villageDatabase.getData(homeVillageId);
                //  if (homeVillage != null) {
                //      source.sendFailure(Component.literal("Player is already in a village: " + homeVillage.getName()));
                //      return 0;
                //  }
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
            VillageData villageData = new VillageData(villageId, villageName, player.chunkPosition(), 1, villageChunks, true, 0);
            villageDatabase.putData(villageId, villageData);
            LOGGER.info("Village " + villageName + " created with id " + villageId +
                    " saved with " + villageChunks.size() + " chunks around " + player.blockPosition());

            // STEP 9: Buy plot and mark to db.
            // TODO1 this doesnt buy the plot does it?
            // TODO1 THESE villageId TO USE UUIDS

            // Mark chunks to village.
            for (ChunkPos pos : villageChunks) {
                ChunkData chunk = new ChunkData("village", player.getId(), villageId);
                chunkDataDatabase.putData(pos.toLong(), chunk);
            }

            LOGGER.info("Plot bought at " + chunkPos);
            // TODO1 call a set plot method, separate this out.
            // TODO1 add plot to city.


            // STEP 10: Add player to village list.
            // TODO: Implement

            // STEP 11: Add village to player.
            playerData.setHomeVillageId(villageId);

            // We have to put it back in there otherwise it wont sync to client
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
    public static int showVillageInfo(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // TODO MAKE HELPER METHOD.
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(playerSource.getUUID());
            VillageData village = null;
            if (villageName == null) {
                UUID villageId = playerData.getHomeVillageUUID();
                DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
                village = dataBase.getData(villageId);
            } else {
                village = findVillageByName(villageName);
            }
            if (village == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Build a response message
            NumberFormat numberFormat = NumberFormat.getInstance();
            MutableComponent response = Component.literal("")
                    .append(Component.literal("---------- Village Name: " + village.getName() + " ----------\n").withStyle(ChatFormatting.YELLOW)) // Yellow
                    .append(Component.literal("Level: " + village.getLevel() + " \n")) // White
                    .append(Component.literal("Coins: " + numberFormat.format(village.getCoins()) + " \n")
                            .withStyle(village.getCoins() < 0 ? ChatFormatting.RED : ChatFormatting.WHITE)) // Red if negative, white otherwise
                    .append(Component.literal(" at " + village.getPosition().getWorldPosition().toShortString() + " \n")) // White
                    .append(Component.literal(" with claimed chunks = " + village.getClaimedChunks().size() + "\n")); // White
            //            response = response.append(Component.literal("Created by: " + data.getNameForPlayer(serverLevel) + ", "));

            // Loop over all plots and count them, and farms.
            int plotCnt = getPlotCount(village);
            int farmCnt = 0;
            for (ChunkPos pos : village.getClaimedChunks()) {
                ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
                if (chunkData != null) {
                    if (chunkData.getType().equalsIgnoreCase("farm")) {
                        farmCnt++;
                    }
                }
            }
            response = response.append(Component.literal(" with " + plotCnt + " plots and " + farmCnt + " farms. \n"));

            // Plot Cost: 100 + 30 * plots TODO testing
            int plotCost = PlotCommand.calculatePlotCost(village, "plot");
            response = response.append(Component.literal(" Plot cost: " + numberFormat.format(plotCost) + " coins. \n"));

            // TODO MAKE METHOD
            // Daily Cost: villageLevel * 100 + 50 per plot? TODO test lowered 50>30
//            int dailyCost = village.getLevel() * 100 + plotCnt * 30;
            int dailyCost = getDailyCost(village);
            response = response.append(Component.literal(" Daily cost: " + numberFormat.format(dailyCost) + " coins. \n"));

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception  in show village info - see logs"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int getDailyCost(VillageData village) {
        int dailyCost = village.getLevel() * 100 + getPlotCount(village) * 30;

        return dailyCost;
    }

    public static int getPlotCount(VillageData village) {
        int plotCnt = 0;
        for (ChunkPos pos : village.getClaimedChunks()) {
            ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
            if (chunkData != null) {
                if (!chunkData.getType().equalsIgnoreCase("village")) {
                    plotCnt++;
                }
            }
        }

        return plotCnt;
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
//            Level level = summoner.level();

            // TODO load all village from db.
            // TODO MAKE METHOD.
            DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
            Collection<VillageData> villageList = dataBase.getValues();
            if (villageList == null || villageList.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No villages data found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Village list: \n");
            int index = 1;
            // What order here?  TODO Make alpha.
            for (VillageData village : villageList) {
                response = response.append(Component.literal(index++
                        + ". " + village.getName() +
                        " Level " + village.getLevel() +
                        " at " + village.getPosition().getWorldPosition().toShortString()
                        + " with " + village.getClaimedChunks().size() + " chunks, \n"));
                // TODO1 bug size is not getting right here, or claim got too many.
                LOGGER.info("DEBUG TODO Village info for: village = " + village.getName()
                        + ", chunks = " + village.getClaimedChunks().stream().count()
                        + " claimedChunkSet = " + village.getClaimedChunkSet().size()
                );
            }

            // TODO1 claimed chunks is wayyyyyy too large.
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

    public static int deleteVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = findVillageByName(villageName);
            if (village == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // TODO remove from player data.
            // TODO NEED player data to get uuid dont have, save to db!!!
            // Loop over all players, if home village is this one, remove it.
//            DataBase<UUID, PlayerData> playerDataDatabase = ModEvents.getPlayerDatabase();
//            Collection<PlayerData> playerDataList = playerDataDatabase.getValues();
//            if (playerDataList != null && !playerDataList.isEmpty()) {
//                for (PlayerData playerData : playerDataList) {
//                    if (playerData.getHomeVillageUUID() != null && playerData.getHomeVillageUUID().equals(villageData.getUUID())) {
//                        playerData.setHomeVillageId(null);
//                        playerDataDatabase.putData(player.getUUID(), playerData);
//                    }
//                }
//            }


            // TODO remove chunks data. test
            DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();
            for (ChunkPos chunkPos : village.getClaimedChunks()) {
                ChunkData chunkData = chunkDataDatabase.getData(chunkPos.toLong());
                if (chunkData != null) {
                    chunkDataDatabase.removeDataAsync(chunkPos.toLong(), null);
                }
            }
            // TODO test once more.

            villageDatabase.removeDataAsync(village.getUUID(), null);
            villageDatabase.setDirty();


            // Build a response message
            MutableComponent response = Component.literal("Village deleted: " + village.getName());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Rename a village.
    public static int renameVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            if (villageName == null || villageName.isEmpty()) {
                source.sendFailure(Component.literal("Village name is required for village plot type."));
                return 0;
            }

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData villageData = villageDatabase.getData(player.getHomeVillageUUID());
            if (villageData == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Check if name is unique.
            if (!isVillageNameUnique(villageName)) {
                source.sendFailure(Component.literal("Village name " + villageName + " is not unique, please choose another."));
                return 0;
            }

            // Update name in db.
            villageData.setName(villageName);
            villageDatabase.putData(villageData.getUUID(), villageData);
            // TODO TEST

            // Build a response message
            MutableComponent response = Component.literal("Village renamed to: " + villageData.getName());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Sets the village level - NO CHUNK CHANGES!
    public static int setVillageLevel(CommandSourceStack source, Integer level) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());

            // Update level in db.
            village.setLevel(level);
            villageDatabase.putData(village.getUUID(), village);

            // Build a response message
            MutableComponent response = Component.literal("Village level set to: " + village.getLevel());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Level up a village.
    public static int levelUpVillage(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());

            int currLevel = village.getLevel();

            // STEP 2: Check if already max level.
            if (currLevel >= 10) {
                source.sendFailure(Component.literal("Village is already at max level 10."));
                return 0;
            }

            // STEP 3: Check cost to level up.
            // TODO check if can afford.
            int levelUpCost = currLevel * 200;
            // todo helper method.
            Level level = playerSource.level();
            Registry<Coin> coinRegistry = level.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            if (!player.getWallet().hasEnough(bronzeCoin, levelUpCost)) {
                source.sendFailure(Component.literal("Not enough coins to level up village. Cost is " + levelUpCost));
                return 0;
            }
            // STEP 4: Subtract money out of player.  TODO helper method hide this???
            player.getWallet().remove(bronzeCoin, levelUpCost);
            ModEvents.getPlayerDatabase().putData(playerSource.getUUID(), player);

            // check any other requirements.

            // STEP 5: Update level in db.
            village.setLevel(currLevel + 1);

            // STEP 6: Add new chunks.
            int newChunksCount = addNewVillageChunks(village, playerSource);

            // TODO Draw out in text grid to test.

            villageDatabase.putData(village.getUUID(), village);

            // Build a response message
            MutableComponent response = Component.literal("Village leveled up to: " + village.getLevel());
            response = response.append(Component.literal(", and added " + newChunksCount + " new chunks. \n"));
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // When a village levels, add new chunks all around.
    public static int addNewVillageChunks(VillageData village, Player playerSource) {
        PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
        DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
        UUID villageId = village.getUUID();

        // Add in all new chunks.....
        DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();

        // STEP 1: Add extra chunks until you equal (4 + level) * 2 + 1 squared chunks.
        int currentLevelSize = (4 + village.getLevel()) * 2 + 1;
        int previousLevelSize = (4 + (village.getLevel() - 1)) * 2 + 1;
        int chunksCount = (currentLevelSize * currentLevelSize) - (previousLevelSize * previousLevelSize);
        int newChunksCount = chunksCount;
        ChunkPos centerChunkPos = village.getPosition();
        LOGGER.info("Level up village: " + village.getLevel() + " chunksCount = " + chunksCount);
        LOGGER.info("Center chunk at " + centerChunkPos.toString());

        // STEP 2: Loop and add all new chunks until done.
        // Add them randomly along the edge of current chunks.
        int tries = 0;
        // Calculate the range based on the village level
        int range = 8 + 3 * village.getLevel();
        while (chunksCount > 0 && tries < 5000) {
            tries++;

            // Generate random x and z positions within the range, centered around the current position
            int x = centerChunkPos.x + (int) ((Math.random() * 2 - 1) * range);
            int z = centerChunkPos.z + (int) ((Math.random() * 2 - 1) * range);
            ChunkPos chunkPos = new ChunkPos(x, z);

            // RULE 1: Must be touching claims.
            if (!touchingVillageChunk(village, chunkPos)) {
//                LOGGER.info("DEBUG not adding chunk not near claims... " + chunkPos.toString());
                continue;
            }

//            LOGGER.info("DEBUG Checking from " + centerChunkPos.toString() + "  at chunkPos = " + chunkPos.toString() + ", chunksCount = " + chunksCount + ", tries = " + tries);

            // RULE 2: If an ocean chunk 2/3 chance skip it and continue.
            Holder<Biome> biome = playerSource.level().getBiome(chunkPos.getWorldPosition());
            if (biome.is(BiomeTags.IS_OCEAN)) {
                LOGGER.info("DEBUG notice Ocean chunk at: " + chunkPos.toString());
                if (Math.random() < 0.66) {
                    LOGGER.info("DEBUG SKIPPING OCEAN CHUNK ");
                    continue;
                }
                LOGGER.info("DEBUG adding ocean chunk ");
            }

            // If already claimed, skip.
            if (village.getClaimedChunks().contains(chunkPos)) {
                continue;
            }
            // RULE 3: If claimed by another village 50% chance to take it over.
            // If taking over another village chunk send text to world chat now.
            // Get chunk village.
            ChunkData chunkData = chunkDatabase.getData(chunkPos.toLong());
            if (chunkData != null) {
                VillageData otherVillage = villageDatabase.getData(chunkData.getVillageId());
                if (otherVillage != null && !otherVillage.getUUID().equals(villageId)) {
                    LOGGER.info("DEBUG: Checking other village owns it... at " + chunkPos.toString() + ", other village = " + otherVillage.getName());
                    if (Math.random() < 0.5) {
                        LOGGER.info("DEBUG SKIPPING chunk owned by another village: " + otherVillage.getName());
                        continue;
                    }
                    LOGGER.info("DEBUG taking over chunk owned by another village: " + otherVillage.getName());

                    // Change chunk data.
                    chunkData.setVillageId(villageId);
                    chunkData.setType("village");
                    chunkDatabase.putData(chunkPos.toLong(), chunkData);

                    // Remove from old village.
                    otherVillage.removeClaimedChunk(chunkPos);
                    villageDatabase.putData(otherVillage.getUUID(), otherVillage);
                    village.addClaimedChunk(chunkPos);
                    villageDatabase.putData(village.getUUID(), otherVillage);

                    // Add world chat message. Show center chunk pos.
                    MutableComponent message = Component.literal("Village " + village.getName()
                            + " took over " + otherVillage.getName() + " chunk"
                            + " at " + chunkPos.getMiddleBlockPosition(64).getX() + ", " + chunkPos.getMiddleBlockPosition(64).getZ());
                    message.withStyle(ChatFormatting.RED);
                    playerSource.sendSystemMessage(message);
                    chunksCount--;
                    continue;
                }
            }

            // Add now.
            LOGGER.info("DEBUG  Adding new chunk at: " + chunkPos.toString());
            ChunkData chunk = new ChunkData("village", player.getId(), villageId);
            chunkDatabase.putData(chunkPos.toLong(), chunk);
            village.addClaimedChunk(chunkPos);
            // TODO PUT BACK

            chunksCount--;
        } // while

        // Log an alert if we hit max without getting the new ones done.
        if (tries >= 5000) {
            LOGGER.info("DEBUG TODO MAX tries hit, not adding new chunks. ");
            // TODO add message to player.
            MutableComponent message = Component.literal("Error Village " + village.getName() +
                    " has hit max tries for adding new chunks, please report to dev.");
            message.withStyle(ChatFormatting.RED);
            playerSource.sendSystemMessage(message);
        }

        return newChunksCount;
    }

    public static int runVillageDailyUpkeep(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());

            // TODO run daily upkeep.
            // TODO TEST
            // Subtract daily upkeep cost from village coins.
            int dailyCost = getDailyCost(village);
            village.subtractCoins(dailyCost);

            // Build a response message.
            MutableComponent response = Component.literal("Village daily upkeep ran, charged " + dailyCost + " coins.");
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Method to loop over all chunks in a village, and get the center spot biome there.
    // Then unique and sort count the list.
    // Then show the list of biomes.
    // Two methods, one to get one to show, later can store on an object.
    // Uses current player village.
    public static int showVillageBiomes(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Get the village data for the player.
            DataBase<UUID, PlayerData> playerDataDB = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDB.getData(playerSource.getUUID());
            if (playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("Player is not in a village right now."));
                return 0;
            }

            // Get the village data for the player.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(source.getLevel());
            VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());

            // Get the list of biomes in the village.
            Map<String, Integer> biomes = getVillageBiomes(villageData);

            // Show the list of biomes.
            // TODO first line yellow.
            MutableComponent response = Component.literal("Biomes in village: ");
            for (Map.Entry<String, Integer> entry : biomes.entrySet()) {
                String biome = entry.getKey();
                // Remove mod tag, dont need really.  With regex all before the ":"
                String biomeName = biome.toString().replaceAll("^[^:]+:", "");

                int count = entry.getValue();
                response.append(Component.literal(biomeName + " (" + count + "), \n"));
            }
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static Map<String, Integer> getVillageBiomes(VillageData villageData) {
        Level level = Minecraft.getInstance().level;

        // Loop over each chunk in territory.
        // Get the biome for each chunk.
        List<ChunkPos> chunks = villageData.getClaimedChunks();
        // Count of each biome here.
        Map<String, Integer> biomeCounts = new HashMap<>();

        for (ChunkPos chunk : chunks) {
            // Get the biome for the chunk center.
            BlockPos blockPos = chunk.getMiddleBlockPosition(64); // default height notice.
            // Get height at that position.
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
            blockPos = new BlockPos(blockPos.getX(), height, blockPos.getZ());

            // Update blockPos
            assert level != null;
            Biome biome = level.getBiome(blockPos).value();
            ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
            if (biomeName == null) {
                LOGGER.info("Biome name is null for chunk " + chunk);
                continue;
            }

            // Add to a count of biomes hash.
            String biomeString = biomeName.toString();
            LOGGER.info("DEBUG Biome name for chunk " + chunk + " is " + biomeString);
            if (biomeCounts.containsKey(biomeString)) {
                // Increment the count for this biome
                biomeCounts.put(biomeString, biomeCounts.get(biomeString) + 1);
            } else {
                // Add this biome to the map with an initial count of 1
                biomeCounts.put(biomeString, 1);
            }
        }

        // Sort the map by count in descending order
        Map<String, Integer> sortedBiomeCounts = biomeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);

        return sortedBiomeCounts;
    }


    // Helper methods

    // Make sure at least one neighbor is a village chunk we own.
    public static boolean touchingVillageChunk(VillageData village, ChunkPos chunkPos) {
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();

        // Check four neighbors nearby.
        ChunkData chunkData = null;
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x + 1, chunkPos.z).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x - 1, chunkPos.z).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x, chunkPos.z + 1).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x, chunkPos.z - 1).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }

        return false;
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

    public static String findVillageByChunkPos(ChunkPos chunkPos) {
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = dataBase.getData(chunkPos.toLong());
        if (chunkData == null) {
            return null;
        }
//        return chunkData.getType();

        VillageData village = ModEvents.getVillageDatabase().getData(chunkData.getVillageId());
        return village != null ? village.getName() : null;
    }
}
