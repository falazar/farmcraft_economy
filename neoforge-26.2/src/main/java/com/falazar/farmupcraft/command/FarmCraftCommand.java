package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.StructureManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.events.WorldScheduler;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.StructureUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FarmCraftCommand {
    public static final CustomLogger LOGGER = new CustomLogger(FarmCraftCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("farmupcraft").executes(ctx -> showHelp(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource()))));
        var root = Commands.literal("farmcraft")
                .then(Commands.literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(Commands.literal("scheduler").executes(ctx -> showSchedulerInfo(ctx.getSource())))
                .then(Commands.literal("runchecks").requires(FarmCraftCommand::isAdmin)
                        .executes(ctx -> runHourlyChecks(ctx.getSource())))
                .then(Commands.literal("lan").executes(ctx -> toggleLanNotice(ctx.getSource())));
        var map = Commands.literal("map").requires(FarmCraftCommand::isAdmin)
                .then(Commands.literal("waypoint")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> addMapWaypoint(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "name"))))))))
                .then(Commands.literal("chunk")
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                                .executes(ctx -> highlightChunk(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkZ"),
                                                        StringArgumentType.getString(ctx, "label")))))))
                .then(Commands.literal("here")
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(ctx -> highlightHereChunk(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "label")))))
                .then(Commands.literal("village").executes(ctx -> showVillageChunks(ctx.getSource()))
                        .then(Commands.literal("clear").executes(ctx -> clearVillageChunks(ctx.getSource()))))
                .then(Commands.literal("clearwaypoints")
                        .executes(ctx -> clearModWaypoints(ctx.getSource(), false, 20))
                        .then(Commands.literal("test")
                                .executes(ctx -> clearModWaypoints(ctx.getSource(), true, 20))
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> clearModWaypoints(ctx.getSource(), true,
                                                IntegerArgumentType.getInteger(ctx, "chunks")))))
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> clearModWaypoints(ctx.getSource(), false,
                                        IntegerArgumentType.getInteger(ctx, "chunks")))))
                .then(Commands.literal("removenearestwaypoint")
                        .executes(ctx -> removeNearestWaypoint(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clearAllMapOverlays(ctx.getSource())));
        var settings = Commands.literal("settings").executes(ctx -> showAdminSettings(ctx.getSource()));
        for (String setting : List.of("useFarms", "useBiomeCropRules", "lycaniteLightBlock", "mobTorchBreaking")) {
            settings.then(Commands.literal(setting)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setAdminSetting(ctx.getSource(), setting,
                                    BoolArgumentType.getBool(ctx, "value")))));
        }
        dispatcher.register(root.then(map)
                .then(Commands.literal("admin").requires(FarmCraftCommand::isAdmin).then(settings)));
        dispatcher.register(Commands.literal("frecipe")
                .then(Commands.argument("itemId", StringArgumentType.greedyString())
                        .executes(ctx -> openRecipeForPlayer(ctx.getSource(),
                                StringArgumentType.getString(ctx, "itemId")))));
    }

    public static int showHelp(CommandSourceStack source) {
        for (String line : com.falazar.farmupcraft.util.PlayerOnboarding.HELP) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static ServerPlayer player(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("Must be a player."));
        return null;
    }

    public static int toggleLanNotice(CommandSourceStack source) {
        if (player(source) == null) return 0;
        var database = ModEvents.getWorldDataDatabase(source.getLevel());
        WorldData data = database.getOrCreate(0, WorldData::new);
        boolean enabled = !data.isLanNoticeEnabled();
        data.setLanNoticeEnabled(enabled);
        database.putData(0, data);
        source.sendSuccess(() -> Component.literal("LAN notice " + (enabled ? "enabled." : "disabled."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    public static void displayLanNotice(Player player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer().isPublished()) return;
        WorldData data = ModEvents.getWorldDataDatabase(level).getOrCreate(0, WorldData::new);
        if (!data.isLanNoticeEnabled()) return;
        player.sendSystemMessage(Component.literal(
                "You are not Open To LAN. Use /farmcraft lan to toggle this notice.")
                .withStyle(ChatFormatting.YELLOW));
    }

    public static int showSchedulerInfo(CommandSourceStack source) {
        int seconds = WorldScheduler.getTicksUntilNextRun() / 20;
        WorldData data = ModEvents.getWorldDataDatabase(source.getLevel()).getOrCreate(0, WorldData::new);
        MutableComponent response = Component.literal("--- FarmCraft Scheduler ---\n")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Next hourly run in: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(seconds / 60 + "m " + seconds % 60 + "s\n")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("Last market daily: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(data.getLastRanMarketDaily() + "\n")
                        .withStyle(data.hasMarketRanTodayAlready() ? ChatFormatting.GREEN : ChatFormatting.RED));
        source.sendSuccess(() -> response, false);
        return 0;
    }

    public static int runHourlyChecks(CommandSourceStack source) {
        WorldScheduler.runHourlyChecks();
        source.sendSuccess(() -> Component.literal("Hourly checks triggered manually.")
                .withStyle(ChatFormatting.GREEN), false);
        return 0;
    }

    public static int openRecipeForPlayer(CommandSourceStack source, String itemId) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        EDBMessages.sendToPlayer(new OpenJeiRecipePacket(itemId), player);
        return 1;
    }

    public static int clearModWaypoints(CommandSourceStack source, boolean testOnly, int chunks) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        int blocks = chunks * 16;
        EDBMessages.sendToPlayer(new RemoveModWaypointsPacket(
                player.getX(), player.getY(), player.getZ(), blocks, testOnly), player);
        source.sendSuccess(() -> Component.literal((testOnly ? "[TEST] Scanning" : "Removing")
                + " mod waypoints within " + chunks + " chunks (" + blocks + " blocks)...")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    public static int removeNearestWaypoint(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        EDBMessages.sendToPlayer(new RemoveModWaypointsPacket(
                player.getX(), player.getY(), player.getZ(), 16, false, null, true), player);
        source.sendSuccess(() -> Component.literal("Removing nearest mod waypoint around you...")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    public static int addMapWaypoint(CommandSourceStack source, int x, int y, int z, String name) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        EDBMessages.sendToPlayer(new AddJMWaypointPacket(x, y, z, name,
                player.level().dimension().identifier().toString()), player);
        source.sendSuccess(() -> Component.literal(
                "Waypoint \"" + name + "\" added at " + x + ", " + y + ", " + z + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int highlightChunk(CommandSourceStack source, int chunkX, int chunkZ, String label) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        EDBMessages.sendToPlayer(new HighlightChunkPacket(chunkX, chunkZ, label,
                player.level().dimension().identifier().toString(), 0x00FF00), player);
        source.sendSuccess(() -> Component.literal(
                "Chunk [" + chunkX + ", " + chunkZ + "] highlighted as \"" + label + "\".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int highlightHereChunk(CommandSourceStack source, String label) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        ChunkPos pos = ChunkPos.containing(player.blockPosition());
        EDBMessages.sendToPlayer(new HighlightChunkPacket(pos.x(), pos.z(), label,
                player.level().dimension().identifier().toString(), 0x00FF00), player);
        source.sendSuccess(() -> Component.literal("Your current chunk [" + pos.x() + ", " + pos.z()
                + "] highlighted as \"" + label + "\".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int showVillageChunks(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        List<int[]> chunks = buildVillageChunkOverlayData(player);
        if (chunks == null) {
            source.sendFailure(Component.literal("You are not a member of any village."));
            return 0;
        }
        setVillageMapPreference(player, true);
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(chunks, true,
                player.level().dimension().identifier().toString()), player);
        source.sendSuccess(() -> Component.literal("Showing " + chunks.size()
                + " village chunks on map. Use /village map clear to remove.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static void refreshVillageChunksOverlay(ServerPlayer player) {
        PlayerData data = ModEvents.getPlayerDatabase(player.level()).getData(player.getUUID());
        if (data == null || !data.isVillageMapShow()) return;
        List<int[]> chunks = buildVillageChunkOverlayData(player);
        if (chunks == null) return;
        String dimension = player.level().dimension().identifier().toString();
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(List.of(), false, dimension), player);
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(chunks, true, dimension), player);
    }

    static List<int[]> buildVillageChunkOverlayData(ServerPlayer player) {
        DataBase<UUID, PlayerData> players = ModEvents.getPlayerDatabase(player.level());
        PlayerData data = players.getData(player.getUUID());
        if (data == null || data.getHomeVillageUUID() == null) return null;
        var village = ModEvents.getVillageDatabase(player.level()).getData(data.getHomeVillageUUID());
        if (village == null) return null;
        DataBase<Long, ChunkData> database = ModEvents.getChunkDataDatabase(player.level());
        List<int[]> chunks = new ArrayList<>();
        for (ChunkPos pos : village.getClaimedChunks()) {
            ChunkData chunk = database.getData(pos.pack());
            String type = chunk == null ? "village" : chunk.getType();
            boolean plot = pos.equals(village.getPosition()) || !"village".equalsIgnoreCase(type);
            chunks.add(new int[]{pos.x(), pos.z(), plot ? 0x0055FF : 0x00E000});
        }
        return chunks;
    }

    public static int clearVillageChunks(CommandSourceStack source) {
        return clearOverlays(source, "Village chunk overlays cleared.");
    }

    public static int clearAllMapOverlays(CommandSourceStack source) {
        return clearOverlays(source, "All map overlays cleared.");
    }

    private static int clearOverlays(CommandSourceStack source, String message) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        setVillageMapPreference(player, false);
        EDBMessages.sendToPlayer(new ShowVillageChunksPacket(List.of(), false,
                player.level().dimension().identifier().toString()), player);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void setVillageMapPreference(ServerPlayer player, boolean show) {
        var database = ModEvents.getPlayerDatabase(player.level());
        PlayerData data = database.getData(player.getUUID());
        if (data == null) throw new IllegalStateException("Missing player data for " + player.getUUID());
        data.setVillageMapShow(show);
        database.putData(player.getUUID(), data);
    }

    public static int showAdminSettings(CommandSourceStack source) {
        WorldData data = ModEvents.getWorldDataDatabase(source.getLevel()).getOrCreate(0, WorldData::new);
        MutableComponent response = adminSettings(data);
        source.sendSuccess(() -> response, false);
        return 1;
    }

    static MutableComponent adminSettings(WorldData data) {
        MutableComponent response = Component.literal("--- FarmCraft Admin Settings ---\n")
                .withStyle(ChatFormatting.YELLOW);
        appendSetting(response, "useFarms", data.isUseVillageFarms());
        appendSetting(response, "useBiomeCropRules", data.isUseBiomeCropRules());
        appendSetting(response, "lycaniteLightBlock", data.isLycaniteLightBlock());
        appendSetting(response, "mobTorchBreaking", data.isMobTorchBreaking());
        return response;
    }

    private static void appendSetting(MutableComponent response, String name, boolean value) {
        response.append(Component.literal(name + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(value + "\n")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static int setAdminSetting(CommandSourceStack source, String setting, boolean value) {
        var database = ModEvents.getWorldDataDatabase(source.getLevel());
        if (!setAdminSetting(database, setting, value)) {
            source.sendFailure(Component.literal("Unknown setting: " + setting));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Set " + setting + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static boolean setAdminSetting(DataBase<Integer, WorldData> database, String setting, boolean value) {
        WorldData data = database.getOrCreate(0, WorldData::new);
        switch (setting) {
            case "useFarms" -> data.setUseVillageFarms(value);
            case "useBiomeCropRules" -> data.setUseBiomeCropRules(value);
            case "lycaniteLightBlock" -> data.setLycaniteLightBlock(value);
            case "mobTorchBreaking" -> data.setMobTorchBreaking(value);
            default -> {
                return false;
            }
        }
        database.putData(0, data);
        return true;
    }

    public static int showNearestStructureDetail(CommandSourceStack source, int index, boolean searchChests) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        var database = ModEvents.getGameStructureDatabase(level);
        if (database.getSize() == 0) {
            source.sendSystemMessage(Component.literal("No structures in database.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        BlockPos playerPos = player.blockPosition();
        List<Map.Entry<Long, GameStructureData>> all = new ArrayList<>();
        for (Long key : database.getKeys()) {
            GameStructureData data = database.getData(key);
            if (data != null) all.add(Map.entry(key, data));
        }
        all.sort(Comparator.comparingDouble(entry -> entry.getValue().getCenterPos().distSqr(playerPos)));
        if (index < 1 || index > all.size()) {
            source.sendFailure(Component.literal(
                    "Index " + index + " out of range — DB has " + all.size() + " structure(s)."));
            return 0;
        }
        var entry = all.get(index - 1);
        GameStructureData data = entry.getValue();
        int distance = (int) Math.sqrt(data.getCenterPos().distSqr(playerPos));
        detail(source, "=== Structure #" + index + " of " + all.size() + " (nearest) ===", ChatFormatting.GOLD);
        detail(source, "Name:   " + data.getName(), ChatFormatting.WHITE);
        detail(source, "Type:   " + data.getType(), ChatFormatting.WHITE);
        detail(source, "ID:     " + entry.getKey(), ChatFormatting.WHITE);
        detail(source, "Center: " + data.getCenterPos().toShortString() + "  (d=" + distance + ")", ChatFormatting.WHITE);
        detail(source, "Claimed:" + data.isOnClaimedPlot() + "  Visited:" + data.wasVisited(), ChatFormatting.WHITE);
        if (data.hasBoundingBox()) {
            BlockPos min = data.getMinPos(), max = data.getMaxPos();
            detail(source, "BBox:   min=" + min.toShortString() + "  max=" + max.toShortString(), ChatFormatting.AQUA);
            detail(source, "Size:   " + (max.getX() - min.getX()) + "x" + (max.getY() - min.getY())
                    + "x" + (max.getZ() - min.getZ()), ChatFormatting.AQUA);
        } else {
            detail(source, "BBox:   (missing — run /village structures rescan near this location)", ChatFormatting.YELLOW);
        }
        if (data.hasChunkPositions()) {
            StringBuilder chunks = new StringBuilder();
            for (long packed : data.getChunkPositions()) {
                ChunkPos pos = ChunkPos.unpack(packed);
                if (!chunks.isEmpty()) chunks.append("  ");
                chunks.append("[").append(pos.x()).append(",").append(pos.z()).append("]");
            }
            detail(source, "Chunks: " + data.getChunkPositions().size() + " — " + chunks, ChatFormatting.AQUA);
        }
        if (data.hasSpecialChest()) {
            detail(source, "SpecialChest: " + data.getSpecialChestPos().toShortString() + "  opened="
                    + data.isSpecialChestOpened() + "  totalChests=" + data.getTotalChestCount(), ChatFormatting.LIGHT_PURPLE);
        } else {
            detail(source, "SpecialChest: none set", ChatFormatting.GRAY);
        }
        // Legacy detail always scans chests, including calls without the optional "chests" argument.
        VillageCommand.scanAndShowStructureChests(source, data, level);
        return 1;
    }

    private static void detail(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static int setupSpecialChestCommand(CommandSourceStack source) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        HitResult hit = player.pick(5.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Not looking at a block."));
            return 0;
        }
        BlockPos chestPos = blockHit.getBlockPos().immutable();
        if (!StructureManager.isStructureChest(level.getBlockState(chestPos).getBlock())) {
            source.sendFailure(Component.literal("Look at a chest or barrel first."));
            return 0;
        }
        var database = ModEvents.getGameStructureDatabase(level);
        GameStructureData data = StructureUtils.findStructureForPlayer(database, player);
        if (data == null) {
            source.sendFailure(Component.literal("You are not standing inside a known structure (no bbox match)."));
            return 0;
        }
        boolean placed = StructureManager.setupSpecialChest(level, data, List.of(chestPos), database);
        if (placed) {
            detail(source, "Special chest set at " + data.getSpecialChestPos().toShortString()
                    + " in " + data.getName(), ChatFormatting.GREEN);
        } else {
            source.sendFailure(Component.literal("Failed to place special chest — check log."));
        }
        return placed ? 1 : 0;
    }
}
