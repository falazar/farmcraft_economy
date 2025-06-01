package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.structure.BuildableStructureInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructurePreviewManager {
    private final Map<UUID, GhostStructureRenderer> activePreviews = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRemovals = ConcurrentHashMap.newKeySet();

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
        pendingRemovals.add(id);
    }


    public void clearAll() {
        activePreviews.clear();
    }
    public void tickAll() {
        for (Map.Entry<UUID, GhostStructureRenderer> entry : activePreviews.entrySet()) {
            entry.getValue().tick();
        }

        for (UUID id : pendingRemovals) {
            activePreviews.remove(id);
        }
        pendingRemovals.clear();
    }


    public void renderAll(PoseStack stack, Vec3 camPos) {
        for (Map.Entry<UUID, GhostStructureRenderer> entry : activePreviews.entrySet()) {
            GhostStructureRenderer renderer = entry.getValue();
            if (renderer.isInRange(camPos)) {
                renderer.render(stack, camPos);
            }
        }

        for (UUID id : pendingRemovals) {
            activePreviews.remove(id);
        }
        pendingRemovals.clear();
    }



    public boolean isEmpty() {
        return activePreviews.isEmpty();
    }

    public boolean hasPreview(UUID id) {
        return activePreviews.containsKey(id);
    }

}
