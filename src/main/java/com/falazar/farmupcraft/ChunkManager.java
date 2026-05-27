package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.falazar.farmupcraft.command.StructureCommand;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;
import static com.falazar.farmupcraft.command.VillageCommand.findVillageByChunkPos;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkManager {
    public static final CustomLogger LOGGER = new CustomLogger(ChunkManager.class.getSimpleName());

    // ============================================================
    // LYCANITES MOB TUNING
    // Centralised place for spawn/drop rate adjustments.
    // TODO: Later — replace flat rates with a per-mob named list, e.g.:
    // private static final Map<String, Double> LYCANITES_MOB_SPAWN_RATES =
    // Map.of("lycanitesmobs:conba", 0.25, "lycanitesmobs:specter", 0.10);
    // then look up by entityId.toString() and fall back to the default rate.
    // ============================================================
    private static final String LYCANITES_MODID = "lycanitesmobs";
    /** 50 % of all Lycanites spawns are cancelled globally. */
    private static final double LYCANITES_SPAWN_RATE = 0.50;
    /**
     * Lycanites mobs that are always blocked in village territory regardless of the
     * 50% roll — typically water/air hostiles that wander onto bought plots.
     */
    private static final java.util.Set<String> LYCANITES_HIGH_BLOCK = java.util.Set.of(
            "lycanitesmobs:jengu",
            "lycanitesmobs:vespidqueen");
    /**
     * Each Lycanites drop stack is multiplied by this and floored (1 -> 0 = no
     * drop).
     */
    private static final double LYCANITES_DROP_RATE = 0.50;
    private static final java.util.Random LYCANITES_RAND = new java.util.Random();
    /**
     * Tracks recent Lycanites mob death positions so item entities spawned by their
     * custom drop
     * pipeline can be intercepted in onEntityJoinLevel (Lycanites bypasses
     * LivingDropsEvent).
     */
    private static final java.util.concurrent.ConcurrentLinkedDeque<long[]> LYCANITES_DEATH_POS = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final long DEATH_WINDOW_MS = 3_000L; // 3 seconds

    /**
     * Tracks the last time (ms) each player was shown the village-center
     * description.
     */
    private static final java.util.Map<java.util.UUID, Long> VILLAGE_CENTER_SHOWN_AT = new java.util.HashMap<>();
    private static final long VILLAGE_CENTER_COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour

    /**
     * Tracks the last time (ms) each player triggered a wilderness structure scan.
     * Used to enforce a cooldown so the 5 % per-chunk roll can't fire too often.
     */
    private static final java.util.Map<java.util.UUID, Long> WILDERNESS_SCAN_LAST_AT = new java.util.HashMap<>();
    private static final long WILDERNESS_SCAN_COOLDOWN_MS = 30_000L; // 30 seconds
    private static final java.util.Random WILDERNESS_RAND = new java.util.Random();

    /** Returns true if this player should receive [DEBUG] chat messages. */
    public static boolean isDebugPlayer(net.minecraft.world.entity.player.Player player) {
        return player.getGameProfile().getName().equalsIgnoreCase("BossPanda96366");
    }

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

            // --- Wilderness structure discovery ---
            // 5 % chance per new XZ-chunk entered while outside any village.
            // Radius-10 scan is fast: it only queries already-loaded structure data.
            boolean changedXZChunk = !newPos.chunk().equals(oldPos.chunk());
            // 5% chance per new XZ-chunk entered while outside any village.
            if (changedXZChunk && currChunkVillageName == null && WILDERNESS_RAND.nextFloat() < 0.05f) {
                long nowMs = System.currentTimeMillis();
                Long lastScan = WILDERNESS_SCAN_LAST_AT.get(player.getUUID());
                if (lastScan == null || (nowMs - lastScan) >= WILDERNESS_SCAN_COOLDOWN_MS) {
                    WILDERNESS_SCAN_LAST_AT.put(player.getUUID(), nowMs);
                    if (isDebugPlayer(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[DEBUG] Wilderness scan triggered").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    }
                    if (player.level() instanceof ServerLevel scanLevel) {
                        try {
                            java.util.List<com.falazar.farmupcraft.command.StructureCommand.StructureInfo> found = StructureCommand
                                    .findNearbyStructuresForVillage(
                                            player.blockPosition(), scanLevel, player, null, "all", 10);
                            if (!found.isEmpty()) {
                                // Fake a CommandSourceStack-compatible source for chest scanning.
                                StructureCommand.scanAndSetupChests(
                                        player.createCommandSourceStack(), found, scanLevel);
                            }
                        } catch (Exception ex) {
                            LOGGER.error("Error in wilderness structure scan: " + ex.getMessage());
                        }
                    }
                }
            }

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

    /**
     * Returns true if any of the 8 surrounding chunks (or the chunk itself) is a
     * claimed plot (has non-empty ChunkData type). Used to extend spawn blocking
     * one chunk beyond the plot border so mobs can't spawn just outside and walk
     * in.
     */
    private static boolean isAdjacentToBoughtPlot(ChunkPos center) {
        DataBase<Long, ChunkData> db = ModEvents.getChunkDataDatabase();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos neighbor = new ChunkPos(center.x + dx, center.z + dz);
                ChunkData data = db.getData(neighbor.toLong());
                if (data != null && !data.getType().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final int DEFAULT_PLOT_PROTECTION_MIN_Y = 56;

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
            // Resync the block and inventory to the client to prevent ghost-block glitch.
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
                                event.getLevel(), pos));
                serverPlayer.inventoryMenu.sendAllDataToRemote();
            }
            return;
        }

        // Prevent placing logs in nursery plots to avoid double bonuses.
        // Only block manual placement (player holding a log item in either hand) —
        // not tree growth from bonemeal, where neither hand holds a log.
        BlockState placingState = event.getPlacedBlock();
        if (placingState.is(BlockTags.LOGS)) {
            boolean holdingLog = isHoldingLog(player.getMainHandItem())
                    || isHoldingLog(player.getOffhandItem());
            if (holdingLog) {
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

    private static boolean isHoldingLog(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi))
            return false;
        return bi.getBlock().defaultBlockState().is(BlockTags.LOGS);
    }

    private static boolean isRestrictedBucket(ItemStack stack) {
        return stack != null && (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET));
    }

    /**
     * Prevent modded hostile mobs (e.g. Lycanites) from spawning naturally in any
     * claimed village plot chunk, but only above Y=45. Vanilla mobs are not
     * blocked.
     */
    @SubscribeEvent
    public static void onMobSpawnCheck(MobSpawnEvent.FinalizeSpawn event) {
        try {
            if (event.getLevel().isClientSide())
                return;

            net.minecraft.world.entity.LivingEntity entity = event.getEntity();
            BlockPos pos = entity.blockPosition();

            // Only apply above Y=45.
            if (pos.getY() < 45)
                return;

            // Only block modded mobs — skip anything from the "minecraft" namespace.
            net.minecraft.resources.ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(entity.getType());
            if (entityId == null || "minecraft".equals(entityId.getNamespace()))
                return;

            // ---- LYCANITES GLOBAL 50 % SPAWN RATE ----
            // Cancel half of all Lycanites spawns regardless of location or hostility.
            if (LYCANITES_MODID.equals(entityId.getNamespace())) {
                // Jengu gets a stricter 90% global cancel — it walks into plots from unclaimed
                // chunks.
                if (LYCANITES_HIGH_BLOCK.contains(entityId.toString())) {
                    if (LYCANITES_RAND.nextDouble() >= 0.10) {
                        event.setSpawnCancelled(true);
                        // LOGGER.info("[SPAWN] Lycanites 90% suppressed (jengu): " + entityId + " |
                        // uuid="
                        // + entity.getUUID() + " | pos=" + pos);
                        return;
                    }
                    // LOGGER.info("[SPAWN] ALLOWED jengu (10% passed global roll): " + entityId + "
                    // | uuid="
                    // + entity.getUUID() + " | pos=" + pos);
                } else if (LYCANITES_RAND.nextDouble() >= LYCANITES_SPAWN_RATE) {
                    event.setSpawnCancelled(true);
                    // LOGGER.info("[SPAWN] Lycanites 50% suppressed: " + entityId + " at " + pos);
                    return;
                }

                // ---- LYCANITES TORCHLIGHT BLOCK ----
                // When enabled (default ON), no Lycanites mob — hostile or not — may spawn
                // in a well-lit area (block light >= 8), mirroring vanilla mob rules.
                com.falazar.farmupcraft.data.WorldData _wd = com.falazar.farmupcraft.events.ModEvents.getWorldData();
                if (_wd != null && _wd.isLycaniteLightBlock()
                        && event.getLevel() instanceof Level _lightLevel) {
                    int bLight = _lightLevel.getBrightness(LightLayer.BLOCK, pos);
                    if (bLight >= 8) {
                        event.setSpawnCancelled(true);
                        return;
                    }
                }
            }

            // Log all modded mob spawn attempts so we can see what's around.
            // Use same hostile check as onEntityJoinLevel — some Lycanites mobs (e.g.
            // jengu)
            // use CREATURE category but implement Monster/Enemy interface.
            boolean isHostile = entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
                    || entity instanceof net.minecraft.world.entity.monster.Monster
                    || entity instanceof net.minecraft.world.entity.monster.Enemy;
            // During worldgen feature placement the level is a WorldGenRegion, not a Level
            // — skip safely.
            if (!(event.getLevel() instanceof Level worldLevel))
                return;

            // Block modded mobs that are hostile (MONSTER category OR Monster/Enemy
            // interface).
            if (!isHostile)
                return;

            // Allow spawns in dark areas (block light = 0) — players can still have
            // mob-spawning cellars/caves inside village/plot chunks by leaving them unlit.
            int blockLight = worldLevel.getBrightness(LightLayer.BLOCK, pos);
            if (blockLight == 0)
                return;

            // getPlotType returns "" for unowned/unclaimed chunks.
            String plotType = getPlotType(pos, worldLevel);
            if (!plotType.isEmpty()) {
                // In base village chunks (type "village"), Lycanites monsters get an extra 50%
                // cancel on top of the global 50% (= ~75% total suppression). In purchased
                // plot chunks they are blocked entirely.
                if (plotType.equalsIgnoreCase("village") && LYCANITES_MODID.equals(entityId.getNamespace())) {
                    // Always-blocked hostile Lycanites (e.g. jengu) — skip the 50% roll.
                    if (LYCANITES_HIGH_BLOCK.contains(entityId.toString())) {
                        event.setSpawnCancelled(true);
                    } else if (LYCANITES_RAND.nextDouble() < 0.50) {
                        event.setSpawnCancelled(true);
                    } else {
                        // Passed the village 50% roll — this mob is spawning.
                        LOGGER.info("[SPAWN-ALLOWED] {} | location=VILLAGE-CHUNK | blockLight={} | pos={}",
                                entityId, blockLight, pos);
                    }
                } else {
                    event.setSpawnCancelled(true);
                }
            } else if (LYCANITES_HIGH_BLOCK.contains(entityId.toString())) {
                // Unclaimed chunk — but block always-block mobs (e.g. jengu) if they are
                // spawning directly adjacent to a claimed plot, to prevent them walking in.
                ChunkPos chunkPos = new ChunkPos(pos);
                if (isAdjacentToBoughtPlot(chunkPos)) {
                    event.setSpawnCancelled(true);
                    LOGGER.info("[SPAWN] Lycanites always-block adjacent-to-plot suppressed: "
                            + entityId + " | uuid=" + entity.getUUID() + " | pos=" + pos);
                } else {
                    LOGGER.info("[SPAWN] ALLOWED jengu in unclaimed non-adjacent chunk: "
                            + entityId + " | uuid=" + entity.getUUID() + " | pos=" + pos);
                }
            }
        } catch (Exception e) {
            LOGGER.error("onMobSpawnCheck error: " + e.getMessage(), e);
        }
    }

    /**
     * Secondary guard: catches modded hostile mobs that bypass FinalizeSpawn
     * (e.g. Lycanites summons, sleep-triggered spawns, or event-based spawning).
     * If a MONSTER-category modded mob joins the world inside a claimed plot chunk
     * it is immediately cancelled.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide())
            return;

        // ---- LYCANITES DROP INTERCEPTION ----
        // Lycanites uses its own drop pipeline, bypassing LivingDropsEvent entirely.
        // When an item entity spawns within 5 blocks of a recent Lycanites death, roll
        // to cancel it at the configured drop rate.
        if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            long now = System.currentTimeMillis();
            net.minecraft.world.phys.Vec3 ip = itemEntity.position();
            for (long[] death : LYCANITES_DEATH_POS) {
                if ((now - death[3]) > DEATH_WINDOW_MS)
                    continue;
                double dx = ip.x - death[0];
                double dy = ip.y - death[1];
                double dz = ip.z - death[2];
                if (dx * dx + dy * dy + dz * dz > 25.0)
                    continue; // 5-block radius
                if (LYCANITES_RAND.nextDouble() >= LYCANITES_DROP_RATE) {
                    event.setCanceled(true);
                    LOGGER.info("DEBUG: Lycanites item drop CANCELLED near death pos: "
                            + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(itemEntity.getItem().getItem())
                            + " x" + itemEntity.getItem().getCount()
                            + " at " + itemEntity.blockPosition());
                } else {
                    LOGGER.info("DEBUG: Lycanites item drop KEPT near death pos: "
                            + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(itemEntity.getItem().getItem())
                            + " x" + itemEntity.getItem().getCount()
                            + " at " + itemEntity.blockPosition());
                }
                return;
            }
            return; // not near any Lycanites death — pass through
        }

        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob))
            return;

        BlockPos pos = mob.blockPosition();
        if (pos.getY() < 45)
            return;

        net.minecraft.resources.ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType());
        if (entityId == null || "minecraft".equals(entityId.getNamespace()))
            return;

        // Block mobs that are MONSTER category OR implement the Monster/Enemy interface
        // (Lycanites mobs like spriggan use CREATURE category but are still hostile).
        boolean isMobHostile = mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
                || mob instanceof net.minecraft.world.entity.monster.Monster
                || mob instanceof net.minecraft.world.entity.monster.Enemy;
        if (!isMobHostile)
            return;

        // Allow spawns in completely dark areas (block light = 0) — but NOT for
        // always-block mobs (jengu/vespidqueen) which exploit underground dark spawns
        // to enter plots.
        int blockLight = ((Level) event.getLevel()).getBrightness(LightLayer.BLOCK, pos);
        if (blockLight == 0 && !LYCANITES_HIGH_BLOCK.contains(entityId.toString())) {
            return;
        }

        String plotType = getPlotType(pos, (Level) event.getLevel());
        if (plotType.isEmpty()) {
            // if (LYCANITES_HIGH_BLOCK.contains(entityId.toString()))
            // LOGGER.info("[ENTITYJOIN] ALLOWED jengu in unclaimed chunk: " + entityId
            // + " | uuid=" + mob.getUUID() + " | pos=" + pos);
            return;
        }

        if (plotType.equalsIgnoreCase("village") && LYCANITES_MODID.equals(entityId.getNamespace())) {
            // Always-blocked hostile Lycanites (e.g. jengu) — skip the 50% roll.
            if (LYCANITES_HIGH_BLOCK.contains(entityId.toString())) {
                event.setCanceled(true);
                return;
            }
            // Village chunks: extra 50% Lycanites suppression
            if (LYCANITES_RAND.nextDouble() < 0.50) {
                event.setCanceled(true);
            } else {
                // Passed the 50% roll — this Lycanites mob is spawning in a village chunk.
                LOGGER.info("[SPAWN-ALLOWED] {} | location=VILLAGE-CHUNK | blockLight={} | pos={}",
                        entityId, blockLight, pos);
            }
        } else if (!plotType.equalsIgnoreCase("village")) {
            // Any owned plot chunk (farm, pasture, etc.): block entirely
            event.setCanceled(true);
        } else {
            // Village chunk, non-Lycanites hostile — allowed through.
            LOGGER.info("[SPAWN-ALLOWED] {} | location=VILLAGE-CHUNK (non-Lycanites) | blockLight={} | pos={}",
                    entityId, blockLight, pos);
        }
    }

    /**
     * Log when any modded mob dies — shows entity ID, hostile category, and killer.
     */
    @SubscribeEvent
    public static void onModdedMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide())
            return;

        net.minecraft.world.entity.LivingEntity entity = event.getEntity();
        net.minecraft.resources.ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(entity.getType());
        if (entityId == null || "minecraft".equals(entityId.getNamespace()))
            return;

        // MobCategory.MONSTER alone misses Lycanites mobs that use CREATURE/AMBIENT
        // categories.
        // Also check the Monster interface (net.minecraft.world.entity.monster.Enemy).
        boolean isHostile = entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
                || entity instanceof net.minecraft.world.entity.monster.Monster
                || entity instanceof net.minecraft.world.entity.monster.Enemy;
        // Only log deaths caused by a player.
        if (!(event.getSource().getEntity() instanceof Player))
            return;

        String killerName = event.getSource().getEntity().getName().getString();
        BlockPos deathPos = entity.blockPosition();
        String plotType = getPlotType(deathPos, (Level) entity.level());
        String plotLabel = plotType.isEmpty() ? "unclaimed" : plotType;

        LOGGER.info("DEBUG: Modded mob death: " + entityId
                + " | uuid=" + entity.getUUID()
                + " | hostile=" + isHostile
                + " | category=" + entity.getType().getCategory()
                + " | plot=" + plotLabel
                + " | killedBy=" + killerName
                + " | pos=" + deathPos);
    }

    /**
     * Log when a Player is killed — shows what entity killed them,
     * flagging modded killers so we can track dangerous mobs.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (!(event.getEntity() instanceof Player victim))
            return;

        String killerDesc = buildKillerDesc(event);
        LOGGER.info("KILL: PLAYER " + victim.getName().getString()
                + " was killed by " + killerDesc
                + " at " + victim.blockPosition());
    }

    /**
     * When a villager dies in a claimed village chunk, log the kill and notify all
     * online players whose home village matches — also drops a temporary red
     * JourneyMap waypoint at the death position for each notified player.
     */
    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.Villager victim))
            return;

        String killerDesc = buildKillerDesc(event);
        LOGGER.info("KILL: VILLAGER " + victim.getName().getString()
                + " was killed by " + killerDesc
                + " at " + victim.blockPosition());

        try {
            String villagerName = victim.getName().getString();
            BlockPos deathPos = victim.blockPosition();

            // Build a short cause: "zombie", "centipede", "fall", "onFire", etc.
            String killerShort = killerDesc;

            net.minecraft.server.MinecraftServer server = victim.level().getServer();
            if (server == null)
                return;

            // --- Try to find the village this villager belongs to ---
            ChunkPos deathChunk = new ChunkPos(deathPos);
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDb.getData(deathChunk.toLong());
            VillageData village = null;
            if (chunkData != null && chunkData.getVillageId() != null) {
                DataBase<java.util.UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
                village = villageDb.getData(chunkData.getVillageId());
            }

            // --- Reincarnation pool (only if in a tracked village) ---
            if (village != null) {
                DataBase<java.util.UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
                com.falazar.farmupcraft.data.VillagerRecord reincRecord = new com.falazar.farmupcraft.data.VillagerRecord(
                        victim.getUUID(), villagerName, deathPos);
                village.addToReincarnationPool(reincRecord);
                villageDb.putData(village.getUUID(), village);
                LOGGER.info("[Reincarnation] Saved to pool: " + villagerName + " | uuid=" + victim.getUUID()
                        + " | pool_size=" + village.getReincarnationPool().size());
            }

            // --- Build message ---
            String villageLabel = village != null ? "[" + village.getName() + "]" : "[Village]";
            String dimId = victim.level().dimension().location().toString();
            String wpName = villagerName + " by " + killerShort;

            Component msg = Component.literal(villageLabel + " Villager ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(villagerName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" was killed by " + killerDesc
                            + " at " + deathPos.toShortString()).withStyle(ChatFormatting.RED));

            // --- Notify: village members + any player within 10 chunks (160 blocks) ---
            DataBase<java.util.UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            final double NEARBY_DIST_SQ = 160.0 * 160.0;
            java.util.Set<java.util.UUID> notified = new java.util.HashSet<>();

            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                // Village member check
                if (village != null) {
                    PlayerData pd = playerDb.getData(sp.getUUID());
                    if (pd != null && village.getUUID().equals(pd.getHomeVillageUUID())) {
                        sp.sendSystemMessage(msg);
                        EDBMessages.sendToPlayer(new AddJMWaypointPacket(
                                deathPos.getX(), deathPos.getY(), deathPos.getZ(),
                                wpName, dimId, true, 0xFF4040), sp);
                        notified.add(sp.getUUID());
                        continue;
                    }
                }
                // Nearby player check (same dimension, within 160 blocks)
                if (!notified.contains(sp.getUUID())
                        && sp.level() == victim.level()
                        && sp.distanceToSqr(deathPos.getX(), deathPos.getY(), deathPos.getZ()) <= NEARBY_DIST_SQ) {
                    sp.sendSystemMessage(msg);
                    notified.add(sp.getUUID());
                }
            }
        } catch (Exception ex) {
            LOGGER.error("onVillagerDeath: villager notify error - " + ex.getMessage());
        }
    }

    /** Builds a human-readable killer description from a LivingDeathEvent. */
    private static String buildKillerDesc(LivingDeathEvent event) {
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        if (killer == null)
            killer = event.getSource().getDirectEntity();
        if (killer == null)
            return event.getSource().getMsgId();
        net.minecraft.resources.ResourceLocation killerId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(killer.getType());
        return killerId != null ? killerId.getPath() : killer.getType().toString();
    }

    /**
     * Records the death position of any Lycanites mob so that item entities spawned
     * by their custom drop pipeline can be intercepted in onEntityJoinLevel.
     */
    @SubscribeEvent
    public static void onLycanitesMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide())
            return;

        net.minecraft.resources.ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(event.getEntity().getType());
        if (entityId == null || !LYCANITES_MODID.equals(entityId.getNamespace()))
            return;

        BlockPos pos = event.getEntity().blockPosition();
        long now = System.currentTimeMillis();
        LYCANITES_DEATH_POS.removeIf(e -> (now - e[3]) > DEATH_WINDOW_MS);
        LYCANITES_DEATH_POS.addLast(new long[] { pos.getX(), pos.getY(), pos.getZ(), now });
        LOGGER.info("DEBUG: Lycanites death recorded for drop interception: "
                + entityId + " at " + pos + " | tracked=" + LYCANITES_DEATH_POS.size());
    }

    /**
     * Fallback: halve drops via LivingDropsEvent if Lycanites ever uses vanilla
     * drops.
     * (Most Lycanites mobs bypass this entirely — see onLycanitesMobDeath above.)
     */
    @SubscribeEvent
    public static void onLycanitesDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide())
            return;

        net.minecraft.resources.ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(event.getEntity().getType());
        if (entityId == null || !LYCANITES_MODID.equals(entityId.getNamespace()))
            return;

        LOGGER.info("DEBUG: Lycanites LivingDropsEvent fired: " + entityId
                + " | dropCount=" + event.getDrops().size());
        event.getDrops().forEach(itemEntity -> {
            net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
            int before = stack.getCount();
            int halved = (int) Math.floor(before * LYCANITES_DROP_RATE);
            stack.setCount(halved);
            LOGGER.info("DEBUG: Lycanites drop: " + entityId
                    + " | item=" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                    + " | looting=" + event.getLootingLevel()
                    + " | before=" + before + " -> after=" + halved);
        });
        // Remove any stacks that became 0.
        event.getDrops().removeIf(itemEntity -> itemEntity.getItem().getCount() <= 0);
    }
}
