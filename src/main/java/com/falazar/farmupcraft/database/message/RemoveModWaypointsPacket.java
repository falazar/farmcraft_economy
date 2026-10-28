package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.JourneyMapIntegration;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client packet that removes (or previews) this mod's JourneyMap
 * waypoints within a given block range of a position.
 *
 * /farmcraft map clearwaypoints [test] [chunks]
 */
public class RemoveModWaypointsPacket {

    private final double originX;
    private final double originY;
    private final double originZ;
    /** Range in blocks. */
    private final int rangeBlocks;
    /** If true, just list matching waypoints — do NOT remove them. */
    private final boolean testOnly;
    /** Optional name filter; when set, only waypoints containing this text match. */
    private final String nameFilter;
    /** If true, process only the nearest matched waypoint. */
    private final boolean nearestOnly;

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly) {
        this(x, y, z, rangeBlocks, testOnly, null);
    }

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly,
            String nameFilter) {
        this(x, y, z, rangeBlocks, testOnly, nameFilter, false);
    }

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly,
            String nameFilter, boolean nearestOnly) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.rangeBlocks = rangeBlocks;
        this.testOnly = testOnly;
        this.nameFilter = nameFilter;
        this.nearestOnly = nearestOnly;
    }

    public RemoveModWaypointsPacket(FriendlyByteBuf buf) {
        this.originX = buf.readDouble();
        this.originY = buf.readDouble();
        this.originZ = buf.readDouble();
        this.rangeBlocks = buf.readInt();
        this.testOnly = buf.readBoolean();
        this.nameFilter = buf.readBoolean() ? buf.readUtf(256) : null;
        this.nearestOnly = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeInt(rangeBlocks);
        buf.writeBoolean(testOnly);
        if (nameFilter == null || nameFilter.isBlank()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(nameFilter, 256);
        }
        buf.writeBoolean(nearestOnly);
    }

    public static void handle(RemoveModWaypointsPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> process(msg));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void process(RemoveModWaypointsPacket msg) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null)
            return;

        List<Waypoint> candidates = api.getWaypoints(FarmUpCraft.MODID);
        if (candidates == null || candidates.isEmpty()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                        "[Waypoints] No mod-created waypoints found.").withStyle(ChatFormatting.YELLOW));
            }
            return;
        }

        double rangeSq = (double) msg.rangeBlocks * msg.rangeBlocks;
        List<Waypoint> matched = new ArrayList<>();
        for (Waypoint wp : candidates) {
            if (msg.nameFilter != null && !msg.nameFilter.isBlank()) {
                String wpName = wp.getName();
                if (wpName == null || !wpName.toLowerCase().contains(msg.nameFilter.toLowerCase())) {
                    continue;
                }
            }
            double dx = wp.getPosition().getX() - msg.originX;
            double dy = wp.getPosition().getY() - msg.originY;
            double dz = wp.getPosition().getZ() - msg.originZ;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                matched.add(wp);
            }
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null)
            return;

        if (matched.isEmpty()) {
            String suffix = (msg.nameFilter == null || msg.nameFilter.isBlank())
                ? ""
                : " matching filter '" + msg.nameFilter + "'";
            mc.player.sendSystemMessage(Component.literal(
                "[Waypoints] No mod waypoints within " + msg.rangeBlocks + " blocks" + suffix + ".")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (msg.nearestOnly && matched.size() > 1) {
            Waypoint nearest = null;
            double bestDistSq = Double.MAX_VALUE;
            for (Waypoint wp : matched) {
                double dx = wp.getPosition().getX() - msg.originX;
                double dy = wp.getPosition().getY() - msg.originY;
                double dz = wp.getPosition().getZ() - msg.originZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    nearest = wp;
                }
            }
            matched = new ArrayList<>(List.of(nearest));
        }

        if (msg.testOnly) {
            mc.player.sendSystemMessage(Component.literal(
                    "[Waypoints TEST] Would remove " + matched.size() + " waypoint(s):")
                    .withStyle(ChatFormatting.AQUA));
            for (Waypoint wp : matched) {
                mc.player.sendSystemMessage(Component.literal(
                        "  - " + wp.getName() + " @ " + wp.getPosition().toShortString())
                        .withStyle(ChatFormatting.WHITE));
            }
        } else {
            int removed = 0;
            for (Waypoint wp : matched) {
                try {
                    api.remove(wp);
                    removed++;
                } catch (Exception e) {
                    FarmUpCraft.LOGGER.warn("Failed to remove waypoint '{}': {}", wp.getName(), e.getMessage());
                }
            }
            int finalRemoved = removed;
            if (msg.nearestOnly && !matched.isEmpty()) {
                Waypoint removedWp = matched.get(0);
                mc.player.sendSystemMessage(Component.literal(
                        "[Waypoints] Removed nearest waypoint: " + removedWp.getName() + " @ "
                                + removedWp.getPosition().toShortString())
                        .withStyle(ChatFormatting.GREEN));
            } else {
                mc.player.sendSystemMessage(Component.literal(
                        "[Waypoints] Removed " + finalRemoved + " mod waypoint(s) within " + msg.rangeBlocks
                                + " blocks.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
