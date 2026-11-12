package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.NpcSpeechPacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcConversationLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.falazar.farmupcraft.util.OllamaService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class NpcManager {
    public static final CustomLogger LOGGER = new CustomLogger(NpcManager.class.getSimpleName());
    private static final long CONVERSATION_TIMEOUT_MS = 120_000;
    private static final double MAX_TALK_DISTANCE = 10.0;
    private static final Map<UUID, ActiveConversation> ACTIVE_CONVERSATIONS = new ConcurrentHashMap<>();
    private static final java.util.Set<ActiveConversation> THINKING = ConcurrentHashMap.newKeySet();

    private NpcManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickNpc(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target))
            return;
        ServerLevel level = player.level();
        requireServerThread(level.getServer());
        if (target instanceof Villager villager
                && player.getMainHandItem().is(net.minecraft.world.item.Items.BREAD)) {
            VillagerFollowManager.toggle(player, villager);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        // Villagers always greet; claimed non-villager mobs can use their assigned AI
        // profiles too.
        if (!(target instanceof Villager) && ModEvents.getNpcDatabase(level).getData(target.getUUID()) == null)
            return;
        String name = target.getName().getString();
        boolean familiar = target.getPersistentData().getCompoundOrEmpty(NpcReplyRetry.TALKED_PLAYERS_KEY)
                .getBooleanOr(player.getUUID().toString(), false);
        if (!familiar) {
            player.sendSystemMessage(Component.literal("Hello, I am ")
                    .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("!")).withStyle(ChatFormatting.GREEN), false);
            EDBMessages.sendToPlayer(NpcSpeechPacket.speech(target.getUUID(), "Hello, I am " + name + "!"), player);
        }
        clearConversation(player.getUUID());
        NpcData profile = NpcDataLoader.findProfile(level, name, target.getUUID());
        if (profile == null) {
            if (!(target instanceof Villager)) {
                tell(player, "(This NPC has no readable profile and cannot be spoken to.)");
                return;
            }
            profile = com.falazar.farmupcraft.gems.GemWorld.generic(name, target.getUUID(),
                    villagerProfession((Villager) target));
        }
        beginConversation(player, target);
        String greeting = familiar ? NpcReplyRetry.returningGreeting(
                player.getName().getString(), level.getRandom().nextInt(10)) : "";
        tell(player, NpcReplyRetry.listeningMessage(name, greeting, familiar));
        if (familiar)
            EDBMessages.sendToPlayer(NpcSpeechPacket.speech(target.getUUID(),
                    greeting), player);
    }

    static void beginConversation(ServerPlayer player, LivingEntity npc) {
        requireServerThread(player.level().getServer());
        ACTIVE_CONVERSATIONS.put(player.getUUID(),
                new ActiveConversation(npc.getUUID(), npc.getName().getString(), player.level(),
                        System.currentTimeMillis(), player));
        if (npc instanceof Mob mob)
            holdConversation(mob);
    }

    public static boolean holdConversation(Mob mob) {
        if (!(mob.level() instanceof ServerLevel))
            return false;
        ActiveConversation focused = null;
        for (var conversation : ACTIVE_CONVERSATIONS.values()) {
            if (!conversation.npc.equals(mob.getUUID()) || availableNpc(conversation.player, conversation) != mob)
                continue;
            if (focused == null || conversation.startedAt > focused.startedAt
                    || conversation.startedAt == focused.startedAt
                            && conversation.player.getUUID().compareTo(focused.player.getUUID()) > 0)
                focused = conversation;
        }
        if (focused == null)
            return false;
        mob.getNavigation().stop();
        mob.setXxa(0);
        mob.setYya(0);
        mob.setSpeed(0);
        mob.setJumping(false);
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, focused.player.getEyePosition());
        mob.setYHeadRot(mob.getYRot());
        mob.setYBodyRot(mob.getYRot());
        return true;
    }

    @SubscribeEvent
    public static void onConversationTick(ServerTickEvent.Post event) {
        ACTIVE_CONVERSATIONS.entrySet().removeIf(entry -> {
            var conversation = entry.getValue();
            if (conversation.level.getServer() != event.getServer()
                    || availableNpc(conversation.player, conversation) != null)
                return false;
            tell(conversation.player, "[NPC] Your conversation with " + conversation.name
                    + " ended (time, distance or world changed).");
            return true;
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.level().getServer();
        requireServerThread(server);
        ActiveConversation conversation = ACTIVE_CONVERSATIONS.get(player.getUUID());
        if (conversation == null)
            return;
        LivingEntity npc = availableNpc(player, conversation);
        if (npc == null) {
            clearConversation(player.getUUID());
            tell(player,
                    "[NPC] Your conversation with " + conversation.name + " ended (time, distance or world changed).");
            return;
        }
        NpcData profile = NpcDataLoader.findProfile(conversation.level, conversation.name, conversation.npc);
        if (profile == null) {
            if (!(npc instanceof Villager)) {
                clearConversation(player.getUUID());
                tell(player, "[NPC] The profile for " + conversation.name + " is missing or invalid.");
                return;
            }
            profile = com.falazar.farmupcraft.gems.GemWorld.generic(conversation.name, conversation.npc,
                    villagerProfession((Villager) npc));
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
        if (THINKING.stream().anyMatch(pending -> pending.player.getUUID().equals(playerId))
                || !THINKING.add(conversation)) {
            tell(player, "[NPC] Please wait for the current reply.");
            return;
        }
        var gemTopic = com.falazar.farmupcraft.gems.GemWorld.conversationGem(
                conversation.level, conversation.npc, npc.blockPosition(), message);
        String prompt;
        try {
            prompt = AIManager.buildNpcSystemPrompt(profile, playerName, message,
                    npc instanceof Villager villager ? villagerProfession(villager) : null);
            if (gemTopic != null)
                prompt += NpcPrompts.gems(
                        com.falazar.farmupcraft.gems.GemPlatform.configDir(),
                        gemTopic.kind() == com.falazar.farmupcraft.gems.GemRules.Kind.NONE ? "IGNORANCE"
                                : gemTopic.kind().name(),
                        gemTopic.fact());
        } catch (IllegalStateException | IllegalArgumentException error) {
            THINKING.remove(conversation);
            LOGGER.error("NPC prompt could not be loaded for {}", conversation.name, error);
            tell(player, "[NPC Error] Could not load the conversation prompt. Check the prompt files and server log.");
            return;
        }
        LOGGER.info("[NPC Prompt] player={}, npc={}, gemMode={}\n{}", playerName, conversation.name,
                gemTopic == null ? "NONE" : gemTopic.kind(), prompt);
        String requestPrompt = prompt;
        NpcReplyRetry.request(
                () -> OllamaService.chatNpc(requestPrompt, message).orTimeout(30,
                        java.util.concurrent.TimeUnit.SECONDS),
                server::execute,
                () -> server.getPlayerList().getPlayer(playerId) == player
                        && ACTIVE_CONVERSATIONS.get(playerId) == conversation
                        && availableNpc(player, conversation) != null,
                reply -> gemTopic != null
                        ? com.falazar.farmupcraft.gems.GemWorld.enforceGemReply(conversation.level, gemTopic, reply)
                        : reply,
                gemTopic != null,
                () -> LOGGER.warn("NPC gem reply rejected for {}; retrying once with the same prompt",
                        conversation.npc))
                .whenComplete((reply, error) -> server.execute(() -> {
                    THINKING.remove(conversation);
                    // Never deliver to a retained player/entity from a previous world, login or
                    // conversation.
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    if (current != player || ACTIVE_CONVERSATIONS.get(playerId) != conversation
                            || availableNpc(current, conversation) == null)
                        return;
                    String safeReply = NpcReplyRetry.replyOrBusy(reply, error,
                            (failedReply, failure) -> LOGGER.error(
                                    "NPC conversation failed for {}: {}; using busy reply",
                                    conversation.npc, failure != null ? failure.getMessage() : failedReply, failure));
                    if (safeReply.startsWith("[NPC Error]")) {
                        tell(current, safeReply);
                        return;
                    }
                    NpcConversationLogger.log(playerName, conversation.name, safeReply);
                    LOGGER.info("[NPC Conversation] <{}> to {}: {}", conversation.name, playerName, safeReply);
                    tell(current, "[NPC] " + conversation.name + ": " + safeReply);
                    EDBMessages.sendToPlayer(NpcSpeechPacket.speech(conversation.npc, safeReply), current);
                    if (NpcReplyRetry.countsAsConversation(reply, error)) {
                        var target = availableNpc(current, conversation);
                        var talked = target.getPersistentData().getCompoundOrEmpty(NpcReplyRetry.TALKED_PLAYERS_KEY);
                        talked.putBoolean(playerId.toString(), true);
                        target.getPersistentData().put(NpcReplyRetry.TALKED_PLAYERS_KEY, talked);
                    }
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

    private static String villagerProfession(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
                .map(key -> key.identifier().getPath().replace('_', ' '))
                .filter(name -> !name.equals("none"))
                .orElse("unemployed");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level))
            return;
        requireServerThread(level.getServer());
        holdConversation(mob);
        NpcData data = ModEvents.getNpcDatabase(level).getData(mob.getUUID());
        if (data != null && "nonhostile".equalsIgnoreCase(data.getBehavior())
                && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level))
            return;
        requireServerThread(level.getServer());
        NpcData data = ModEvents.getNpcDatabase(level).getData(mob.getUUID());
        if (data == null)
            return;
        if ("nonhostile".equalsIgnoreCase(data.getBehavior()) && mob.getTarget() != null)
            mob.setTarget(null);
        if (data.isForeverKid() && mob instanceof Villager villager && villager.getAge() != -24000)
            villager.setAge(-24000);
    }

    public static void clearConversation(UUID playerUUID) {
        ACTIVE_CONVERSATIONS.remove(playerUUID);
    }

    /**
     * Call on server stopping before shutting down OllamaService. Also clear on
     * logout/dimension change.
     */
    public static void shutdown(MinecraftServer server) {
        requireServerThread(server);
        ACTIVE_CONVERSATIONS.entrySet().removeIf(entry -> entry.getValue().level.getServer() == server);
        THINKING.removeIf(conversation -> conversation.level.getServer() == server);
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GREEN), false);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread())
            throw new IllegalStateException("NPC mutation must run on the server thread");
    }

    private record ActiveConversation(UUID npc, String name, ServerLevel level, long startedAt, ServerPlayer player) {
    }
}
