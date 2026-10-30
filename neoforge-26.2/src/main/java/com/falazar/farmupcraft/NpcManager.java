package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcConversationLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.falazar.farmupcraft.util.OllamaService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class NpcManager {
    public static final CustomLogger LOGGER = new CustomLogger(NpcManager.class.getSimpleName());
    private static final long CONVERSATION_TIMEOUT_MS = 120_000;
    private static final double MAX_TALK_DISTANCE = 10.0;
    private static final Map<UUID, ActiveConversation> ACTIVE_CONVERSATIONS = new ConcurrentHashMap<>();

    private NpcManager() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickNpc(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target)) return;
        ServerLevel level = player.level();
        requireServerThread(level.getServer());
        // Villagers always greet; claimed non-villager mobs can use their assigned AI profiles too.
        if (!(target instanceof Villager) && ModEvents.getNpcDatabase(level).getData(target.getUUID()) == null)
            return;
        String name = target.getName().getString();
        player.sendSystemMessage(Component.literal("Hello, I am ")
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("!")).withStyle(ChatFormatting.GREEN), false);
        clearConversation(player.getUUID());
        NpcData profile = NpcDataLoader.findProfile(level, name, target.getUUID());
        if (profile == null) {
            tell(player, "(This NPC has no readable profile and cannot be spoken to.)");
            return;
        }
        ACTIVE_CONVERSATIONS.put(player.getUUID(),
                new ActiveConversation(target.getUUID(), name, level, System.currentTimeMillis()));
        tell(player, "[NPC] " + name + " is listening. Say something in chat within 2 minutes!");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.level().getServer();
        requireServerThread(server);
        ActiveConversation conversation = ACTIVE_CONVERSATIONS.get(player.getUUID());
        if (conversation == null) return;
        LivingEntity npc = availableNpc(player, conversation);
        if (npc == null) {
            clearConversation(player.getUUID());
            tell(player, "[NPC] Your conversation with " + conversation.name + " ended (time, distance or world changed).");
            return;
        }
        NpcData profile = NpcDataLoader.findProfile(conversation.level, conversation.name, conversation.npc);
        if (profile == null) {
            clearConversation(player.getUUID());
            tell(player, "[NPC] The profile for " + conversation.name + " is missing or invalid.");
            return;
        }
        String message = event.getMessage().getString();
        String playerName = player.getName().getString();
        UUID playerId = player.getUUID();
        event.setCanceled(true);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("<" + playerName + "> " + message).withStyle(ChatFormatting.WHITE), false);
        NpcConversationLogger.log(playerName, playerName, message);
        LOGGER.info("[NPC Conversation] <{}> to {}: {}", playerName, conversation.name, message);
        tell(player, "[NPC] " + conversation.name + " is thinking...");
        String prompt = AIManager.buildNpcSystemPrompt(profile, playerName, message);
        OllamaService.chat(prompt).whenComplete((reply, error) -> server.execute(() -> {
            // Never deliver to a retained player/entity from a previous world, login or conversation.
            ServerPlayer current = server.getPlayerList().getPlayer(playerId);
            if (current != player || ACTIVE_CONVERSATIONS.get(playerId) != conversation
                    || availableNpc(current, conversation) == null) return;
            if (error != null || reply == null || reply.isBlank() || reply.startsWith("[AI Error]")) {
                LOGGER.error("NPC conversation failed for {}: {}", conversation.npc,
                        error != null ? error.getMessage() : reply, error);
                tell(current, "[NPC Error] Could not obtain a reply. Please try again.");
                return;
            }
            NpcConversationLogger.log(playerName, conversation.name, reply);
            LOGGER.info("[NPC Conversation] <{}> to {}: {}", conversation.name, playerName, reply);
            tell(current, "[NPC] " + conversation.name + ": " + reply);
        }));
    }

    private static LivingEntity availableNpc(ServerPlayer player, ActiveConversation conversation) {
        if (player.level() != conversation.level || !player.isAlive()
                || System.currentTimeMillis() - conversation.startedAt > CONVERSATION_TIMEOUT_MS)
            return null;
        var entity = conversation.level.getEntity(conversation.npc);
        return entity instanceof LivingEntity npc && npc.isAlive() && !npc.isRemoved()
                && player.distanceToSqr(npc) <= MAX_TALK_DISTANCE * MAX_TALK_DISTANCE ? npc : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        requireServerThread(level.getServer());
        NpcData data = ModEvents.getNpcDatabase(level).getData(mob.getUUID());
        if (data != null && "nonhostile".equalsIgnoreCase(data.getBehavior())
                && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        requireServerThread(level.getServer());
        NpcData data = ModEvents.getNpcDatabase(level).getData(mob.getUUID());
        if (data == null) return;
        if ("nonhostile".equalsIgnoreCase(data.getBehavior()) && mob.getTarget() != null) mob.setTarget(null);
        if (data.isForeverKid() && mob instanceof Villager villager && villager.getAge() != -24000)
            villager.setAge(-24000);
    }

    public static void clearConversation(UUID playerUUID) {
        ACTIVE_CONVERSATIONS.remove(playerUUID);
    }

    /** Call on server stopping before shutting down OllamaService. Also clear on logout/dimension change. */
    public static void shutdown(MinecraftServer server) {
        requireServerThread(server);
        ACTIVE_CONVERSATIONS.entrySet().removeIf(entry -> entry.getValue().level.getServer() == server);
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GREEN), false);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("NPC mutation must run on the server thread");
    }

    private record ActiveConversation(UUID npc, String name, ServerLevel level, long startedAt) {}
}
