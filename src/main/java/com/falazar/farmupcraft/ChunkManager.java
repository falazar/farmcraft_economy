package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;
import static com.falazar.farmupcraft.command.VillageCommand.findVillageByChunkPos;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkManager {
    public static final CustomLogger LOGGER = new CustomLogger(ChunkManager.class.getSimpleName());

    // Check anytime a player enters a new chunk.
    // Tell if they have entered a village or not.
    @SubscribeEvent
    public static void onPlayerEnterChunk(EntityEvent.EnteringSection event) {
        try {
            // Check if the event is on client side, then skip.
            if (event.getEntity().level().isClientSide) {
                return;
            }

            // Get the player and their current chunk position.
            Entity entity = event.getEntity();
            if (entity == null) {
                return;
            }
            if (!(entity instanceof Player)) {
                return;
            }
            Player player = (Player) event.getEntity();
            // BlockPos pos = player.blockPosition();

            // If changed only y level, skip this notice!!! jumping in a farm triggers a ton
            // of these.
            // Change this later for dungeon areas.
            // What is SectionPos object? Is this a chunk, plus y and others.
            SectionPos oldPos = event.getOldPos();
            SectionPos newPos = event.getNewPos();
            // Log for debugging help.
            // LOGGER.info("DEBUG: onPlayerEnterChunk triggered at position " +
            // pos.toShortString() + " from old position " + oldPos.toShortString() + " to
            // new position " + newPos.toShortString());

            // Get village name from old position, and new position, compare, show if
            // different.
            String lastChunkVillageName = findVillageByChunkPos(oldPos.chunk());
            String currChunkVillageName = findVillageByChunkPos(newPos.chunk());

            // Check for structures in the new section
            checkForStructuresInSection(player, newPos);

            if (currChunkVillageName == null && lastChunkVillageName != null) {
                // Send leaving village message.
                player.displayClientMessage(Component.literal("You left the village of " + lastChunkVillageName),
                        false);
                return;
            } else if (currChunkVillageName != null && lastChunkVillageName == null) {
                // Send entering village message.
                player.displayClientMessage(Component.literal("You entered the village of " + currChunkVillageName),
                        false);
                return;
            } else if (currChunkVillageName != null && lastChunkVillageName != null) {
                // If same village name no message needed
                if (currChunkVillageName.equals(lastChunkVillageName)) {
                    return;
                }
                // Send entering village message.
                player.displayClientMessage(Component.literal("You entered the village of " + currChunkVillageName),
                        false);
                return;
            } else {
                // No change in village locations.
                return;
            }

        } catch (Exception ex) {
            LOGGER.error("Error in onPlayerEnterChunk: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Checks if the given section contains any structures and notifies the player.
     * Also marks structures as visited in the database.
     */
    private static void checkForStructuresInSection(Player player, SectionPos sectionPos) {
        try {
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            // Get the structure database
            var gameStructureDatabase = ModEvents.getGameStructureDatabase(serverLevel);

            // Check all structures in the database to see if any are in this section
            for (Long structureId : gameStructureDatabase.getKeys()) {
                GameStructureData structureData = gameStructureDatabase.getData(structureId);
                if (structureData == null) {
                    continue;
                }

                // Check if this structure is in the current section using SectionPos comparison
                SectionPos structureSection = SectionPos.of(structureData.getCenterPos());
                if (!structureSection.equals(sectionPos)) {
                    continue;
                }

                // Send message to player
                String claimedStatus = structureData.isOnClaimedPlot() ? " (claimed)" : "";
                String visitedStatus = structureData.wasVisited() ? " (visited)" : " (new!)";
                player.displayClientMessage(
                        Component.literal("You discovered: " + structureData.getName() + claimedStatus + visitedStatus),
                        false);

                // Mark as visited if not already (but not in creative mode)
                if (!structureData.wasVisited() && !player.isCreative()) {
                    structureData.setWasVisited(true);

                    // Now that it's been visited, check if it's on a claimed plot and sync that
                    // status.
                    ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
                    ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(structureChunk.toLong());
                    boolean isOnClaimedPlot = chunkData != null && !chunkData.getType().equals("village");
                    structureData.setOnClaimedPlot(isOnClaimedPlot);

                    gameStructureDatabase.putData(structureId, structureData);
                    gameStructureDatabase.setDirty();
                    LOGGER.info(
                            "Marked structure as visited: " + structureData.getName() + " (ID: " + structureId + ")"
                                    + (isOnClaimedPlot ? " [CLAIMED]" : ""));
                }
            }

        } catch (Exception ex) {
            LOGGER.error("Error checking for structures in section: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Check plot type pos is on now.
    public static String getPlotType(BlockPos pos, Level level) {
        ChunkPos chunkPos = new ChunkPos(pos);
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData data = dataBase.getData(chunkPos.toLong());
        if (data == null) {
            // LOGGER.info("DEBUG3: checkPlotType: no data found for chunk at " + chunkPos);
            return "";
        }
        // LOGGER.info("DEBUG3: checkPlotType: found data for chunk at " + chunkPos +
        // " with type " + data.getType());
        return data.getType();
    }

    // Get the current plot we are on now.
    public static ChunkData getPlot(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData data = dataBase.getData(chunkPos.toLong());
        if (data == null) {
            LOGGER.info("DEBUG4: getplot: no data found for chunk at " + chunkPos);
            return null;
        }
        LOGGER.info("DEBUG4: getplot: found data for chunk at " + chunkPos);
        return data;
    }
}
