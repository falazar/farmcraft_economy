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

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(PlayerCommand::showPlayerInfo);
        builder.then(infoBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showPlayerInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

//            Level level = player.level(); todo not needed.
            ServerLevel serverLevel = context.getSource().getLevel();
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(player.getUUID());

            LOGGER.info("DEBUG: Player info test name: " + player.getScoreboardName());
            // TODO make helper methods for get name and send text.
            // STEP 1: Show player info.
            context.getSource().sendSuccess(() ->Component.literal("Player: " + playerData.getNameForPlayer(serverLevel)), false);

            // STEP 2: TODO Pull money from wallet.
            // TODO helper method.
            Wallet wallet = playerData.getWallet();
            Registry<Coin> coinRegistry = serverLevel.registryAccess().registryOrThrow(FUCRegistries.Keys.COIN);
            Coin bronzeCoin = coinRegistry.get(CoinRegistry.BRONZE_COIN);
            int bronzeCoins = wallet.get(bronzeCoin);
            context.getSource().sendSuccess(() ->Component.literal("Coins: " + bronzeCoins), false);


            // STEP 3: Pull home village info if set.
            if (playerData.getHomeVillageUUID() != null) {
                // todo helper method on player manager or village manager.
                DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(serverLevel);
                VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());
                if (villageData != null) {
                    context.getSource().sendSuccess(() ->Component.literal("Home village: " + villageData.getName()), false);
                } else {
                    context.getSource().sendFailure(Component.literal("Home village not found."));
                }
            }

        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("player info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

}
