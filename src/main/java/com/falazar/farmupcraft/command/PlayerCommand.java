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
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            return givePlayerCoins(context.getSource(), amount);
                        }))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        builder.then(giveCoinsBuilder);

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
            // TODO make this bolder and colorful.
            source.sendSuccess(() -> Component.literal("Player: " + player.getNameForPlayer(serverLevel, playerSource.getUUID())), false);

            // STEP 2: TODO Pull money from wallet.
            // TODO helper method.
            Wallet wallet = player.getWallet();
            Registry<Coin> coinRegistry = serverLevel.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Coins: " + bronzeCoins), false);

            // STEP 3: Pull home village info if set.
            if (player.getHomeVillageUUID() == null) {
                source.sendSuccess(() -> Component.literal("No home village."), false);
            } else {
                // todo helper method on player manager or village manager.
                // todo player.getHomeVillageName();
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
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Add money to wallet.
            Wallet wallet = ModEvents.getPlayerDatabase().getData(player.getUUID()).getWallet();
            Registry<Coin> coinRegistry = player.level().registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);

            wallet.add(bronzeCoin, amount);
            int bronzeCoins = wallet.get(bronzeCoin);
            source.sendSuccess(() -> Component.literal("Player: "
                    + player.getScoreboardName() + " given " + amount + " coins. Total: " + bronzeCoins), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("give coins Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Leave village command
    public static int leaveVillage(CommandSourceStack source) {
        try {
            PlayerData player = getPlayer(source);

            // TODO make helper method for this.
            source.sendSuccess(() -> Component.literal("Leaving home village now"), false);
            player.setHomeVillageId(null);

        } catch (Exception ex) {
            source.sendFailure(Component.literal("player info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Set village command
    public static int setVillage(CommandSourceStack source, String villageName) {
        try {
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
}
