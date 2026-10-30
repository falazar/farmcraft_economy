package com.falazar.farmupcraft.client.border;

import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

/** Reads the actual production-extracted snapshot; never invokes extraction or constructs test walls. */
public final class VisualBorderProbe {
    private VisualBorderProbe() {}

    @SuppressWarnings("unchecked")
    public static boolean hasClaimWalls(ExtractLevelRenderStateEvent event) {
        try {
            var field = BorderRenderer.class.getDeclaredField("BORDERS");
            field.setAccessible(true);
            var key = (ContextKey<BorderRenderer.Snapshot>) field.get(null);
            var snapshot = event.getRenderState().getRenderData(key);
            return snapshot != null && snapshot.walls().size() == 4
                    && snapshot.minY() == event.getLevel().getMinY()
                    && snapshot.maxY() == event.getLevel().getMaxY() + 1
                    && snapshot.color() == BorderRenderer.color("blue");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Production border snapshot is not observable", error);
        }
    }
}
