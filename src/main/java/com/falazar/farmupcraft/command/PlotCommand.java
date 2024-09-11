package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class PlotCommand {
    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "show"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("plot")
                .requires(s -> s.hasPermission(0));  // Adjust permission as needed

        // Define the "plots" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(c -> showPlotInfo(c))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Add the "info" sub-command to the "plot" command
        builder.then(infoBuilder);

        // Register the main "plot" command with the dispatcher
        pDispatcher.register(builder);
    }


    public static int showPlotInfo(CommandContext<CommandSourceStack> c) {
        try {
            Entity nullableSummoner = c.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                c.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            Level level = summoner.level();

            ChunkPos chunkPos = new ChunkPos(summoner.blockPosition());
            DataBaseAccess<ChunkPos, ChunkData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.CHUNK_DATA_DATABASE.getDatabaseName());
            DataBase<ChunkPos, ChunkData> dataBase = dataBaseAccess.get(level);
            ChunkData data = dataBase.getData(chunkPos);

            // TODO load plot data here.

            // Build a response message
            MutableComponent response = Component.literal("Plot info for  " + chunkPos + ": ");
            response = response.append(Component.literal(" TODO INFO "));
            MutableComponent finalResponse = response;
            c.getSource().sendSuccess(() -> finalResponse, false);

        } catch (Exception ex) {
            c.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }
}
