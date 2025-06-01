package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.structure.BuildableStructureInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StructurePreviewManager {
    private final Map<UUID, GhostStructureRenderer> activePreviews = new HashMap<>();

    public UUID showPreview(BuildableStructureInstance instance) {
        GhostStructureRenderer renderer = new GhostStructureRenderer();
        renderer.setStructure(instance);

        UUID id = UUID.randomUUID();
        activePreviews.put(id, renderer);
        return id;
    }
    public void updatePreview(UUID id, BuildableStructureInstance newInstance) {
        GhostStructureRenderer renderer = activePreviews.get(id);
        if (renderer != null) {
            renderer.setStructure(newInstance); // this should update blocks internally
        }
    }

    public void removePreview(UUID id) {
        activePreviews.remove(id);
    }

    public void clearAll() {
        activePreviews.clear();
    }

    public void tickAll() {
        // No snapshot needed here unless tick() modifies the map
        activePreviews.values().forEach(GhostStructureRenderer::tick);
    }

    public void renderAll(PoseStack stack, Vec3 camPos) {
        List<GhostStructureRenderer> snapshot = new ArrayList<>(activePreviews.values());
        for (GhostStructureRenderer renderer : snapshot) {
            if (renderer.isInRange(camPos)) {
                renderer.render(stack, camPos);
            }
        }
    }


    public boolean isEmpty() {
        return activePreviews.isEmpty();
    }

    public boolean hasPreview(UUID id) {
        return activePreviews.containsKey(id);
    }

}
