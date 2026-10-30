package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.database.DataBaseManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Objects;

public final class EDBMessages {
    private EDBMessages() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(DataBaseEntryS2C.TYPE, DataBaseEntryS2C.STREAM_CODEC);
        registrar.playToClient(DataBaseFullS2C.TYPE, DataBaseFullS2C.STREAM_CODEC);
        registrar.playToClient(AddJMWaypointPacket.TYPE, AddJMWaypointPacket.STREAM_CODEC);
        registrar.playToClient(RemoveModWaypointsPacket.TYPE, RemoveModWaypointsPacket.STREAM_CODEC);
        registrar.playToClient(ScanWaypointsPacket.TYPE, ScanWaypointsPacket.STREAM_CODEC);
        registrar.playToClient(HighlightChunkPacket.TYPE, HighlightChunkPacket.STREAM_CODEC);
        registrar.playToClient(ShowVillageChunksPacket.TYPE, ShowVillageChunksPacket.STREAM_CODEC);
        registrar.playToClient(OpenJeiRecipePacket.TYPE, OpenJeiRecipePacket.STREAM_CODEC);
    }

    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        Objects.requireNonNull(payload);
        var server = player.level().getServer();
        server.execute(() -> PacketDistributor.sendToPlayer(player, payload));
    }

    public static void sendToClients(CustomPacketPayload payload) {
        Objects.requireNonNull(payload);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Cannot synchronize a database without a running server");
        }
        server.execute(() -> PacketDistributor.sendToAllPlayers(payload));
    }

    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSnapshots(player);
        }
    }

    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSnapshots(player);
        }
    }

    private static void sendSnapshots(ServerPlayer player) {
        for (var name : DataBaseManager.getDataBasesToSync()) {
            var access = DataBaseManager.getDataBaseAccess(name);
            if (access == null) {
                throw new IllegalStateException("Synchronization configured for an unregistered database: " + name);
            }
            var database = access.get(player.level());
            var snapshot = database.save(new CompoundTag());
            sendToPlayer(new DataBaseFullS2C(snapshot, database.getDatabaseName()), player);
        }
    }
}
