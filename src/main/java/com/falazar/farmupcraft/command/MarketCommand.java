package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
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
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

public class MarketCommand {
    public static final CustomLogger LOGGER = new CustomLogger(MarketCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "market"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("market");

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(MarketCommand::showMarketInfo);
        builder.then(infoBuilder);

        // todo add sub commands.

        // Define the "show" sub-command
        // with four options: misc, stone, food, wood
        LiteralArgumentBuilder<CommandSourceStack> showBuilder = Commands.literal("show")
                .then(Commands.literal("misc").executes(context -> {
                    return showMarketList(context, "misc");
                }))
                .then(Commands.literal("stone").executes(context -> {
                    return showMarketList(context, "stone");
                }))
                .then(Commands.literal("food").executes(context -> {
                    return showMarketList(context, "food");
                }))
                .then(Commands.literal("wood").executes(context -> {
                    return showMarketList(context, "wood");
                }));

        // Register the main "market" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showMarketInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Market info options: ");
            response = response.append(Component.literal("/market show misc "));
            response = response.append(Component.literal("/market show stone "));
            response = response.append(Component.literal("/market show food"));
            response = response.append(Component.literal("/market show wood");
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Show market info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int showMarketList(CommandContext<CommandSourceStack> context, String type) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Build a response message
            // TODO loop over all items and prices in text.

            MutableComponent response = Component.literal("Market info options: ");
            response = response.append(Component.literal("item1: 10 coins"));
            response = response.append(Component.literal("item2: 10 coins"));
            response = response.append(Component.literal("item3: 10 coins"));
            response = response.append(Component.literal("item4: 10 coins"));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Show Market List Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }


}
