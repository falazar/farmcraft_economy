package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;
import static com.falazar.farmupcraft.command.VillageCommand.findVillageByChunkPos;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkManager {
    public static final CustomLogger LOGGER = new CustomLogger(ChunkManager.class.getSimpleName());

    /**
     * Tracks the last time (ms) each player was shown the village-center
     * description.
     */
    private static final java.util.Map<java.util.UUID, Long> VILLAGE_CENTER_SHOWN_AT = new java.util.HashMap<>();
    private static final long VILLAGE_CENTER_COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour

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
     * Also shows the village-center description (at most once per hour).
     */
    private static void checkForStructuresInSection(Player player, SectionPos sectionPos) {
        try {
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            // Show village center description when entering that chunk (throttled to
            // 1/hour).
            ChunkPos enteredChunk = sectionPos.chunk();
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData enteredChunkData = chunkDb.getData(enteredChunk.toLong());
            if (enteredChunkData != null && enteredChunkData.getType().equalsIgnoreCase("village center")) {
                long now = System.currentTimeMillis();
                Long lastShown = VILLAGE_CENTER_SHOWN_AT.get(player.getUUID());
                if (lastShown == null || (now - lastShown) >= VILLAGE_CENTER_COOLDOWN_MS) {
                    VILLAGE_CENTER_SHOWN_AT.put(player.getUUID(), now);
                    java.util.UUID villageId = enteredChunkData.getVillageId();
                    if (villageId != null) {
                        VillageData village = ModEvents.getVillageDatabase().getData(villageId);
                        if (village != null) {
                            net.minecraft.network.chat.MutableComponent msg = Component
                                    .literal("--- " + village.getName() + " ---")
                                    .withStyle(ChatFormatting.YELLOW);
                            msg = msg.append(Component.literal("\nLevel: " + village.getLevel())
                                    .withStyle(ChatFormatting.WHITE));
                            if (!village.getFounder().isEmpty()) {
                                msg = msg.append(Component.literal("  |  Founded by: " + village.getFounder())
                                        .withStyle(ChatFormatting.WHITE));
                            }
                            if (!village.getAnimalType().isEmpty()) {
                                msg = msg.append(Component.literal("\nVillage animal: " + village.getAnimalType())
                                        .withStyle(ChatFormatting.WHITE));
                            }
                            msg = msg.append(Component.literal("\nChunks: " + village.getClaimedChunks().size())
                                    .withStyle(ChatFormatting.GRAY));
                            player.displayClientMessage(msg, false);
                        }
                    }
                }
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

    private static final int DEFAULT_PLOT_PROTECTION_MIN_Y = 60;

    // Prevent non-owners from opening doors, trapdoors, and chests in owned house
    // plots.
    @SubscribeEvent
    public static void onRightClickPlotBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        BlockPos pos = event.getPos();

        ChunkPos chunkPos = new ChunkPos(pos);
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = dataBase.getData(chunkPos.toLong());

        // Prevent water/lava bucket griefing in villages the player does not belong to.
        if (chunkData != null && isRestrictedBucket(event.getItemStack())
                && !isPlayerVillageMember(player, chunkData)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You are not a member of this village.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // Only check protected block types.
        BlockState state = event.getLevel().getBlockState(pos);
        if (!isProtectedAccessBlock(state))
            return;

        // Get chunk data for this plot.
        if (chunkData == null)
            return;

        // Only check at/above the stored purchase Y to avoid underground interference.
        int protectionMinY = chunkData.getBoughtY() > 0 ? chunkData.getBoughtY() : DEFAULT_PLOT_PROTECTION_MIN_Y;
        if (pos.getY() < protectionMinY)
            return;

        // Creative/admin players are exempt from all restrictions.
        if (player.isCreative())
            return;

        // Village membership check: block anyone not in this village from using
        // doors/chests.
        if (!isPlayerVillageMember(player, chunkData)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You are not a member of this village.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // House plot owner/visitor check: within the village, only the owner and named
        // visitors
        // may open doors and chests on a house plot.
        if (chunkData.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, chunkData, dataBase, chunkPos)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You do not have permission on this house plot.")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    // Prevent non-members from placing blocks in village plots, and enforce house
    // owner/visitor permissions on house plots.
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        if (player.isCreative())
            return;

        BlockPos pos = event.getPos();

        // Prevent stacked rock paths (placing a rock path on top of another rock path).
        BlockState placedState = event.getLevel().getBlockState(pos);
        BlockState belowState = event.getLevel().getBlockState(pos.below());
        if (isRockPathBlock(placedState) && isRockPathBlock(belowState)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You cannot stack rock paths.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // Prevent placing logs in nursery plots to avoid double bonuses.
        BlockState placingState = event.getPlacedBlock();
        if (placingState.is(BlockTags.LOGS)) {
            Level level = player.getCommandSenderWorld();
            if (getPlotType(pos, level).equals("nursery")) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component.literal("You cannot place logs in a nursery plot.")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = dataBase.getData(chunkPos.toLong());
        if (chunkData == null)
            return;

        int protectionMinY = chunkData.getBoughtY() > 0 ? chunkData.getBoughtY() : DEFAULT_PLOT_PROTECTION_MIN_Y;
        if (pos.getY() < protectionMinY)
            return;

        if (!isPlayerVillageMember(player, chunkData)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You are not a member of this village.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        if (chunkData.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, chunkData, dataBase, chunkPos)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You do not have permission to place blocks on this house plot.")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    // Prevent non-members from breaking blocks in village plots, and enforce house
    // owner/visitor permissions on house plots.
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide())
            return;

        Player player = event.getPlayer();
        if (player == null || player.isCreative())
            return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = dataBase.getData(chunkPos.toLong());
        if (chunkData == null)
            return;

        int protectionMinY = chunkData.getBoughtY() > 0 ? chunkData.getBoughtY() : DEFAULT_PLOT_PROTECTION_MIN_Y;
        if (pos.getY() < protectionMinY)
            return;

        if (!isPlayerVillageMember(player, chunkData)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("You are not a member of this village.")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        if (chunkData.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, chunkData, dataBase, chunkPos)) {
            event.setCanceled(true);
            player.displayClientMessage(
                Component.literal("You do not have permission to break blocks on this house plot.")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    private static boolean isProtectedAccessBlock(BlockState state) {
        boolean isDoor = state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS);
        boolean isGate = isGateBlock(state);
        boolean isContainer = state.is(BlockTags.SHULKER_BOXES) || isChestOrBarrel(state);
        return isDoor || isGate || isContainer;
    }

    private static boolean isGateBlock(BlockState state) {
        if (state.is(BlockTags.FENCE_GATES))
            return true;

        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();
        return id.contains("fence_gate")
                || id.contains("upgrade_gate")
                || id.contains("picket_gate");
    }

    private static boolean isPlayerVillageMember(Player player, ChunkData chunkData) {
        java.util.UUID chunkVillageId = chunkData.getVillageId();
        if (chunkVillageId == null)
            return true;
        PlayerData playerData = ModEvents.getPlayerDatabase().getData(player.getUUID());
        return playerData != null && chunkVillageId.equals(playerData.getHomeVillageUUID());
    }

    private static boolean hasHouseAccess(Player player, ChunkData chunkData, DataBase<Long, ChunkData> dataBase,
            ChunkPos chunkPos) {
        java.util.UUID ownerUUID = chunkData.getOwnerUUID();

        // Legacy migration: older house plots may not have owner_uuid saved.
        if (ownerUUID == null && chunkData.getPlayerId() == player.getId()) {
            chunkData.setOwnerUUID(player.getUUID());
            dataBase.putData(chunkPos.toLong(), chunkData);
            ownerUUID = player.getUUID();
        }

        if (ownerUUID == null) {
            return chunkData.isVisitor(player.getName().getString());
        }
        if (ownerUUID.equals(player.getUUID())) {
            return true;
        }
        return chunkData.isVisitor(player.getName().getString());
    }

    private static boolean isChestOrBarrel(BlockState state) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();
        return id.contains("chest") || id.contains("barrel");
    }

    private static boolean isRockPathBlock(BlockState state) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();
        return id.contains("rock") && id.contains("path");
    }

    private static boolean isRestrictedBucket(ItemStack stack) {
        return stack != null && (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET));
    }
}
