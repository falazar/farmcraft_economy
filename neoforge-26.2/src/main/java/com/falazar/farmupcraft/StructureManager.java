package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Structure chest scanning, threat notes, traps and spawner maintenance. */
@EventBusSubscriber(modid = "farmupcraft")
public class StructureManager {
    public static final CustomLogger LOGGER = new CustomLogger(StructureManager.class.getSimpleName());
    private static final Random TRAP_RNG = new Random();
    private static final int TRAP_SPAWN_RADIUS = 20;
    private static final int TRAP_SPAWN_ATTEMPTS = 40;
    private static final int TRAP_DOWNWARD_SCAN = 20;
    private static final long TRAP_SPAWN_CACHE_MS = 300_000L;
    private static final String SPAWNER_CHECKED_TAG = "farmupcraft_spawner_checked";
    private static final double LYCANITES_SPAWNER_CHANCE = 0.30;
    private record TrapKey(ResourceKey<Level> dimension, long position) {}
    private static final Map<TrapKey, Long> RECENT_TRAP_POS_CACHE = new ConcurrentHashMap<>();
    private static final Map<TrapKey, Long> RECENT_TRAP_CHUNK_CACHE = new ConcurrentHashMap<>();
    private static volatile long lifecycle;

    private static final Identifier[] LYCANITES_TRAP_POOL = {
            Identifier.fromNamespaceAndPath("lycanitesmobs", "geonach"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "wraamon"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "argus"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "grue"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "xaphan"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "behemoth"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "shade"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "reaper"),
            Identifier.fromNamespaceAndPath("lycanitesmobs", "geist")
    };

    /** Invalidate pending world callbacks as well as trap cooldowns on server stop. */
    public static void clearCaches() {
        lifecycle++;
        RECENT_TRAP_POS_CACHE.clear();
        RECENT_TRAP_CHUNK_CACHE.clear();
    }

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
                event.getEntity() instanceof ServerPlayer player ? player : null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpecialChestBreak(BreakBlockEvent event) {
        if (event instanceof com.falazar.farmupcraft.command.BuildCommand.MoveBreakEvent) return;
        // This is a cancellable pre-break event; the spawner's block entity still exists.
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level))
            return;
        triggerSpecialChestTrap(level, event.getPos(),
                event.getPlayer() instanceof ServerPlayer player ? player : null);
        handleSpawnerBreakExtraSpawn(event);
    }

    @SubscribeEvent
    public static void onSpawnerTorchPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;
        Block block = event.getPlacedBlock().getBlock();
        if (block != Blocks.TORCH && block != Blocks.SOUL_TORCH
                && block != Blocks.WALL_TORCH && block != Blocks.SOUL_WALL_TORCH)
            return;
        if (!(event.getLevel().getBlockState(event.getPos().below()).getBlock() instanceof SpawnerBlock))
            return;
        event.setCanceled(true);
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            player.sendOverlayMessage(
                    Component.literal("Torches cannot be placed on spawners.").withStyle(ChatFormatting.RED));
        }
    }

    static EntityType<?> registeredEntityType(Identifier id) {
        // The entity registry is defaulted: a missing optional mod must not become a pig.
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
    }

    private static void handleSpawnerBreakExtraSpawn(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SpawnerBlock)
                || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner))
            return;
        EntityType<?> type = registeredEntityType(readSpawnerEntityId(spawner, level));
        if (type == null)
            type = EntityTypes.ZOMBIE;
        Entity extra = type.create(level, EntitySpawnReason.SPAWNER);
        if (extra == null)
            return;
        extra.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        if (extra instanceof Mob mob)
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.SPAWNER, null);
        level.addFreshEntity(extra);
        LOGGER.info("Spawner at {} broken; spawned extra {}", pos, type);
    }

    private static Identifier readSpawnerEntityId(SpawnerBlockEntity spawner, ServerLevel level) {
        try {
            return readSpawnerEntityId(spawner.saveWithoutMetadata(level.registryAccess()));
        } catch (Exception e) {
            LOGGER.warn("Failed to read spawner entity id: {}", e.getMessage());
            return null;
        }
    }

    static Identifier readSpawnerEntityId(CompoundTag tag) {
        CompoundTag spawnData = tag.getCompound("SpawnData").orElse(null);
        if (spawnData == null)
            return null;
        CompoundTag entity = spawnData.getCompound("entity").orElse(spawnData);
        return entity.getString("id").map(Identifier::tryParse).orElse(null);
    }

    private static EntityType<?> pickRandomLycanitesEntityType() {
        List<EntityType<?>> pool = new ArrayList<>();
        for (Identifier id : LYCANITES_TRAP_POOL) {
            EntityType<?> type = registeredEntityType(id);
            if (type != null)
                pool.add(type);
        }
        return pool.isEmpty() ? null : pool.get(TRAP_RNG.nextInt(pool.size()));
    }

    private static int rerollLycanitesSpawnersInBbox(GameStructureData sd, ServerLevel world, String logPrefix) {
        int rerolled = 0;
        for (int x = sd.getMinPos().getX(); x <= sd.getMaxPos().getX(); x++) {
            for (int z = sd.getMinPos().getZ(); z <= sd.getMaxPos().getZ(); z++) {
                if (!world.hasChunkAt(x, z))
                    continue;
                for (int y = sd.getMinPos().getY(); y <= sd.getMaxPos().getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!(state.getBlock() instanceof SpawnerBlock)
                            || !(world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner))
                        continue;
                    CompoundTag persistent = spawner.getPersistentData();
                    if (persistent.getBooleanOr(SPAWNER_CHECKED_TAG, false))
                        continue;
                    persistent.putBoolean(SPAWNER_CHECKED_TAG, true);
                    spawner.setChanged();
                    if (TRAP_RNG.nextDouble() >= LYCANITES_SPAWNER_CHANCE)
                        continue;
                    EntityType<?> type = pickRandomLycanitesEntityType();
                    if (type == null)
                        continue;
                    spawner.getSpawner().setEntityId(type, world, world.getRandom(), pos);
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                    rerolled++;
                    LOGGER.info("{}: spawner at {} rerolled to lycanites type {}", logPrefix, pos, type);
                }
            }
        }
        return rerolled;
    }

    public static boolean isStructureChest(Block block) {
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL)
            return true;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && id.getNamespace().equals("iceandfire") && id.getPath().contains("chest");
    }

    public static void triggerSpecialChestTrap(Level level, BlockPos pos, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        try {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (!isStructureChest(block))
                return;
            BlockPos partner = null;
            if (block instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                partner = pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
            DataBase<Long, GameStructureData> db = ModEvents.getGameStructureDatabase(serverLevel);
            GameStructureData structure = findStructureAtPos(db, pos);
            if (structure == null || !structure.hasSpecialChest()
                    || structure.getType().contains("village") || structure.getName().contains("village"))
                return;
            if (structure.isChestOpened(pos) || (partner != null && structure.isChestOpened(partner)))
                return;
            structure.markChestOpened(pos);
            if (partner != null)
                structure.markChestOpened(partner);
            db.putData(structure.getId(), structure);
            db.setDirty();
            double chance = structure.getTotalChestCount() > 4 ? 0.30 : 0.70;
            if (TRAP_RNG.nextDouble() >= chance) {
                LOGGER.info("Chest opened at {} in {} — no spawn ({}% miss)", pos, structure.getName(),
                        (int) ((1 - chance) * 100));
                return;
            }
            long now = System.currentTimeMillis();
            if (wasRecentTrap(level.dimension(), pos, now)) {
                LOGGER.info("Chest trap at {} in {} skipped — recent trap spawn cache hit (chunk={})",
                        pos, structure.getName(), ChunkPos.containing(pos));
                return;
            }
            LOGGER.info("Chest trap triggered at {} in structure {} ({}) — spawning mob",
                    pos, structure.getName(), structure.getId());
            spawnTrapMob(serverLevel, findRandomSpawnPos(serverLevel, pos, TRAP_SPAWN_RADIUS, TRAP_SPAWN_ATTEMPTS),
                    player, structure);
            rememberTrap(level.dimension(), pos, now);
        } catch (Throwable t) {
            LOGGER.error("triggerSpecialChestTrap failed at {} for player {}: {}", pos,
                    player != null ? player.getName().getString() : "unknown", t.getMessage(), t);
        }
    }

    static GameStructureData findStructureAtPos(DataBase<Long, GameStructureData> db, BlockPos pos) {
        for (Long id : db.getKeys()) {
            GameStructureData sd = db.getData(id);
            if (sd != null && containsPosition(sd, pos))
                return sd;
        }
        return null;
    }

    static boolean containsPosition(GameStructureData sd, BlockPos pos) {
        if (!sd.hasBoundingBox())
            return false;
        BlockPos min = sd.getMinPos();
        BlockPos max = sd.getMaxPos();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static void spawnTrapMob(ServerLevel level, BlockPos pos, ServerPlayer player, GameStructureData s) {
        Mob mob = createTrapMob(level);
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        if (mob.getType() == EntityTypes.SKELETON || mob.getType() == EntityTypes.ZOMBIE) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET))
                mob.setDropChance(slot, 0f);
        }
        level.addFreshEntity(mob);
        if (player != null) {
            String[] messages = trapMessages(s.getName());
            player.sendSystemMessage(Component.literal(messages[TRAP_RNG.nextInt(messages.length)]
                    + "Something appears!").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static Mob createTrapMob(ServerLevel level) {
        ArrayList<EntityType<?>> pool = new ArrayList<>(List.of(EntityTypes.SKELETON, EntityTypes.ZOMBIE));
        for (Identifier id : LYCANITES_TRAP_POOL) {
            EntityType<?> type = registeredEntityType(id);
            if (type != null)
                pool.add(type);
        }
        Collections.shuffle(pool, TRAP_RNG);
        for (EntityType<?> type : pool) {
            Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (entity instanceof Mob mob)
                return mob;
        }
        return new Skeleton(EntityTypes.SKELETON, level);
    }

    private static void cleanupRecentTrapCache(long now) {
        RECENT_TRAP_POS_CACHE.entrySet().removeIf(e -> now - e.getValue() > TRAP_SPAWN_CACHE_MS);
        RECENT_TRAP_CHUNK_CACHE.entrySet().removeIf(e -> now - e.getValue() > TRAP_SPAWN_CACHE_MS);
    }

    static boolean wasRecentTrap(ResourceKey<Level> dimension, BlockPos pos, long now) {
        cleanupRecentTrapCache(now);
        return RECENT_TRAP_POS_CACHE.containsKey(new TrapKey(dimension, pos.asLong()))
                || RECENT_TRAP_CHUNK_CACHE.containsKey(new TrapKey(dimension, ChunkPos.pack(pos)));
    }

    static void rememberTrap(ResourceKey<Level> dimension, BlockPos pos, long now) {
        RECENT_TRAP_POS_CACHE.put(new TrapKey(dimension, pos.asLong()), now);
        RECENT_TRAP_CHUNK_CACHE.put(new TrapKey(dimension, ChunkPos.pack(pos)), now);
    }

    public static void scanAndSetupNearbyStructureChests(ServerPlayer player, ServerLevel world, int radiusChunks) {
        try {
            int radiusBlocks = Math.max(1, radiusChunks) * 16;
            BlockPos center = player.blockPosition();
            var db = ModEvents.getGameStructureDatabase(world);
            List<GameStructureData> nearby = new ArrayList<>();
            double distance = (double) radiusBlocks * radiusBlocks;
            for (Long id : db.getKeys()) {
                GameStructureData sd = db.getData(id);
                if (sd != null && sd.getCenterPos().distSqr(center) <= distance)
                    nearby.add(sd);
            }
            nearby.sort(Comparator.comparingDouble(sd -> sd.getCenterPos().distSqr(center)));
            if (nearby.isEmpty())
                return;
            ChestScanStats stats = scanAndSetupStructures(world, nearby, null, "scanAndSetupNearbyStructureChests");
            if (ChunkManager.isDebugPlayer(player)) {
                player.sendSystemMessage(Component.literal("[DEBUG] Wilderness scan: " + nearby.size()
                        + " structure(s), " + stats.chestsScanned + " chest(s), " + stats.specialChestsPlanted
                        + " special planted").withStyle(ChatFormatting.DARK_GRAY));
            }
        } catch (Throwable t) {
            LOGGER.error("scanAndSetupNearbyStructureChests failed for player {}: {}",
                    player != null ? player.getName().getString() : "unknown", t.getMessage(), t);
        }
    }

    public static void scanAndSetupStructureChestsByIds(CommandSourceStack source, List<Long> ids, ServerLevel world) {
        if (source == null || ids == null || ids.isEmpty())
            return;
        var db = ModEvents.getGameStructureDatabase(world);
        List<GameStructureData> structures = new ArrayList<>(ids.size());
        for (Long id : ids) {
            GameStructureData sd = db.getData(id);
            if (sd == null)
                LOGGER.info("scanAndSetupStructureChestsByIds: no DB entry for id={}", id);
            else
                structures.add(sd);
        }
        scanAndSetupStructures(world, structures, source, "scanAndSetupStructureChestsByIds");
    }

    private static ChestScanStats scanAndSetupStructures(ServerLevel world, List<GameStructureData> structures,
            CommandSourceStack source, String logPrefix) {
        var db = ModEvents.getGameStructureDatabase(world);
        ChestScanStats stats = new ChestScanStats();
        for (GameStructureData sd : structures) {
            if (sd == null)
                continue;
            if (!sd.hasBoundingBox()) {
                LOGGER.info("{}: no bbox for {} (id={})", logPrefix, sd.getName(), sd.getId());
                continue;
            }
            int chunks = sd.hasChunkPositions() ? sd.getChunkPositions().size()
                    : ((sd.getMaxPos().getX() >> 4) - (sd.getMinPos().getX() >> 4) + 1)
                    * ((sd.getMaxPos().getZ() >> 4) - (sd.getMinPos().getZ() >> 4) + 1);
            if (chunks >= 80) {
                LOGGER.info("{}: skipping {} — too large ({} chunks)", logPrefix, sd.getName(), chunks);
                if (source != null)
                    source.sendSystemMessage(Component.literal("  Skipping " + sd.getName()
                            + " — too large (" + chunks + " chunks).").withStyle(ChatFormatting.GRAY));
                continue;
            }
            int rerolled = rerollLycanitesSpawnersInBbox(sd, world, logPrefix);
            if (rerolled > 0 && source != null
                    && source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                    && ChunkManager.isDebugPlayer(player))
                source.sendSystemMessage(Component.literal("  " + sd.getName() + ": rerolled " + rerolled
                        + " spawner(s) to Lycanites").withStyle(ChatFormatting.DARK_PURPLE));
            List<BlockPos> chests = scanBboxForChests(sd, world);
            LOGGER.info("{}: {} — found {} chest(s), visited={}, hasSpecial={}",
                    logPrefix, sd.getName(), chests.size(), sd.wasVisited(), sd.hasSpecialChest());
            boolean planted = false;
            if (!chests.isEmpty()) {
                stats.chestsScanned += chests.size();
                if (sd.getTotalChestCount() != chests.size()) {
                    sd.setTotalChestCount(chests.size());
                    db.putData(sd.getId(), sd);
                    db.setDirty();
                }
                if (!sd.wasVisited() && !sd.hasSpecialChest())
                    planted = setupSpecialChest(world, sd, chests, db);
            } else if (!sd.wasVisited() && !sd.hasSpecialChest()) {
                BlockPos placed = placeChestAtStructureCenter(sd, world);
                if (placed != null) {
                    stats.chestsScanned++;
                    sd.setTotalChestCount(1);
                    db.putData(sd.getId(), sd);
                    db.setDirty();
                    planted = setupSpecialChest(world, sd, List.of(placed), db);
                    if (source != null)
                        source.sendSystemMessage(Component.literal("  " + sd.getName() + ": placed chest at "
                                + placed.toShortString()).withStyle(ChatFormatting.YELLOW));
                } else {
                    LOGGER.info("{}: {} — could not find solid ground to place chest", logPrefix, sd.getName());
                }
            }
            if (planted) {
                stats.specialChestsPlanted++;
                LOGGER.info("{}: planted special chest for {} ({})", logPrefix, sd.getName(), sd.getId());
            }
            if (source != null) {
                String message = "  " + sd.getName() + ": " + chests.size() + " chest(s)";
                if (planted)
                    message += " [special chest planted]";
                else if (sd.hasSpecialChest())
                    message += " [has special]";
                else if (sd.wasVisited())
                    message += " [visited]";
                source.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
            }
        }
        if (source != null && (stats.chestsScanned > 0 || stats.specialChestsPlanted > 0))
            source.sendSystemMessage(Component.literal("Total: " + stats.chestsScanned + " chest(s), "
                    + stats.specialChestsPlanted + " special chest(s) planted.").withStyle(ChatFormatting.GOLD));
        return stats;
    }

    private static final class ChestScanStats {
        int chestsScanned;
        int specialChestsPlanted;
    }

    public static List<BlockPos> scanBboxForChests(GameStructureData sd, ServerLevel world) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = sd.getMinPos().getX(); x <= sd.getMaxPos().getX(); x++) {
            for (int z = sd.getMinPos().getZ(); z <= sd.getMaxPos().getZ(); z++) {
                if (!world.hasChunkAt(x, z))
                    continue;
                for (int y = sd.getMinPos().getY(); y <= sd.getMaxPos().getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isStructureChest(world.getBlockState(pos).getBlock()))
                        found.add(pos);
                }
            }
        }
        return found;
    }

    public static boolean setupSpecialChest(ServerLevel world, GameStructureData structure,
            List<BlockPos> chestPositions, DataBase<Long, GameStructureData> db) {
        if (chestPositions.isEmpty() || structure.wasVisited() || structure.hasSpecialChest())
            return false;
        BlockPos pos = chestPositions.get(TRAP_RNG.nextInt(chestPositions.size())).immutable();
        BlockEntity original = world.getBlockEntity(pos);
        if (!(original instanceof Container)) {
            LOGGER.warn("setupSpecialChest: block entity at {} is not a Container", pos);
            return false;
        }
        structure.setSpecialChestPos(pos);
        db.putData(structure.getId(), structure);
        db.setDirty();
        String name = structure.getName();
        LOGGER.info("setupSpecialChest: placed trap chest at {} for structure {} ({})", pos, name, structure.getId());
        debug(world, "[DEBUG] Special chest + book placed in " + name + " at " + pos.toShortString(),
                ChatFormatting.LIGHT_PURPLE);
        long generation = lifecycle;
        AIManager.CachedThreatNote cached = AIManager.popCachedThreatNote();
        if (cached != null) {
            LOGGER.info("setupSpecialChest: using cached threat note for {} ({} remaining in cache).",
                    name, cached.remaining());
            writeNote(world, pos, original, generation, "A Strange Note", cached.text());
            debug(world, "[DEBUG] Chest note OK for " + name + " — using cached text", ChatFormatting.GREEN);
            return true;
        }
        AIManager.generateChestThreatNote(name).orTimeout(30, TimeUnit.SECONDS).whenComplete((text, error) -> {
            boolean failed = isFailedNote(text, error);
            String trimmed = text == null ? "" : text.trim();
            String[] fallback = failed ? FALLBACK_NOTES[TRAP_RNG.nextInt(FALLBACK_NOTES.length)] : null;
            String title = failed ? fallback[0] : "A Strange Note";
            String body = failed ? fallback[1] : trimmed;
            if (failed)
                LOGGER.warn("setupSpecialChest: AI note failed ({}), using fallback",
                        error != null ? error.getMessage() : "empty or AI error text");
            else
                LOGGER.info("setupSpecialChest: AI note for {}: {}", name, body);
            if (!isCurrentWorld(world, generation))
                return;
            world.getServer().execute(() -> {
                if (!isCurrentWorld(world, generation))
                    return;
                debug(world, failed ? "[DEBUG] AI chest note FAILED for " + name + " — using fallback"
                        : "[DEBUG] Chest note OK for " + name + " — using AI text",
                        failed ? ChatFormatting.RED : ChatFormatting.GREEN);
                writeNote(world, pos, original, generation, title, body);
            });
        });
        return true;
    }

    static boolean isFailedNote(String text, Throwable error) {
        String trimmed = text == null ? "" : text.trim();
        return error != null || trimmed.isBlank() || trimmed.startsWith("[AI Error]")
                || trimmed.startsWith("AI Error")
                || trimmed.toLowerCase(Locale.ROOT).contains("ollama returned http");
    }

    private static boolean isCurrentWorld(ServerLevel world, long generation) {
        return lifecycle == generation && !world.getServer().isStopped()
                && world.getServer().getLevel(world.dimension()) == world;
    }

    private static void debug(ServerLevel world, String message, ChatFormatting color) {
        for (ServerPlayer online : world.getServer().getPlayerList().getPlayers()) {
            if (ChunkManager.isDebugPlayer(online))
                online.sendSystemMessage(Component.literal(message).withStyle(color));
        }
    }

    static ItemStack createNoteBook(String title, String body) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title),
                "???", 0, List.of(Filterable.passThrough(Component.literal(body))), false));
        return book;
    }

    private static void writeNote(ServerLevel world, BlockPos pos, BlockEntity original, long generation,
            String title, String body) {
        if (!isCurrentWorld(world, generation) || !world.isLoaded(pos))
            return;
        BlockEntity current = world.getBlockEntity(pos);
        if (current != original || !(current instanceof Container container)) {
            LOGGER.warn("setupSpecialChest: chest at {} gone by the time note was ready", pos);
            return;
        }
        container.setItem(0, createNoteBook(title, body));
        current.setChanged();
        world.blockEntityChanged(pos);
        LOGGER.info("setupSpecialChest: book written in chest at {}", pos);
    }

    private static final String[][] FALLBACK_NOTES = {
            { "Scrawled Note", "You opened the wrong chest and broke the wrong seal. Leave before the halls decide to keep you." },
            { "Warning", "I tracked your footsteps from the first doorway. Every step deeper made your return less likely." },
            { "A Torn Page", "The torches in this place do not burn for guests. They burn to mark where the next body falls." },
            { "Ravings", "You are not alone in these ruins and never were. The dark only pretended to be empty." },
            { "Crumpled Letter", "You touched what was hidden and woke what was patient. It has waited longer than your bloodline." },
            { "Charcoal Scribble", "Close the chest and walk away while you still can. The next sound you hear may be behind you." },
            { "Broken Ledger", "The old owners wrote names here before they vanished. Your name fits neatly on the next line." },
            { "Hunter's Mark", "A red symbol stains this page with fresh intent. It means you have been selected, not warned." },
            { "Field Report", "Cyrus was listed as a target in this region. The order came unsigned and marked only with black wax." },
            { "Intercepted Message", "Lazarus is marked for capture before dawn. The sender's name was cut off the page." },
            { "Seal Fragment", "Someone shattered the ward and left this note as proof. The thing that slipped through is hunting by scent." },
            { "Captain's Note", "Your armor may stop steel but not panic. This ruin feeds on panic first." },
            { "Old Dispatch", "The patrol entered laughing and left in silence. Only this page came back." },
            { "Shaky Handwriting", "If you can read this then it is already close. Do not run in a straight line." },
            { "Wax-Sealed Slip", "A bounty notice names Cyrus as a target. Lazarus is mentioned in a separate list, both marked for retrieval." },
            { "Salt-Stained Note", "Your lantern is bright enough to guide the hunter. Blow it out and pray you are forgotten." },
            { "Barn Door Notice", "Do not trust the quiet rooms in this place. Quiet means something is listening." },
            { "Stamped Order", "Lazarus is to be taken alive if possible. Witnesses are to vanish, but no commander is named." },
            { "Messenger Scrap", "A rider never reached the village with this warning. The horse returned without him." },
            { "Watch Post Memo", "The trap here is older than your maps and newer than your courage. It resets every time greed wins." },
            { "Bent Corner Page", "I counted three exits before the walls moved. Count again if you still think you can escape." },
            { "Ash-Covered Note", "Do not touch the second chest you find. The first one was bait for the careful." },
            { "Hasty Confession", "I sold your route to the hunters for coin and mercy. They are tracking Cyrus, and now they are tracking me too." },
            { "Guard Rotation", "If the bells ring once, hide and wait. If they ring twice, run and never look back." },
            { "Quartermaster Log", "Supplies vanished and no doors were forced. Something here opens locks without keys." },
            { "Folded Warning", "I left this note because I could not lift my sword. Fear weighs more than steel in these halls." },
            { "Pale Ink", "Lazarus was seen near these ruins at moonrise. By morning, unnamed hunters had already closed the circle." },
            { "Redacted Memo", "Cyrus and Lazarus are both listed as targets in this sector. The rest of the names are blacked out." },
            { "Torn Contract", "Your party was not the first to be promised this treasure. You are simply the first to arrive on time." },
            { "Final Notice", "When the chest closes, the bargain begins. Leave now or stay forever as payment." },
            { "Quiet Threat", "The shadows here keep records better than scribes. They already archived your face." }
    };

    private static BlockPos placeChestAtStructureCenter(GameStructureData sd, ServerLevel world) {
        BlockPos center = sd.getCenterPos();
        int top = sd.hasBoundingBox() ? sd.getMaxPos().getY() + 2 : center.getY() + 10;
        int bottom = sd.hasBoundingBox() ? sd.getMinPos().getY() : Math.max(center.getY() - 20, 0);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int y = top; y >= bottom; y--) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (!world.isLoaded(pos))
                        continue;
                    BlockState below = world.getBlockState(pos.below());
                    BlockState at = world.getBlockState(pos);
                    BlockState above = world.getBlockState(pos.above());
                    if (below.isSolidRender() && (at.isAir() || at.canBeReplaced())
                            && (above.isAir() || above.canBeReplaced())) {
                        world.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos findRandomSpawnPos(ServerLevel level, BlockPos center, int radius, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int dx = TRAP_RNG.nextInt(radius * 2 + 1) - radius;
            int dz = TRAP_RNG.nextInt(radius * 2 + 1) - radius;
            int startY = center.getY() + TRAP_RNG.nextInt(5) - 2;
            for (int drop = 0; drop <= TRAP_DOWNWARD_SCAN; drop++) {
                BlockPos pos = new BlockPos(center.getX() + dx, startY - drop, center.getZ() + dz);
                if (canSpawnAt(level, pos))
                    return pos;
            }
        }
        for (int drop = 0; drop <= TRAP_DOWNWARD_SCAN; drop++) {
            BlockPos pos = center.below(drop);
            if (canSpawnAt(level, pos))
                return pos;
        }
        return center;
    }

    private static boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.isLoaded(pos.below()) && level.isLoaded(pos.above())
                && level.getBlockState(pos.below()).isSolidRender()
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }
}
