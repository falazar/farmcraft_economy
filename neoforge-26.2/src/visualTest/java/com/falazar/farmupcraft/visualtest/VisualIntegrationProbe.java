package com.falazar.farmupcraft.visualtest;

import com.falazar.farmupcraft.client.JeiIntegration;
import com.falazar.farmupcraft.client.JourneyMapChunkOverlays;
import com.falazar.farmupcraft.client.JourneyMapIntegration;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.ui.component.LayeredScreen;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.waypoint.Waypoint;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.falazar.farmupcraft.visualtest.VisualAcceptance.require;

/**
 * Real GUI driving and fail-closed observations of the pinned runtime implementations.
 * Reflection bridges runtime-only GUI classes, not fabricated API implementations or unchecked casts.
 */
final class VisualIntegrationProbe {
    private static final String MANAGER = "journeymap.client.ui.waypointmanager.WaypointManager";
    private static final String EDITOR = "journeymap.client.ui.waypointmanager.waypoint.WaypointEditor";
    private static final String FULLSCREEN = "journeymap.client.ui.fullscreen.Fullscreen";
    private static final String ABOUT = "journeymap.client.ui.dialog.about.AboutScreen";
    private static final String CLIENT_WAYPOINT = "journeymap.client.waypoint.ClientWaypointImpl";
    private static final int MAP_ZOOM = 2048;
    private static Screen ownedScreen;
    private static List<PolygonOverlay> observedPolygons = List.of();
    private static String detail = "integration stage not observed";
    private static boolean aboutDismissed;
    private static boolean fullscreenFrameObserved;
    private static boolean mapFramed;

    private VisualIntegrationProbe() {}

    static void begin(Minecraft minecraft, int stage) {
        require(VisualAcceptance.enabled(), "Integration probe is property gated");
        require(JeiIntegration.getRuntime() != null, "Pinned JEI runtime is not ready");
        require(JourneyMapIntegration.getApi() != null, "Pinned JourneyMap API is not ready");
        ownedScreen = null;
        if (stage == 4) {
            detail = "awaiting production OpenJeiRecipePacket output focus minecraft:bread";
        } else if (stage == 5) {
            minecraft.gui.setScreen(null);
            Waypoint waypoint = nativeWaypoint(VisualAcceptance.WAYPOINT_NAME);
            require(waypoint != null && type(CLIENT_WAYPOINT).isInstance(waypoint),
                    "Production native waypoint missing before manager");
            ownedScreen = constructScreen(MANAGER, new Class<?>[]{type(CLIENT_WAYPOINT), Screen.class},
                    new Object[]{waypoint, null});
            minecraft.gui.setScreen(ownedScreen);
        } else if (stage == 8) {
            minecraft.gui.setScreen(null);
            ownedScreen = constructScreen(MANAGER, new Class<?>[]{Screen.class}, new Object[]{null});
            minecraft.gui.setScreen(ownedScreen);
        } else if (stage == 6) {
            Waypoint waypoint = nativeWaypoint(VisualAcceptance.WAYPOINT_NAME);
            require(waypoint != null, "Native waypoint missing before real editor");
            require(type(CLIENT_WAYPOINT).isInstance(waypoint), "Unexpected native waypoint implementation");
            ownedScreen = constructScreen(EDITOR, new Class<?>[]{type(CLIENT_WAYPOINT)}, new Object[]{waypoint});
            require(ownedScreen instanceof LayeredScreen, "Pinned editor is not a layered screen");
            if (ownedScreen instanceof LayeredScreen layered) layered.display();
            Object value = field(ownedScreen, EDITOR, "nameTextBox");
            require(value instanceof EditBox, "Pinned JourneyMap name field is not a vanilla EditBox");
            if (value instanceof EditBox name) {
                name.setValue(VisualAcceptance.EDITED_WAYPOINT_NAME);
                require(name.getValue().equals(VisualAcceptance.EDITED_WAYPOINT_NAME), "Editor rejected new name");
            }
        } else if (stage == 7) {
            require(nativeWaypoint(VisualAcceptance.EDITED_WAYPOINT_NAME) != null,
                    "Real editor save did not update API state");
            minecraft.gui.setScreen(null);
            ownedScreen = constructScreen(FULLSCREEN, new Class<?>[0], new Object[0]);
            aboutDismissed = false;
            fullscreenFrameObserved = false;
            mapFramed = false;
            minecraft.gui.setScreen(ownedScreen);
        }
    }

    static void tick(Minecraft minecraft) {
        require(VisualAcceptance.enabled(), "Integration probe is property gated");
        var screen = minecraft.gui.screen();
        if (screen == ownedScreen) {
            if (fullscreenFrameObserved && !mapFramed) {
                frameMap();
                mapFramed = true;
                VisualAcceptance.report("UI_FRAMING real Fullscreen frame observed before public zoom/follow controls;"
                        + " requested zoom=2048 (4 render pixels/block), follow=false;"
                        + " preserving supported initial player-centered viewport=4,4; no pan requested;"
                        + " exact viewport/source gates still required");
            }
            return;
        }
        if (screen == null || !screen.getClass().getName().equals(ABOUT)) return;
        require(!aboutDismissed, "JourneyMap first-open AboutScreen unexpectedly repeated");
        String closeLabel = Component.translatable("jm.common.close").getString();
        var matches = screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().getString().equals(closeLabel)).toList();
        require(matches.size() == 1, "Pinned AboutScreen must expose exactly one public Close button");
        if (matches.getFirst() instanceof Button button) {
            require(button.active && button.visible, "JourneyMap AboutScreen Close button is not available");
            aboutDismissed = true;
            VisualAcceptance.report("UI_INTERSTITIAL real JourneyMap AboutScreen; activating public jm.common.close button");
            button.onPress(new KeyEvent(257, 0, 0));
            require(minecraft.gui.screen() == ownedScreen,
                    "JourneyMap AboutScreen Close callback did not return to the actual fixture Fullscreen");
            // UIManager.open resets the renderer; wait for its first real fullscreen frame before framing.
            fullscreenFrameObserved = false;
            mapFramed = false;
            VisualAcceptance.report("UI_INTERSTITIAL closed through real button callback; returned same Fullscreen;"
                    + " awaiting first real fullscreen frame before zoom/follow; normal source/pixel gates still required");
        }
    }

    private static void frameMap() {
        for (int attempts = 0; attempts < 16; attempts++) {
            Object value = call(ownedScreen, FULLSCREEN, "getZoom", new Class<?>[0]);
            require(value instanceof Integer, "Fullscreen zoom is not an integer");
            if (value instanceof Integer zoom) {
                if (zoom == MAP_ZOOM) break;
                call(ownedScreen, FULLSCREEN, zoom < MAP_ZOOM ? "zoomIn" : "zoomOut", new Class<?>[0]);
            }
        }
        call(ownedScreen, FULLSCREEN, "setFollow", new Class<?>[]{Boolean.class}, false);
    }

    static boolean ready(Minecraft minecraft, int stage) {
        require(VisualAcceptance.enabled(), "Integration probe is property gated");
        if (stage == 4) return jei(minecraft);
        if (minecraft.gui.screen() != ownedScreen) {
            detail = "expected real JourneyMap screen=" + (ownedScreen == null ? "missing" : ownedScreen.getClass().getName());
            return false;
        }
        return switch (stage) {
            case 5 -> waypoints();
            case 6 -> editor();
            case 7 -> polygons(minecraft);
            case 8 -> cleanupObserved();
            default -> false;
        };
    }

    private static boolean jei(Minecraft minecraft) {
        var runtime = JeiIntegration.getRuntime();
        if (runtime == null || minecraft.gui.screen() != runtime.getRecipesGui()) {
            detail = "JEI actual recipes GUI not current";
            return false;
        }
        var screen = minecraft.gui.screen();
        require(screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"), "Unexpected pinned JEI screen");
        Object logic = field(screen, "mezz.jei.gui.recipes.RecipesGui", "logic");
        Object state = field(logic, "mezz.jei.gui.recipes.RecipeGuiLogic", "state");
        Object focuses = call(state, "mezz.jei.gui.recipes.lookups.ILookupState", "getFocuses", new Class<?>[0]);
        require(focuses instanceof IFocusGroup, "JEI lookup did not expose a focus group");
        boolean breadOutput = focuses instanceof IFocusGroup group && group.getAllFocuses().size() == 1
                && group.getItemStackFocuses(RecipeIngredientRole.OUTPUT)
                .anyMatch(focus -> focus.getTypedValue().getIngredient().is(Items.BREAD));
        Object layouts = call(screen, "mezz.jei.gui.recipes.RecipesGui", "getRecipeLayouts", new Class<?>[0]);
        Object visible = field(layouts, "mezz.jei.gui.recipes.RecipeGuiLayouts", "recipeLayoutsWithButtons");
        require(visible instanceof List<?>, "JEI visible layout model changed");
        int count = visible instanceof List<?> list ? list.size() : 0;
        detail = "production JEI recipes GUI; single OUTPUT minecraft:bread focus=" + breadOutput
                + "; visible recipe layouts=" + count + "; pixels require manual review";
        return breadOutput && count > 0;
    }

    private static boolean waypoints() {
        String name = VisualAcceptance.WAYPOINT_NAME;
        Waypoint nativePoint = nativeWaypoint(name);
        if (nativePoint == null) {
            detail = "awaiting production native waypoint " + name;
            return false;
        }
        require(nativePoint.isPersistent() && nativePoint.getColor() == 0xFFD700
                        && nativePoint.getX() == 8 && nativePoint.getY() == 81 && nativePoint.getZ() == 8
                        && nativePoint.getDimensions().contains("minecraft:overworld"),
                "Production native waypoint fields changed");
        var scans = JourneyMapIntegration.getScanWaypoints();
        require(scans.size() == 1, "Expected one production temporary scan waypoint");
        var scan = scans.getFirst();
        require(scan.getName().equals(VisualAcceptance.SCAN_NAME) && scan.getModId().equals("farmupcraft")
                        && !scan.isPersistent() && scan.getColor() == 0x00FF00
                        && scan.getX() == 24 && scan.getY() == 81 && scan.getZ() == 8
                        && JourneyMapIntegration.getApi().getAllWaypoints().stream()
                        .anyMatch(point -> point.getGuid().equals(scan.getGuid())),
                "Production temporary scan waypoint missing from real API");
        Object controller = field(ownedScreen, MANAGER, "controller");
        Object state = call(controller, "journeymap.client.ui.waypointmanager.WaypointManagerController",
                "getViewState", new Class<?>[0]);
        Object visible = call(state, "journeymap.client.ui.waypointmanager.WaypointManagerViewState",
                "visibleWaypointGuids", new Class<?>[0]);
        require(visible instanceof List<?>, "JourneyMap manager view model changed");
        boolean listed = visible instanceof List<?> list && list.contains(nativePoint.getGuid());
        detail = "real native waypoint manager; native owner=journeymap origin=farmupcraft persistent=true"
                + " color=FFD700 position=8,81,8; scan owner=farmupcraft persistent=false color=00FF00"
                + " position=24,81,8; native GUID listed=" + listed
                + "; persistence flag is not a restart/disk-persistence assertion";
        return listed;
    }

    private static boolean editor() {
        Object value = field(ownedScreen, EDITOR, "nameTextBox");
        boolean changed = value instanceof EditBox name
                && name.getValue().equals(VisualAcceptance.EDITED_WAYPOINT_NAME);
        detail = "real native JourneyMap layered editor; vanilla name EditBox=" + changed
                + "; save follows persisted editor framebuffer, not yet asserted";
        return changed;
    }

    static void captured(int stage) {
        if (stage != 6) return;
        require(ownedScreen != null && ownedScreen.getClass().getName().equals(EDITOR), "Real editor missing");
        require(ownedScreen.keyPressed(new KeyEvent(257, 0, 0)), "Native editor did not consume Enter/save");
        require(nativeWaypoint(VisualAcceptance.EDITED_WAYPOINT_NAME) != null
                        && nativeWaypoint(VisualAcceptance.WAYPOINT_NAME) == null,
                "Actual native editor save failed to rename waypoint in API");
        VisualAcceptance.report("SOURCE real native editor Enter/save renamed API waypoint to "
                + VisualAcceptance.EDITED_WAYPOINT_NAME
                + "; persistent flag remains checked at map stage; restart/disk persistence not asserted");
    }

    private static boolean polygons(Minecraft minecraft) {
        var api = JourneyMapIntegration.getApi();
        if (!mapFramed) {
            UIState initial = api.getUIState(Context.UI.Fullscreen);
            fullscreenFrameObserved = initial != null && initial.active
                    && initial.dimension.equals(minecraft.level.dimension());
            detail = "awaiting initialized real fullscreen frame, then public zoom/follow controls without pan; observed="
                    + fullscreenFrameObserved;
            return false;
        }
        Waypoint edited = nativeWaypoint(VisualAcceptance.EDITED_WAYPOINT_NAME);
        require(edited != null && edited.isPersistent(), "Saved native waypoint not persistent in API");
        var polygons = productionPolygons();
        if (polygons.size() != 2) {
            detail = "awaiting production polygon handles: " + polygons.size() + "/2";
            return false;
        }
        int highlights = 0;
        int villages = 0;
        for (var polygon : polygons) {
            require(api.exists(polygon) && polygon.getModId().equals("farmupcraft")
                            && polygon.getDimension().equals(minecraft.level.dimension()),
                    "Production polygon not registered in actual JourneyMap API/dimension");
            var shape = polygon.getShapeProperties();
            var points = polygon.getOuterArea().getPoints();
            int minX = points.stream().mapToInt(point -> point.getX()).min().orElseThrow();
            int maxX = points.stream().mapToInt(point -> point.getX()).max().orElseThrow();
            int minZ = points.stream().mapToInt(point -> point.getZ()).min().orElseThrow();
            int maxZ = points.stream().mapToInt(point -> point.getZ()).max().orElseThrow();
            String actual = " bounds=[" + minX + "," + minZ + ".." + maxX + "," + maxZ + "] points=" + points
                    + " strokeARGB=" + Integer.toHexString(shape.getStrokeColor())
                    + " fillARGB=" + Integer.toHexString(shape.getFillColor())
                    + " strokeWidth=" + shape.getStrokeWidth() + " strokeOpacity=" + shape.getStrokeOpacity()
                    + " fillOpacity=" + shape.getFillOpacity() + " label=" + polygon.getLabel();
            require(points.size() == 4 && minZ == 0 && maxZ == 16
                            && points.stream().allMatch(point -> point.getY() == 64),
                    "Polygon Z/Y chunk geometry changed;" + actual);
            // Pinned ShapeProperties setters use Displayable.clampRGB: 0xFF000000 | RGB.
            if (shape.getStrokeColor() == 0xFFFF0000) {
                require(minX == 0 && maxX == 16 && shape.getStrokeWidth() == 2.0f
                                && shape.getFillColor() == 0xFFFF0000 && shape.getStrokeOpacity() == 1.0f
                                && shape.getFillOpacity() == 0.05f
                                && "Visual Acceptance Highlight".equals(polygon.getLabel()),
                        "Highlight polygon geometry/style/label changed;" + actual);
                highlights++;
            } else if (shape.getStrokeColor() == 0xFF0000FF) {
                require(minX == 16 && maxX == 32 && shape.getFillColor() == 0xFF0000FF
                                && shape.getStrokeWidth() == 1.5f && shape.getStrokeOpacity() == 0.9f
                                && shape.getFillOpacity() == 0.10f,
                        "Village polygon geometry/style changed;" + actual);
                villages++;
            } else {
                throw new IllegalStateException("Unexpected production polygon ARGB;" + actual);
            }
        }
        require(highlights == 1 && villages == 1, "Expected one highlight and one village polygon");
        UIState ui = api.getUIState(Context.UI.Fullscreen);
        Object state = call(null, FULLSCREEN, "state", new Class<?>[0]);
        Object follow = field(state, "journeymap.client.model.map.MapState", "follow");
        require(follow instanceof AtomicBoolean, "Pinned fullscreen follow state changed");
        boolean following = follow instanceof AtomicBoolean flag && flag.get();
        if (ui == null || !ui.active || !ui.dimension.equals(minecraft.level.dimension())
                || following || ui.zoom != MAP_ZOOM || ui.blockSize != 4.0
                || ui.mapCenter.getX() != 4 || ui.mapCenter.getZ() != 4
                || Math.abs(minecraft.player.getX() - 4.5) > 0.1 || Math.abs(minecraft.player.getZ() - 4.5) > 0.1
                || ui.displayBounds.getWidth() < 256 || ui.displayBounds.getHeight() < 192
                || ui.blockBounds.minX > 0 || ui.blockBounds.maxX < 32
                || ui.blockBounds.minZ > 0 || ui.blockBounds.maxZ < 16) {
            detail = "JourneyMap fullscreen not framed at zoom=2048 blockSize=4 initial-player-center=4,4 follow=false;"
                    + " fixture player must remain at x/z=4.5,4.5; both 64x64-render-pixel chunks must fit;"
                    + " actual player=" + minecraft.player.position() + " follow=" + following + " UI=" + ui;
            return false;
        }
        Object prepared = call(api, "journeymap.api.client.impl.ClientAPI", "getPreparedOverlayView",
                new Class<?>[]{UIState.class}, ui);
        Object steps = call(prepared, "journeymap.api.client.impl.PreparedOverlayView", "drawSteps", new Class<?>[0]);
        require(steps instanceof List<?>, "JourneyMap prepared overlay draw list changed");
        int found = 0;
        if (steps instanceof List<?> list) {
            for (Object step : list) {
                Object overlay = call(step, "journeymap.client.render.draw.OverlayDrawStep",
                        "getOverlay", new Class<?>[0]);
                if (polygons.contains(overlay)) found++;
            }
        }
        detail = "real fullscreen; registered production red highlight chunk=0,0 and blue village chunk=1,0;"
                + " exact 16-block source geometry/styles; stroke/fill ARGB=FFFF0000,FF0000FF;"
                + " zoom=2048 blockSize=4 center=" + ui.mapCenter + " follow=false;"
                + " supported initial player-centered viewport, fixture x/z=4.5,4.5; no pan requested;"
                + " each chunk projects to 64x64 render pixels;"
                + " prepared fullscreen draw steps=" + found
                + "/2; edited native waypoint persistent flag=true; pixels require manual review";
        observedPolygons = List.copyOf(polygons);
        return found == 2;
    }

    private static boolean cleanupObserved() {
        var api = JourneyMapIntegration.getApi();
        boolean clean = nativeWaypoint(VisualAcceptance.WAYPOINT_NAME) == null
                && nativeWaypoint(VisualAcceptance.EDITED_WAYPOINT_NAME) == null
                && api.getAllWaypoints().stream().noneMatch(point -> point.getName().equals(VisualAcceptance.SCAN_NAME))
                && JourneyMapIntegration.getScanWaypoints().isEmpty()
                && productionPolygons().isEmpty() && observedPolygons.size() == 2
                && observedPolygons.stream().noneMatch(api::exists);
        detail = "real waypoint manager after production scan CLEAR, filtered native removal and polygon clear;"
                + " API/tracked waypoints absent and both observed polygon handles removed=" + clean;
        return clean;
    }

    private static Waypoint nativeWaypoint(String name) {
        var api = JourneyMapIntegration.getApi();
        require(api != null, "JourneyMap API unavailable");
        var found = api.getAllWaypoints().stream().filter(point -> point.getName().equals(name)).toList();
        require(found.size() <= 1, "Duplicate acceptance native waypoint");
        if (found.isEmpty()) return null;
        var point = found.getFirst();
        require(point.getModId().equals("journeymap")
                        && "farmupcraft".equals(point.getCustomData("farmupcraft:waypoint")),
                "Native waypoint ownership/origin changed");
        return point;
    }

    private static List<PolygonOverlay> productionPolygons() {
        Object value = field(null, JourneyMapChunkOverlays.class.getName(), "overlays");
        require(value instanceof Map<?, ?>, "Production polygon handle map changed");
        var result = new ArrayList<PolygonOverlay>();
        if (value instanceof Map<?, ?> map) {
            for (Object entry : map.values()) {
                require(entry instanceof PolygonOverlay, "Unexpected production overlay handle");
                if (entry instanceof PolygonOverlay polygon) result.add(polygon);
            }
        }
        return result;
    }

    static String detail() {
        return detail;
    }

    static void cleanupOnFailure() {
        if (!VisualAcceptance.enabled() || JourneyMapIntegration.getApi() == null) return;
        JourneyMapIntegration.scanWaypoints(new ScanWaypointsPacket(ScanWaypointsPacket.CLEAR, List.of()));
        JourneyMapIntegration.removeWaypoints(new RemoveModWaypointsPacket(8, 81, 8, 64,
                false, "Visual Acceptance", false), message -> VisualAcceptance.report("CLEANUP " + message.getString()));
        JourneyMapChunkOverlays.showVillage(new ShowVillageChunksPacket(List.of(), false, "minecraft:overworld"));
    }

    private static Screen constructScreen(String owner, Class<?>[] parameters, Object[] arguments) {
        try {
            Object value = type(owner).getConstructor(parameters).newInstance(arguments);
            if (value instanceof Screen screen) return screen;
            throw new IllegalStateException("Pinned GUI is not a Screen: " + owner);
        } catch (ReflectiveOperationException error) {
            throw reflectionFailure(owner, error);
        }
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            throw reflectionFailure(name, error);
        }
    }

    private static Object field(Object target, String owner, String name) {
        try {
            var field = type(owner).getDeclaredField(name);
            require(field.trySetAccessible(), "Cannot inspect pinned field " + owner + "." + name);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw reflectionFailure(owner + "." + name, error);
        }
    }

    private static Object call(Object target, String owner, String name, Class<?>[] parameters, Object... arguments) {
        try {
            return type(owner).getMethod(name, parameters).invoke(target, arguments);
        } catch (ReflectiveOperationException error) {
            throw reflectionFailure(owner + "." + name, error);
        }
    }

    private static IllegalStateException reflectionFailure(String member, ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException invoked ? invoked.getCause() : error;
        return new IllegalStateException("Pinned integration API mismatch/failure at " + member, cause);
    }
}
