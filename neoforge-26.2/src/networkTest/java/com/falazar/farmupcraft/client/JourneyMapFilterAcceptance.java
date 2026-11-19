package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.networktest.PlayerActionAcceptance;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.waypoint.Waypoint;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

final class JourneyMapFilterAcceptance {
    private JourneyMapFilterAcceptance() {}

    static void verify(Minecraft minecraft) {
        var api = JourneyMapIntegration.getApi();
        if (api == null) throw new IllegalStateException("JourneyMap API is not ready");
        if (JourneyMapIntegration.getScanWaypoints().stream()
                .noneMatch(point -> point.getName().equals("LoginProbe Lake")))
            throw new IllegalStateException("Saved lake was not synchronized on login");
        var savedEntries = JourneyMapIntegration.getScanWaypoints().stream()
                .map(point -> new ScanWaypointsPacket.Entry(point.getName(), point.getX(), point.getY(),
                        point.getZ(), point.getColor())).toList();
        String dimension = minecraft.level.dimension().identifier().toString();
        String personalName = "Filter probe personal";
        String lakeName = "Filter probe lake";
        JourneyMapIntegration.addWaypoint(new AddJMWaypointPacket(10, 81, 10, personalName, dimension, false, 1));
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(List.of(
                new ScanWaypointsPacket.Entry(lakeName, 11, 81, 10, 2))));
        try {
            List<? extends Waypoint> points = api.getAllWaypoints().stream()
                    .filter(point -> point.getName().equals(personalName) || point.getName().equals(lakeName))
                    .toList();
            if (points.size() != 2) throw new IllegalStateException("Map filter fixture markers missing");
            Class<?> rendererType = Class.forName("journeymap.client.render.map.Renderer");
            Class<?> factoryType = Class.forName("journeymap.client.render.draw.WaypointDrawStepFactory");
            Class.forName("journeymap.client.ui.fullscreen.layer.WaypointLayer");
            Object factory = factoryType.getConstructor().newInstance();
            var prepare = factoryType.getMethod("prepareSteps", Collection.class, rendererType,
                    boolean.class, boolean.class);
            Object full = Proxy.newProxyInstance(rendererType.getClassLoader(), new Class<?>[] {rendererType},
                    (proxy, method, args) -> method.getName().equals("getContext") ? Context.UI.Fullscreen : null);
            Object mini = Proxy.newProxyInstance(rendererType.getClassLoader(), new Class<?>[] {rendererType},
                    (proxy, method, args) -> method.getName().equals("getContext") ? Context.UI.Minimap : null);
            JourneyMapFeatureFilter.setEnabled(false);
            if (((List<?>) prepare.invoke(factory, points, full, false, true)).size() != 2)
                throw new IllegalStateException("Unfiltered full map does not show both fixture markers");
            WaypointPayloadHandler.hideMapFeatures(message -> {});
            WaypointPayloadHandler.setMapFilter(true, message -> {});
            if (((List<?>) prepare.invoke(factory, points, full, false, true)).size() != 1
                    || ((List<?>) prepare.invoke(factory, points, mini, false, true)).size() != 1)
                throw new IllegalStateException("Map filter did not isolate the lake on both maps after hiding");
            JourneyMapFeatureFilter.setEnabled(false);
            if (((List<?>) prepare.invoke(factory, points, full, false, true)).size() != 2)
                throw new IllegalStateException("Fullscreen filter did not restore both markers");
            WaypointPayloadHandler.hideMapFeatures(message -> {});
            for (Object renderer : List.of(full, mini)) {
                List<?> hidden = (List<?>) prepare.invoke(factory, points, renderer, false, true);
                if (hidden.size() != 1
                        || !personalName.equals(((Waypoint) hidden.getFirst().getClass()
                                .getField("waypoint").get(hidden.getFirst())).getName()))
                    throw new IllegalStateException("Feature toggle did not leave only the personal marker");
            }
            WaypointPayloadHandler.setMapFilter(false, message -> {});
            if (((List<?>) prepare.invoke(factory, points, full, false, true)).size() != 2
                    || ((List<?>) prepare.invoke(factory, points, mini, false, true)).size() != 2)
                throw new IllegalStateException("Feature toggle did not restore both maps");
            Waypoint personal = points.stream().filter(point -> personalName.equals(point.getName())).findFirst().orElseThrow();
            personal.setShowOnMap(false);
            JourneyMapFeatureFilter.setEnabled(true);
            JourneyMapFeatureFilter.setEnabled(false);
            if (personal.showOnMap() || ((List<?>) prepare.invoke(factory, points, full, false, true)).size() != 1)
                throw new IllegalStateException("Fullscreen filter changed the marker's existing visibility");
            PlayerActionAcceptance.report("PASS map filter transformed client: fullscreen, minimap, feature toggle and restore");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot verify transformed JourneyMap map filter", error);
        } finally {
            JourneyMapFeatureFilter.reset();
            JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket());
            JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(savedEntries));
            api.getAllWaypoints().stream().filter(point -> personalName.equals(point.getName()))
                    .toList().forEach(point -> api.removeWaypoint("farmupcraft", point));
        }
    }

    static void openFullscreen() {
        try {
            JourneyMapFeatureFilter.setEnabled(true);
            Minecraft.getInstance().gui.setScreen(null);
            Class<?> managerType = Class.forName("journeymap.client.ui.UIManager");
            managerType.getMethod("getOrOpenFullscreenMap").invoke(managerType.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot open actual JourneyMap fullscreen screen", error);
        }
    }

    static void verifyFullscreen(Minecraft minecraft) {
        try {
            Class<?> screenType = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
            if (!screenType.isInstance(minecraft.gui.screen()))
                throw new IllegalStateException("Actual JourneyMap fullscreen screen did not stay open: "
                        + minecraft.gui.screen());
            var stateField = screenType.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(null);
            var stepsField = state.getClass().getDeclaredField("drawWaypointStepList");
            stepsField.setAccessible(true);
            List<?> steps = (List<?>) stepsField.get(state);
            Object step = steps.stream().filter(candidate -> {
                try {
                    var point = (Waypoint) candidate.getClass().getField("waypoint").get(candidate);
                    return point.getX() == 32 && point.getZ() == 36;
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
            }).findFirst().orElseThrow(() -> new IllegalStateException(
                    "Login-synchronized lake missing from actual fullscreen draw steps: " + steps.size()));
            var positionField = step.getClass().getDeclaredField("lastPosition");
            var rendererField = screenType.getDeclaredField("mapRenderer");
            var showLabel = step.getClass().getDeclaredField("showLabel");
            positionField.setAccessible(true);
            rendererField.setAccessible(true);
            showLabel.setAccessible(true);
            Object position = positionField.get(step);
            Object renderer = rendererField.get(null);
            boolean onScreen = position != null && (boolean) renderer.getClass()
                    .getMethod("isOnScreen", java.awt.geom.Point2D.Double.class).invoke(renderer, position);
            var point = (Waypoint) step.getClass().getField("waypoint").get(step);
            var synced = JourneyMapIntegration.getScanWaypoints().stream()
                    .filter(candidate -> candidate.getX() == 32 && candidate.getZ() == 36)
                    .findFirst().orElseThrow();
            if ("LoginProbe Lake".equals(point.getName()) || !synced.getName().equals(point.getName()))
                throw new IllegalStateException("Renamed discovery did not refresh the actual fullscreen label");
            if (!onScreen || !showLabel.getBoolean(step) || !point.showLabel())
                throw new IllegalStateException("Actual fullscreen lake label did not render: onScreen="
                        + onScreen + ", drawLabel=" + showLabel.getBoolean(step) + ", showLabel=" + point.showLabel());
            PlayerActionAcceptance.report("PASS map fullscreen: saved login discovery reached real on-screen labeled draw step");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect actual fullscreen draw state", error);
        }
    }
}
