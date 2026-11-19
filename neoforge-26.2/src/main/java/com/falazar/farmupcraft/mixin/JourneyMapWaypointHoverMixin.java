package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.client.JourneyMapFeatureFilter;
import journeymap.api.v2.common.waypoint.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "journeymap.client.ui.fullscreen.layer.WaypointLayer", remap = false)
public abstract class JourneyMapWaypointHoverMixin {
    @Redirect(method = "onMouseMove", at = @At(value = "INVOKE",
            target = "Ljourneymap/client/waypoint/ClientWaypointImpl;isEnabled()Z"), remap = false)
    private boolean farmupcraft$filterHover(@Coerce Object waypoint) {
        Waypoint marker = (Waypoint) waypoint;
        return marker.isEnabled() && JourneyMapFeatureFilter.allows(marker);
    }
}
