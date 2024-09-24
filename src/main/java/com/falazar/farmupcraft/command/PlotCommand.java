package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlotCommand {
    public static final CustomLogger LOGGER = new CustomLogger(PlotCommand.class.getSimpleName());
    private static final List<String> VALID_PLOT_TYPES = Arrays.asList("village", "farm");

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "show"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("plot");

        // Define the "plots" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(PlotCommand::showPlotInfo);

        // Define the "buy" sub-command
//        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
//                .executes(PlotCommand::buyPlot);
        // Define the "buy" sub-command with a string argument
        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((context, builder2) -> {
                            builder2.suggest("village");
                            builder2.suggest("farm");
                            return builder2.buildFuture();
                        })
                        .executes(PlotCommand::buyPlot));

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
            DataBaseAccess<ChunkPos, ChunkData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.CHUNK_DATA_DATABASE.getDatabaseName());
            DataBase<ChunkPos, ChunkData> dataBase = dataBaseAccess.get(level);
            ChunkData data = dataBase.getData(chunkPos);
            if (data == null) {
                context.getSource().sendFailure(Component.literal("Plot is not owned."));
                // todo show closest village still though.
                return 0;
            }

            // Pull out plot info and owner and village.
            ServerLevel serverLevel = context.getSource().getLevel();

            // BUG here maybe.
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


    // TODO buy with a name, farm, village, etc.
    public static int buyPlot(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            String plotType = StringArgumentType.getString(context, "type");
            if (!VALID_PLOT_TYPES.contains(plotType)) {
                context.getSource().sendFailure(Component.literal("Invalid plot type. Must be 'village' or 'farm'."));
                return 0;
            }

            Level level = summoner.level();
            ChunkPos chunkPos = new ChunkPos(summoner.blockPosition());
            DataBaseAccess<ChunkPos, ChunkData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.CHUNK_DATA_DATABASE.getDatabaseName());
            DataBase<ChunkPos, ChunkData> dataBase = dataBaseAccess.get(level);
            ChunkData data = dataBase.getData(chunkPos);
            if (data != null) {
                context.getSource().sendFailure(Component.literal("Plot is already owned."));
                return 0;
            }

            // TODO limit types to farm, village, nursery for now.

            // TODO implement plot buying logic here.
            // Step 1: Check who owns, if already owned, just show info.

            // Step 2: Find nearest village.
            // Check if in range of village.
            // Check if member of village.
            String village = "testobj";

            // STEP 3: Calc cost to buy plot.
            String player = "testobj";
            int cost = calculatePlotCost(village, player, plotType);
//            if (player.checkPlayerMoney(cost)) {
//                context.getSource().sendFailure(Component.literal("Player does not have enough money."));
//                return 0;
//            }

            // STEP 4: Buy plot and mark to db.
//            data.setPlayerId(summoner.getId());
//            data.setVillageId(0); // TODO set to village id.
//            data.setType("farm"); // TODO set to type.
            // mark dirty to save.
            ChunkData newPlot = new ChunkData(plotType, summoner.getId(), 1234); // hack test.
            dataBase.putData(chunkPos, newPlot);
            dataBase.setDirty();
            LOGGER.info("Plot bought at " + chunkPos);

            // STEP 5: Subtract money out of player.
//            player.subtractMoney(cost);

            // STEP 6: if village, setup and save village object and owner - make method.
//            createNewVillage(village, player, chunkPos);

            // Build a response message
            MutableComponent response = Component.literal("Plot bought at " + chunkPos + " as " + plotType);
            context.getSource().sendSuccess(() -> response, false);

        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    private static int calculatePlotCost(String village, String player, String plotType) {
        // TODO implement cost calculation logic here.
        int baseCost = 100;
        // 100 + 100 for each plot.... whatevers.
        int totalCost = baseCost;

        return totalCost;
    }
}
