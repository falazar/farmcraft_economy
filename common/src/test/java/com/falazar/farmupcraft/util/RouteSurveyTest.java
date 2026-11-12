package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteSurveyTest {
    private static final class World implements Source {
        final Map<Point, List<Link>> edges = new HashMap<>();
        final Set<Point> seeds = new TreeSet<>();
        final Map<Point, Set<String>> ruins = new HashMap<>();
        final Map<Point, Set<String>> claims = new HashMap<>();
        final Set<String> issues = new HashSet<>();

        void line(int x0, int x1, int z) {
            for (int x = x0; x <= x1; x++)
                add(new Point(x, 64, z));
        }

        void add(Point p) {
            edges.computeIfAbsent(p, k -> new ArrayList<>());
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                Point n = p.offset(d[0], 0, d[1]);
                if (edges.containsKey(n))
                    link(p, n);
            }
        }

        void link(Point a, Point b) {
            edges.get(a).add(new Link(b, a.distance(b)));
            edges.get(b).add(new Link(a, a.distance(b)));
        }

        @Override
        public Iterator<Point> seeds() {
            return seeds.iterator();
        }

        @Override
        public List<Link> neighbors(Point p) {
            return edges.getOrDefault(p, List.of());
        }

        @Override
        public boolean inside(Point p) {
            return p.x() < 16;
        }

        @Override
        public Set<String> claims(Point p) {
            return claims.getOrDefault(p, Set.of());
        }

        @Override
        public Set<String> ruins(Point p) {
            return ruins.getOrDefault(p, Set.of());
        }

        @Override
        public Set<String> issues() {
            return issues;
        }

        Result run(Kind kind) {
            var scan = new RouteSurvey(this, kind, "village/dimension");
            int calls = 0;
            while (!scan.advance(7))
                assertTrue(++calls < 100_000);
            return scan.result();
        }
    }

    @Test
    void exactMinimumAndEntryThreshold() {
        for (int distance : new int[] { 4, 5, 47, 48, 49 }) {
            World w = new World();
            w.line(0, distance, 0);
            w.seeds.add(new Point(15 > distance ? 0 : 15, 64, 0));
            Result result = w.run(Kind.ROAD);
            assertEquals(distance < 48 ? 0 : distance, result.length(), 0.001);
        }
    }

    @Test
    void shortSegmentsSurviveJunctionAndRuinSplitsAndIdsStayStable() {
        World w = new World();
        w.line(0, 100, 0);
        for (int z = 1; z <= 12; z++)
            w.add(new Point(60, 64, z));
        w.ruins.put(new Point(80, 64, 0), Set.of("surface-ruin"));
        w.claims.put(new Point(95, 64, 0), Set.of("other-village"));
        w.seeds.add(new Point(15, 64, 0));
        Result first = w.run(Kind.ROAD), second = w.run(Kind.ROAD);
        assertEquals(112, first.length(), 0.001);
        assertEquals(3, first.segments().size(), "Only the real 12-block fork splits the road into 3 branches");
        assertTrue(first.segments().stream().anyMatch(s -> s.length() < 48));
        assertTrue(first.segments().stream().anyMatch(s -> s.ruins().contains("surface-ruin")));
        assertTrue(first.segments().stream().anyMatch(s -> s.claims().contains("other-village")));
        assertEquals(first, second);
    }

    @Test
    void shortSpursAndClaimTransitionsAreWrappedIntoOneDistinctRoad() {
        World w = new World();
        w.line(0, 80, 0);
        // A 2-block side spur at x=40 and plot/claim transitions at x=20 and x=50 must
        // not split the road.
        w.add(new Point(40, 64, 1));
        w.add(new Point(40, 64, 2));
        w.claims.put(new Point(20, 64, 0), Set.of("plot:a"));
        w.claims.put(new Point(50, 64, 0), Set.of("plot:b"));
        w.seeds.add(new Point(15, 64, 0));
        Result r = w.run(Kind.ROAD);
        assertEquals(1, r.segments().size());
        assertEquals(80, r.length(), 0.001);
        assertEquals(Set.of("plot:a", "plot:b"), r.segments().get(0).claims());
        Segment southRoad = new Segment("south", List.of(new Point(480, 75, -172), new Point(486, 69, -238)),
                48.5, 27.7, Set.of(), Set.of());
        Segment oriented = RouteSurvey.orientOutward(southRoad, 504, -296);
        assertEquals(new Point(486, 69, -238), oriented.points().get(0));
        assertEquals(new Point(480, 75, -172), oriented.points().get(1));
        assertEquals("S", RouteDisplay.direction(oriented.points().get(0), oriented.points().get(1)));
    }

    @Test
    void multipleExitsDoNotDoubleCountAndWidthDoesNotMultiplyLength() {
        World w = new World();
        for (int z = -1; z <= 1; z++)
            w.line(0, 100, z);
        w.seeds.add(new Point(15, 64, 0));
        w.seeds.add(new Point(16, 64, 0));
        Result r = w.run(Kind.ROAD);
        assertTrue(r.length() >= 96 && r.length() <= 100, "Centreline distance, not 303 paving blocks: " + r.length());
    }

    @Test
    void railwayUsesOnlySuppliedTrackConnections() {
        World w = new World();
        w.line(0, 60, 0);
        w.line(0, 60, 2);
        w.seeds.add(new Point(0, 64, 0));
        assertEquals(60, w.run(Kind.RAIL).length(), 0.001);
    }

    @Test
    void loopsAreSavedOnceAndMultipleExitsDoNotDuplicateTheirEdges() {
        World w = new World();
        w.line(0, 20, 0);
        w.line(0, 20, 20);
        for (int z = 1; z < 20; z++) {
            w.add(new Point(0, 64, z));
            w.add(new Point(20, 64, z));
        }
        w.seeds.add(new Point(15, 64, 0));
        w.seeds.add(new Point(15, 64, 20));
        Result result = w.run(Kind.ROAD);
        assertEquals(80, result.length(), .001);
        Set<String> edges = new HashSet<>();
        for (Segment s : result.segments()) {
            for (int i = 1; i < s.points().size(); i++) {
                Point a = s.points().get(i - 1), b = s.points().get(i);
                String key = a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
                assertTrue(edges.add(key), "Shared/loop edge must not be counted twice");
            }
        }
    }

    @Test
    void eightHundredOutwardPlusInsideAndIncompleteRetainsSuccess() {
        World w = new World();
        w.line(0, 1000, 0);
        w.seeds.add(new Point(15, 64, 0));
        Result limited = w.run(Kind.ROAD);
        assertFalse(limited.complete());
        assertTrue(limited.issues().contains("800-block limit"));
        assertEquals(815, limited.length(), 0.001);
        Result good = new Result(List.of(), Set.of());
        Saved saved = Saved.empty().begin(100).finish(good, 110);
        saved = saved.begin(100 + INTERVAL_MILLIS).finish(limited, 200 + INTERVAL_MILLIS);
        assertEquals(good, saved.successful());
        assertEquals(110, saved.lastSuccess());
        assertEquals(limited, saved.latest());
    }

    @Test
    void realTimeGatePersistsAndDoesNotRetryOnRestartOrClockRollback() {
        Saved saved = Saved.empty().begin(1000);
        Saved loaded = RouteSurveyJson.decode(RouteSurveyJson.encode(saved));
        assertEquals(saved, loaded);
        assertFalse(loaded.due(999));
        assertFalse(loaded.due(1000 + INTERVAL_MILLIS - 1));
        assertTrue(loaded.due(1000 + INTERVAL_MILLIS));
        assertThrows(IllegalStateException.class, () -> loaded.begin(1001));
        assertEquals(1001, loaded.beginForced(1001).lastChecked());
        assertThrows(RuntimeException.class, () -> RouteSurveyJson.decode("{\"version\":2}"));
    }

    @Test
    void completeResurveyReplacesRemovedSegmentsAndIncompleteIsExplicit() {
        World w = new World();
        w.line(0, 60, 0);
        w.seeds.add(new Point(15, 64, 0));
        Saved saved = Saved.empty().begin(1000).finish(w.run(Kind.ROAD), 1100);
        World empty = new World();
        saved = saved.begin(1000 + INTERVAL_MILLIS).finish(empty.run(Kind.ROAD), 1100 + INTERVAL_MILLIS);
        assertTrue(saved.successful().segments().isEmpty());
        w.issues.add("unloaded terrain");
        assertFalse(w.run(Kind.ROAD).complete());
    }

    @Test
    void backgroundMeasurementNeverReadsLiveSource() throws InterruptedException {
        World w = new World();
        w.line(0, 100, 0);
        w.seeds.add(new Point(15, 64, 0));
        Thread serverThread = Thread.currentThread();
        Source checked = new Source() {
            void check() {
                assertSame(serverThread, Thread.currentThread(), "Live source access must stay on server thread");
            }

            @Override
            public Iterator<Point> seeds() {
                check();
                return w.seeds();
            }

            @Override
            public List<Link> neighbors(Point p) {
                check();
                return w.neighbors(p);
            }

            @Override
            public boolean inside(Point p) {
                check();
                return w.inside(p);
            }

            @Override
            public Set<String> claims(Point p) {
                check();
                return w.claims(p);
            }

            @Override
            public Set<String> ruins(Point p) {
                check();
                return w.ruins(p);
            }

            @Override
            public Set<String> issues() {
                check();
                return w.issues();
            }
        };
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var scan = new RouteSurvey(checked, Kind.ROAD, "fixture", executor);
            int calls = 0;
            while (!scan.advance(8)) {
                assertTrue(++calls < 10000, "Background measurement must finish");
                Thread.sleep(1);
            }
            assertEquals(100, scan.result().length(), .001);
        } finally {
            executor.shutdownNow();
        }
    }
}
