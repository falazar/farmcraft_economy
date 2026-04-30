package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.JourneyMapIntegration;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.util.PolygonHelper;
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
 * Server → Client packet that tells the client to draw a chunk highlight on the
 * JourneyMap.
 */
public class HighlightChunkPacket {

    private final int chunkX;
    private final int chunkZ;
    private final String label;
    private final String dimId;
    private final int color; // RGB hex e.g. 0x00FF00

    public HighlightChunkPacket(int chunkX, int chunkZ, String label, String dimId, int color) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.label = label;
        this.dimId = dimId;
        this.color = color;
    }

    public HighlightChunkPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.label = buf.readUtf();
        this.dimId = buf.readUtf();
        this.color = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(label);
        buf.writeUtf(dimId);
        buf.writeInt(color);
    }

    public static void handle(HighlightChunkPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> highlightChunk(msg));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void highlightChunk(HighlightChunkPacket msg) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null)
            return;

        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(msg.dimId));
        MapPolygon polygon = PolygonHelper.createChunkPolygon(msg.chunkX, 64, msg.chunkZ);

        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(msg.color)
                .setStrokeOpacity(1.0f)
                .setStrokeWidth(2.0f)
                .setFillColor(msg.color)
                .setFillOpacity(0.25f);

        String overlayId = "chunk_" + msg.chunkX + "_" + msg.chunkZ + "_" + msg.label;
        PolygonOverlay overlay = new PolygonOverlay(FarmUpCraft.MODID, overlayId, dim, shape, polygon);

        try {
            api.show(overlay);
        } catch (Exception e) {
            FarmUpCraft.LOGGER.warn("Failed to add JourneyMap chunk highlight: " + e.getMessage());
        }
    }
}
