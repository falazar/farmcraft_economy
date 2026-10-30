package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

/**
 * Handles structure-related gameplay logic: chest trap detection,
 * mob spawning, and spawn position resolution.
 */
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructureManager {
    public static final CustomLogger LOGGER = new CustomLogger(StructureManager.class.getSimpleName());
    private static final Random TRAP_RNG = new Random();
    private static final int TRAP_SPAWN_RADIUS = 20;
    private static final int TRAP_SPAWN_ATTEMPTS = 40;
    private static final int TRAP_DOWNWARD_SCAN = 20;
    private static final long TRAP_SPAWN_CACHE_MS = 300_000L; // Updated to 5 minutes
    private static final String SPAWNER_CHECKED_TAG = "farmupcraft_spawner_checked";
    private static final double LYCANITES_SPAWNER_CHANCE = 0.30;

    // Short-term anti-double-spawn cache for nearby chests.
    // Keyed by exact trap chest position and by chunk key so adjacent chests
    // in the same chunk don't both spawn mobs in rapid succession.
    private static final Map<Long, Long> RECENT_TRAP_POS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, Long> RECENT_TRAP_CHUNK_CACHE = new ConcurrentHashMap<>();

    private static final ResourceLocation[] LYCANITES_TRAP_POOL = new ResourceLocation[] {
            new ResourceLocation("lycanitesmobs", "geonach"),
            new ResourceLocation("lycanitesmobs", "wraamon"),
            new ResourceLocation("lycanitesmobs", "argus"),
            new ResourceLocation("lycanitesmobs", "grue"),
            new ResourceLocation("lycanitesmobs", "xaphan"), // New mob
            new ResourceLocation("lycanitesmobs", "behemoth"), // New mob
            new ResourceLocation("lycanitesmobs", "shade"), // New mob
            new ResourceLocation("lycanitesmobs", "reaper"), // New mob
            new ResourceLocation("lycanitesmobs", "geist") // New mob
    };

    private static String[] trapMessages(String structureName) {
        return new String[] {
                "You disturbed something in " + structureName + "... ",
                "A dark presence stirs in " + structureName + "... ",
                "The air grows cold near " + structureName + "... ",
                "You hear whispers from " + structureName + "... ",
                "A chilling aura surrounds " + structureName + "... ",
                "Something awakens in " + structureName + "... ",
                "You feel uneasy near " + structureName + "... ",
                "A sinister force emerges from " + structureName + "... ",
                "The ground trembles in " + structureName + "... ",
                "An ominous shadow looms over " + structureName + "... "
        };
    }

    @SubscribeEvent
    public static void onSpecialChestInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;
        triggerSpecialChestTrap(event.getLevel(), event.getPos(),
                event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
    }

    @SubscribeEvent
    public static void onSpecialChestBreak(BlockEvent.BreakEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        if (event.getLevel().isClientSide())
            return;
        net.minecraft.world.level.Level level = (net.minecraft.world.level.Level) event.getLevel();
        triggerSpecialChestTrap(level, event.getPos(),
                event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);

        handleSpawnerBreakExtraSpawn(event);
    }

    @SubscribeEvent
    public static void onSpawnerTorchPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;

        BlockState placed = event.getPlacedBlock();
        Block block = placed.getBlock();
        if (block != Blocks.TORCH && block != Blocks.SOUL_TORCH
                && block != Blocks.WALL_TORCH && block != Blocks.SOUL_WALL_TORCH) {
            return;
        }

        BlockPos below = event.getPos().below();
        BlockState belowState = event.getLevel().getBlockState(below);
        if (!(belowState.getBlock() instanceof SpawnerBlock))
            return;

        event.setCanceled(true);
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(
                    Component.literal("Torches cannot be placed on spawners.").withStyle(ChatFormatting.RED),
                    true);
        }
    }

    private static void handleSpawnerBreakExtraSpawn(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        BlockPos pos = event.getPos();
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof SpawnerBlock))
            return;

        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof SpawnerBlockEntity spawnerBe))
            return;

        ResourceLocation id = readSpawnerEntityId(spawnerBe);
        EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            type = EntityType.ZOMBIE;
        }

        Entity extra = type.create(serverLevel);
        if (extra == null)
            return;

        extra.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        if (extra instanceof Mob mob) {
            mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null,
                    null);
        }
        serverLevel.addFreshEntity(extra);
        LOGGER.info("Spawner at {} broken; spawned extra {}", pos, type);
    }

    private static ResourceLocation readSpawnerEntityId(SpawnerBlockEntity spawnerBe) {
        try {
            CompoundTag tag = spawnerBe.saveWithoutMetadata();
            if (!tag.contains("SpawnData", Tag.TAG_COMPOUND))
                return null;
            CompoundTag spawnData = tag.getCompound("SpawnData");
            CompoundTag entity = spawnData;
            if (spawnData.contains("entity", Tag.TAG_COMPOUND)) {
                entity = spawnData.getCompound("entity");
            }
            if (!entity.contains("id", Tag.TAG_STRING))
                return null;
            return new ResourceLocation(entity.getString("id"));
        } catch (Exception e) {
            LOGGER.warn("Failed to read spawner entity id: {}", e.getMessage());
            return null;
        }
    }

    private static EntityType<?> pickRandomLycanitesEntityType() {
        List<EntityType<?>> pool = new ArrayList<>();
        for (ResourceLocation rl : LYCANITES_TRAP_POOL) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type != null) {
                pool.add(type);
            }
        }
        if (pool.isEmpty())
            return null;
        return pool.get(TRAP_RNG.nextInt(pool.size()));
    }

    private static int rerollLycanitesSpawnersInBbox(GameStructureData sd, ServerLevel world, String logPrefix) {
        int rerolled = 0;
        for (int bx = sd.getMinPos().getX(); bx <= sd.getMaxPos().getX(); bx++) {
            for (int bz = sd.getMinPos().getZ(); bz <= sd.getMaxPos().getZ(); bz++) {
                if (!world.isLoaded(new BlockPos(bx, 64, bz))) {
                    continue;
                }
                for (int by = sd.getMinPos().getY(); by <= sd.getMaxPos().getY(); by++) {
                    BlockPos bp = new BlockPos(bx, by, bz);
                    BlockState state = world.getBlockState(bp);
                    if (!(state.getBlock() instanceof SpawnerBlock)) {
                        continue;
                    }
                    BlockEntity be = world.getBlockEntity(bp);
                    if (!(be instanceof SpawnerBlockEntity spawnerBe)) {
                        continue;
                    }

                    CompoundTag persistent = spawnerBe.getPersistentData();
                    if (persistent.getBoolean(SPAWNER_CHECKED_TAG)) {
                        continue;
                    }
                    persistent.putBoolean(SPAWNER_CHECKED_TAG, true);

                    if (TRAP_RNG.nextDouble() >= LYCANITES_SPAWNER_CHANCE) {
                        continue;
                    }

                    EntityType<?> lycanitesType = pickRandomLycanitesEntityType();
                    if (lycanitesType == null) {
                        continue;
                    }

                    spawnerBe.getSpawner().setEntityId(lycanitesType, world, world.getRandom(), bp);
                    spawnerBe.setChanged();
                    world.sendBlockUpdated(bp, state, state, Block.UPDATE_ALL);
                    rerolled++;
                    LOGGER.info("{}: spawner at {} rerolled to lycanites type {}", logPrefix, bp, lycanitesType);
                }
            }
        }
        return rerolled;
    }

    /**
     * Returns true if the block is a chest-like block (vanilla or Ice and Fire
     * cursed chest).
     */
    public static boolean isStructureChest(Block blk) {
        if (blk == Blocks.CHEST || blk == Blocks.TRAPPED_CHEST || blk == Blocks.BARREL) {
            return true;
        }
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(blk);
        return rl != null && rl.getNamespace().equals("iceandfire") && rl.getPath().contains("chest");
    }

    /**
     * Entry point called from ForgeEvents when any block is right-clicked or
     * broken.
     * Filters to chest-like blocks inside active structure traps, then fires the
     * trap.
     */
    public static void triggerSpecialChestTrap(Level level, BlockPos pos, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        try {

            // Must be a chest or barrel (vanilla or modded).
            Block blk = level.getBlockState(pos).getBlock();
            if (!isStructureChest(blk))
                return;

            // Resolve double-chest partner position.
            BlockPos partnerPos = null;
            BlockState state = level.getBlockState(pos);
            if (blk instanceof ChestBlock) {
                ChestType chestType = state.getValue(ChestBlock.TYPE);
                if (chestType != ChestType.SINGLE) {
                    net.minecraft.core.Direction partnerDir = ChestBlock.getConnectedDirection(state);
                    partnerPos = pos.relative(partnerDir).immutable();
                }
            }

            DataBase<Long, GameStructureData> structureDb = ModEvents.getGameStructureDatabase(serverLevel);
            GameStructureData s = findStructureAtPos(structureDb, pos);
            if (s == null || !s.hasSpecialChest())
                return;

            // Village structures don't trigger traps.
            if (s.getType().contains("village") || s.getName().contains("village"))
                return;

            // Already opened this chest (or its double-chest partner)?
            if (s.isChestOpened(pos) || (partnerPos != null && s.isChestOpened(partnerPos)))
                return;

            // Mark both halves as opened so this chest never triggers again.
            s.markChestOpened(pos);
            if (partnerPos != null)
                s.markChestOpened(partnerPos);
            structureDb.putData(s.getId(), s);
            structureDb.setDirty();

            // Spawn chance: >4 chests in structure -> 30%, <=4 -> 70%.
            int count = s.getTotalChestCount();
            double chance = count > 4 ? 0.30 : 0.70;
            if (Math.random() >= chance) {
                LOGGER.info("Chest opened at {} in {} — no spawn ({}% miss)", pos, s.getName(),
                        (int) ((1 - chance) * 100));
                return;
            }

            long now = System.currentTimeMillis();
            cleanupRecentTrapCache(now);
            long posKey = pos.asLong();
            long chunkKey = new ChunkPos(pos).toLong();
            Long recentPos = RECENT_TRAP_POS_CACHE.get(posKey);
            Long recentChunk = RECENT_TRAP_CHUNK_CACHE.get(chunkKey);
            if (recentPos != null || recentChunk != null) {
                LOGGER.info("Chest trap at {} in {} skipped — recent trap spawn cache hit (chunk={})",
                        pos, s.getName(), new ChunkPos(pos));
                return;
            }

            LOGGER.info("Chest trap triggered at {} in structure {} ({}) — spawning mob", pos, s.getName(), s.getId());

            BlockPos spawnPos = findRandomSpawnPos(serverLevel, pos, TRAP_SPAWN_RADIUS, TRAP_SPAWN_ATTEMPTS);
            spawnTrapMob(serverLevel, spawnPos, player, s);
            RECENT_TRAP_POS_CACHE.put(posKey, now);
            RECENT_TRAP_CHUNK_CACHE.put(chunkKey, now);
        } catch (Throwable t) {
            String playerName = player != null ? player.getName().getString() : "unknown";
            LOGGER.error("triggerSpecialChestTrap failed at {} for player {}: {}", pos, playerName, t.getMessage(), t);
        }
    }

    /**
     * Local fallback lookup to avoid hard runtime dependency failures in trap
     * logic.
     */
    private static GameStructureData findStructureAtPos(DataBase<Long, GameStructureData> structureDb, BlockPos pos) {
        for (Long id : structureDb.getKeys()) {
            GameStructureData sd = structureDb.getData(id);
            if (sd == null || !sd.hasBoundingBox())
                continue;
            BlockPos mn = sd.getMinPos();
            BlockPos mx = sd.getMaxPos();
            if (pos.getX() >= mn.getX() && pos.getX() <= mx.getX()
                    && pos.getY() >= mn.getY() && pos.getY() <= mx.getY()
                    && pos.getZ() >= mn.getZ() && pos.getZ() <= mx.getZ()) {
                return sd;
            }
        }
        return null;
    }

    /** Spawns the trap mob at the given position and notifies the player. */
    private static void spawnTrapMob(ServerLevel serverLevel, BlockPos spawnPos,
            ServerPlayer player, GameStructureData s) {
        Mob trapMob = createTrapMob(serverLevel);
        trapMob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // Keep vanilla traps dangerous but not profitable.
        if (trapMob.getType() == EntityType.SKELETON || trapMob.getType() == EntityType.ZOMBIE) {
            trapMob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            trapMob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            trapMob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            trapMob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            for (EquipmentSlot slot : new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
                trapMob.setDropChance(slot, 0f);
            }
        }

        trapMob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        serverLevel.addFreshEntity(trapMob);

        if (player != null) {
            String[] messages = trapMessages(s.getName());
            String randomMessage = messages[TRAP_RNG.nextInt(messages.length)];

            player.sendSystemMessage(Component.literal(
                    randomMessage + "Something appears!")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static Mob createTrapMob(ServerLevel serverLevel) {
        ArrayList<EntityType<?>> pool = new ArrayList<>();
        pool.add(EntityType.SKELETON);
        pool.add(EntityType.ZOMBIE);
        for (ResourceLocation rl : LYCANITES_TRAP_POOL) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type != null) {
                pool.add(type);
            }
        }

        Collections.shuffle(pool, TRAP_RNG);
        for (EntityType<?> type : pool) {
            Entity created = type.create(serverLevel);
            if (created instanceof Mob mob) {
                return mob;
            }
        }
        return new Skeleton(EntityType.SKELETON, serverLevel);
    }

    private static void cleanupRecentTrapCache(long now) {
        RECENT_TRAP_POS_CACHE.entrySet().removeIf(e -> (now - e.getValue()) > TRAP_SPAWN_CACHE_MS);
        RECENT_TRAP_CHUNK_CACHE.entrySet().removeIf(e -> (now - e.getValue()) > TRAP_SPAWN_CACHE_MS);
    }

    /**
     * Runtime-safe wilderness scan path used by ChunkManager.
     * Keeps structure/chest maintenance out of command classes so tick events don't
     * classload command-only inner types.
     */
    public static void scanAndSetupNearbyStructureChests(ServerPlayer player, ServerLevel world, int radiusChunks) {
        try {
            int radiusBlocks = Math.max(1, radiusChunks) * 16;
            BlockPos center = player.blockPosition();
            var structureDb = ModEvents.getGameStructureDatabase(world);

            List<GameStructureData> nearby = new ArrayList<>();
            double maxDistSqr = (double) radiusBlocks * radiusBlocks;
            for (Long id : structureDb.getKeys()) {
                GameStructureData sd = structureDb.getData(id);
                if (sd == null)
                    continue;
                if (sd.getCenterPos().distSqr(center) <= maxDistSqr) {
                    nearby.add(sd);
                }
            }
            nearby.sort(Comparator.comparingDouble(sd -> sd.getCenterPos().distSqr(center)));
            if (nearby.isEmpty())
                return;
            ChestScanStats stats = scanAndSetupStructures(world, nearby, null,
                    "scanAndSetupNearbyStructureChests");

            if (ChunkManager.isDebugPlayer(player)) {
                player.sendSystemMessage(Component.literal(
                        "[DEBUG] Wilderness scan: " + nearby.size() + " structure(s), "
                                + stats.chestsScanned + " chest(s), " + stats.specialChestsPlanted
                                + " special planted")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } catch (Throwable t) {
            LOGGER.error("scanAndSetupNearbyStructureChests failed for player {}: {}",
                    player != null ? player.getName().getString() : "unknown", t.getMessage(), t);
        }
    }

    /**
     * Runs chest scan/setup for an explicit list of structure IDs.
     * This is used by commands so the heavy logic lives in the manager, not
     * command classes.
     */
    public static void scanAndSetupStructureChestsByIds(CommandSourceStack source,
            List<Long> structureIds, ServerLevel world) {
        if (source == null || structureIds == null || structureIds.isEmpty()) {
            return;
        }

        var structureDb = ModEvents.getGameStructureDatabase(world);
        List<GameStructureData> structures = new ArrayList<>(structureIds.size());
        for (Long structureId : structureIds) {
            GameStructureData sd = structureDb.getData(structureId);
            if (sd == null) {
                LOGGER.info("scanAndSetupStructureChestsByIds: no DB entry for id={}", structureId);
                continue;
            }
            structures.add(sd);
        }
        scanAndSetupStructures(world, structures, source, "scanAndSetupStructureChestsByIds");
    }

    private static ChestScanStats scanAndSetupStructures(ServerLevel world, List<GameStructureData> structures,
            CommandSourceStack source, String logPrefix) {
        var structureDb = ModEvents.getGameStructureDatabase(world);
        ChestScanStats stats = new ChestScanStats();

        for (GameStructureData sd : structures) {
            if (sd == null) {
                continue;
            }
            if (!sd.hasBoundingBox()) {
                LOGGER.info("{}: no bbox for {} (id={})", logPrefix, sd.getName(), sd.getId());
                continue;
            }

            int chunkCount = sd.hasChunkPositions() ? sd.getChunkPositions().size()
                    : ((sd.getMaxPos().getX() >> 4) - (sd.getMinPos().getX() >> 4) + 1)
                            * ((sd.getMaxPos().getZ() >> 4) - (sd.getMinPos().getZ() >> 4) + 1);
            if (chunkCount >= 80) {
                LOGGER.info("{}: skipping {} — too large ({} chunks)", logPrefix, sd.getName(), chunkCount);
                if (source != null) {
                    source.sendSystemMessage(Component.literal(
                            "  Skipping " + sd.getName() + " — too large (" + chunkCount + " chunks).")
                            .withStyle(ChatFormatting.GRAY));
                }
                continue;
            }

                int spawnersRerolled = rerollLycanitesSpawnersInBbox(sd, world, logPrefix);
                if (spawnersRerolled > 0 && source != null
                    && source.getEntity() instanceof net.minecraft.world.entity.player.Player p
                    && ChunkManager.isDebugPlayer(p)) {
                source.sendSystemMessage(Component.literal(
                        "  " + sd.getName() + ": rerolled " + spawnersRerolled + " spawner(s) to Lycanites")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }

            List<BlockPos> foundChests = scanBboxForChests(sd, world);
            LOGGER.info("{}: {} — found {} chest(s), visited={}, hasSpecial={}",
                    logPrefix, sd.getName(), foundChests.size(), sd.wasVisited(), sd.hasSpecialChest());

            boolean plantedSpecial = false;
            if (!foundChests.isEmpty()) {
                stats.chestsScanned += foundChests.size();
                if (sd.getTotalChestCount() != foundChests.size()) {
                    sd.setTotalChestCount(foundChests.size());
                    structureDb.putData(sd.getId(), sd);
                    structureDb.setDirty();
                }
                if (!sd.wasVisited() && !sd.hasSpecialChest()) {
                    if (setupSpecialChest(world, sd, foundChests, structureDb)) {
                        stats.specialChestsPlanted++;
                        plantedSpecial = true;
                    }
                }
            } else if (!sd.wasVisited() && !sd.hasSpecialChest()) {
                BlockPos placed = placeChestAtStructureCenter(sd, world);
                if (placed != null) {
                    stats.chestsScanned++;
                    sd.setTotalChestCount(1);
                    structureDb.putData(sd.getId(), sd);
                    structureDb.setDirty();
                    if (setupSpecialChest(world, sd, java.util.List.of(placed), structureDb)) {
                        stats.specialChestsPlanted++;
                        plantedSpecial = true;
                    }
                    if (source != null) {
                        source.sendSystemMessage(Component.literal(
                                "  " + sd.getName() + ": placed chest at " + placed.toShortString())
                                .withStyle(ChatFormatting.YELLOW));
                    }
                } else {
                    LOGGER.info("{}: {} — could not find solid ground to place chest", logPrefix, sd.getName());
                }
            }

            if (plantedSpecial) {
                LOGGER.info("{}: planted special chest for {} ({})", logPrefix, sd.getName(), sd.getId());
            }

            if (source != null) {
                String chestMsg = "  " + sd.getName() + ": " + foundChests.size() + " chest(s)";
                if (plantedSpecial)
                    chestMsg += " [special chest planted]";
                else if (sd.hasSpecialChest())
                    chestMsg += " [has special]";
                else if (sd.wasVisited())
                    chestMsg += " [visited]";
                source.sendSystemMessage(Component.literal(chestMsg).withStyle(ChatFormatting.AQUA));
            }
        }

        if (source != null && (stats.chestsScanned > 0 || stats.specialChestsPlanted > 0)) {
            source.sendSystemMessage(Component.literal(
                    "Total: " + stats.chestsScanned + " chest(s), "
                            + stats.specialChestsPlanted + " special chest(s) planted.")
                    .withStyle(ChatFormatting.GOLD));
        }

        return stats;
    }

    private static final class ChestScanStats {
        int chestsScanned;
        int specialChestsPlanted;
    }

    /**
     * Walks a structure's bounding box and returns all chest/barrel positions.
     * Only reads blocks in loaded chunks.
     */
    public static List<BlockPos> scanBboxForChests(GameStructureData sd, ServerLevel world) {
        List<BlockPos> found = new ArrayList<>();
        for (int bx = sd.getMinPos().getX(); bx <= sd.getMaxPos().getX(); bx++) {
            for (int bz = sd.getMinPos().getZ(); bz <= sd.getMaxPos().getZ(); bz++) {
                if (!world.isLoaded(new BlockPos(bx, 64, bz))) {
                    continue;
                }
                for (int by = sd.getMinPos().getY(); by <= sd.getMaxPos().getY(); by++) {
                    BlockPos bp = new BlockPos(bx, by, bz);
                    if (isStructureChest(world.getBlockState(bp).getBlock())) {
                        found.add(bp.immutable());
                    }
                }
            }
        }
        return found;
    }

    /**
     * Places a written-book trap item in a random chest of a structure and records
     * the chest position in the DB.
     */
    public static boolean setupSpecialChest(ServerLevel world, GameStructureData s,
            List<BlockPos> chestPositions, DataBase<Long, GameStructureData> structureDb) {
        if (chestPositions.isEmpty())
            return false;
        if (s.wasVisited()) {
            LOGGER.info("setupSpecialChest: skipping {} — already visited", s.getName());
            return false;
        }
        if (s.hasSpecialChest()) {
            LOGGER.info("setupSpecialChest: skipping {} — already has special chest", s.getName());
            return false;
        }

        BlockPos chestPos = chestPositions.get(new java.util.Random().nextInt(chestPositions.size()));
        net.minecraft.world.level.block.entity.BlockEntity be = world.getBlockEntity(chestPos);
        if (!(be instanceof net.minecraft.world.Container)) {
            LOGGER.warn("setupSpecialChest: block entity at {} is not a Container", chestPos);
            return false;
        }

        s.setSpecialChestPos(chestPos);
        structureDb.putData(s.getId(), s);
        structureDb.setDirty();
        LOGGER.info("setupSpecialChest: placed trap chest at {} for structure {} ({})",
                chestPos, s.getName(), s.getId());

        for (net.minecraft.server.level.ServerPlayer online : world.getServer().getPlayerList().getPlayers()) {
            if (com.falazar.farmupcraft.ChunkManager.isDebugPlayer(online)) {
                online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[DEBUG] Special chest + book placed in " + s.getName()
                                + " at " + chestPos.toShortString())
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
        }

        final String structureName = s.getName();
        String cachedNote = com.falazar.farmupcraft.util.NpcDataLoader.popThreatNote();
        if (cachedNote != null) {
            LOGGER.info("setupSpecialChest: using cached threat note for {} ({} remaining in cache).",
                    structureName, com.falazar.farmupcraft.util.NpcDataLoader.getThreatNoteCacheSize());
            final String noteTitleSync = "A Strange Note";
            final String noteBodySync = cachedNote;
            world.getServer().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity beSync = world.getBlockEntity(chestPos);
                if (!(beSync instanceof net.minecraft.world.Container containerSync)) {
                    LOGGER.warn("setupSpecialChest: chest at {} gone before cached note could be written", chestPos);
                    return;
                }
                for (net.minecraft.server.level.ServerPlayer online : world.getServer().getPlayerList().getPlayers()) {
                    if (com.falazar.farmupcraft.ChunkManager.isDebugPlayer(online)) {
                        online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[DEBUG] Chest note OK for " + structureName + " — using cached text")
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                    }
                }
                net.minecraft.world.item.ItemStack bookSync = new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.WRITTEN_BOOK);
                net.minecraft.nbt.CompoundTag tagSync = bookSync.getOrCreateTag();
                tagSync.putString("title", noteTitleSync);
                tagSync.putString("author", "???");
                net.minecraft.nbt.ListTag pagesSync = new net.minecraft.nbt.ListTag();
                pagesSync.add(net.minecraft.nbt.StringTag.valueOf(
                        net.minecraft.network.chat.Component.Serializer.toJson(
                                net.minecraft.network.chat.Component.literal(noteBodySync))));
                tagSync.put("pages", pagesSync);
                tagSync.putInt("generation", 0);
                containerSync.setItem(0, bookSync);
                beSync.setChanged();
                world.blockEntityChanged(chestPos);
                LOGGER.info("setupSpecialChest: cached book written in chest at {}", chestPos);
            });
            return true;
        }

        com.falazar.farmupcraft.AIManager.generateChestThreatNote(structureName)
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((aiText, err) -> {
                    String noteTitle;
                    String noteBody;
                    final String aiTextTrimmed = aiText == null ? "" : aiText.trim();
                    final boolean aiReturnedErrorText = aiTextTrimmed.startsWith("[AI Error]")
                            || aiTextTrimmed.startsWith("AI Error")
                            || aiTextTrimmed.toLowerCase(java.util.Locale.ROOT).contains("ollama returned http");
                    final boolean aiFailure = (err != null || aiTextTrimmed.isBlank() || aiReturnedErrorText);
                    if (aiFailure) {
                        LOGGER.warn("setupSpecialChest: AI note failed ({}), using fallback",
                                err != null ? err.getMessage()
                                        : (aiReturnedErrorText ? "AI returned error text" : "empty"));
                        String[][] threats = {
                                { "Scrawled Note",
                                        "You opened the wrong chest and broke the wrong seal. Leave before the halls decide to keep you." },
                                { "Warning",
                                        "I tracked your footsteps from the first doorway. Every step deeper made your return less likely." },
                                { "A Torn Page",
                                        "The torches in this place do not burn for guests. They burn to mark where the next body falls." },
                                { "Ravings",
                                        "You are not alone in these ruins and never were. The dark only pretended to be empty." },
                                { "Crumpled Letter",
                                        "You touched what was hidden and woke what was patient. It has waited longer than your bloodline." },
                                { "Charcoal Scribble",
                                        "Close the chest and walk away while you still can. The next sound you hear may be behind you." },
                                { "Broken Ledger",
                                        "The old owners wrote names here before they vanished. Your name fits neatly on the next line." },
                                { "Hunter's Mark",
                                        "A red symbol stains this page with fresh intent. It means you have been selected, not warned." },
                                { "Field Report",
                                        "Cyrus was listed as a target in this region. The order came unsigned and marked only with black wax." },
                                { "Intercepted Message",
                                        "Lazarus is marked for capture before dawn. The sender's name was cut off the page." },
                                { "Seal Fragment",
                                        "Someone shattered the ward and left this note as proof. The thing that slipped through is hunting by scent." },
                                { "Captain's Note",
                                        "Your armor may stop steel but not panic. This ruin feeds on panic first." },
                                { "Old Dispatch",
                                        "The patrol entered laughing and left in silence. Only this page came back." },
                                { "Shaky Handwriting",
                                        "If you can read this then it is already close. Do not run in a straight line." },
                                { "Wax-Sealed Slip",
                                        "A bounty notice names Cyrus as a target. Lazarus is mentioned in a separate list, both marked for retrieval." },
                                { "Salt-Stained Note",
                                        "Your lantern is bright enough to guide the hunter. Blow it out and pray you are forgotten." },
                                { "Barn Door Notice",
                                        "Do not trust the quiet rooms in this place. Quiet means something is listening." },
                                { "Stamped Order",
                                        "Lazarus is to be taken alive if possible. Witnesses are to vanish, but no commander is named." },
                                { "Messenger Scrap",
                                        "A rider never reached the village with this warning. The horse returned without him." },
                                { "Watch Post Memo",
                                        "The trap here is older than your maps and newer than your courage. It resets every time greed wins." },
                                { "Bent Corner Page",
                                        "I counted three exits before the walls moved. Count again if you still think you can escape." },
                                { "Ash-Covered Note",
                                        "Do not touch the second chest you find. The first one was bait for the careful." },
                                { "Hasty Confession",
                                        "I sold your route to the hunters for coin and mercy. They are tracking Cyrus, and now they are tracking me too." },
                                { "Guard Rotation",
                                        "If the bells ring once, hide and wait. If they ring twice, run and never look back." },
                                { "Quartermaster Log",
                                        "Supplies vanished and no doors were forced. Something here opens locks without keys." },
                                { "Folded Warning",
                                        "I left this note because I could not lift my sword. Fear weighs more than steel in these halls." },
                                { "Pale Ink",
                                        "Lazarus was seen near these ruins at moonrise. By morning, unnamed hunters had already closed the circle." },
                                { "Redacted Memo",
                                        "Cyrus and Lazarus are both listed as targets in this sector. The rest of the names are blacked out." },
                                { "Torn Contract",
                                        "Your party was not the first to be promised this treasure. You are simply the first to arrive on time." },
                                { "Final Notice",
                                        "When the chest closes, the bargain begins. Leave now or stay forever as payment." },
                                { "Quiet Threat",
                                        "The shadows here keep records better than scribes. They already archived your face." }
                        };
                        int pick = new java.util.Random().nextInt(threats.length);
                        noteTitle = threats[pick][0];
                        noteBody = threats[pick][1];
                    } else {
                        noteTitle = "A Strange Note";
                        noteBody = aiTextTrimmed;
                        LOGGER.info("setupSpecialChest: AI note for {}: {}", structureName, noteBody);
                    }

                    world.getServer().execute(() -> {
                        if (aiFailure) {
                            for (net.minecraft.server.level.ServerPlayer online : world.getServer().getPlayerList()
                                    .getPlayers()) {
                                if (com.falazar.farmupcraft.ChunkManager.isDebugPlayer(online)) {
                                    online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                            "[DEBUG] AI chest note FAILED for " + structureName + " — using fallback")
                                            .withStyle(net.minecraft.ChatFormatting.RED));
                                }
                            }
                        } else {
                            for (net.minecraft.server.level.ServerPlayer online : world.getServer().getPlayerList()
                                    .getPlayers()) {
                                if (com.falazar.farmupcraft.ChunkManager.isDebugPlayer(online)) {
                                    online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                            "[DEBUG] Chest note OK for " + structureName
                                                    + " — using AI text")
                                            .withStyle(net.minecraft.ChatFormatting.GREEN));
                                }
                            }
                        }
                        net.minecraft.world.level.block.entity.BlockEntity beLate = world.getBlockEntity(chestPos);
                        if (!(beLate instanceof net.minecraft.world.Container containerLate)) {
                            LOGGER.warn("setupSpecialChest: chest at {} gone by the time AI responded", chestPos);
                            return;
                        }
                        net.minecraft.world.item.ItemStack book2 = new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.WRITTEN_BOOK);
                        net.minecraft.nbt.CompoundTag tag2 = book2.getOrCreateTag();
                        tag2.putString("title", noteTitle);
                        tag2.putString("author", "???");
                        net.minecraft.nbt.ListTag pages2 = new net.minecraft.nbt.ListTag();
                        pages2.add(net.minecraft.nbt.StringTag.valueOf(
                                net.minecraft.network.chat.Component.Serializer.toJson(
                                        net.minecraft.network.chat.Component.literal(noteBody))));
                        tag2.put("pages", pages2);
                        tag2.putInt("generation", 0);
                        containerLate.setItem(0, book2);
                        beLate.setChanged();
                        world.blockEntityChanged(chestPos);
                        LOGGER.info("setupSpecialChest: book written in chest at {}", chestPos);
                    });
                });
        return true;
    }

    /**
     * Finds solid ground near the structure center and places a chest.
     */
    private static BlockPos placeChestAtStructureCenter(GameStructureData sd, ServerLevel world) {
        BlockPos center = sd.getCenterPos();
        int searchTop = sd.hasBoundingBox() ? sd.getMaxPos().getY() + 2 : center.getY() + 10;
        int searchBot = sd.hasBoundingBox() ? sd.getMinPos().getY() : Math.max(center.getY() - 20, 0);

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                for (int y = searchTop; y >= searchBot; y--) {
                    BlockPos candidate = new BlockPos(cx, y, cz);
                    if (!world.isLoaded(candidate))
                        continue;
                    BlockPos below = candidate.below();
                    net.minecraft.world.level.block.state.BlockState belowState = world.getBlockState(below);
                    net.minecraft.world.level.block.state.BlockState atState = world.getBlockState(candidate);
                    net.minecraft.world.level.block.state.BlockState aboveState = world
                            .getBlockState(candidate.above());
                    if (belowState.isSolidRender(world, below)
                            && (atState.isAir() || atState.canBeReplaced())
                            && (aboveState.isAir() || aboveState.canBeReplaced())) {
                        world.setBlock(candidate, Blocks.CHEST.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_ALL);
                        return candidate.immutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a random passable position within {@code radius} blocks of
     * {@code center}.
     */
    public static BlockPos findRandomSpawnPos(ServerLevel level, BlockPos center, int radius, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int dx = TRAP_RNG.nextInt(radius * 2 + 1) - radius;
            int dz = TRAP_RNG.nextInt(radius * 2 + 1) - radius;
            int startY = center.getY() + (TRAP_RNG.nextInt(5) - 2);

            // Tower-safe search: scan downward from the candidate Y to find a valid
            // ground position up to TRAP_DOWNWARD_SCAN blocks below.
            for (int drop = 0; drop <= TRAP_DOWNWARD_SCAN; drop++) {
                BlockPos candidate = new BlockPos(center.getX() + dx, startY - drop, center.getZ() + dz);
                if (level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())
                        && level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.above()).isAir()) {
                    return candidate;
                }
            }
        }

        // Fallback at chest column: try to find the closest valid position downward.
        for (int drop = 0; drop <= TRAP_DOWNWARD_SCAN; drop++) {
            BlockPos candidate = center.below(drop);
            if (level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return center;
    }
}
