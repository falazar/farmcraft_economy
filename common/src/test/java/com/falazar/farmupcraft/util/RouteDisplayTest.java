package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteDisplayTest {
    @Test void compassUsesMinecraftNorthAndAllEightDirections() {
        Point start = new Point(0, 64, 0);
        int[][] offsets = {{0,-10},{10,-10},{10,0},{10,10},{0,10},{-10,10},{-10,0},{-10,-10}};
        String[] expected = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        for (int i = 0; i < offsets.length; i++)
            assertEquals(expected[i], RouteDisplay.direction(start, start.offset(offsets[i][0], 0, offsets[i][1])));
        assertEquals("LOOP", RouteDisplay.direction(start, start));
        assertEquals("UP", RouteDisplay.direction(start, start.offset(0, 1, 0)));
    }

    @Test void rowShowsStartEndMeasuredLengthDirectionAndConnections() {
        var segment = new Segment("id", List.of(new Point(10, 64, 20), new Point(110, 64, 20)),
                16, 84, Set.of("village:other"), Set.of("surface-ruin"));
        String row = RouteDisplay.segment(1, segment, c -> "Name(" + c + ")");
        assertTrue(row.contains("Start 10, 64, 20 -> 110, 64, 20"));
        assertTrue(row.contains("100.0 blocks | dir E"));
        assertTrue(row.contains("Name(village:other)"));
        assertTrue(row.contains("Name(ruin:surface-ruin)"));
        var empty = new Segment("none", segment.points(), 16, 84, Set.of(), Set.of());
        assertTrue(RouteDisplay.segment(2, empty, c -> c).endsWith("connections: none)"));
        assertTrue(RouteDisplay.friendlySummary(1, segment, Kind.ROAD, c -> "Name(" + c + ")")
                .contains("100") && RouteDisplay.friendlySummary(1, segment, Kind.ROAD, c -> "Name(" + c + ")")
                .contains("east road"));
    }
}
