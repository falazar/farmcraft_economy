package com.falazar.farmupcraft;

import com.falazar.farmupcraft.AIManager;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcConversationLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.falazar.farmupcraft.util.OllamaService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NpcManager {
    public static final CustomLogger LOGGER = new CustomLogger(NpcManager.class.getSimpleName());

    /** How long (ms) a conversation window stays open after right-clicking. */
    private static final long CONVERSATION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes

    /** Max blocks distance to still receive a reply. */
    private static final double MAX_TALK_DISTANCE = 10.0;

    /**
     * Active conversations: player UUID → the NPC they are talking to.
     * Cleared when the timer expires or the player moves too far away.
     */
    private static final Map<UUID, ActiveConversation> ACTIVE_CONVERSATIONS = new ConcurrentHashMap<>();
    private static final java.util.Set<ActiveConversation> THINKING = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // Right-click — open conversation window
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickNpc(PlayerInteractEvent.EntityInteract event) {
        try {
            if (event.getLevel().isClientSide())
                return;
            if (event.getHand() != InteractionHand.MAIN_HAND)
                return;

            Entity source = event.getEntity();
            if (!(source instanceof ServerPlayer player))
                return;

            Entity target = event.getTarget();
            if (!(target instanceof Villager villager))
                return;

            if (player.getMainHandItem().is(net.minecraft.world.item.Items.BREAD)) {
                VillagerFollowManager.toggle(player, villager);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            String npcName = villager.getName().getString();
            UUID npcUUID = villager.getUUID();

            LOGGER.info("Right-click on villager: {} ({})", npcName, npcUUID);

            boolean familiar = villager.getPersistentData().getCompound(NpcReplyRetry.TALKED_PLAYERS_KEY)
                    .getBoolean(player.getUUID().toString());
            if (!familiar) {
                MutableComponent greeting = Component.literal("Hello, I am ")
                        .append(Component.literal(npcName).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("!"))
                        .withStyle(ChatFormatting.GREEN);
                player.displayClientMessage(greeting, false);
            }
            clearConversation(player.getUUID());

            // Check if this villager has an AI profile on disk.

            // Open conversation window for 2 minutes.
            beginConversation(player, villager);

            String greeting = familiar ? NpcReplyRetry.returningGreeting(
                    player.getName().getString(), event.getLevel().getRandom().nextInt(10)) : "";
            player.displayClientMessage(
                    Component.literal(NpcReplyRetry.listeningMessage(npcName, greeting, familiar))
                            .withStyle(ChatFormatting.GREEN),
                    false);
        } catch (Throwable t) {
            LOGGER.error("Unhandled NPC right-click error: {}", t.getMessage(), t);
        }
    }

    // -------------------------------------------------------------------------
    // Chat event — intercept player messages while in conversation
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            ActiveConversation conv = ACTIVE_CONVERSATIONS.get(playerUUID);
            if (conv == null)
                return;

            // Check timer.
            if (System.currentTimeMillis() - conv.startedAt > CONVERSATION_TIMEOUT_MS) {
                clearConversation(playerUUID);
                player.displayClientMessage(
                        Component.literal("[NPC] Your conversation with " + conv.npcName + " has timed out.")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                return;
            }

            // Check distance — villager must still be nearby.
            if (availableNpc(player, conv) == null) {
                clearConversation(playerUUID);
                player.displayClientMessage(
                        Component.literal("[NPC] You moved too far from " + conv.npcName + ".")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                return;
            }

            // Load profile from disk (cheap — only reads one file).
            NpcData profile = findProfileSafely(conv.npcName, conv.npcUUID);
            if (profile == null) {
                profile = com.falazar.farmupcraft.gems.GemWorld.generic(conv.npcName, conv.npcUUID);
            }

            // Log and broadcast the player's message publicly before canceling.
            // WHITE matches normal vanilla player chat color.
            String playerMessage = event.getMessage().getString();
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + player.getName().getString() + "> " + playerMessage)
                            .withStyle(ChatFormatting.WHITE),
                    false);

            // Append to logs/<playerName>/<YY-MM-DD>/NPC.txt
            try {
                NpcConversationLogger.log(player.getName().getString(), player.getName().getString(), playerMessage);
            } catch (Throwable logErr) {
                LOGGER.error("NpcConversationLogger unavailable (player msg): {}", logErr.getMessage());
            }
            LOGGER.info("[NPC Conversation] <{}> to {}: {}", player.getName().getString(), conv.npcName, playerMessage);

            // Cancel so the vanilla chat handler doesn't also send it.
            event.setCanceled(true);
            var gemTopic = com.falazar.farmupcraft.gems.GemWorld.conversationGem(
                    (net.minecraft.server.level.ServerLevel) conv.villager.level(),
                    conv.npcUUID, conv.villager.blockPosition(), playerMessage);
            String systemPrompt;
            try {
                systemPrompt = AIManager.buildNpcSystemPrompt(profile, player.getName().getString(), playerMessage);
                if (gemTopic != null) systemPrompt += NpcPrompts.gems(
                        com.falazar.farmupcraft.gems.GemPlatform.configDir(),
                        gemTopic.kind() == com.falazar.farmupcraft.gems.GemRules.Kind.NONE ? "IGNORANCE" : gemTopic.kind().name(),
                        gemTopic.fact());
            } catch (IllegalStateException | IllegalArgumentException error) {
                LOGGER.error("NPC prompt could not be loaded for {}", conv.npcName, error);
                player.displayClientMessage(Component.literal(
                        "[NPC Error] Could not load the conversation prompt. Check the prompt files and server log.")
                        .withStyle(ChatFormatting.GREEN), false);
                return;
            }

            player.displayClientMessage(
                    Component.literal("[NPC] " + conv.npcName + " is thinking...").withStyle(ChatFormatting.GREEN),
                    false);

            MinecraftServer server = player.getServer();
            if (THINKING.stream().anyMatch(pending -> pending.player.getUUID().equals(playerUUID)) || !THINKING.add(conv)) {
                player.displayClientMessage(Component.literal("[NPC] Please wait for the current reply.")
                        .withStyle(ChatFormatting.GREEN), false);
                return;
            }
            LOGGER.info("[NPC Prompt] player={}, npc={}, gemMode={}\n{}", player.getName().getString(), conv.npcName,
                    gemTopic == null ? "NONE" : gemTopic.kind(), systemPrompt);
            String requestPrompt = systemPrompt;
            NpcReplyRetry.request(
                    () -> OllamaService.chatNpc(requestPrompt, playerMessage)
                            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS),
                    server::execute,
                    () -> server.getPlayerList().getPlayer(playerUUID) == player
                            && ACTIVE_CONVERSATIONS.get(playerUUID) == conv && availableNpc(player, conv) != null,
                    reply -> gemTopic != null
                            ? com.falazar.farmupcraft.gems.GemWorld.enforceGemReply(
                                    (net.minecraft.server.level.ServerLevel) conv.villager.level(), gemTopic, reply)
                            : reply,
                    gemTopic != null,
                    () -> LOGGER.warn("NPC gem reply rejected for {}; retrying once with the same prompt", conv.npcUUID))
                    .whenComplete((reply, error) -> server.execute(() -> {
                        THINKING.remove(conv);
                        if (server.getPlayerList().getPlayer(playerUUID) != player
                                || ACTIVE_CONVERSATIONS.get(playerUUID) != conv || availableNpc(player, conv) == null)
                            return;
                        String safeReply = NpcReplyRetry.replyOrBusy(reply, error,
                                (failedReply, failure) -> LOGGER.error("NPC conversation failed for {}: {}; using busy reply",
                                        conv.npcUUID, failure != null ? failure.getMessage() : failedReply, failure));
                        if (safeReply.startsWith("[NPC Error]")) {
                            player.displayClientMessage(Component.literal(safeReply).withStyle(ChatFormatting.GREEN), false);
                            return;
                        }
                        // Append NPC reply to logs/<playerName>/<YY-MM-DD>/NPC.txt
                        try {
                            NpcConversationLogger.log(player.getName().getString(), conv.npcName, safeReply);
                        } catch (Throwable logErr) {
                            LOGGER.error("NpcConversationLogger unavailable (npc reply): {}", logErr.getMessage());
                        }
                        LOGGER.info("[NPC Conversation] <{}> to {}: {}", conv.npcName, player.getName().getString(),
                                safeReply);
                        player.displayClientMessage(
                                Component.literal("[NPC] " + conv.npcName + ": " + safeReply)
                                        .withStyle(ChatFormatting.GREEN),
                                false);
                        if (NpcReplyRetry.countsAsConversation(reply, error)) {
                            var talked = conv.villager.getPersistentData().getCompound(NpcReplyRetry.TALKED_PLAYERS_KEY);
                            talked.putBoolean(playerUUID.toString(), true);
                            conv.villager.getPersistentData().put(NpcReplyRetry.TALKED_PLAYERS_KEY, talked);
                        }
                    }));
        } catch (Throwable t) {
            LOGGER.error("Unhandled NPC chat error: {}", t.getMessage(), t);
            try {
                event.setCanceled(true);
            } catch (Throwable ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static void beginConversation(ServerPlayer player, Villager npc) {
        ACTIVE_CONVERSATIONS.put(player.getUUID(),
                new ActiveConversation(npc.getUUID(), npc.getName().getString(), npc,
                        System.currentTimeMillis(), player));
        holdConversation(npc);
    }

    private static Villager availableNpc(ServerPlayer player, ActiveConversation conversation) {
        var npc = conversation.villager;
        return player.level() == npc.level() && player.isAlive() && npc.isAlive() && !npc.isRemoved()
                && System.currentTimeMillis() - conversation.startedAt <= CONVERSATION_TIMEOUT_MS
                && player.distanceToSqr(npc) <= MAX_TALK_DISTANCE * MAX_TALK_DISTANCE ? npc : null;
    }

    public static boolean holdConversation(Mob mob) {
        if (!(mob.level() instanceof ServerLevel)) return false;
        ActiveConversation focused = null;
        for (var conversation : ACTIVE_CONVERSATIONS.values()) {
            if (!conversation.npcUUID.equals(mob.getUUID()) || availableNpc(conversation.player, conversation) != mob)
                continue;
            if (focused == null || conversation.startedAt > focused.startedAt
                    || conversation.startedAt == focused.startedAt
                    && conversation.player.getUUID().compareTo(focused.player.getUUID()) > 0)
                focused = conversation;
        }
        if (focused == null) return false;
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
    public static void onConversationTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE_CONVERSATIONS.entrySet().removeIf(entry -> {
            var conversation = entry.getValue();
            if (availableNpc(conversation.player, conversation) != null) return false;
            conversation.player.displayClientMessage(Component.literal("[NPC] Your conversation with "
                    + conversation.npcName + " ended (time, distance or world changed).")
                    .withStyle(ChatFormatting.GREEN), false);
            return true;
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearConversation(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearConversation(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_CONVERSATIONS.clear();
        THINKING.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (!(event.getEntity() instanceof Mob mob))
            return;

        NpcData npcData = ModEvents.getNpcDatabase().getData(mob.getUUID());
        if (npcData == null)
            return;

        String behavior = npcData.getBehavior();
        if (behavior == null || !"nonhostile".equalsIgnoreCase(behavior))
            return;

        if (event.getNewTarget() != null) {
            event.setCanceled(true);
        }
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClaimedNpcTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide())
            return;
        if (!(event.getEntity() instanceof Mob mob))
            return;
        holdConversation(mob);
        if (mob.getTarget() == null)
            return;

        NpcData npcData = ModEvents.getNpcDatabase().getData(mob.getUUID());
        if (npcData == null)
            return;
        String behavior = npcData.getBehavior();
        if (behavior == null || !"nonhostile".equalsIgnoreCase(behavior))
            return;

        mob.setTarget(null);
    }

    /** Clears a conversation if one is active for the given player. */
    public static void clearConversation(UUID playerUUID) {
        try {
            ACTIVE_CONVERSATIONS.remove(playerUUID);
        } catch (Throwable t) {
            LOGGER.error("Failed to clear NPC conversation for {}: {}", playerUUID, t.getMessage(), t);
        }
    }

    private static boolean hasProfileSafely(String npcName, UUID npcUUID) {
        try {
            return NpcDataLoader.hasProfile(npcName, npcUUID);
        } catch (Throwable t) {
            LOGGER.error("NpcDataLoader.hasProfile failed for {} ({}): {}", npcName, npcUUID, t.getMessage(), t);
            return false;
        }
    }

    private static NpcData findProfileSafely(String npcName, UUID npcUUID) {
        try {
            return NpcDataLoader.findProfile(npcName, npcUUID);
        } catch (Throwable t) {
            LOGGER.error("NpcDataLoader.findProfile failed for {} ({}): {}", npcName, npcUUID, t.getMessage(), t);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Inner record
    // -------------------------------------------------------------------------

    private record ActiveConversation(UUID npcUUID, String npcName, Villager villager, long startedAt, ServerPlayer player) {
    }
}
