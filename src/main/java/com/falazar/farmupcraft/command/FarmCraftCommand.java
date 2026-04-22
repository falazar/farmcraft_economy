package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.events.WorldScheduler;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
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
}
