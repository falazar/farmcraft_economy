package com.falazar.farmupcraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class NpcConversationMemory {
    public static final int MAX_EXCHANGES = 10;
    private static final String KEY = "farmupcraft_conversation_memory";

    public record Exchange(String playerMessage, String npcReply) {
        public Exchange {
            Objects.requireNonNull(playerMessage);
            Objects.requireNonNull(npcReply);
        }
    }

    private NpcConversationMemory() {}

    static List<Exchange> recall(LivingEntity npc, UUID player) {
        var stored = npc.getPersistentData().get(KEY);
        if (stored == null) return List.of();
        if (!(stored instanceof CompoundTag players))
            throw invalidMemory(npc, player);
        var entries = players.get(player.toString());
        if (entries == null) return List.of();
        if (!(entries instanceof ListTag exchanges))
            throw invalidMemory(npc, player);
        var result = new ArrayList<Exchange>();
        for (int i = Math.max(0, exchanges.size() - MAX_EXCHANGES); i < exchanges.size(); i++) {
            if (!(exchanges.get(i) instanceof CompoundTag exchange))
                throw invalidMemory(npc, player);
            String message = exchange.getStringOr("player", "");
            String reply = exchange.getStringOr("assistant", "");
            if (message.isBlank() || !NpcReplyRetry.countsAsConversation(reply, null))
                throw invalidMemory(npc, player);
            result.add(new Exchange(message, reply));
        }
        return List.copyOf(result);
    }

    static boolean remember(LivingEntity npc, UUID player, String message, String reply, Throwable error) {
        if (!NpcReplyRetry.countsAsConversation(reply, error)) return false;
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Cannot remember an empty player message");
        var exchanges = new ArrayList<>(recall(npc, player));
        exchanges.add(new Exchange(message, reply));
        var saved = new ListTag();
        for (int i = Math.max(0, exchanges.size() - MAX_EXCHANGES); i < exchanges.size(); i++) {
            var exchange = exchanges.get(i);
            var entry = new CompoundTag();
            entry.putString("player", exchange.playerMessage());
            entry.putString("assistant", exchange.npcReply());
            saved.add(entry);
        }
        var persistent = npc.getPersistentData();
        var players = persistent.getCompoundOrEmpty(KEY);
        players.put(player.toString(), saved);
        persistent.put(KEY, players);
        var familiar = persistent.getCompoundOrEmpty(NpcReplyRetry.TALKED_PLAYERS_KEY);
        familiar.putBoolean(player.toString(), true);
        persistent.put(NpcReplyRetry.TALKED_PLAYERS_KEY, familiar);
        return true;
    }

    private static IllegalStateException invalidMemory(LivingEntity npc, UUID player) {
        return new IllegalStateException("Invalid saved conversation memory for NPC " + npc.getUUID()
                + ", player " + player);
    }
}
