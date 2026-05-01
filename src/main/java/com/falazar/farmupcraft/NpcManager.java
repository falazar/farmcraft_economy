package com.falazar.farmupcraft;

import com.falazar.farmupcraft.AIManager;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcConversationLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.falazar.farmupcraft.util.OllamaService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

    // -------------------------------------------------------------------------
    // Right-click — open conversation window
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickNpc(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide())
            return;
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        Entity target = event.getTarget();
        if (!(target instanceof Villager villager))
            return;

        String npcName = villager.getName().getString();
        UUID npcUUID = villager.getUUID();

        LOGGER.info("Right-click on villager: {} ({})", npcName, npcUUID);

        // Always say hello.
        MutableComponent greeting = Component.literal("Hello, I am ")
                .append(Component.literal(npcName).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("!"))
                .withStyle(ChatFormatting.GREEN);
        player.displayClientMessage(greeting, false);

        // Check if this villager has an AI profile on disk.
        if (!NpcDataLoader.hasProfile(npcName, npcUUID)) {
            player.displayClientMessage(
                    Component.literal("(This villager has no profile and cannot be spoken to.)")
                            .withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }

        // Open conversation window for 2 minutes.
        ACTIVE_CONVERSATIONS.put(player.getUUID(),
                new ActiveConversation(npcUUID, npcName, villager, System.currentTimeMillis()));

        player.displayClientMessage(
                Component.literal("[NPC] " + npcName + " is listening. Say something in chat within 2 minutes!")
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    // -------------------------------------------------------------------------
    // Chat event — intercept player messages while in conversation
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUUID();

        ActiveConversation conv = ACTIVE_CONVERSATIONS.get(playerUUID);
        if (conv == null)
            return;

        // Check timer.
        if (System.currentTimeMillis() - conv.startedAt > CONVERSATION_TIMEOUT_MS) {
            ACTIVE_CONVERSATIONS.remove(playerUUID);
            player.displayClientMessage(
                    Component.literal("[NPC] Your conversation with " + conv.npcName + " has timed out.")
                            .withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }

        // Check distance — villager must still be nearby.
        double dist = player.distanceTo(conv.villager);
        if (dist > MAX_TALK_DISTANCE) {
            ACTIVE_CONVERSATIONS.remove(playerUUID);
            player.displayClientMessage(
                    Component.literal("[NPC] You moved too far from " + conv.npcName + ".")
                            .withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }

        // Load profile from disk (cheap — only reads one file).
        NpcData profile = NpcDataLoader.findProfile(conv.npcName, conv.npcUUID);
        if (profile == null) {
            ACTIVE_CONVERSATIONS.remove(playerUUID);
            return;
        }

        // Log and broadcast the player's message publicly before canceling.
        // WHITE matches normal vanilla player chat color.
        String playerMessage = event.getMessage().getString();
        player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + player.getName().getString() + "> " + playerMessage)
                        .withStyle(ChatFormatting.WHITE),
                false);

        // Append to logs/<playerName>/<YY-MM-DD>/NPC.txt
        NpcConversationLogger.log(player.getName().getString(), player.getName().getString(), playerMessage);
        LOGGER.info("[NPC Conversation] <{}> to {}: {}", player.getName().getString(), conv.npcName, playerMessage);

        // Cancel so the vanilla chat handler doesn't also send it.
        event.setCanceled(true);

        // TODO: Send the full conversation history to the AI instead of just the latest
        // message. Track a List<ChatMessage> in ActiveConversation (alternating
        // user/assistant turns) and pass the whole list to OllamaService.chat().

        // Build a system prompt that gives the AI the NPC's personality.
        String systemPrompt = AIManager.buildNpcSystemPrompt(profile, player.getName().getString(), playerMessage);

        player.displayClientMessage(
                Component.literal("[NPC] " + conv.npcName + " is thinking...").withStyle(ChatFormatting.GREEN), false);

        MinecraftServer server = player.getServer();
        OllamaService.chat(systemPrompt)
                .thenAccept(reply -> server.execute(() -> {
                    // Append NPC reply to logs/<playerName>/<YY-MM-DD>/NPC.txt
                    NpcConversationLogger.log(player.getName().getString(), conv.npcName, reply);
                    LOGGER.info("[NPC Conversation] <{}> to {}: {}", conv.npcName, player.getName().getString(), reply);
                    player.displayClientMessage(
                            Component.literal("[NPC] " + conv.npcName + ": " + reply)
                                    .withStyle(ChatFormatting.GREEN),
                            false);
                }))
                .exceptionally(err -> {
                    server.execute(() -> player.displayClientMessage(
                            Component.literal("[NPC Error] " + err.getMessage())
                                    .withStyle(ChatFormatting.GREEN),
                            false));
                    return null;
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Clears a conversation if one is active for the given player. */
    public static void clearConversation(UUID playerUUID) {
        ACTIVE_CONVERSATIONS.remove(playerUUID);
    }

    // -------------------------------------------------------------------------
    // Inner record
    // -------------------------------------------------------------------------

    private record ActiveConversation(UUID npcUUID, String npcName, Villager villager, long startedAt) {
    }
}
