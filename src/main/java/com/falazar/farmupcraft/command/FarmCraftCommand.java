package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        // /farmcraft map waypoint <x> <y> <z> <name> - add a JourneyMap waypoint (admin
        // only)
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
                                        StringArgumentType.getString(context, "label")))))
                // /farmcraft map village - draw all claimed village chunks on JourneyMap
                // /farmcraft map village clear - remove all village chunk overlays
                .then(Commands.literal("village")
                        .executes(context -> showVillageChunks(context.getSource()))
                        .then(Commands.literal("clear")
                                .executes(context -> clearVillageChunks(context.getSource()))))
                // /farmcraft map clear - remove ALL farmcraft overlays (chunks, village, etc.)
                .then(Commands.literal("clear")
                        .executes(context -> clearAllMapOverlays(context.getSource()))));

        pDispatcher.register(builder);

        // /farmcraft admin settings - view/toggle world settings (admin only)
        pDispatcher.register(Commands.literal("farmcraft")
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("settings")
                                // /farmcraft admin settings - show current values
                                .executes(context -> showAdminSettings(context.getSource()))
                                // /farmcraft admin settings useFarms <true|false>
                                .then(Commands.literal("useFarms")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> setAdminSetting(context.getSource(), "useFarms",
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")))))
                                // /farmcraft admin settings useBiomeCropRules <true|false>
                                .then(Commands.literal("useBiomeCropRules")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> setAdminSetting(context.getSource(), "useBiomeCropRules",
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")))))))); 

        // /frecipe <itemId> - sends OpenJeiRecipePacket to the player so JEI opens
        // client-side
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
            source.sendSuccess(
                    () -> Component.literal("Hourly checks triggered manually.").withStyle(ChatFormatting.GREEN),
                    false);
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
        source.sendSuccess(
                () -> Component.literal("Waypoint \"" + name + "\" added at " + x + ", " + y + ", " + z + ".")
                        .withStyle(ChatFormatting.GREEN),
                false);
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
        source.sendSuccess(
                () -> Component.literal("Chunk [" + chunkX + ", " + chunkZ + "] highlighted as \"" + label + "\".")
                        .withStyle(ChatFormatting.GREEN),
                false);
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
        source.sendSuccess(() -> Component
                .literal("Your current chunk [" + pos.x + ", " + pos.z + "] highlighted as \"" + label + "\".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * /farmcraft map village — draws a colored overlay on every claimed village
     * chunk in JourneyMap.
     * Blue (0x0055FF) = chunk has a plot type (farm, house, pasture, etc.)
     * Green (0x00E000) = chunk is claimed but has no plot ("village" type)
     */
    public static int showVillageChunks(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        List<int[]> chunks = buildVillageChunkOverlayData(player);
        if (chunks == null) {
            source.sendFailure(Component.literal("You are not a member of any village."));
            return 0;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(chunks, true, dimId), player);
        source.sendSuccess(() -> Component.literal(
                "Showing " + chunks.size() + " village chunks on map. Use /village map clear to remove.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // Rebuild and redraw all village chunk overlays for this player.
    // This clears stale colors first, then re-renders from current DB data.
    public static void refreshVillageChunksOverlay(ServerPlayer player) {
        List<int[]> chunks = buildVillageChunkOverlayData(player);
        if (chunks == null) {
            return;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(List.of(), false, dimId), player);
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(chunks, true, dimId), player);
    }

    private static List<int[]> buildVillageChunkOverlayData(ServerPlayer player) {
        DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
        PlayerData playerData = playerDb.getData(player.getUUID());
        if (playerData == null || playerData.getHomeVillageUUID() == null) {
            return null;
        }
        VillageData village = ModEvents.getVillageDatabase().getData(playerData.getHomeVillageUUID());
        if (village == null) {
            return null;
        }
        DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
        List<int[]> chunks = new ArrayList<>();
        for (ChunkPos cp : village.getClaimedChunks()) {
            ChunkData cd = chunkDb.getData(cp.toLong());
            String type = cd != null ? cd.getType() : "village";
            boolean hasPlot = !type.equalsIgnoreCase("village") && !type.equalsIgnoreCase("village center");
            int color = hasPlot ? 0x0055FF : 0x00E000; // blue = plot, green = village-only
            chunks.add(new int[] { cp.x, cp.z, color });
        }
        return chunks;
    }

    /**
     * /farmcraft map village clear — removes all village chunk overlays from
     * JourneyMap.
     */
    public static int clearVillageChunks(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(List.of(), false, dimId), player);
        source.sendSuccess(() -> Component.literal("Village chunk overlays cleared.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * /farmcraft map clear — removes ALL farmcraft map overlays (chunk highlights,
     * village overlays, etc.).
     */
    public static int clearAllMapOverlays(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        String dimId = player.level().dimension().location().toString();
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(List.of(), false, dimId), player);
        source.sendSuccess(() -> Component.literal("All map overlays cleared.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int showAdminSettings(CommandSourceStack source) {
        com.falazar.farmupcraft.data.WorldData worldData = ModEvents.getWorldData();
        MutableComponent response = Component.literal("--- FarmCraft Admin Settings ---\n")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("useFarms: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(worldData.isUseVillageFarms()) + "\n")
                        .withStyle(worldData.isUseVillageFarms() ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("useBiomeCropRules: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(worldData.isUseBiomeCropRules()) + "\n")
                        .withStyle(worldData.isUseBiomeCropRules() ? ChatFormatting.GREEN : ChatFormatting.RED));
        source.sendSuccess(() -> response, false);
        return 1;
    }

    public static int setAdminSetting(CommandSourceStack source, String setting, boolean value) {
        com.falazar.farmupcraft.database.DataBase<Integer, com.falazar.farmupcraft.data.WorldData> worldDb =
                ModEvents.getWorldDataDatabase();
        com.falazar.farmupcraft.data.WorldData worldData = ModEvents.getWorldData();
        if (setting.equals("useFarms")) {
            worldData.setUseVillageFarms(value);
        } else if (setting.equals("useBiomeCropRules")) {
            worldData.setUseBiomeCropRules(value);
        } else {
            source.sendFailure(Component.literal("Unknown setting: " + setting));
            return 0;
        }
        worldDb.putData(0, worldData);
        source.sendSuccess(() -> Component.literal("Set " + setting + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
