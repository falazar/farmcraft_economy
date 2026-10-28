package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GameZone;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * ZoneCommand provides commands for managing GameZones.
 * Includes commands to list nearby zones and rename zones.
 */
public class ZoneCommand {
    public static final CustomLogger LOGGER = new CustomLogger(ZoneCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "zone"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("zone");

        // Define the "list" sub-command
        LiteralArgumentBuilder<CommandSourceStack> listBuilder = Commands.literal("list")
                .executes(context -> {
                    // List all zones within 50 chunks
                    return listNearbyZones(context.getSource(), 50, null);
                })
                .then(Commands.argument("type", StringArgumentType.string())
                        .executes(context -> {
                            String typeStr = StringArgumentType.getString(context, "type");
                            return listNearbyZones(context.getSource(), 50, typeStr);
                        })
                );
        builder.then(listBuilder);

        // Define the "rename" sub-command
        LiteralArgumentBuilder<CommandSourceStack> renameBuilder = Commands.literal("rename")
                .then(Commands.argument("oldName", StringArgumentType.string())
                        .then(Commands.argument("newName", StringArgumentType.string())
                                .executes(context -> {
                                    String oldName = StringArgumentType.getString(context, "oldName");
                                    String newName = StringArgumentType.getString(context, "newName");
                                    return renameZone(context.getSource(), oldName, newName);
                                })
                        )
                );
        builder.then(renameBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    /**
     * Lists nearby GameZones within the specified chunk radius, optionally filtered by type.
     * @param source The command source
     * @param chunkRadius The radius in chunks to search
     * @param typeFilter Optional type filter (e.g., "LAKE", "ISLAND")
     * @return Command result
     */
    private static int listNearbyZones(CommandSourceStack source, int chunkRadius, String typeFilter) {
        try {
            Entity entity = source.getEntity();
            if (!(entity instanceof Player player)) {
                source.sendSystemMessage(Component.literal("This command can only be used by players.").withStyle(ChatFormatting.RED));
                return 0;
            }

            BlockPos playerPos = player.blockPosition();
            ChunkPos playerChunk = player.chunkPosition();
            
            DataBase<UUID, GameZone> gameZoneDb = ModEvents.getGameZoneDatabase();
            Collection<GameZone> allZones = gameZoneDb.getValues();

            // Filter zones by type if specified
            List<GameZone> filteredZones = allZones.stream()
                    .filter(zone -> typeFilter == null || zone.getType().name().equalsIgnoreCase(typeFilter))
                    .toList();

            if (filteredZones.isEmpty()) {
                String message = typeFilter != null ? 
                    "No zones of type '" + typeFilter + "' found." : 
                    "No zones found.";
                source.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
                return 0;
            }

                        // Calculate distances and sort by distance
            List<GameZone> nearbyZones = filteredZones.stream()
                    .filter(zone -> {
                        int zoneChunkX = zone.getCenter().getX() >> 4;
                        int zoneChunkZ = zone.getCenter().getZ() >> 4;
                        int distance = Math.abs(zoneChunkX - playerChunk.x) + Math.abs(zoneChunkZ - playerChunk.z);
                        return distance <= chunkRadius;
                    })
                    .sorted(Comparator.comparing(zone -> {
                        int zoneChunkX = zone.getCenter().getX() >> 4;
                        int zoneChunkZ = zone.getCenter().getZ() >> 4;
                        return Math.abs(zoneChunkX - playerChunk.x) + Math.abs(zoneChunkZ - playerChunk.z);
                    }))
                    .toList();

            if (nearbyZones.isEmpty()) {
                String message = typeFilter != null ? 
                    "No zones of type '" + typeFilter + "' found within " + chunkRadius + " chunks." : 
                    "No zones found within " + chunkRadius + " chunks.";
                source.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
                return 0;
            }

            // Display results
            source.sendSystemMessage(Component.literal("Nearby zones:").withStyle(ChatFormatting.GREEN));
            
            for (GameZone zone : nearbyZones) {
                int zoneChunkX = zone.getCenter().getX() >> 4;
                int zoneChunkZ = zone.getCenter().getZ() >> 4;
                int distance = Math.abs(zoneChunkX - playerChunk.x) + Math.abs(zoneChunkZ - playerChunk.z);
                
                MutableComponent zoneMessage = Component.literal(String.format("• %s (%s) - %d chunks away, size: %d sections", 
                        zone.getName(), 
                        zone.getType().name(), 
                        distance, 
                        zone.getSize()))
                        .withStyle(ChatFormatting.WHITE);
                
                // Make clickable for teleport
                zoneMessage.withStyle(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                        "/tp " + zone.getCenter().getX() + " " + zone.getCenter().getY() + " " + zone.getCenter().getZ())
                ));
                
                source.sendSystemMessage(zoneMessage);
            }

            return nearbyZones.size();

        } catch (Exception e) {
            LOGGER.error("Error listing nearby zones: " + e.getMessage());
            source.sendSystemMessage(Component.literal("Error listing zones: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Renames a GameZone by finding it by name and updating it in the database.
     * @param source The command source
     * @param oldName The current name of the zone
     * @param newName The new name for the zone
     * @return Command result
     */
    private static int renameZone(CommandSourceStack source, String oldName, String newName) {
        try {
            DataBase<UUID, GameZone> gameZoneDb = ModEvents.getGameZoneDatabase();
            Collection<GameZone> allZones = gameZoneDb.getValues();

            // Find the zone with the old name
            GameZone zoneToRename = allZones.stream()
                    .filter(zone -> zone.getName().equals(oldName))
                    .findFirst()
                    .orElse(null);

            if (zoneToRename == null) {
                source.sendSystemMessage(Component.literal("Zone '" + oldName + "' not found.").withStyle(ChatFormatting.RED));
                return 0;
            }

            // Check if new name already exists
            boolean nameExists = allZones.stream()
                    .anyMatch(zone -> zone.getName().equals(newName));

            if (nameExists) {
                source.sendSystemMessage(Component.literal("A zone with name '" + newName + "' already exists.").withStyle(ChatFormatting.RED));
                return 0;
            }

            // Create new GameZone with new name
            GameZone renamedZone = new GameZone(
                    zoneToRename.getUuid(),
                    newName,
                    zoneToRename.getSections(),
                    zoneToRename.getType(),
                    zoneToRename.getCenter()
            );

            // Update in database
            gameZoneDb.putData(renamedZone.getUuid(), renamedZone);

            source.sendSystemMessage(Component.literal("Zone '" + oldName + "' renamed to '" + newName + "'.").withStyle(ChatFormatting.GREEN));
            return 1;

        } catch (Exception e) {
            LOGGER.error("Error renaming zone: " + e.getMessage());
            source.sendSystemMessage(Component.literal("Error renaming zone: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
} 