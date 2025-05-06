package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.currency.Wallet;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

public class PlayerCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlayerCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "player"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("player");

        // Define the "info" sub-command
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(context -> {
                    return showPlayerInfo(context.getSource());
                });
        builder.then(infoBuilder);

        // Define the leave village command.
        LiteralArgumentBuilder<CommandSourceStack> leaveVillageBuilder = Commands.literal("leavevillage")
                .executes(context -> {
                    return leaveVillage(context.getSource());
                });
        builder.then(leaveVillageBuilder);

        // Admin command joinvillage
        // Define the "joinvillage" sub-command
        LiteralArgumentBuilder<CommandSourceStack> joinVillageBuilder = Commands.literal("joinvillage")
                .then(Commands.argument("village", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "village");
                            return setVillage(context.getSource(), villageName);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(joinVillageBuilder);

        // Admin: Define the "givecoins" and amount sub-command for admin only.
        LiteralArgumentBuilder<CommandSourceStack> giveCoinsBuilder = Commands.literal("givecoins")
                .then(Commands.argument("amount", IntegerArgumentType.integer(-10000))
                        .executes(context -> {
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            return givePlayerCoins(context.getSource(), amount);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(giveCoinsBuilder);

        // Define the "givevillagecoins" and amount sub-command.
        LiteralArgumentBuilder<CommandSourceStack> giveVillageCoinsBuilder = Commands.literal("givevillagecoins")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            return giveVillageCoins(context.getSource(), amount);
                        }));
        builder.then(giveVillageCoinsBuilder);

        // Define the "takevillagecoins" and amount sub-command.
        LiteralArgumentBuilder<CommandSourceStack> takeVillageCoinsBuilder = Commands.literal("takevillagecoins")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            return takeVillageCoins(context.getSource(), amount);
                        }));
        builder.then(takeVillageCoinsBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showPlayerInfo(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            PlayerData player = getPlayer(source);
            ServerLevel serverLevel = source.getLevel();

//            LOGGER.info("DEBUG: Player info test name: " + playerSource.getScoreboardName());
            // TODO make helper methods for get name and send text.
            // STEP 1: Show player info.
            source.sendSuccess(() -> Component.literal("---------- Player Name: " + player.getNameForPlayer(serverLevel, playerSource.getUUID()) + " ----------")
                    .withStyle(ChatFormatting.YELLOW), false);

            // STEP 2: Get money from wallet.
            // TODO helper method.
            Wallet wallet = player.getWallet();
            Registry<Coin> coinRegistry = serverLevel.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Coins: " + String.format("%,d", bronzeCoins)), false);

            // STEP 3: Pull home village info if set.
            if (player.getHomeVillageUUID() == null) {
                source.sendSuccess(() -> Component.literal("No home village."), false);
            } else {
                // todo helper method on player manager or village manager.
                // todo player.getHomeVillage();
                DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(serverLevel);
                VillageData villageData = villageDataDB.getData(player.getHomeVillageUUID());
                if (villageData != null) {
                    source.sendSuccess(() -> Component.literal("Home village: " + villageData.getName()), false);
                } else {
                    source.sendFailure(Component.literal("Error, Home village not found."));
                }
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("player info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Give player coins method.
    public static int givePlayerCoins(CommandSourceStack source, int amount) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            PlayerData player = getPlayer(source);

            // Add money to wallet.
            Wallet wallet = player.getWallet();
            Registry<Coin> coinRegistry = playerSource.level().registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);

            wallet.add(bronzeCoin, amount);

            // TODO THIS IS ALL THATS NEEDED? save is not quite working.
            savePlayer(playerSource.getUUID(), player);

            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Player: "
                    + playerSource.getScoreboardName() + " given " + amount + " coins. Total: " + bronzeCoins), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("give coins Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Leave village command
    public static int leaveVillage(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            PlayerData player = getPlayer(source);

            // TODO make helper method for this.
            source.sendSuccess(() -> Component.literal("Leaving home village now"), false);
            player.setHomeVillageId(null);
            savePlayer(playerSource.getUUID(), player);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("player info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Set village command
    public static int setVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            PlayerData player = getPlayer(source);

            // Get village data from database. and uuid
            VillageData village = VillageCommand.findVillageByName(villageName);
            if (village == null) {
                source.sendFailure(Component.literal("Village not found."));
                return 0;
            }

            // TODO make helper method for this.
            source.sendSuccess(() -> Component.literal("Joining home village now: " + villageName), false);
            player.setHomeVillageId(village.getUUID());
            savePlayer(playerSource.getUUID(), player);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("player info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // A helper method to get the player data.
    public static PlayerData getPlayer(CommandSourceStack source) {
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
        if (playerSource == null) {
            source.sendFailure(Component.literal("Player not found."));
            return null;
        }
        DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
        PlayerData player = playerDataDataBase.getData(playerSource.getUUID());
        if (player == null) {
            source.sendFailure(Component.literal("Player data not found."));
            return null;
        }

        return player;
    }

    // TODO get uuid from within player instead!!!! update me.
    public static void savePlayer(UUID uuid, PlayerData player) {
        DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();

        playerDataDataBase.putData(uuid, player);
    }

    // Give village coins method.
    public static int giveVillageCoins(CommandSourceStack source, int amount) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // STEP 1: Get player data and village data.
            PlayerData player = getPlayer(source);
            ServerLevel serverLevel = source.getLevel();
            // TODO helper methods.
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase(serverLevel);
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("No home village set."));
                return 0;
            }

            // STEP 2: Check enough money in wallet.
            Wallet wallet = player.getWallet();
            Registry<Coin> coinRegistry = source.getLevel().registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            if (!player.getWallet().hasEnough(bronzeCoin, amount)) {
                source.sendFailure(Component.literal("Not enough coins in wallet, only have " + wallet.get(bronzeCoin)));
                return 0;
            }

            // STEP 3: Subtract coins from player.
            // TODO use helper method.
            wallet.remove(bronzeCoin, amount);
            savePlayer(playerSource.getUUID(), player);

            // STEP 4: Add to village coins.
            village.addCoins(amount);
            villageDatabase.putData(village.getUUID(), village);

            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Village: "
                    + village.getName() + " given " + amount + " coins. Total: " + bronzeCoins), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("give coins Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int takeVillageCoins(CommandSourceStack source, int amount) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // STEP 1: Get player data and village data.
            PlayerData player = getPlayer(source);
            ServerLevel serverLevel = source.getLevel();
            // TODO helper methods.
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase(serverLevel);
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("No home village set."));
                return 0;
            }

            // STEP 2: Check enough money in village
            if (!village.hasEnoughCoins(amount)) {
                source.sendFailure(Component.literal("Not enough coins in village, only have " + village.getCoins()));
                return 0;
            }

            // STEP 3: Subtract coins from village.
            village.subtractCoins(amount);
            villageDatabase.putData(village.getUUID(), village);

            // STEP 4: Add to player coins.
            Wallet wallet = player.getWallet();
            Registry<Coin> coinRegistry = source.getLevel().registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            wallet.add(bronzeCoin, amount);

            // TODO remove almost all playerDatabase calls within this file, use helpers.
            // TODO remove almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.

//            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            savePlayer(playerSource.getUUID(), player);

            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Village: "
                    + village.getName() + " taken " + amount + " coins. Total: " + bronzeCoins), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("give coins Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

}
