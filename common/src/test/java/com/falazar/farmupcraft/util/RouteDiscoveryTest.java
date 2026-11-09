package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteDiscoveryTest {
    private Result road(int length, String id, Set<String> claims) {
        List<Point> points = new ArrayList<>();
        for (int x = 0; x <= length; x++) points.add(new Point(x, 64, 0));
        return new Result(List.of(new Segment(id, points, 16, length - 16, claims, Set.of())), Set.of());
    }

    @Test void firstScanQueuesEveryMemberAndUnchangedOrRenamedSegmentsNeverRepeat() {
        var first = RouteDiscovery.empty().discover(road(60, "first", Set.of()),
                "Mykos", Kind.ROAD, List.of("online", "offline"), 1000);
        assertEquals(1, first.additions());
        assertEquals(first.state().pending().get("online"), first.state().pending().get("offline"));
        var delivered = first.state().delivered("online");
        var repeated = delivered.discover(road(60, "new-segment-id", Set.of()),
                "Mykos", Kind.ROAD, List.of("online", "offline"), 2000);
        assertEquals(0, repeated.additions());
        assertFalse(repeated.state().pending().containsKey("online"));
        assertEquals(1, repeated.state().pending().get("offline").size());
        var decoded = RouteDiscovery.decode(RouteDiscovery.encode(repeated.state()));
        assertEquals(repeated.state(), decoded);
        assertTrue(decoded.delivered("offline").pending().isEmpty());
    }

    @Test void dailyResegmentationAndCentrelineShiftDoNotAnnounceNewRoad() {
        var state = RouteDiscovery.empty().discover(road(60, "original", Set.of()), "Mykos",
                Kind.ROAD, List.of("member"), 1000).state().delivered("member");
        List<Point> a = new ArrayList<>(), b = new ArrayList<>();
        for (int x = 0; x <= 30; x++) a.add(new Point(x, 64, 1));
        for (int x = 30; x <= 60; x++) b.add(new Point(x, 64, 1));
        var result = new Result(List.of(new Segment("split-a", a, 16, 14, Set.of(), Set.of()),
                new Segment("split-b", b, 0, 30, Set.of(), Set.of())), Set.of());
        var update = state.discover(result, "Mykos", Kind.ROAD, List.of("member"), 2000);
        assertEquals(0, update.additions());
        assertTrue(update.state().pending().isEmpty());
    }

    @Test void genuinelyNewExtensionOrConnectionNotifiesOnceAndIncludesPartialStatus() {
        var state = RouteDiscovery.empty().discover(road(60, "first", Set.of()), "Mykos",
                Kind.RAIL, List.of("member"), 1000).state().delivered("member");
        var extended = road(80, "extended", Set.of("village:other"));
        var partial = new Result(extended.segments(), Set.of("unloaded terrain"));
        var update = state.discover(partial, "Mykos", Kind.RAIL, List.of("member"), 2000);
        assertEquals(1, update.additions());
        assertEquals(1, update.newConnections());
        assertTrue(update.state().pending().get("member").get(0).contains("railway"));
        assertTrue(update.state().pending().get("member").get(0).contains("partial survey"));
        var next = update.state().delivered("member").discover(extended,
                "Mykos", Kind.RAIL, List.of("member"), 3000);
        assertTrue(next.state().pending().isEmpty());
        assertEquals(0, next.newConnections());
    }

    @Test void rebuildingKnownRoadDoesNotRepeatAndFormerMembersArePruned() {
        var state = RouteDiscovery.empty().discover(road(60, "first", Set.of()), "Mykos",
                Kind.ROAD, List.of("former", "member"), 1000).state().delivered("member");
        state = state.discover(new Result(List.of(), Set.of()), "Mykos", Kind.ROAD, List.of("member"), 2000).state();
        assertFalse(state.pending().containsKey("former"));
        assertTrue(state.discover(road(60, "rebuilt", Set.of()), "Mykos",
                Kind.ROAD, List.of("member"), 3000).state().pending().isEmpty());
    }

    @Test void emptyFirstSurveyDoesNotNotify() {
        var update = RouteDiscovery.empty().discover(new Result(List.of(), Set.of()), "Mykos",
                Kind.ROAD, List.of("member"), 1000);
        assertTrue(update.state().pending().isEmpty());
    }
}
