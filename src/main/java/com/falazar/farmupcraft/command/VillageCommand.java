package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class VillageCommand {
    public static final CustomLogger LOGGER = new CustomLogger(VillageCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command for "village"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("village");

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(VillageCommand::showVillageInfo);

        // Define the list subcommand:
        LiteralArgumentBuilder<CommandSourceStack> listBuilder = Commands.literal("list")
                .executes(VillageCommand::listVillages);

        // TODO Add subcommand for nearest village
        // /village info nearest

        // TODO And per village name:
        // /village info <village name>

        // Add the sub-commands to the "village" command
        builder.then(infoBuilder);
        builder.then(listBuilder);

        // Register the main "village" command with the dispatcher
        pDispatcher.register(builder);
    }

    // Show the village that currnet player is in.
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

            DataBaseAccess<String, VillageData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.VILLAGE_DATABASE.getDatabaseName());
            DataBase<String, VillageData> dataBase = dataBaseAccess.get(level);
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
                    + " at " + villageData.getPosition().toShortString() + " with id = " + villageData.getId());
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
            DataBaseAccess<String, VillageData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.VILLAGE_DATABASE.getDatabaseName());
            DataBase<String, VillageData> dataBase = dataBaseAccess.get(level);
            Collection<VillageData> dataList = dataBase.getValues();
            if (dataList == null || dataList.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No villages data found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Village list: ");
            int index = 1;
            for (VillageData data : dataList) {
                response = response.append(Component.literal(index++ + ". " + data.getName() + " id = " + data.getId() +
                        " at " + data.getPosition().toShortString() + ", \n"));
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
