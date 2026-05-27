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

    public RemoveModWaypointsPacket(double x, double y, double z, int rangeBlocks, boolean testOnly) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.rangeBlocks = rangeBlocks;
        this.testOnly = testOnly;
    }

    public RemoveModWaypointsPacket(FriendlyByteBuf buf) {
        this.originX = buf.readDouble();
        this.originY = buf.readDouble();
        this.originZ = buf.readDouble();
        this.rangeBlocks = buf.readInt();
        this.testOnly = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeInt(rangeBlocks);
        buf.writeBoolean(testOnly);
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
            mc.player.sendSystemMessage(Component.literal(
                    "[Waypoints] No mod waypoints within " + msg.rangeBlocks + " blocks.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
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
            mc.player.sendSystemMessage(Component.literal(
                    "[Waypoints] Removed " + finalRemoved + " mod waypoint(s) within " + msg.rangeBlocks + " blocks.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
