package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.VillagerRecord;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class VillagerManager {
    public static final CustomLogger LOGGER = new CustomLogger(VillagerManager.class.getSimpleName());

    private static final long BELL_WARN_COOLDOWN_MS = 30 * 60 * 1000L;
    private static final int BELL_CHECK_TICKS = 12000;
    private static final Map<UUID, Long> BELL_WARN_CACHE = new HashMap<>();
    private static final Set<UUID> ATTACK_ALERTED = new HashSet<>();
    private static final Map<UUID, StuckRecord> STUCK_CACHE = new HashMap<>();
    private static final int STUCK_CHECK_TICKS = 100;
    private static final int STUCK_THRESHOLD_TICKS = 1200;
    private static final double STUCK_DIST_SQ = 0.1 * 0.1;
    private static final int MAX_STUCK_MESSAGES = 3;
    private static final long STUCK_MSG_COOLDOWN_MS = 15 * 60 * 1000L;
    private static final double NEARBY_PLAYER_DIST_SQ = 32.0 * 32.0;
    private static LocalDate lastCacheClearDate = LocalDate.now();

    static final class StuckRecord {
        BlockPos lastPos;
        int ticksAtPos;
        int messagesSent;
        long lastMessageAt;

        StuckRecord(BlockPos pos) {
            lastPos = pos.immutable();
        }

        boolean check(BlockPos pos, long now) {
            if (pos.distSqr(lastPos) > STUCK_DIST_SQ) {
                lastPos = pos.immutable();
                ticksAtPos = 0;
                return false;
            }
            ticksAtPos += STUCK_CHECK_TICKS;
            if (ticksAtPos < STUCK_THRESHOLD_TICKS || messagesSent >= MAX_STUCK_MESSAGES
                    || now - lastMessageAt < STUCK_MSG_COOLDOWN_MS) {
                return false;
            }
            messagesSent++;
            lastMessageAt = now;
            return true;
        }
    }

    /** Clear server-only warning state when a server stops or a new world starts. */
    public static void clearCaches() {
        STUCK_CACHE.clear();
        BELL_WARN_CACHE.clear();
        ATTACK_ALERTED.clear();
        lastCacheClearDate = LocalDate.now();
    }

    static void clearDailyCaches(LocalDate today) {
        if (!today.equals(lastCacheClearDate)) {
            clearCaches();
            lastCacheClearDate = today;
        }
    }

    static boolean markAttackAlert(UUID id) {
        return ATTACK_ALERTED.add(id);
    }

    static boolean shouldWarnAboutBell(UUID id, long now) {
        Long lastWarn = BELL_WARN_CACHE.get(id);
        if (lastWarn != null && now - lastWarn <= BELL_WARN_COOLDOWN_MS) {
            return false;
        }
        BELL_WARN_CACHE.put(id, now);
        return true;
    }

    @SubscribeEvent
    public static void onVillagerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        // Incoming damage is the cancellable replacement; cancel before mitigation and side effects.
        if (event.getSource().is(DamageTypes.FALL)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Mob attacker && markAttackAlert(villager.getUUID())) {
            BlockPos pos = villager.blockPosition();
            Component msg = Component.literal("[Village] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(villager.getName().getString()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" is under attack by " + attacker.getName().getString()
                            + " at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]!")
                            .withStyle(ChatFormatting.RED));
            MinecraftServer server = villager.level().getServer();
            if (server != null && !notifyVillagePlayers(server, villager, msg)) {
                notifyNearbyPlayers(server, villager, msg);
            }
        }
    }

    @SubscribeEvent
    public static void onVillagerTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        clearDailyCaches(LocalDate.now());
        NpcData npcData = ModEvents.getNpcDatabase(villager.level()).getData(villager.getUUID());
        if (npcData != null && npcData.isForeverKid() && villager.getAge() > -24000) {
            villager.setAge(-24000);
        }
        long now = System.currentTimeMillis();
        if (villager.tickCount % BELL_CHECK_TICKS == 0
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT)
                && shouldWarnAboutBell(villager.getUUID(), now)) {
            LOGGER.info("VillagerManager: {} has no bell/meeting point (plot={}, pos={})",
                    villager.getName().getString(), getVillagerPlotContext(villager),
                    (int) villager.getX() + ", " + (int) villager.getY() + ", " + (int) villager.getZ());
        }
        if (villager.tickCount % STUCK_CHECK_TICKS != 0) {
            return;
        }
        BlockPos pos = villager.blockPosition();
        StuckRecord rec = STUCK_CACHE.computeIfAbsent(villager.getUUID(), id -> new StuckRecord(pos));
        if (rec.check(pos, now)) {
            LOGGER.info("VillagerManager: {} appears stuck at {} plot={} (msg {}/{})",
                    villager.getName().getString(), pos.toShortString(), getVillagerPlotContext(villager),
                    rec.messagesSent, MAX_STUCK_MESSAGES);
        }
    }

    private static boolean notifyVillagePlayers(MinecraftServer server, Villager villager, Component msg) {
        ChunkData chunk = ModEvents.getChunkDataDatabase(villager.level())
                .getData(ChunkPos.containing(villager.blockPosition()).pack());
        if (chunk == null || chunk.getVillageId() == null) {
            return false;
        }
        VillageData village = ModEvents.getVillageDatabase(villager.level()).getData(chunk.getVillageId());
        if (village == null) {
            return false;
        }
        DataBase<UUID, PlayerData> players = ModEvents.getPlayerDatabase(villager.level());
        boolean sent = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = players.getData(player.getUUID());
            if (data != null && village.getUUID().equals(data.getHomeVillageUUID())) {
                player.sendSystemMessage(msg);
                sent = true;
            }
        }
        return sent;
    }

    public static boolean isStandingInVillage(ServerPlayer player) {
        ChunkData chunk = ModEvents.getChunkDataDatabase(player.level())
                .getData(ChunkPos.containing(player.blockPosition()).pack());
        return chunk != null && chunk.getVillageId() != null;
    }

    private static void notifyNearbyPlayers(MinecraftServer server, Villager villager, Component msg) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == villager.level() && player.distanceToSqr(villager) <= NEARBY_PLAYER_DIST_SQ
                    && isStandingInVillage(player)) {
                player.sendSystemMessage(msg);
            }
        }
    }

    public static String getVillagerPlotContextStatic(Villager villager) {
        return getVillagerPlotContext(villager);
    }

    private static String getVillagerPlotContext(Villager villager) {
        return plotContext(ChunkPos.containing(villager.blockPosition()),
                ModEvents.getChunkDataDatabase(villager.level())::getData);
    }

    static String plotContext(ChunkPos center, LongFunction<ChunkData> chunks) {
        ChunkData own = chunks.apply(center.pack());
        if (own != null && isNamedPlot(own.getType())) {
            return own.getType();
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ChunkData neighbor = chunks.apply(ChunkPos.pack(center.x() + dx, center.z() + dz));
                if (neighbor != null && isNamedPlot(neighbor.getType())) {
                    return "near:" + neighbor.getType();
                }
            }
        }
        return own != null && own.getType() != null && !own.getType().isEmpty() ? own.getType() : "unclaimed";
    }

    private static boolean isNamedPlot(String type) {
        return type != null && !type.isEmpty() && !type.equalsIgnoreCase("village");
    }

    static boolean isForeverKid(UUID villager, UUID village) {
        return Math.floorMod(villager.getLeastSignificantBits() ^ village.getMostSignificantBits(), 5) == 0;
    }

    @SubscribeEvent
    public static void onVillagerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof Villager villager)) {
            return;
        }
        ChunkData chunk = ModEvents.getChunkDataDatabase(event.getLevel())
                .getData(ChunkPos.containing(villager.blockPosition()).pack());
        if (chunk == null || chunk.getVillageId() == null) {
            return;
        }
        DataBase<UUID, VillageData> villages = ModEvents.getVillageDatabase(event.getLevel());
        VillageData village = villages.getData(chunk.getVillageId());
        if (village == null) {
            return;
        }
        VillagerRecord record = village.pollReincarnation();
        if (record != null) {
            // This event runs before entity registration, so the restored UUID is indexed correctly.
            villager.setUUID(record.getUuid());
            villager.setCustomName(Component.literal(record.getName()));
            villages.putData(village.getUUID(), village);
            LOGGER.info("[Reincarnation] Applied: " + record.getName() + " | uuid=" + record.getUuid()
                    + " | village=" + village.getName() + " | pos=" + villager.blockPosition()
                    + " | pool_remaining=" + village.getReincarnationPool().size());
        }
        var maxHealth = villager.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() <= 20.0) {
            maxHealth.setBaseValue(60.0);
            villager.setHealth(60.0f);
            LOGGER.info("[Health] {} boosted to 60 HP in village {}", villager.getUUID(), village.getUUID());
        }
        if (isForeverKid(villager.getUUID(), village.getUUID())) {
            villager.setBaby(true);
            DataBase<UUID, NpcData> npcs = ModEvents.getNpcDatabase(event.getLevel());
            NpcData npc = npcs.getData(villager.getUUID());
            if (npc == null) {
                String name = villager.hasCustomName() ? villager.getName().getString() : "";
                npc = new NpcData(villager.getUUID(), name, village.getUUID(), "", "", village.getName(), true);
            } else {
                npc.setForeverKid(true);
            }
            npcs.putData(villager.getUUID(), npc);
            LOGGER.info("[ForeverKid] {} assigned as forever kid in village {}",
                    villager.getUUID(), village.getName());
        }
    }
}
