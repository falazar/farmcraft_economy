package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteSurveyTerrainTest {
    private static final class Terrain extends RouteSurveyTerrain {
        final Set<Point> roads = new HashSet<>();
        final Map<Point, String> rails = new HashMap<>();
        Terrain(Kind kind) { super(kind, List.of(new Column(15, 0)), 64, 66); }
        @Override protected boolean loaded(int x, int z) { return true; }
        @Override protected int surfaceY(int x, int z) { return 64; }
        @Override protected boolean road(Point p) { return roads.contains(p); }
        @Override protected boolean gapClear(Point p) { return true; }
        @Override protected String railShape(Point p) { return rails.getOrDefault(p, ""); }
        @Override public boolean inside(Point p) { return p.x() < 16; }
        @Override public Set<String> claims(Point p) { return Set.of(); }
        @Override public Set<String> ruins(Point p) { return Set.of(); }
        Result run() {
            var scan = new RouteSurvey(this, kind, "fixture");
            int ticks = 0;
            while (!scan.advance(8)) assertTrue(++ticks < 10000);
            return scan.result();
        }
    }

    @Test void exactlyOneMissingBlockIsAllowedNotTwo() {
        for (int gap : new int[]{1, 2}) {
            Terrain t = new Terrain(Kind.ROAD);
            for (int x = 0; x <= 60; x++)
                if (x < 30 || x >= 30 + gap) t.roads.add(new Point(x, 64, 0));
            assertEquals(gap == 1 ? 60 : 0, t.run().length(), 0.001);
        }
    }

    @Test void wideSteppingStoneRoadKeepsCentrelineAcrossSingleGaps() {
        Terrain t = new Terrain(Kind.ROAD);
        for (int x = 0; x <= 60; x++)
            for (int z = -1; z <= 1; z++)
                if (x != 30) t.roads.add(new Point(x, 64, z));
        assertEquals(60, t.run().length(), .001);
    }

    @Test void exact48BlockWideRoadQualifies() {
        Terrain t = new Terrain(Kind.ROAD);
        for (int x = 0; x <= 48; x++)
            for (int z = -1; z <= 1; z++) t.roads.add(new Point(x, 64, z));
        assertEquals(48, t.run().length(), .001);
    }

    @Test void slopesAndStairsRemainConnected() {
        Terrain t = new Terrain(Kind.ROAD);
        for (int x = 0; x <= 60; x++) t.roads.add(new Point(x, x < 30 ? 64 : 65, 0));
        assertEquals(59 + Math.sqrt(2), t.run().length(), .001);
    }

    @Test void railBreaksAndWrongOrientationDoNotJoin() {
        Terrain t = new Terrain(Kind.RAIL);
        for (int x = 0; x <= 60; x++) {
            if (x != 30) t.rails.put(new Point(x, 64, 0), "east_west");
            t.rails.put(new Point(x, 64, 1), "east_west");
        }
        assertEquals(0, t.run().length(), .001);
        t.rails.put(new Point(30, 64, 0), "north_south");
        assertEquals(0, t.run().length(), .001);
        t.rails.put(new Point(30, 64, 0), "east_west");
        assertEquals(60, t.run().length(), .001);
    }

    @Test void railwayCurvesAndAscentsUseMatchingEndpoints() {
        Terrain t = new Terrain(Kind.RAIL);
        for (int x = 0; x < 30; x++) t.rails.put(new Point(x, 64, 0), "east_west");
        t.rails.put(new Point(30, 64, 0), "ascending_east");
        for (int x = 31; x < 40; x++) t.rails.put(new Point(x, 65, 0), "east_west");
        t.rails.put(new Point(40, 65, 0), "south_west");
        for (int z = 1; z <= 30; z++) t.rails.put(new Point(40, 65, z), "north_south");
        assertEquals(69 + Math.sqrt(2), t.run().length(), .001);
    }
}
