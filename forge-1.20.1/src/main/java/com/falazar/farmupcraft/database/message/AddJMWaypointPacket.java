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

import java.util.function.Supplier;

/**
 * Server → Client packet that tells the client to add a JourneyMap waypoint.
 */
public class AddJMWaypointPacket {

    // Create waypoints as JourneyMap-owned so players can right-click remove them
    // like normal JM waypoints on the map UI.
    private static final String JOURNEYMAP_OWNER_MODID = "journeymap";

    private final int x;
    private final int y;
    private final int z;
    private final String name;
    private final String dimId;
    private final boolean persistent;
    private final int color;
    private final boolean openMap;

    /** Convenience constructor — persistent gold waypoint (existing behaviour). */
    public AddJMWaypointPacket(int x, int y, int z, String name, String dimId) {
        this(x, y, z, name, dimId, true, 0xFFD700);
    }

    public AddJMWaypointPacket(int x, int y, int z, String name, String dimId, boolean persistent, int color) {
        this(x, y, z, name, dimId, persistent, color, false);
    }

    public AddJMWaypointPacket(int x, int y, int z, String name, String dimId, boolean persistent, int color, boolean openMap) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.dimId = dimId;
        this.persistent = persistent;
        this.color = color;
        this.openMap = openMap;
    }

    public AddJMWaypointPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.name = buf.readUtf();
        this.dimId = buf.readUtf();
        this.persistent = buf.readBoolean();
        this.color = buf.readInt();
        this.openMap = buf.isReadable() && buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeUtf(name);
        buf.writeUtf(dimId);
        buf.writeBoolean(persistent);
        buf.writeInt(color);
        buf.writeBoolean(openMap);
    }

    public static void handle(AddJMWaypointPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> addWaypoint(msg));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void addWaypoint(AddJMWaypointPacket msg) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "JourneyMap is not installed or is not ready."));
            return;
        }

        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(msg.dimId));
        Waypoint waypoint = new Waypoint(
                JOURNEYMAP_OWNER_MODID,
                msg.name,
                dim,
                new BlockPos(msg.x, msg.y, msg.z));
        waypoint.setColor(msg.color);
        waypoint.setPersistent(msg.persistent);
        waypoint.setEditable(true);

        try {
            api.show(waypoint);
            if (msg.openMap) JourneyMapIntegration.openMapAt(msg.x, msg.z);
        } catch (Exception e) {
            FarmUpCraft.LOGGER.warn("Failed to add JourneyMap waypoint: " + e.getMessage());
        }
    }
}
