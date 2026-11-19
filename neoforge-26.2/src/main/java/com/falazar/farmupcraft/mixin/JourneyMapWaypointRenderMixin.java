package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.client.JourneyMapFeatureFilter;
import journeymap.api.v2.common.waypoint.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "journeymap.client.render.draw.WaypointDrawStepFactory", remap = false)
public abstract class JourneyMapWaypointRenderMixin {
    @Redirect(method = "prepareSteps", at = @At(value = "INVOKE",
            target = "Ljourneymap/client/waypoint/ClientWaypointImpl;showOnMap()Z"), remap = false)
    private boolean farmupcraft$filterMaps(@Coerce Object waypoint) {
        return JourneyMapFeatureFilter.showOnMap((Waypoint) waypoint);
    }
}
