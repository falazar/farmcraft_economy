package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.StructureCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.VillagerRecord;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class ChunkManager {
    public static final CustomLogger LOGGER = new CustomLogger(ChunkManager.class.getSimpleName());
    public static final String VILLAGER_DEATH_WAYPOINT_TAG = "villager";

    private static final String LYCANITES_MODID = "lycanitesmobs";
    private static final double LYCANITES_SPAWN_RATE = 0.50;
    private static final Set<String> LYCANITES_HIGH_BLOCK = Set.of(
            "lycanitesmobs:jengu", "lycanitesmobs:vespidqueen");
    private static final double LYCANITES_DROP_RATE = 0.50;
    private static final Random LYCANITES_RAND = new Random();
    private static final long DEATH_WINDOW_MS = 3_000L;
    private static final ConcurrentLinkedDeque<RecentDeath> LYCANITES_DEATH_POS = new ConcurrentLinkedDeque<>();
    private static final Map<UUID, Long> VILLAGE_CENTER_SHOWN_AT = new HashMap<>();
    private static final long VILLAGE_CENTER_COOLDOWN_MS = 60 * 60 * 1000L;
    private static final Map<UUID, Long> WILDERNESS_SCAN_LAST_AT = new HashMap<>();
    private static final long WILDERNESS_SCAN_COOLDOWN_MS = 30_000L;
    private static final Random WILDERNESS_RAND = new Random();
    private static final int DEFAULT_PLOT_PROTECTION_MIN_Y = 56;

    private record RecentDeath(BlockPos pos, ResourceKey<Level> dimension, long time) {
        boolean matches(ItemEntity item, long now) {
            return isRecentDeathNear(pos, dimension, time, item.level().dimension(), item.position(), now);
        }
    }

    static boolean isRecentDeathNear(BlockPos deathPos, ResourceKey<Level> deathDimension, long time,
                                     ResourceKey<Level> itemDimension, Vec3 itemPos, long now) {
        return now - time <= DEATH_WINDOW_MS && deathDimension.equals(itemDimension)
                && itemPos.distanceToSqr(deathPos.getX(), deathPos.getY(), deathPos.getZ()) <= 25.0;
    }

    /** Called at server stop so cooldowns and drop interception cannot outlive a world. */
    public static void clearTransientState() {
        VILLAGE_CENTER_SHOWN_AT.clear();
        WILDERNESS_SCAN_LAST_AT.clear();
        LYCANITES_DEATH_POS.clear();
    }

    public static boolean isDebugPlayer(Player player) {
        return player.getGameProfile().name().equalsIgnoreCase("BossPanda96366");
    }

    private static String findVillageByChunkPos(ChunkPos pos, Level level) {
        ChunkData chunk = ModEvents.getChunkDataDatabase(level).getData(pos.pack());
        if (chunk == null || chunk.getVillageId() == null) return null;
        VillageData village = ModEvents.getVillageDatabase(level).getData(chunk.getVillageId());
        return village == null ? null : village.getName();
    }

    @SubscribeEvent
    public static void onPlayerEnterChunk(EntityEvent.EnteringSection event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            SectionPos oldPos = event.getOldPos();
            SectionPos newPos = event.getNewPos();
            String previous = findVillageByChunkPos(oldPos.chunk(), player.level());
            String current = findVillageByChunkPos(newPos.chunk(), player.level());
            checkForStructuresInSection(player, newPos);

            if (!newPos.chunk().equals(oldPos.chunk()) && current == null && WILDERNESS_RAND.nextFloat() < 0.05f) {
                long now = System.currentTimeMillis();
                Long last = WILDERNESS_SCAN_LAST_AT.get(player.getUUID());
                if (last == null || now - last >= WILDERNESS_SCAN_COOLDOWN_MS) {
                    WILDERNESS_SCAN_LAST_AT.put(player.getUUID(), now);
                    if (isDebugPlayer(player)) {
                        player.sendSystemMessage(Component.literal("[DEBUG] Wilderness scan triggered")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                    try {
                        StructureManager.scanAndSetupNearbyStructureChests(player, player.level(), 10);
                    } catch (Throwable ex) {
                        LOGGER.error("Error in wilderness structure scan: " + ex.getMessage(), ex);
                    }
                }
            }
            if (current == null && previous != null) {
                player.sendSystemMessage(Component.literal("You left the village of " + previous));
            } else if (current != null && !current.equals(previous)) {
                player.sendSystemMessage(Component.literal("You entered the village of " + current));
            }
        } catch (Throwable ex) {
            LOGGER.error("Error in onPlayerEnterChunk: " + ex.getMessage(), ex);
        }
    }

    private static void checkForStructuresInSection(Player player, SectionPos sectionPos) {
        try {
            if (!(player.level() instanceof ServerLevel level)) return;
            ChunkData entered = ModEvents.getChunkDataDatabase(level).getData(sectionPos.chunk().pack());
            if (entered != null && entered.getType().equalsIgnoreCase("village center")) {
                long now = System.currentTimeMillis();
                Long last = VILLAGE_CENTER_SHOWN_AT.get(player.getUUID());
                if (last == null || now - last >= VILLAGE_CENTER_COOLDOWN_MS) {
                    VILLAGE_CENTER_SHOWN_AT.put(player.getUUID(), now);
                    VillageData village = entered.getVillageId() == null ? null
                            : ModEvents.getVillageDatabase(level).getData(entered.getVillageId());
                    if (village != null) {
                        var msg = Component.literal("--- " + village.getName() + " ---").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal("\nLevel: " + village.getLevel()).withStyle(ChatFormatting.WHITE));
                        if (!village.getFounder().isEmpty()) {
                            msg.append(Component.literal("  |  Founded by: " + village.getFounder()).withStyle(ChatFormatting.WHITE));
                        }
                        if (!village.getAnimalType().isEmpty()) {
                            msg.append(Component.literal("\nVillage animal: " + village.getAnimalType()).withStyle(ChatFormatting.WHITE));
                        }
                        msg.append(Component.literal("\nChunks: " + village.getClaimedChunks().size()).withStyle(ChatFormatting.GRAY));
                        player.sendSystemMessage(msg);
                    }
                }
            }

            var structures = ModEvents.getGameStructureDatabase(level);
            for (Long id : structures.getKeys()) {
                GameStructureData data = structures.getData(id);
                if (data == null || !SectionPos.of(data.getCenterPos()).equals(sectionPos)) continue;
                player.sendSystemMessage(Component.literal("You discovered: " + data.getName()
                        + (data.isOnClaimedPlot() ? " (claimed)" : "")
                        + (data.wasVisited() ? " (visited)" : " (new!)")));
                if (!data.wasVisited() && !player.isCreative()) {
                    data.setWasVisited(true);
                    StructureCommand.refreshStructureClaim(data, ModEvents.getChunkDataDatabase(level));
                    structures.putData(id, data);
                    structures.setDirty();
                    LOGGER.info("Marked structure as visited: " + data.getName() + " (ID: " + id + ")"
                            + (data.isOnClaimedPlot() ? " [CLAIMED]" : ""));
                }
            }
        } catch (Throwable ex) {
            LOGGER.error("Error checking for structures in section: " + ex.getMessage(), ex);
        }
    }

    public static String getPlotType(BlockPos pos, Level level) {
        ChunkData data = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.pack(pos));
        return data == null ? "" : data.getType();
    }

    public static ChunkData getPlot(BlockPos pos) {
        return ModEvents.getChunkDataDatabase().getData(ChunkPos.pack(pos));
    }

    public static ChunkData getPlot(BlockPos pos, Level level) {
        return ModEvents.getChunkDataDatabase(level).getData(ChunkPos.pack(pos));
    }

    static boolean isAdjacentToBoughtPlot(ChunkPos center, DataBase<Long, ChunkData> database) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkData data = database.getData(ChunkPos.pack(center.x() + dx, center.z() + dz));
                if (data != null && !data.getType().isEmpty()) return true;
            }
        }
        return false;
    }

    static int protectionMinY(ChunkData data) {
        return data.getBoughtY() > 0 ? data.getBoughtY() : DEFAULT_PLOT_PROTECTION_MIN_Y;
    }

    @SubscribeEvent
    public static void onRightClickPlotBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        var database = ModEvents.getChunkDataDatabase(event.getLevel());
        ChunkPos chunk = ChunkPos.containing(pos);
        ChunkData data = database.getData(chunk.pack());
        if (data != null && isRestrictedBucket(event.getItemStack()) && !isPlayerVillageMember(player, data)) {
            event.setCanceled(true);
            deny(player, "You are not a member of this village.");
            return;
        }
        if (!isProtectedAccessBlock(event.getLevel().getBlockState(pos)) || data == null
                || pos.getY() < protectionMinY(data) || player.isCreative()) return;
        if (!isPlayerVillageMember(player, data)) {
            event.setCanceled(true);
            deny(player, "You are not a member of this village.");
        } else if (data.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, data, database, chunk)) {
            event.setCanceled(true);
            deny(player, "You do not have permission on this house plot.");
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player) || player.isCreative()) return;
        BlockPos pos = event.getPos();
        BlockState placed = event.getLevel().getBlockState(pos);
        if (isRockPathBlock(placed) && isRockPathBlock(event.getLevel().getBlockState(pos.below()))) {
            event.setCanceled(true);
            deny(player, "You cannot stack rock paths.");
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundBlockUpdatePacket(event.getLevel(), pos));
                serverPlayer.inventoryMenu.sendAllDataToRemote();
            }
            return;
        }
        if (event.getPlacedBlock().is(BlockTags.LOGS)
                && (isHoldingLog(player.getMainHandItem()) || isHoldingLog(player.getOffhandItem()))
                && getPlotType(pos, player.level()).equals("nursery")) {
            event.setCanceled(true);
            deny(player, "You cannot place logs in a nursery plot.");
            return;
        }
        var database = ModEvents.getChunkDataDatabase(player.level());
        ChunkPos chunk = ChunkPos.containing(pos);
        ChunkData data = database.getData(chunk.pack());
        if (data == null || pos.getY() < protectionMinY(data)) return;
        if (!isPlayerVillageMember(player, data)) {
            event.setCanceled(true);
            deny(player, "You are not a member of this village.");
        } else if (data.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, data, database, chunk)) {
            event.setCanceled(true);
            deny(player, "You do not have permission to place blocks on this house plot.");
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        BlockPos pos = event.getPos();
        ChunkPos chunk = ChunkPos.containing(pos);
        var database = ModEvents.getChunkDataDatabase(player.level());
        ChunkData data = database.getData(chunk.pack());
        if (data == null || pos.getY() < protectionMinY(data)) return;
        if (!isPlayerVillageMember(player, data)) {
            event.setCanceled(true);
            event.setNotifyClient(true);
            deny(player, "You are not a member of this village.");
        } else if (data.getType().equalsIgnoreCase("house") && !hasHouseAccess(player, data, database, chunk)) {
            event.setCanceled(true);
            event.setNotifyClient(true);
            deny(player, "You do not have permission to break blocks on this house plot.");
        }
    }

    private static void deny(Player player, String message) {
        player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    static boolean isProtectedAccessBlock(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.SHULKER_BOXES) || id.contains("fence_gate") || id.contains("upgrade_gate")
                || id.contains("picket_gate") || id.contains("chest") || id.contains("barrel");
    }

    private static boolean isPlayerVillageMember(Player player, ChunkData data) {
        if (data.getVillageId() == null) return true;
        return isPlayerVillageMember(data.getVillageId(), ModEvents.getPlayerDatabase(player.level()).getData(player.getUUID()));
    }

    static boolean isPlayerVillageMember(UUID villageId, PlayerData player) {
        return villageId == null || player != null && villageId.equals(player.getHomeVillageUUID());
    }

    private static boolean hasHouseAccess(Player player, ChunkData data, DataBase<Long, ChunkData> database, ChunkPos pos) {
        return hasHouseAccess(player.getUUID(), player.getId(), player.getName().getString(), data, database, pos);
    }

    static boolean hasHouseAccess(UUID playerId, int entityId, String name, ChunkData data,
                                  DataBase<Long, ChunkData> database, ChunkPos pos) {
        UUID owner = data.getOwnerUUID();
        if (owner == null && data.getPlayerId() == entityId) {
            data.setOwnerUUID(playerId);
            database.putData(pos.pack(), data);
            owner = playerId;
        }
        return playerId.equals(owner) || data.isVisitor(name);
    }

    public static boolean isRockPathBlock(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return id.contains("rock") && id.contains("path");
    }

    static boolean isHoldingLog(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && item.getBlock().defaultBlockState().is(BlockTags.LOGS);
    }

    static boolean isRestrictedBucket(ItemStack stack) {
        return stack != null && (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET));
    }

    private static boolean isConcapedeEntity(Identifier id) {
        return id != null && id.getPath().toLowerCase(java.util.Locale.ROOT).contains("concapede");
    }

    private static boolean isConcapedeOrWispEntity(Identifier id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("concapede") || path.contains("wisp");
    }

    private static void logTrackedEntityWithPlot(String marker, Entity entity, Identifier id, Level level) {
        String plot = getPlotType(entity.blockPosition(), level);
        LOGGER.info("{} {} | uuid={} | dim={} | pos={} | plot={}", marker, id, entity.getUUID(),
                level.dimension().identifier(), entity.blockPosition(), plot.isEmpty() ? "unclaimed" : plot);
    }

    private static boolean isHostile(Entity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER || entity instanceof Monster || entity instanceof Enemy;
    }

    static double joinedMobSuppressionChance(Identifier id, int y, int light, String plot) {
        return joinedMobSuppressionChance(id, y, light, plot, false);
    }

    static double joinedMobSuppressionChance(Identifier id, int y, int light, String plot, boolean bredOffspring) {
        return joinedMobSuppressionChance(id, y, light, plot, bredOffspring, null);
    }

    static double joinedMobSuppressionChance(Identifier id, int y, int light, String plot, boolean bredOffspring,
                                             net.minecraft.world.entity.EntitySpawnReason reason) {
        if (net.minecraft.world.entity.EntitySpawnReason.isSpawner(reason)) return 0;
        if (id == null || "minecraft".equals(id.getNamespace()) || y < 45 || plot.isEmpty()) return 0;
        if (com.falazar.farmupcraft.util.PlotSpawnExemptions.allows(id.toString(), bredOffspring)) return 0;
        if (light == 0 && !LYCANITES_HIGH_BLOCK.contains(id.toString())) return 0;
        if (!plot.equalsIgnoreCase("village")) return 1;
        if (!LYCANITES_MODID.equals(id.getNamespace())) return 0;
        if (LYCANITES_HIGH_BLOCK.contains(id.toString())) return 1;
        return y >= 55 ? 0.95 : 0.50;
    }

    @SubscribeEvent
    public static void onMobSpawnCheck(FinalizeSpawnEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            Mob entity = event.getEntity();
            if (net.minecraft.world.entity.EntitySpawnReason.isSpawner(event.getSpawnType())) {
                // Custom-NBT spawners can skip finalizeSpawn, leaving Mob.getSpawnType() unset.
                entity.getPersistentData().putBoolean(
                        com.falazar.farmupcraft.util.PlotSpawnExemptions.SPAWNER_ORIGIN, true);
                return;
            }
            BlockPos pos = entity.blockPosition();
            if (pos.getY() < 45) return;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null || "minecraft".equals(id.getNamespace())) return;
            if (com.falazar.farmupcraft.util.PlotSpawnExemptions.allows(id.toString(),
                    entity.getPersistentData().getBooleanOr(
                            com.falazar.farmupcraft.util.PlotSpawnExemptions.BRED_OFFSPRING, false))) return;
            if (LYCANITES_MODID.equals(id.getNamespace())) {
                double rate = LYCANITES_HIGH_BLOCK.contains(id.toString()) ? 0.10 : LYCANITES_SPAWN_RATE;
                if (LYCANITES_RAND.nextDouble() >= rate) {
                    event.setSpawnCancelled(true);
                    return;
                }
                // Worldgen regions are not Levels, but their owning server level supplies database context.
                var worldData = ModEvents.getWorldDataDatabase(event.getLevel().getLevel()).getOrCreate(0,
                        com.falazar.farmupcraft.data.WorldData::new);
                if (worldData.isLycaniteLightBlock() && event.getLevel() instanceof Level lightLevel
                        && lightLevel.getBrightness(LightLayer.BLOCK, pos) >= 8) {
                    event.setSpawnCancelled(true);
                    return;
                }
            }
            if (!isHostile(entity) || !(event.getLevel() instanceof Level level)) return;
            int light = level.getBrightness(LightLayer.BLOCK, pos);
            if (light == 0) return;
            String plot = getPlotType(pos, level);
            if (!plot.isEmpty()) {
                if (plot.equalsIgnoreCase("village") && LYCANITES_MODID.equals(id.getNamespace())) {
                    if (LYCANITES_HIGH_BLOCK.contains(id.toString()) || LYCANITES_RAND.nextDouble() < 0.50) {
                        event.setSpawnCancelled(true);
                    } else {
                        LOGGER.info("[SPAWN-ALLOWED] {} | location=VILLAGE-CHUNK | blockLight={} | pos={}", id, light, pos);
                    }
                } else {
                    event.setSpawnCancelled(true);
                }
            } else if (LYCANITES_HIGH_BLOCK.contains(id.toString())) {
                if (isAdjacentToBoughtPlot(ChunkPos.containing(pos), ModEvents.getChunkDataDatabase(level))) {
                    event.setSpawnCancelled(true);
                    LOGGER.info("[SPAWN] Lycanites always-block adjacent-to-plot suppressed: "
                            + id + " | uuid=" + entity.getUUID() + " | pos=" + pos);
                } else {
                    LOGGER.info("[SPAWN] ALLOWED jengu in unclaimed non-adjacent chunk: "
                            + id + " | uuid=" + entity.getUUID() + " | pos=" + pos);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("onMobSpawnCheck error: " + ex.getMessage(), ex);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onBabyEntitySpawn(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        var child = event.getChild();
        if (event.isCanceled() || child == null || child.level().isClientSide()) return;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(child.getType());
        if (com.falazar.farmupcraft.util.PlotSpawnExemptions.allows(id.toString(), true)) {
            child.getPersistentData().putBoolean(
                    com.falazar.farmupcraft.util.PlotSpawnExemptions.BRED_OFFSPRING, true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (isConcapedeEntity(id)) {
            logTrackedEntityWithPlot("[CONCAPEDE-TOPLEVEL-JOIN]", entity, id, event.getLevel());
        }
        if (entity instanceof ItemEntity item) {
            long now = System.currentTimeMillis();
            LYCANITES_DEATH_POS.removeIf(death -> now - death.time() > DEATH_WINDOW_MS);
            for (RecentDeath death : LYCANITES_DEATH_POS) {
                if (!death.matches(item, now)) continue;
                if (LYCANITES_RAND.nextDouble() >= LYCANITES_DROP_RATE) event.setCanceled(true);
                return;
            }
            return;
        }
        if (!(entity instanceof Mob) || entity.blockPosition().getY() < 45
                || id == null || "minecraft".equals(id.getNamespace())) return;
        if (entity.getPersistentData().getBooleanOr(
                com.falazar.farmupcraft.util.PlotSpawnExemptions.SPAWNER_ORIGIN, false)) return;
        BlockPos pos = entity.blockPosition();
        int light = event.getLevel().getBrightness(LightLayer.BLOCK, pos);
        String plot = getPlotType(pos, event.getLevel());
        double chance = joinedMobSuppressionChance(id, pos.getY(), light, plot,
                entity.getPersistentData().getBooleanOr(
                        com.falazar.farmupcraft.util.PlotSpawnExemptions.BRED_OFFSPRING, false), ((Mob) entity).getSpawnType());
        if (chance == 1 || chance > 0 && LYCANITES_RAND.nextDouble() < chance) {
            event.setCanceled(true);
            if (plot.equalsIgnoreCase("village") && !LYCANITES_HIGH_BLOCK.contains(id.toString())) {
                LOGGER.info("[SPAWN-BLOCKED] Lycanites {}% {} suppressed in village chunk: {} | blockLight={} | pos={}",
                        pos.getY() >= 55 ? 95 : 50, pos.getY() >= 55 ? "aboveground" : "underground", id, light, pos);
            }
        } else if (plot.equalsIgnoreCase("village") && light != 0) {
            LOGGER.info("[SPAWN-ALLOWED] {} | location=VILLAGE-CHUNK{} | blockLight={} | pos={}",
                    id, LYCANITES_MODID.equals(id.getNamespace()) ? "" : " (non-Lycanites)", light, pos);
        }
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!isConcapedeOrWispEntity(id)) return;
        logTrackedEntityWithPlot("[DIMENSION-ENTER]", entity, id, entity.level());
        LOGGER.info("[DIMENSION-ENTER] targetDim={} | {} | uuid={}", event.getDimension().identifier(), id, entity.getUUID());
    }

    @SubscribeEvent
    public static void onModdedMobDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || "minecraft".equals(id.getNamespace()) || !(event.getSource().getEntity() instanceof Player killer)) return;
        String plot = getPlotType(entity.blockPosition(), entity.level());
        LOGGER.info("DEBUG: Modded mob death: " + id + " | uuid=" + entity.getUUID() + " | hostile=" + isHostile(entity)
                + " | category=" + entity.getType().getCategory() + " | plot=" + (plot.isEmpty() ? "unclaimed" : plot)
                + " | killedBy=" + killer.getName().getString() + " | pos=" + entity.blockPosition());
    }

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof Level level)) return;
        BlockPos pos = event.getPos();
        String plot = getPlotType(pos, level);
        if (plot.equalsIgnoreCase("portal")) return;
        event.setCanceled(true);
        LOGGER.info("[PORTAL] Blocked portal ignition outside portal plot at {} (plot='{}')", pos, plot);
        level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(8)).forEach(player -> {
            if (!player.isCreative() && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("Portals can only be ignited on a designated portal plot. "
                        + "Buy one with /plot buy portal.").withStyle(ChatFormatting.RED));
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Player player)) return;
        LOGGER.info("KILL: PLAYER " + player.getName().getString() + " was killed by " + buildKillerDesc(event)
                + " at " + player.blockPosition());
    }

    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Villager victim)) return;
        String killer = buildKillerDesc(event);
        LOGGER.info("Villager " + victim.getName().getString() + " died by " + killer + " at " + victim.blockPosition());
        try {
            String name = victim.getName().getString();
            BlockPos pos = victim.blockPosition();
            var server = victim.level().getServer();
            if (server == null) return;
            ChunkData chunk = getPlot(pos, victim.level());
            var villages = ModEvents.getVillageDatabase(victim.level());
            VillageData village = chunk == null || chunk.getVillageId() == null ? null : villages.getData(chunk.getVillageId());
            if (village != null) {
                village.addToReincarnationPool(new VillagerRecord(victim.getUUID(), name, pos));
                villages.putData(village.getUUID(), village);
                LOGGER.info("[Reincarnation] Saved to pool: " + name + " | uuid=" + victim.getUUID()
                        + " | pool_size=" + village.getReincarnationPool().size());
            }
            String label = village == null ? "[Village]" : "[" + village.getName() + "]";
            String dimension = victim.level().dimension().identifier().toString();
            String waypoint = VILLAGER_DEATH_WAYPOINT_TAG + " " + name + " died by " + killer;
            Component message = Component.literal(label + " Villager ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" was killed by " + killer + " at " + pos.toShortString()).withStyle(ChatFormatting.RED));
            var players = ModEvents.getPlayerDatabase(victim.level());
            Set<UUID> notified = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (village != null) {
                    PlayerData data = players.getData(player.getUUID());
                    if (data != null && village.getUUID().equals(data.getHomeVillageUUID())) {
                        player.sendSystemMessage(message);
                        EDBMessages.sendToPlayer(new AddJMWaypointPacket(pos.getX(), pos.getY(), pos.getZ(),
                                waypoint, dimension, true, 0xFF4040), player);
                        notified.add(player.getUUID());
                        continue;
                    }
                }
                if (!notified.contains(player.getUUID()) && player.level() == victim.level()
                        && player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 160.0 * 160.0) {
                    player.sendSystemMessage(message);
                    notified.add(player.getUUID());
                }
            }
        } catch (Exception ex) {
            LOGGER.error("onVillagerDeath: villager notify error - " + ex.getMessage());
        }
    }

    private static String buildKillerDesc(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (killer == null) killer = event.getSource().getDirectEntity();
        if (killer == null) return event.getSource().getMsgId();
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType());
        return id == null ? killer.getType().toString() : id.getPath();
    }

    @SubscribeEvent
    public static void onLycanitesMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (id == null || !LYCANITES_MODID.equals(id.getNamespace())) return;
        BlockPos pos = event.getEntity().blockPosition();
        long now = System.currentTimeMillis();
        LYCANITES_DEATH_POS.removeIf(death -> now - death.time() > DEATH_WINDOW_MS);
        LYCANITES_DEATH_POS.addLast(new RecentDeath(pos.immutable(), event.getEntity().level().dimension(), now));
        LOGGER.info("DEBUG: Lycanites death recorded for drop interception: " + id + " at " + pos
                + " | tracked=" + LYCANITES_DEATH_POS.size());
    }

    @SubscribeEvent
    public static void onLycanitesDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (id == null || !LYCANITES_MODID.equals(id.getNamespace())) return;
        LOGGER.info("DEBUG: Lycanites LivingDropsEvent fired: " + id + " | dropCount=" + event.getDrops().size());
        event.getDrops().forEach(entity -> {
            ItemStack stack = entity.getItem();
            int before = stack.getCount();
            stack.setCount(reducedDropCount(before));
            LOGGER.info("DEBUG: Lycanites drop: " + id + " | item=" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                    + " | before=" + before + " -> after=" + stack.getCount());
        });
        event.getDrops().removeIf(entity -> entity.getItem().getCount() <= 0);
    }

    static int reducedDropCount(int count) {
        return (int) Math.floor(count * LYCANITES_DROP_RATE);
    }
}
