package com.falazar.farmupcraft.util;

import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;

/** Voxel connectivity shared by both Minecraft versions. */
public abstract class RouteSurveyTerrain implements Source {
    public record Column(int x, int z) implements Comparable<Column> {
        @Override public int compareTo(Column p) {
            int c = Integer.compare(x, p.x);
            return c == 0 ? Integer.compare(z, p.z) : c;
        }
    }
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    protected final Kind kind;
    protected final Set<String> problems = new TreeSet<>();
    private final List<Column> columns;
    private final int minY, maxY;
    protected RouteSurveyTerrain(Kind kind, Collection<Column> columns, int minY, int maxY) {
        this.kind = kind;
        this.columns = new ArrayList<>(new TreeSet<>(columns));
        this.minY = minY;
        this.maxY = maxY;
    }
    protected abstract boolean loaded(int x, int z);
    protected abstract int surfaceY(int x, int z);
    protected abstract boolean road(Point p);
    protected abstract boolean gapClear(Point p);
    protected abstract String railShape(Point p);

    @Override public Iterator<Point> seeds() {
        return new Iterator<>() {
            int column, y = minY;
            @Override public boolean hasNext() { return column < columns.size(); }
            @Override public Point next() {
                if (!hasNext()) throw new NoSuchElementException();
                Column c = columns.get(column);
                if (!loaded(c.x, c.z)) {
                    problems.add("unloaded seed plots");
                    column++;
                    y = minY;
                    return null;
                }
                if (kind == Kind.ROAD && y == minY) y = Math.max(minY, surfaceY(c.x, c.z) - 8);
                Point p = new Point(c.x, y, c.z);
                int last = kind == Kind.ROAD ? Math.min(maxY, surfaceY(c.x, c.z)) : maxY;
                if (++y > last) { column++; y = minY; }
                return kind == Kind.ROAD ? (road(p) ? p : null) : (!railShape(p).isEmpty() ? p : null);
            }
        };
    }

    private boolean available(int x, int z) {
        if (loaded(x, z)) return true;
        problems.add("unloaded terrain");
        return false;
    }

    private Point landing(Point p, int dx, int dz) {
        if (!available(p.x() + dx, p.z() + dz)) return null;
        for (int dy : new int[]{0, 1, -1}) {
            Point q = p.offset(dx, dy, dz);
            if (road(q)) return q;
        }
        return null;
    }

    @Override public List<Link> neighbors(Point p) {
        if (!available(p.x(), p.z())) return List.of();
        return kind == Kind.ROAD ? roadNeighbors(p) : railNeighbors(p);
    }

    private List<Link> roadNeighbors(Point p) {
        if (!road(p)) { problems.add("route changed while checking"); return List.of(); }
        List<Link> links = new ArrayList<>();
        Set<Column> immediate = new HashSet<>();
        for (int[] d : DIRECTIONS) {
            Point q = landing(p, d[0], d[1]);
            if (q != null) {
                links.add(new Link(q, p.distance(q)));
                immediate.add(new Column(d[0], d[1]));
            } else if (available(p.x() + d[0], p.z() + d[1]) && gapClear(p.offset(d[0], 0, d[1]))) {
                q = landing(p, d[0] * 2, d[1] * 2);
                if (q != null) links.add(new Link(q, p.distance(q)));
            }
        }
        for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
            if (immediate.contains(new Column(dx, 0)) || immediate.contains(new Column(0, dz))) continue;
            Point q = landing(p, dx, dz);
            if (q != null && gapClear(p.offset(dx, 0, 0)) && gapClear(p.offset(0, 0, dz)))
                links.add(new Link(q, p.distance(q)));
        }
        return links;
    }

    private record End(int x, int z, int rise) {}
    private static List<End> ends(String shape) {
        return switch (shape) {
            case "north_south" -> List.of(new End(0,-1,0), new End(0,1,0));
            case "east_west" -> List.of(new End(-1,0,0), new End(1,0,0));
            case "ascending_east" -> List.of(new End(-1,0,0), new End(1,0,1));
            case "ascending_west" -> List.of(new End(-1,0,1), new End(1,0,0));
            case "ascending_north" -> List.of(new End(0,-1,1), new End(0,1,0));
            case "ascending_south" -> List.of(new End(0,-1,0), new End(0,1,1));
            case "south_east" -> List.of(new End(0,1,0), new End(1,0,0));
            case "south_west" -> List.of(new End(0,1,0), new End(-1,0,0));
            case "north_west" -> List.of(new End(0,-1,0), new End(-1,0,0));
            case "north_east" -> List.of(new End(0,-1,0), new End(1,0,0));
            case "" -> List.of();
            default -> throw new IllegalArgumentException("Unsupported rail shape: " + shape);
        };
    }

    private List<Link> railNeighbors(Point p) {
        List<Link> links = new ArrayList<>();
        for (End end : ends(railShape(p))) {
            if (!available(p.x() + end.x, p.z() + end.z)) continue;
            for (int dy : new int[]{end.rise, end.rise - 1}) {
                Point q = p.offset(end.x, dy, end.z);
                for (End back : ends(railShape(q))) {
                    if (back.x == -end.x && back.z == -end.z && q.y() + back.rise == p.y() + end.rise) {
                        links.add(new Link(q, p.distance(q)));
                        break;
                    }
                }
            }
        }
        return links;
    }

    @Override public Set<String> issues() { return Set.copyOf(problems); }
}
