package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

/**
 * Outlines the synced vanilla world border on the JourneyMap minimap and fullscreen map.
 */
public final class JourneyMapWorldBorder {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int COLOR = 0xFF2020;
    // Vanilla's default border is effectively unbounded; outlining it would only add noise.
    static final double UNBOUNDED_SIZE = 5.0E7;
    private static PolygonOverlay overlay;
    private static Bounds shown;

    private JourneyMapWorldBorder() {
    }

    public static void update(ClientLevel level) {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api != null) {
            update(level == null ? null : Bounds.of(level), api);
        }
    }

    static void update(Bounds bounds, IClientAPI api) {
        // A failed show is remembered with a null overlay so it is not retried every tick.
        if (bounds != null && bounds.equals(shown) && (overlay == null || api.exists(overlay))) {
            return;
        }
        remove(api);
        if (bounds == null) {
            return;
        }
        shown = bounds;
        PolygonOverlay next = new PolygonOverlay(FarmUpCraftNeoForge.MOD_ID, bounds.dimension(),
                new ShapeProperties().setStrokeColor(COLOR).setStrokeOpacity(0.9f).setStrokeWidth(2.0f)
                        .setFillColor(COLOR).setFillOpacity(0.0f),
                PolygonHelper.createBlockRect(new BlockPos(bounds.minX(), 64, bounds.minZ()),
                        new BlockPos(bounds.maxX(), 64, bounds.maxZ())));
        next.setTitle("World border");
        try {
            api.show(next);
            overlay = next;
        } catch (Exception error) {
            LOGGER.warn("Failed to show the world border on JourneyMap", error);
        }
    }

    private static void remove(IClientAPI api) {
        if (overlay != null) {
            try {
                api.remove(overlay);
            } catch (RuntimeException error) {
                LOGGER.warn("Failed to remove the JourneyMap world border", error);
            }
        }
        overlay = null;
        shown = null;
    }

    public static void loggedOut() {
        IClientAPI api = JourneyMapIntegration.getApi();
        if (api != null) {
            remove(api);
        }
        overlay = null;
        shown = null;
    }

    record Bounds(ResourceKey<Level> dimension, int minX, int minZ, int maxX, int maxZ) {
        static Bounds of(ClientLevel level) {
            var border = level.getWorldBorder();
            if (border.getSize() >= UNBOUNDED_SIZE) {
                return null;
            }
            return new Bounds(level.dimension(), (int) Math.floor(border.getMinX()),
                    (int) Math.floor(border.getMinZ()), (int) Math.ceil(border.getMaxX()),
                    (int) Math.ceil(border.getMaxZ()));
        }
    }
}
