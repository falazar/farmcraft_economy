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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client packet that draws (or clears) a JourneyMap polygon overlay
 * for every claimed chunk in the player's village.
 *
 * show=true → draw a colored square on each chunk:
 *   - blue  (0x0055FF) = chunk has a plot (farm, house, pasture, etc.)
 *   - green (0x00E000) = chunk is claimed but has no plot ("village" type)
 * show=false → remove all farmupcraft polygon overlays (clear all village
 * highlights)
 */
public class ShowVillageChunksPacket {

    // Each entry is {chunkX, chunkZ, colorHex}
    private final List<int[]> chunks;
    private final boolean show;
    private final String dimId;

    public ShowVillageChunksPacket(List<int[]> chunks, boolean show, String dimId) {
        this.chunks = chunks;
        this.show = show;
        this.dimId = dimId;
    }

    public ShowVillageChunksPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        this.chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new int[] { buf.readInt(), buf.readInt(), buf.readInt() });
        }
        this.show = buf.readBoolean();
        this.dimId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(chunks.size());
        for (int[] c : chunks) {
            buf.writeInt(c[0]);
            buf.writeInt(c[1]);
            buf.writeInt(c[2]);
        }
        buf.writeBoolean(show);
        buf.writeUtf(dimId);
    }

    public static void handle(ShowVillageChunksPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> apply(msg));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void apply(ShowVillageChunksPacket msg) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null)
            return;

        if (!msg.show) {
            // Clear all polygon overlays for this mod.
            try {
                api.removeAll(FarmUpCraft.MODID);
            } catch (Exception e) {
                FarmUpCraft.LOGGER.warn("Failed to clear village chunk overlays: " + e.getMessage());
            }
            return;
        }

        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(msg.dimId));

        for (int[] chunk : msg.chunks) {
            int cx = chunk[0], cz = chunk[1], color = chunk[2];
            ShapeProperties shape = new ShapeProperties()
                    .setStrokeColor(color)
                    .setStrokeOpacity(0.9f)
                    .setStrokeWidth(1.5f)
                    .setFillColor(color)
                    .setFillOpacity(0.10f); // 10% fill — just barely visible
            String overlayId = "village_chunk_" + cx + "_" + cz;
            MapPolygon polygon = PolygonHelper.createChunkPolygon(cx, 64, cz);
            PolygonOverlay overlay = new PolygonOverlay(FarmUpCraft.MODID, overlayId, dim, shape, polygon);
            try {
                api.show(overlay);
            } catch (Exception e) {
                FarmUpCraft.LOGGER
                        .warn("Failed to show village chunk overlay [" + cx + "," + cz + "]: " + e.getMessage());
            }
        }
    }
}
