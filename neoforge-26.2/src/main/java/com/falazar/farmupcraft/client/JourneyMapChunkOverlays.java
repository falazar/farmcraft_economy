package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * JourneyMap owns the map rendering. Highlights remain until replaced, explicitly cleared, or disconnected.
 */
public final class JourneyMapChunkOverlays {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = FarmUpCraftNeoForge.MOD_ID;
    private static final Map<Key, PolygonOverlay> overlays = new HashMap<>();

    private JourneyMapChunkOverlays() {
    }

    public static void highlight(HighlightChunkPacket payload) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null) {
            LOGGER.warn("Cannot highlight a chunk: JourneyMap client API is not ready");
            return;
        }
        highlight(payload, api);
    }

    static void highlight(HighlightChunkPacket payload, IClientAPI api) {
        var key = new Key(false, payload.dimId(), payload.chunkX(), payload.chunkZ(), payload.label());
        var overlay = polygon(payload.chunkX(), payload.chunkZ(), payload.dimId(), payload.color(), false);
        overlay.setLabel(payload.label()).setTitle(payload.label());
        show(key, overlay, api);
    }

    public static void showVillage(ShowVillageChunksPacket payload) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api == null) {
            LOGGER.warn("Cannot show village chunks: JourneyMap client API is not ready");
            return;
        }
        showVillage(payload, api);
    }

    static void showVillage(ShowVillageChunksPacket payload, IClientAPI api) {
        if (!payload.show()) {
            clear(api);
            return;
        }
        // A village refresh is a snapshot, not an append: unclaimed chunks must disappear.
        var iterator = overlays.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().village() && entry.getKey().dimension().equals(payload.dimId())) {
                try {
                    api.remove(entry.getValue());
                    iterator.remove();
                } catch (RuntimeException error) {
                    LOGGER.warn("Failed to remove a stale JourneyMap village polygon", error);
                }
            }
        }
        for (var chunk : payload.entries()) {
            show(new Key(true, payload.dimId(), chunk.x(), chunk.z(), ""),
                    polygon(chunk.x(), chunk.z(), payload.dimId(), chunk.color(), true), api);
        }
    }

    private static PolygonOverlay polygon(int x, int z, String dimension, int color, boolean village) {
        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(color).setStrokeOpacity(village ? 0.9f : 1.0f)
                .setStrokeWidth(village ? 1.5f : 2.0f)
                .setFillColor(color).setFillOpacity(village ? 0.10f : 0.05f);
        return new PolygonOverlay(MOD_ID,
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)),
                shape, PolygonHelper.createChunkPolygon(x, 64, z));
    }

    private static void show(Key key, PolygonOverlay overlay, IClientAPI api) {
        try {
            // V2 generates display IDs; retain the handles to replace identical logical highlights.
            PolygonOverlay previous = overlays.get(key);
            if (previous != null) {
                api.remove(previous);
                overlays.remove(key);
            }
            api.show(overlay);
            overlays.put(key, overlay);
        } catch (Exception error) {
            LOGGER.warn("Failed to show JourneyMap chunk polygon at {}, {} in {}",
                    key.x(), key.z(), key.dimension(), error);
        }
    }

    static void clear(IClientAPI api) {
        try {
            // Map clear must not delete waypoints or other display types.
            api.removeAll(MOD_ID, DisplayType.Polygon);
            overlays.clear();
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to clear JourneyMap chunk polygons", error);
        }
    }

    public static void loggedOut() {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api != null) {
            clear(api);
        }
        overlays.clear();
    }

    private record Key(boolean village, String dimension, int x, int z, String label) {
    }
}
