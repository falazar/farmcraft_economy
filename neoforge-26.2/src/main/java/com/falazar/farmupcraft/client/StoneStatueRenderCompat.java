package com.falazar.farmupcraft.client;

import net.minecraft.world.entity.Entity;

public final class StoneStatueRenderCompat {
    private StoneStatueRenderCompat() {
    }

    public static void assignModelId(Entity statue, Entity model) {
        if (model != null) {
            // This render-only entity is never added to the world, so reuse its owning statue's ID.
            model.setId(statue.getId());
        }
    }
}
