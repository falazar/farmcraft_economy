package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GameZone;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Lists nearby zones and replaces named zone records without changing their identity. */
public class ZoneCommand {
    public static final CustomLogger LOGGER = new CustomLogger(ZoneCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zone")
                .then(Commands.literal("list")
                        .executes(context -> listNearbyZones(context.getSource(), 50, null))
                        .then(Commands.argument("type", StringArgumentType.string())
                                .executes(context -> listNearbyZones(context.getSource(), 50,
                                        StringArgumentType.getString(context, "type")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("oldName", StringArgumentType.string())
                                .then(Commands.argument("newName", StringArgumentType.string())
                                        .executes(context -> renameZone(context.getSource(),
                                                StringArgumentType.getString(context, "oldName"),
                                                StringArgumentType.getString(context, "newName")))))));
    }

    static long chunkDistance(GameZone zone, ChunkPos origin) {
        ChunkPos center = ChunkPos.containing(zone.getCenter());
        return Math.abs((long) center.x() - origin.x()) + Math.abs((long) center.z() - origin.z());
    }

    static List<GameZone> nearbyZones(Collection<GameZone> zones, ChunkPos origin, int radius, String type) {
        if (radius < 0) throw new IllegalArgumentException("Chunk radius must not be negative");
        return zones.stream()
                .filter(zone -> type == null || zone.getType().name().equalsIgnoreCase(type))
                .filter(zone -> chunkDistance(zone, origin) <= radius)
                .sorted(Comparator.comparingLong(zone -> chunkDistance(zone, origin)))
                .toList();
    }

    private static int listNearbyZones(CommandSourceStack source, int radius, String type) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        Collection<GameZone> all = ModEvents.getGameZoneDatabase(source.getLevel()).getValues();
        boolean hasType = all.stream().anyMatch(zone -> type == null || zone.getType().name().equalsIgnoreCase(type));
        if (!hasType) {
            source.sendSystemMessage(Component.literal(type == null ? "No zones found."
                    : "No zones of type '" + type + "' found.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        ChunkPos origin = player.chunkPosition();
        List<GameZone> nearby = nearbyZones(all, origin, radius, type);
        if (nearby.isEmpty()) {
            source.sendSystemMessage(Component.literal(type == null
                    ? "No zones found within " + radius + " chunks."
                    : "No zones of type '" + type + "' found within " + radius + " chunks.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Nearby zones:").withStyle(ChatFormatting.GREEN));
        for (GameZone zone : nearby) {
            var center = zone.getCenter();
            source.sendSystemMessage(Component.literal(String.format("• %s (%s) - %d chunks away, size: %d sections",
                            zone.getName(), zone.getType().name(), chunkDistance(zone, origin), zone.getSize()))
                    .withStyle(ChatFormatting.WHITE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                            "/tp " + center.getX() + " " + center.getY() + " " + center.getZ()))));
        }
        return nearby.size();
    }

    /** Validates against the same snapshot used for name lookup; no database mutation on failure. */
    static GameZone renamedZone(Collection<GameZone> zones, String oldName, String newName) {
        GameZone original = zones.stream().filter(zone -> zone.getName().equals(oldName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Zone '" + oldName + "' not found."));
        if (zones.stream().anyMatch(zone -> zone.getName().equals(newName))) {
            throw new IllegalArgumentException("A zone with name '" + newName + "' already exists.");
        }
        return new GameZone(original.getUuid(), newName, original.getSections(), original.getType(), original.getCenter());
    }

    private static int renameZone(CommandSourceStack source, String oldName, String newName) {
        DataBase<UUID, GameZone> database = ModEvents.getGameZoneDatabase(source.getLevel());
        GameZone renamed;
        try {
            renamed = renamedZone(database.getValues(), oldName, newName);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        database.putData(renamed.getUuid(), renamed);
        source.sendSystemMessage(Component.literal("Zone '" + oldName + "' renamed to '" + newName + "'.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
