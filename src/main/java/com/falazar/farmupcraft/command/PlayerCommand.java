package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.UUID;
import java.text.NumberFormat;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

public class PlayerCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlayerCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "player"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("player");

        // Define the "info" sub-command — /player info shows self, /player info <name>
        // shows another player (admin)
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(context -> {
                    return showPlayerInfo(context.getSource());
                })
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "playerName");
                            return showPlayerInfoByName(context.getSource(), name);
                        }));
        builder.then(infoBuilder);

        // Define the leave village command.
        LiteralArgumentBuilder<CommandSourceStack> leaveVillageBuilder = Commands.literal("leavevillage")
                .executes(context -> {
                    return leaveVillage(context.getSource());
                });
        builder.then(leaveVillageBuilder);

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

        // Border sub-commands: /player border show / /player border color <color>
        SuggestionProvider<CommandSourceStack> colorSuggestions = (ctx, b) -> {
            for (String c : new String[] { "blue", "yellow", "orange", "pink", "teal", "green", "red", "purple",
                    "white" })
                b.suggest(c);
            return b.buildFuture();
        };
        LiteralArgumentBuilder<CommandSourceStack> borderBuilder = Commands.literal("border")
                .then(Commands.literal("show")
                        .executes(context -> setBorderShow(context.getSource())))
                .then(Commands.literal("color")
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests(colorSuggestions)
                                .executes(context -> {
                                    String color = StringArgumentType.getString(context, "color");
                                    return setBorderColor(context.getSource(), color);
                                })));
        builder.then(borderBuilder);

        // Admin-only sub-commands grouped under "/player admin ..."
        LiteralArgumentBuilder<CommandSourceStack> adminBuilder = Commands.literal("admin")
                .requires(s -> s.hasPermission(2))
                // joinvillage <village>
                .then(Commands.literal("joinvillage")
                        .then(Commands.argument("village", StringArgumentType.string())
                                .executes(context -> {
                                    String villageName = StringArgumentType.getString(context, "village");
                                    return setVillage(context.getSource(), villageName);
                                })))
                // givecoins <amount>
                .then(Commands.literal("givecoins")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-10000))
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    return givePlayerCoins(context.getSource(), amount);
                                })));
        builder.then(adminBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showPlayerInfo(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            LOGGER.info("DEBUG: Player info test name: " + playerSource.getScoreboardName());
            PlayerData player = getPlayer(source);
            ServerLevel serverLevel = source.getLevel();

            // TODO make helper methods for get name and send text.
            // STEP 1: Show player info.
            source.sendSuccess(() -> Component
                    .literal("---------- Player Name: " + player.getNameForPlayer(serverLevel, playerSource.getUUID())
                            + " ----------")
                    .withStyle(ChatFormatting.YELLOW), false);

            // STEP 2: Get money from player coin field.
            int bronzeCoins = player.getCoins();
            LOGGER.info("DEBUG: Player info bronze coins: " + bronzeCoins);
            source.sendSuccess(() -> Component.literal("Coins: " + String.format("%,d", bronzeCoins)), false);

            // STEP 3: Pull home village info if set.
            sendHomeVillageInfo(source, serverLevel, player);
        } catch (Exception ex) {
            LOGGER.error("Player info error for " + source.getTextName() + ": " + ex.getMessage(), ex);
            source.sendFailure(
                    Component.literal("player info error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
        }
        return 0;
    }

    /** /player info <name> — admin command to look up any player's data by name. */
    public static int showPlayerInfoByName(CommandSourceStack source, String targetName) {
        try {
            ServerLevel serverLevel = source.getLevel();

            // Find UUID by checking online players first, then the full player database.
            UUID targetUUID = null;
            net.minecraft.server.level.ServerPlayer online = source.getServer().getPlayerList()
                    .getPlayerByName(targetName);
            if (online != null) {
                targetUUID = online.getUUID();
            } else {
                // Scan all known player records for a name match.
                DataBase<UUID, PlayerData> db = ModEvents.getPlayerDatabase();
                for (UUID uuid : db.getKeys()) {
                    PlayerData pd = db.getData(uuid);
                    if (pd != null) {
                        String recordedName = pd.getNameForPlayer(serverLevel, uuid);
                        if (recordedName.equalsIgnoreCase(targetName)) {
                            targetUUID = uuid;
                            break;
                        }
                    }
                }
            }

            if (targetUUID == null) {
                source.sendFailure(Component.literal("No player data found for '" + targetName + "'."));
                return 0;
            }

            PlayerData player = ModEvents.getPlayerDatabase().getData(targetUUID);
            if (player == null) {
                source.sendFailure(Component.literal("Player data not found for '" + targetName + "'."));
                return 0;
            }

            source.sendSuccess(() -> Component
                    .literal("---------- Player: " + targetName + " ----------")
                    .withStyle(ChatFormatting.YELLOW), false);

            // Coins
            int bronzeCoins = player.getCoins();
            source.sendSuccess(() -> Component.literal("Coins: " + String.format("%,d", bronzeCoins)), false);

            // Village
            sendHomeVillageInfo(source, serverLevel, player);

            // Online status
            boolean isOnline = online != null;
            source.sendSuccess(() -> Component.literal("Online: " + (isOnline ? "Yes" : "No"))
                    .withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

        } catch (Exception ex) {
            LOGGER.error("showPlayerInfoByName error: " + ex.getMessage(), ex);
            source.sendFailure(Component.literal("Error: " + ex.getMessage()));
        }
        return 0;
    }

    private static void sendHomeVillageInfo(CommandSourceStack source, ServerLevel level, PlayerData player) {
        if (player.getHomeVillageUUID() == null) {
            source.sendSuccess(() -> Component.literal("No home village."), false);
            return;
        }

        VillageData village = ModEvents.getVillageDatabase(level).getData(player.getHomeVillageUUID());
        if (village == null) {
            source.sendSuccess(
                    () -> Component.literal("Home village: (unknown uuid: " + player.getHomeVillageUUID() + ")"),
                    false);
            return;
        }

        String safeVillageName = village.getName().replace("\"", "\\\"");
        String cmd = "/village info \"" + safeVillageName + "\"";
        Component villageLink = Component.literal("Home village: ")
                .append(Component.literal(village.getName())
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to view village info")))));
        source.sendSuccess(() -> villageLink, false);
    }

    // Give player coins method.
    public static int givePlayerCoins(CommandSourceStack source, int amount) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            PlayerData player = getPlayer(source);

            // Add money to player coin field.
            player.addCoins(amount);

            // TODO THIS IS ALL THATS NEEDED? save is not quite working.
            savePlayer(playerSource.getUUID(), player);

            int bronzeCoins = player.getCoins();
            NumberFormat numberFormat = NumberFormat.getInstance();
            source.sendSuccess(() -> Component.literal("Player: "
                    + playerSource.getScoreboardName() + " given " + numberFormat.format(amount) + " coins. Total: "
                    + numberFormat.format(bronzeCoins)), false);
        } catch (Exception ex) {
            LOGGER.error("givePlayerCoins error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("givePlayerCoins error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
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
            // Remove from village member list before clearing.
            if (player.getHomeVillageUUID() != null) {
                VillageData oldVillage = ModEvents.getVillageDatabase().getData(player.getHomeVillageUUID());
                if (oldVillage != null) {
                    oldVillage.removeMember(playerSource.getUUID());
                    ModEvents.getVillageDatabase().putData(oldVillage.getUUID(), oldVillage);
                }
            }
            player.setHomeVillageId(new UUID(0L, 0L)); // zero-UUID = no village
            savePlayer(playerSource.getUUID(), player);
        } catch (Exception ex) {
            LOGGER.error("leaveVillage error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("leaveVillage error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
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
            // Add to village member list.
            village.addMember(playerSource.getUUID());
            ModEvents.getVillageDatabase().putData(village.getUUID(), village);
        } catch (Exception ex) {
            LOGGER.error("setVillage error: " + ex.getMessage(), ex);
            source.sendFailure(
                    Component.literal("setVillage error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
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

            // STEP 2: Check enough money in player coins.
            if (player.getCoins() < amount) {
                source.sendFailure(
                        Component.literal("Not enough coins, only have " + player.getCoins()));
                return 0;
            }

            // STEP 3: Subtract coins from player.
            player.removeCoins(amount);
            savePlayer(playerSource.getUUID(), player);

            // STEP 4: Add to village coins.
            village.addCoins(amount);
            villageDatabase.putData(village.getUUID(), village);

            int bronzeCoins = player.getCoins();
            NumberFormat numberFormat = NumberFormat.getInstance();
            source.sendSuccess(() -> Component.literal("Village: "
                    + village.getName() + " given " + numberFormat.format(amount) + " coins. Total: "
                    + numberFormat.format(bronzeCoins)), false);
        } catch (Exception ex) {
            LOGGER.error("giveVillageCoins error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("giveVillageCoins error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
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
            player.addCoins(amount);

            // TODO remove almost all playerDatabase calls within this file, use helpers.
            // TODO remove almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.
            // TODO remoe almost all playerDatabase calls within this file, use helpers.

            // DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            savePlayer(playerSource.getUUID(), player);

            int bronzeCoins = player.getCoins();
            NumberFormat numberFormat = NumberFormat.getInstance();
            source.sendSuccess(() -> Component.literal("Village: "
                    + village.getName() + " taken " + numberFormat.format(amount) + " coins. Total: "
                    + numberFormat.format(bronzeCoins)), false);
        } catch (Exception ex) {
            LOGGER.error("takeVillageCoins error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("takeVillageCoins error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
        }
        return 0;
    }

    // Toggle border visibility on/off and save to PlayerData.
    public static int setBorderShow(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            PlayerData player = getPlayer(source);
            if (player == null)
                return 0;
            boolean newVal = !player.isBorderShow();
            player.setBorderShow(newVal);
            savePlayer(playerSource.getUUID(), player);
            source.sendSuccess(() -> Component.literal("Border display: " + (newVal ? "ON" : "OFF")), false);
        } catch (Exception ex) {
            LOGGER.error("setBorderShow error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("setBorderShow error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
        }
        return 0;
    }

    // Set border color and save to PlayerData. Valid: blue, yellow, orange, pink,
    // teal, green, red, purple, white
    public static int setBorderColor(CommandSourceStack source, String color) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            PlayerData player = getPlayer(source);
            if (player == null)
                return 0;
            player.setBorderColor(color.toLowerCase());
            savePlayer(playerSource.getUUID(), player);
            source.sendSuccess(() -> Component.literal("Border color set to: " + color), false);
        } catch (Exception ex) {
            LOGGER.error("setBorderColor error: " + ex.getMessage(), ex);
            source.sendFailure(Component
                    .literal("setBorderColor error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
        }
        return 0;
    }

}
