package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.VillagerRecord;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VillagerManager {

    public static final CustomLogger LOGGER = new CustomLogger(VillagerManager.class.getSimpleName());

    // ---- Bell (meeting point) missing detection ----
    private static final long BELL_WARN_COOLDOWN_MS = 30 * 60 * 1000L; // 30 minutes between warnings per villager
    private static final int BELL_CHECK_TICKS = 12000; // check every 10 minutes (600s)
    private static final Map<UUID, Long> BELL_WARN_CACHE = new HashMap<>(); // uuid -> last warn time

    // ---- First-mob-attack alert ----
    // Cleared on daily reset alongside STUCK_CACHE.
    private static final Set<UUID> ATTACK_ALERTED = new HashSet<>(); // villager UUIDs already alerted

    // ---- Stuck-villager detection cache (server memory only, never persisted)
    // ----
    // Maps villager UUID -> StuckRecord
    private static final Map<UUID, StuckRecord> STUCK_CACHE = new HashMap<>();

    private static final int STUCK_CHECK_TICKS = 100; // check every 5 seconds
    private static final int STUCK_THRESHOLD_TICKS = 1200; // must not have moved for 60 seconds
    private static final double STUCK_DIST_SQ = 0.1 * 0.1; // less than 0.1 blocks = truly immobile (not just
                                                           // wall-hugging)
    private static final int MAX_STUCK_MESSAGES = 3;
    private static final long STUCK_MSG_COOLDOWN_MS = 15 * 60 * 1000L; // 15 minutes between messages
    private static final double NEARBY_PLAYER_DIST_SQ = 32.0 * 32.0; // 32 blocks

    private static LocalDate lastCacheClearDate = LocalDate.now();

    private static class StuckRecord {
        BlockPos lastPos;
        int ticksAtPos;
        int messagesSent;
        long lastMessageAt;

        StuckRecord(BlockPos pos) {
            this.lastPos = pos;
            this.ticksAtPos = 0;
            this.messagesSent = 0;
            this.lastMessageAt = 0;
        }
    }

    // ---- No fall damage for villagers ----

    @SubscribeEvent
    public static void onVillagerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Villager villager))
            return;
        if (event.getSource().is(DamageTypes.FALL)) {
            event.setCanceled(true);
            return;
        }

        // ---- First mob-attack alert ----
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.Mob
                && !ATTACK_ALERTED.contains(villager.getUUID())) {
            ATTACK_ALERTED.add(villager.getUUID());
            try {
                String vName = villager.getName().getString();
                String attacker = event.getSource().getEntity().getName().getString();
                BlockPos pos = villager.blockPosition();
                Component alertMsg = Component.literal("[Village] ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(vName).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" is under attack by " + attacker
                                + " at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]!")
                                .withStyle(ChatFormatting.RED));
                net.minecraft.server.MinecraftServer server = villager.level().getServer();
                if (server != null) {
                    boolean notified = notifyVillagePlayers(server, villager, alertMsg);
                    if (!notified)
                        notifyNearbyPlayers(server, villager, alertMsg);
                }
            } catch (Exception ex) {
                LOGGER.error("onVillagerHurt alert error: " + ex.getMessage());
            }
        }
    }

    // ---- Stuck-villager detection ----

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager))
            return;
        if (villager.level().isClientSide())
            return;

        // Daily cache clear
        LocalDate today = LocalDate.now();
        if (!today.equals(lastCacheClearDate)) {
            STUCK_CACHE.clear();
            BELL_WARN_CACHE.clear();
            ATTACK_ALERTED.clear();
            lastCacheClearDate = today;
        }

        // ---- Forever-kid: prevent baby villagers from growing up ----
        NpcData npcData = ModEvents.getNpcDatabase().getData(villager.getUUID());
        if (npcData != null && npcData.isForeverKid() && villager.getAge() > -24000) {
            villager.setAge(-24000);
        }

        // ---- Bell (meeting point) check ----
        if (villager.tickCount % BELL_CHECK_TICKS == 0) {
            boolean hasBell = villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT);
            if (!hasBell) {
                UUID bellId = villager.getUUID();
                long now = System.currentTimeMillis();
                Long lastWarn = BELL_WARN_CACHE.get(bellId);
                if (lastWarn == null || now - lastWarn > BELL_WARN_COOLDOWN_MS) {
                    BELL_WARN_CACHE.put(bellId, now);
                    String vName = villager.getName().getString();
                    String plotCtx = getVillagerPlotContext(villager);
                    int vx = (int) villager.getX();
                    int vy = (int) villager.getY();
                    int vz = (int) villager.getZ();
                    String coords = vx + ", " + vy + ", " + vz;
                    LOGGER.info("VillagerManager: {} has no bell/meeting point (plot={}, pos={})", vName, plotCtx,
                            coords);
                    // Component bellMsg = Component.literal("[Village] ")
                    // .withStyle(ChatFormatting.YELLOW)
                    // .append(Component.literal(vName).withStyle(ChatFormatting.WHITE))
                    // .append(Component
                    // .literal(" can't find a bell to sleep/meet at [" + coords + "]")
                    // .withStyle(ChatFormatting.GOLD));
                    net.minecraft.server.MinecraftServer bellServer = villager.level().getServer();
                    if (bellServer != null) {
                        // boolean notified = notifyVillagePlayers(bellServer, villager, bellMsg);
                        // if (!notified)
                        //     notifyNearbyPlayers(bellServer, villager, bellMsg);
                    }
                }
            }
        }

        // Only check every N ticks to avoid overhead
        if (villager.tickCount % STUCK_CHECK_TICKS != 0)
            return;

        UUID id = villager.getUUID();
        BlockPos pos = villager.blockPosition();

        StuckRecord rec = STUCK_CACHE.computeIfAbsent(id, k -> new StuckRecord(pos));

        // If villager moved, reset counter
        if (pos.distSqr(rec.lastPos) > STUCK_DIST_SQ) {
            rec.lastPos = pos;
            rec.ticksAtPos = 0;
            return;
        }

        rec.ticksAtPos += STUCK_CHECK_TICKS;

        // Not stuck long enough yet
        if (rec.ticksAtPos < STUCK_THRESHOLD_TICKS)
            return;

        // Max messages exhausted
        if (rec.messagesSent >= MAX_STUCK_MESSAGES)
            return;

        // Cooldown between messages
        long now = System.currentTimeMillis();
        if (now - rec.lastMessageAt < STUCK_MSG_COOLDOWN_MS)
            return;

        rec.messagesSent++;
        rec.lastMessageAt = now;

        String villagerName = villager.getName().getString();
        String posStr = pos.toShortString();

        // Determine plot context: show own chunk type, or nearest typed neighbor.
        String plotContext = getVillagerPlotContext(villager);

        LOGGER.info("VillagerManager: {} appears stuck at {} plot={} (msg {}/{})", villagerName, posStr, plotContext,
                rec.messagesSent,
                MAX_STUCK_MESSAGES);

        // TODO: re-enable when less spammy
        // Component msg = Component.literal("[Village] ")
        // .withStyle(ChatFormatting.YELLOW)
        // .append(Component.literal(villagerName).withStyle(ChatFormatting.WHITE))
        // .append(Component.literal(" appears to be stuck at " + posStr
        // + " [" + plotContext + "]"
        // + " (" + rec.messagesSent + "/" + MAX_STUCK_MESSAGES +
        // ")").withStyle(ChatFormatting.GRAY));
        //
        // net.minecraft.server.MinecraftServer server = villager.level().getServer();
        // if (server == null)
        // return;
        //
        // // Try to notify village members first; fall back to nearby players
        // boolean notified = notifyVillagePlayers(server, villager, msg);
        // if (!notified) {
        // notifyNearbyPlayers(server, villager, msg);
        // }
    }

    /**
     * Sends the message to all online players whose home village matches the chunk
     * the villager is standing in. Returns true if at least one player was
     * notified.
     */
    private static boolean notifyVillagePlayers(net.minecraft.server.MinecraftServer server,
            Villager villager, Component msg) {
        try {
            ChunkPos chunk = new ChunkPos(villager.blockPosition());
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDb.getData(chunk.toLong());
            if (chunkData == null || chunkData.getVillageId() == null)
                return false;

            DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
            VillageData village = villageDb.getData(chunkData.getVillageId());
            if (village == null)
                return false;

            DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            boolean sent = false;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                PlayerData pd = playerDb.getData(sp.getUUID());
                if (pd != null && village.getUUID().equals(pd.getHomeVillageUUID())) {
                    sp.sendSystemMessage(msg);
                    sent = true;
                }
            }
            return sent;
        } catch (Exception ex) {
            LOGGER.error("notifyVillagePlayers error: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the player is currently standing inside a claimed village
     * chunk (any chunk that has a villageId assigned).
     */
    public static boolean isStandingInVillage(ServerPlayer player) {
        try {
            ChunkPos chunk = new ChunkPos(player.blockPosition());
            ChunkData cd = ModEvents.getChunkDataDatabase().getData(chunk.toLong());
            return cd != null && cd.getVillageId() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Sends the message to all online players within NEARBY_PLAYER_DIST_SQ of the
     * villager, but only if the player is currently standing inside a village chunk
     * (claimed plot).
     */
    private static void notifyNearbyPlayers(net.minecraft.server.MinecraftServer server,
            Villager villager, Component msg) {
        try {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (sp.level() != villager.level())
                    continue;
                if (sp.distanceToSqr(villager) > NEARBY_PLAYER_DIST_SQ)
                    continue;
                if (!isStandingInVillage(sp))
                    continue;
                sp.sendSystemMessage(msg);
            }
        } catch (Exception ex) {
            LOGGER.error("notifyNearbyPlayers error: " + ex.getMessage());
        }
    }

    /**
     * Public static version for use from commands (e.g. /village villagers list).
     */
    public static String getVillagerPlotContextStatic(Villager villager) {
        return getVillagerPlotContext(villager);
    }

    /**
     * Returns a short plot-context string for the villager's position.
     * Shows their own chunk type if it is a named plot (not "village" or "").
     * Otherwise scans 8 neighboring chunks and returns the first typed plot found.
     */
    private static String getVillagerPlotContext(Villager villager) {
        try {
            BlockPos pos = villager.blockPosition();
            net.minecraft.world.level.Level level = villager.level();
            com.falazar.farmupcraft.database.DataBase<Long, com.falazar.farmupcraft.data.ChunkData> chunkDb = ModEvents
                    .getChunkDataDatabase();
            ChunkPos center = new ChunkPos(pos);

            com.falazar.farmupcraft.data.ChunkData own = chunkDb.getData(center.toLong());
            if (own != null) {
                String t = own.getType();
                if (t != null && !t.isEmpty() && !t.equalsIgnoreCase("village")) {
                    return t;
                }
            }

            // Check 8 neighbors for a typed (non-village) plot.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0)
                        continue;
                    ChunkPos neighbor = new ChunkPos(center.x + dx, center.z + dz);
                    com.falazar.farmupcraft.data.ChunkData nd = chunkDb.getData(neighbor.toLong());
                    if (nd != null) {
                        String t = nd.getType();
                        if (t != null && !t.isEmpty() && !t.equalsIgnoreCase("village")) {
                            return "near:" + t;
                        }
                    }
                }
            }

            // Fall back to own type or "unclaimed"
            if (own != null && own.getType() != null && !own.getType().isEmpty()) {
                return own.getType();
            }
            return "unclaimed";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    // ---- Villager reincarnation: restore name + UUID from pool on new spawn ----
    @SubscribeEvent
    public static void onVillagerJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide())
            return;
        // Only handle new spawns, not world-load restores of existing villagers.
        if (event.loadedFromDisk())
            return;
        if (!(event.getEntity() instanceof Villager villager))
            return;

        try {
            BlockPos pos = villager.blockPosition();
            ChunkPos chunkPos = new ChunkPos(pos);
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDb.getData(chunkPos.toLong());
            if (chunkData == null || chunkData.getVillageId() == null)
                return;
            DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
            VillageData village = villageDb.getData(chunkData.getVillageId());
            if (village == null)
                return;

            VillagerRecord rec = village.pollReincarnation();
            if (rec == null)
                return;

            // Assign old identity — must happen before entity is fully registered.
            villager.setUUID(rec.getUuid());
            villager.setCustomName(Component.literal(rec.getName()));

            // Persist the drained pool.
            villageDb.putData(village.getUUID(), village);

            LOGGER.info("[Reincarnation] Applied: " + rec.getName()
                    + " | uuid=" + rec.getUuid()
                    + " | village=" + village.getName()
                    + " | pos=" + pos
                    + " | pool_remaining=" + village.getReincarnationPool().size());
        } catch (Exception ex) {
            LOGGER.error("onVillagerJoin reincarnation error: " + ex.getMessage());
        }

        // ---- Triple max health for village-born villagers ----
        try {
            BlockPos hPos = villager.blockPosition();
            ChunkData hChunk = ModEvents.getChunkDataDatabase().getData(new ChunkPos(hPos).toLong());
            if (hChunk != null && hChunk.getVillageId() != null) {
                var maxHpAttr = villager.getAttribute(Attributes.MAX_HEALTH);
                if (maxHpAttr != null && maxHpAttr.getBaseValue() <= 20.0) {
                    maxHpAttr.setBaseValue(60.0); // triple vanilla 20 HP
                    villager.setHealth(60.0f);
                    LOGGER.info("[Health] {} boosted to 60 HP in village {}", villager.getUUID(),
                            hChunk.getVillageId());
                }
            }
        } catch (Exception ex) {
            LOGGER.error("onVillagerJoin health boost error: " + ex.getMessage());
        }

        // ---- Deterministic forever-kid assignment (~20% of villagers, stable per
        // UUID) ----
        try {
            BlockPos pos2 = villager.blockPosition();
            ChunkPos chunkPos2 = new ChunkPos(pos2);
            DataBase<Long, ChunkData> chunkDb2 = ModEvents.getChunkDataDatabase();
            ChunkData chunkData2 = chunkDb2.getData(chunkPos2.toLong());
            if (chunkData2 != null && chunkData2.getVillageId() != null) {
                DataBase<UUID, VillageData> villageDb2 = ModEvents.getVillageDatabase();
                VillageData village2 = villageDb2.getData(chunkData2.getVillageId());
                if (village2 != null) {
                    long kidSeed = villager.getUUID().getLeastSignificantBits()
                            ^ village2.getUUID().getMostSignificantBits();
                    if (Math.floorMod(kidSeed, 5) == 0) {
                        villager.setBaby(true);
                        DataBase<UUID, NpcData> npcDb = ModEvents.getNpcDatabase();
                        NpcData existing = npcDb.getData(villager.getUUID());
                        if (existing != null) {
                            existing.setForeverKid(true);
                            npcDb.putData(villager.getUUID(), existing);
                        } else {
                            String vn = villager.hasCustomName() ? villager.getName().getString() : "";
                            NpcData newData = new NpcData(villager.getUUID(), vn, village2.getUUID(),
                                    "", "", village2.getName(), true);
                            npcDb.putData(villager.getUUID(), newData);
                        }
                        LOGGER.info("[ForeverKid] {} assigned as forever kid in village {}",
                                villager.getUUID(), village2.getName());
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("onVillagerJoin foreverKid error: " + ex.getMessage());
        }
    }
}
