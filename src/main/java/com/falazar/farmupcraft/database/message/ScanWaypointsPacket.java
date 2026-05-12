package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.JourneyMapIntegration;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client packet for scan waypoints.
 * action=ADD: shows 4 corner waypoints for a found feature.
 * action=CLEAR: removes all previously shown scan waypoints.
 */
public class ScanWaypointsPacket {

    public static final byte ADD = 0;
    public static final byte CLEAR = 1;

    public record Entry(String name, int x, int y, int z, int color) {
    }

    private final byte action;
    private final List<Entry> entries;

    /** ADD constructor */
    public ScanWaypointsPacket(List<Entry> entries) {
        this.action = ADD;
        this.entries = entries;
    }

    /** CLEAR constructor */
    public ScanWaypointsPacket() {
        this.action = CLEAR;
        this.entries = List.of();
    }

    public ScanWaypointsPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.entries = new ArrayList<>();
        if (action == ADD) {
            int count = buf.readInt();
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
            }
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByte(action);
        if (action == ADD) {
            buf.writeInt(entries.size());
            for (Entry e : entries) {
                buf.writeUtf(e.name());
                buf.writeInt(e.x());
                buf.writeInt(e.y());
                buf.writeInt(e.z());
                buf.writeInt(e.color());
            }
        }
    }

    public static void handle(ScanWaypointsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ScanWaypointsPacket msg) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null)
            return;

        if (msg.action == CLEAR) {
            for (Waypoint wp : JourneyMapIntegration.getScanWaypoints()) {
                try {
                    api.remove(wp);
                } catch (Exception ignored) {
                }
            }
            JourneyMapIntegration.clearScanWaypoints();
            return;
        }

        ResourceKey<Level> dim = ResourceKey.create(
                Registries.DIMENSION, new ResourceLocation("minecraft:overworld"));
        for (Entry e : msg.entries) {
            Waypoint wp = new Waypoint(FarmUpCraft.MODID, e.name(), dim, new BlockPos(e.x(), e.y(), e.z()));
            wp.setColor(e.color());
            wp.setPersistent(false);
            try {
                api.show(wp);
                JourneyMapIntegration.addScanWaypoint(wp);
            } catch (Exception ex) {
                FarmUpCraft.LOGGER.warn("Failed to show scan waypoint: " + ex.getMessage());
            }
        }
    }
}
