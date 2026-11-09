package com.falazar.farmupcraft.util;

import java.util.*;
import java.util.function.Function;
import static com.falazar.farmupcraft.util.RouteSurvey.*;

public final class RouteDisplay {
    public static final int PAGE_SIZE = 8;
    private RouteDisplay() {}
    public static String position(Point p) { return p.x() + ", " + p.y() + ", " + p.z(); }
    public static String direction(Point a, Point b) {
        if (a.x() == b.x() && a.z() == b.z()) return a.equals(b) ? "LOOP" : b.y() > a.y() ? "UP" : "DOWN";
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return directions[Math.floorMod((int) Math.round(Math.atan2((double) b.x() - a.x(),
                (double) a.z() - b.z()) / (Math.PI / 4)), 8)];
    }
    public static String directionName(String code) {
        return switch (code) {
            case "N" -> "north";
            case "NE" -> "northeast";
            case "E" -> "east";
            case "SE" -> "southeast";
            case "S" -> "south";
            case "SW" -> "southwest";
            case "W" -> "west";
            case "NW" -> "northwest";
            case "LOOP" -> "loop";
            case "UP" -> "upper";
            case "DOWN" -> "lower";
            default -> code.toLowerCase(Locale.ROOT);
        };
    }

    public static String naturalList(Collection<String> items) {
        List<String> list = new ArrayList<>(items);
        if (list.isEmpty()) return "the wilderness";
        if (list.size() == 1) return list.get(0);
        if (list.size() == 2) return list.get(0) + " and " + list.get(1);
        return String.join(", ", list.subList(0, list.size() - 1)) + ", and " + list.get(list.size() - 1);
    }

    public static String friendlySummary(int number, Segment segment, Kind kind, Function<String, String> connectionName) {
        Point start = segment.points().get(0), end = segment.points().get(segment.points().size() - 1);
        String dirCode = direction(start, end);
        String dirWord = directionName(dirCode);
        String routeNoun = kind == Kind.RAIL ? "railroad" : "road";
        long blocks = Math.round(segment.length());
        Set<String> connections = new TreeSet<>();
        segment.claims().forEach(c -> connections.add(connectionName.apply(c)));
        segment.ruins().forEach(c -> connections.add(connectionName.apply("ruin:" + c)));
        int variant = Math.floorMod(segment.id().hashCode() + number, 5);
        if (connections.isEmpty()) {
            return switch (variant) {
                case 0 -> "Your " + dirWord + " " + routeNoun + " is " + blocks + " blocks long and heads out into the countryside.";
                case 1 -> "The " + dirWord + " " + routeNoun + " runs for " + blocks + " blocks into the open frontier.";
                case 2 -> "Heading " + dirWord + ", your " + routeNoun + " stretches " + blocks + " blocks with no connections yet.";
                case 3 -> "You have a " + blocks + "-block " + dirWord + " " + routeNoun + " reaching out from town.";
                default -> "Your " + dirWord + " " + routeNoun + " travels " + blocks + " blocks toward the frontier.";
            };
        }
        String joined = naturalList(connections);
        return switch (variant) {
            case 0 -> "Your " + dirWord + " " + routeNoun + " is " + blocks + " blocks long and connects to " + joined + ".";
            case 1 -> "The " + dirWord + " " + routeNoun + " runs for " + blocks + " blocks, connecting to " + joined + ".";
            case 2 -> "Heading " + dirWord + ", your " + routeNoun + " stretches " + blocks + " blocks and reaches " + joined + ".";
            case 3 -> "Your " + dirWord + " " + routeNoun + " travels " + blocks + " blocks and links up with " + joined + ".";
            default -> "You have a " + blocks + "-block " + dirWord + " " + routeNoun + " that connects to " + joined + ".";
        };
    }

    public static String segment(int number, Segment segment, Function<String, String> connectionName) {
        return segment(number, segment, Kind.ROAD, connectionName);
    }

    public static String segment(int number, Segment segment, Kind kind, Function<String, String> connectionName) {
        Point start = segment.points().get(0), end = segment.points().get(segment.points().size() - 1);
        Set<String> connections = new TreeSet<>();
        segment.claims().forEach(c -> connections.add(connectionName.apply(c)));
        segment.ruins().forEach(c -> connections.add(connectionName.apply("ruin:" + c)));
        return number + ". " + friendlySummary(number, segment, kind, connectionName)
                + " (Start " + position(start) + " -> " + position(end)
                + " | " + String.format(Locale.ROOT, "%.1f", segment.length()) + " blocks"
                + " | dir " + direction(start, end)
                + " | connections: " + (connections.isEmpty() ? "none" : String.join("; ", connections)) + ")";
    }
}
