package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.events.WorldScheduler;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

public class FarmCraftCommand {
    public static final CustomLogger LOGGER = new CustomLogger(FarmCraftCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("farmcraft");

        // /farmcraft scheduler - show next scheduled run info
        builder.then(Commands.literal("scheduler")
                .executes(context -> showSchedulerInfo(context.getSource())));

        // /farmcraft runchecks - manually trigger the hourly scheduler (admin only)
        builder.then(Commands.literal("runchecks")
                .requires(source -> source.hasPermission(2))
                .executes(context -> runHourlyChecks(context.getSource())));

        // /farmcraft map waypoint <x> <y> <z> <name> - add a JourneyMap waypoint (admin only)
        builder.then(Commands.literal("map")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("waypoint")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> addMapWaypoint(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                IntegerArgumentType.getInteger(context, "z"),
                                                                StringArgumentType.getString(context, "name"))))))))
                // /farmcraft map chunk <chunkX> <chunkZ> <label> - highlight a specific chunk
                .then(Commands.literal("chunk")
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                                .executes(context -> highlightChunk(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "chunkX"),
                                                        IntegerArgumentType.getInteger(context, "chunkZ"),
                                                        StringArgumentType.getString(context, "label")))))))
                // /farmcraft map here <label> - highlight the chunk you're standing in
                .then(Commands.literal("here")
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> highlightHereChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "label"))))));

        pDispatcher.register(builder);

        // /frecipe <itemId> - sends OpenJeiRecipePacket to the player so JEI opens client-side
        pDispatcher.register(Commands.literal("frecipe")
                .then(Commands.argument("itemId", StringArgumentType.greedyString())
                        .executes(context -> {
                            String itemId = StringArgumentType.getString(context, "itemId");
                            return openRecipeForPlayer(context.getSource(), itemId);
                        })));
    }

    public static int showSchedulerInfo(CommandSourceStack source) {
        try {
            int ticksLeft = WorldScheduler.getTicksUntilNextRun();
            // 20 ticks = 1 second, 1200 ticks = 1 minute
            int secondsLeft = ticksLeft / 20;
            int minutesLeft = secondsLeft / 60;
            int secsRemainder = secondsLeft % 60;

            WorldData worldData = ModEvents.getWorldDataDatabase().getOrCreate(0, WorldData::new);

            MutableComponent response = Component.literal("--- FarmCraft Scheduler ---\n")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Next hourly run in: ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(minutesLeft + "m " + secsRemainder + "s\n")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("Last market daily: ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(worldData.getLastRanMarketDaily() + "\n")
                            .withStyle(
                                    worldData.hasMarketRanTodayAlready() ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal("Last village daily: ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(worldData.getLastRanVillageDaily() + "\n")
                            .withStyle(
                                    worldData.hasVillageRanTodayAlready() ? ChatFormatting.GREEN : ChatFormatting.RED));

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception in farmcraft scheduler - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int runHourlyChecks(CommandSourceStack source) {
        try {
            WorldScheduler.runHourlyChecks();
            source.sendSuccess(() -> Component.literal("Hourly checks triggered manually.").withStyle(ChatFormatting.GREEN), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception running hourly checks - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int openRecipeForPlayer(CommandSourceStack source, String itemId) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        EDBMessages.sendToPlayer(new OpenJeiRecipePacket(itemId), player);
        return 1;
    }

    public static int addMapWaypoint(CommandSourceStack source, int x, int y, int z, String name) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new AddJMWaypointPacket(x, y, z, name, dimId), player);
        source.sendSuccess(() -> Component.literal("Waypoint \"" + name + "\" added at " + x + ", " + y + ", " + z + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int highlightChunk(CommandSourceStack source, int chunkX, int chunkZ, String label) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new HighlightChunkPacket(chunkX, chunkZ, label, dimId, 0x00FF00), player);
        source.sendSuccess(() -> Component.literal("Chunk [" + chunkX + ", " + chunkZ + "] highlighted as \"" + label + "\".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int highlightHereChunk(CommandSourceStack source, String label) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        ChunkPos pos = new ChunkPos(player.blockPosition());
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new HighlightChunkPacket(pos.x, pos.z, label, dimId, 0x00FF00), player);
        source.sendSuccess(() -> Component.literal("Your current chunk [" + pos.x + ", " + pos.z + "] highlighted as \"" + label + "\".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
