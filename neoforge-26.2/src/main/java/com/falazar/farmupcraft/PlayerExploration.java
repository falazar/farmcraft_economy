package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ExplorationStats;
import com.falazar.farmupcraft.data.ExplorationStats.Area;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.message.ExplorationRequestPacket;
import com.falazar.farmupcraft.database.message.ExplorationResultPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PlayerExploration {
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();

    private PlayerExploration() {}

    public static void showInfo(CommandSourceStack source, UUID playerId, PlayerData player) {
        var stats = player.getExploration();
        Area current = area(source.getServer().overworld());
        long now = System.currentTimeMillis();
        stats.ifPresent(value -> source.sendSuccess(() -> Component.literal(value.summary()
                + " [" + (value.isFresh(current, now) ? "updated " : "stale; updated ")
                + Instant.ofEpochMilli(value.updatedAt()) + "]")
                .withStyle(ChatFormatting.AQUA), false));
        if (stats.filter(value -> value.isFresh(current, now)).isPresent()) return;

        if (!(source.getEntity() instanceof ServerPlayer requester) || !requester.getUUID().equals(playerId)) {
            source.sendSuccess(() -> Component.literal(
                    "Exploration: no current report; the player must open their own player info to refresh it.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!NetworkRegistry.hasChannel(requester.connection, ExplorationRequestPacket.TYPE.id())) {
            source.sendSuccess(() -> Component.literal(
                    "Exploration unavailable: the client needs an updated FarmUpCraft and JourneyMap.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        Pending pending = PENDING.get(requester);
        if (pending == null || !pending.area().equals(current)
                || now - pending.requestId() >= 60_000 || now < pending.requestId()) {
            PENDING.put(requester, new Pending(now, current));
            PacketDistributor.sendToPlayer(requester, new ExplorationRequestPacket(now, current));
        }
        source.sendSuccess(() -> Component.literal("Exploration: checking your saved JourneyMap (cached for 24 hours)...")
                .withStyle(ChatFormatting.GRAY), false);
    }

    public static void receive(ExplorationResultPacket result, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            throw new IllegalStateException("Exploration reports require a server player");
        }
        Pending pending = PENDING.get(player);
        long now = System.currentTimeMillis();
        if (pending == null || pending.requestId() != result.requestId()
                || now < pending.requestId() || now - pending.requestId() > 600_000) {
            LogUtils.getLogger().warn("Rejected unsolicited or expired exploration report from {}", player.getUUID());
            return;
        }
        if (!result.success()) {
            LogUtils.getLogger().warn("JourneyMap exploration calculation failed for {}", player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "Exploration unavailable: JourneyMap surface tiles could not be read. Check the client log; retry in a minute."));
            return;
        }
        Area current = area(player.level().getServer().overworld());
        if (!pending.area().equals(current) || result.mappedChunks() > current.totalChunks()) {
            LogUtils.getLogger().warn("Rejected mismatched exploration report from {}", player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "Exploration report does not match the current world border. Reopen player info in a minute."));
            return;
        }
        var database = ModEvents.getPlayerDatabase(player.level());
        PlayerData data = database.getData(player.getUUID());
        if (data == null) {
            LogUtils.getLogger().warn("Cannot store exploration: player data is missing for {}", player.getUUID());
            player.sendSystemMessage(Component.literal("Cannot store exploration: player data is missing."));
            return;
        }
        var stats = new ExplorationStats(current, result.mappedChunks(), now);
        data.setExploration(stats);
        database.putData(player.getUUID(), data);
        PENDING.remove(player);
        player.sendSystemMessage(Component.literal(stats.summary()).withStyle(ChatFormatting.AQUA));
    }

    static Area area(ServerLevel overworld) {
        var border = overworld.getWorldBorder();
        return new Area((int) Math.floor(border.getMinX()), (int) Math.floor(border.getMinZ()),
                (int) Math.ceil(border.getMaxX()), (int) Math.ceil(border.getMaxZ()));
    }

    private record Pending(long requestId, Area area) {}
}
