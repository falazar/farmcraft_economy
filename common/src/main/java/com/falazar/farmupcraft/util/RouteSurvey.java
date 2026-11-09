package com.falazar.farmupcraft.util;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Incremental, world-independent route tracing and centreline measurement. */
public final class RouteSurvey {
    public static final long INTERVAL_MILLIS = 86_400_000L;
    public static final int OUTWARD_LIMIT = 800;
    public static final int MAX_POSITIONS = 32_768;
    private static final int MAX_SEED_CHECKS = 1_048_576;
    public enum Kind { ROAD, RAIL }
    public record Point(int x, int y, int z) implements Comparable<Point> {
        public Point offset(int dx, int dy, int dz) { return new Point(x + dx, y + dy, z + dz); }
        public double distance(Point p) {
            return Math.sqrt((double) (x - p.x) * (x - p.x) + (double) (y - p.y) * (y - p.y)
                    + (double) (z - p.z) * (z - p.z));
        }
        @Override public int compareTo(Point p) {
            int c = Integer.compare(x, p.x);
            if (c == 0) c = Integer.compare(z, p.z);
            return c == 0 ? Integer.compare(y, p.y) : c;
        }
    }
    public record Link(Point point, double length) {
        public Link {
            if (!Double.isFinite(length) || length <= 0) throw new IllegalArgumentException("Invalid route edge");
        }
    }
    private static final int MIN_SHORT_SPUR_STEPS = 8;

    public static List<Segment> coalesceSegments(List<Segment> rawSegments) {
        if (rawSegments == null || rawSegments.size() <= 1) {
            return rawSegments == null ? List.of() : List.copyOf(rawSegments);
        }
        Map<Point, List<Integer>> byEndpoint = new HashMap<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            List<Point> pts = rawSegments.get(i).points();
            Point a = pts.get(0);
            Point b = pts.get(pts.size() - 1);
            byEndpoint.computeIfAbsent(a, k -> new ArrayList<>()).add(i);
            if (!a.equals(b)) {
                byEndpoint.computeIfAbsent(b, k -> new ArrayList<>()).add(i);
            }
        }
        Set<Integer> absorbedSpurs = new HashSet<>();
        Map<Point, Set<String>> extraClaims = new HashMap<>();
        Map<Point, Set<String>> extraRuins = new HashMap<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            Segment s = rawSegments.get(i);
            if (s.points().size() - 1 > MIN_SHORT_SPUR_STEPS || s.length() > MIN_SHORT_SPUR_STEPS) continue;
            Point a = s.points().get(0);
            Point b = s.points().get(s.points().size() - 1);
            int degA = byEndpoint.getOrDefault(a, List.of()).size();
            int degB = byEndpoint.getOrDefault(b, List.of()).size();
            Point junction = degA == 1 && degB >= 3 ? b : degB == 1 && degA >= 3 ? a : null;
            if (junction != null) {
                absorbedSpurs.add(i);
                extraClaims.computeIfAbsent(junction, k -> new TreeSet<>()).addAll(s.claims());
                extraRuins.computeIfAbsent(junction, k -> new TreeSet<>()).addAll(s.ruins());
            }
        }
        List<Segment> kept = new ArrayList<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            if (!absorbedSpurs.contains(i)) kept.add(rawSegments.get(i));
        }
        if (kept.size() <= 1 && extraClaims.isEmpty() && extraRuins.isEmpty()) {
            return List.copyOf(kept);
        }
        Map<Point, List<Integer>> adj = new HashMap<>();
        for (int i = 0; i < kept.size(); i++) {
            List<Point> pts = kept.get(i).points();
            Point a = pts.get(0);
            Point b = pts.get(pts.size() - 1);
            adj.computeIfAbsent(a, k -> new ArrayList<>()).add(i);
            if (!a.equals(b)) {
                adj.computeIfAbsent(b, k -> new ArrayList<>()).add(i);
            }
        }
        boolean[] used = new boolean[kept.size()];
        List<Segment> merged = new ArrayList<>();
        // Start from non-degree-2 endpoints first, then any remaining cycles.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < kept.size(); i++) {
                if (used[i]) continue;
                List<Point> pts = kept.get(i).points();
                Point a = pts.get(0);
                Point b = pts.get(pts.size() - 1);
                Point start = pass == 0
                        ? (adj.get(a).size() != 2 ? a : adj.get(b).size() != 2 ? b : null)
                        : a;
                if (start == null) continue;
                merged.add(walkChain(kept, adj, used, i, start, extraClaims, extraRuins));
            }
        }
        return List.copyOf(merged);
    }

    private static Segment walkChain(List<Segment> kept, Map<Point, List<Integer>> adj, boolean[] used,
                                     int firstIdx, Point startEnd,
                                     Map<Point, Set<String>> extraClaims, Map<Point, Set<String>> extraRuins) {
        used[firstIdx] = true;
        Segment first = kept.get(firstIdx);
        List<Point> chain = new ArrayList<>(first.points());
        if (!chain.get(0).equals(startEnd)) {
            Collections.reverse(chain);
        }
        double inside = first.inside();
        double outside = first.outside();
        Set<String> claims = new TreeSet<>(first.claims());
        Set<String> ruins = new TreeSet<>(first.ruins());
        Point cur = chain.get(chain.size() - 1);
        while (adj.getOrDefault(cur, List.of()).size() == 2) {
            int nextIdx = -1;
            for (int cand : adj.get(cur)) {
                if (!used[cand]) {
                    nextIdx = cand;
                    break;
                }
            }
            if (nextIdx < 0) break;
            used[nextIdx] = true;
            Segment nextSeg = kept.get(nextIdx);
            List<Point> nextPts = new ArrayList<>(nextSeg.points());
            if (!nextPts.get(0).equals(cur)) {
                Collections.reverse(nextPts);
            }
            for (int k = 1; k < nextPts.size(); k++) {
                chain.add(nextPts.get(k));
            }
            inside += nextSeg.inside();
            outside += nextSeg.outside();
            claims.addAll(nextSeg.claims());
            ruins.addAll(nextSeg.ruins());
            cur = chain.get(chain.size() - 1);
        }
        for (Point p : chain) {
            claims.addAll(extraClaims.getOrDefault(p, Set.of()));
            ruins.addAll(extraRuins.getOrDefault(p, Set.of()));
        }
        if (chain.get(0).compareTo(chain.get(chain.size() - 1)) > 0) {
            Collections.reverse(chain);
        }
        String id = UUID.nameUUIDFromBytes(chain.toString().getBytes(StandardCharsets.UTF_8)).toString();
        return new Segment(id, chain, inside, outside, claims, ruins);
    }

    public static Segment orientOutward(Segment segment, int centerX, int centerZ) {
        List<Point> pts = segment.points();
        if (pts.size() < 2) return segment;
        Point a = pts.get(0);
        Point b = pts.get(pts.size() - 1);
        long distA = (long) (a.x() - centerX) * (a.x() - centerX) + (long) (a.z() - centerZ) * (a.z() - centerZ);
        long distB = (long) (b.x() - centerX) * (b.x() - centerX) + (long) (b.z() - centerZ) * (b.z() - centerZ);
        if (distA <= distB) return segment;
        List<Point> reversed = new ArrayList<>(pts);
        Collections.reverse(reversed);
        return new Segment(segment.id(), reversed, segment.inside(), segment.outside(), segment.claims(), segment.ruins());
    }

    public record Segment(String id, List<Point> points, double inside, double outside,
                          Set<String> claims, Set<String> ruins) {
        public Segment {
            if (id == null || points.size() < 2 || !Double.isFinite(inside) || !Double.isFinite(outside)
                    || inside < 0 || outside < 0) throw new IllegalArgumentException("Invalid route segment");
            points = List.copyOf(points);
            claims = Set.copyOf(claims);
            ruins = Set.copyOf(ruins);
        }
        public double length() { return inside + outside; }
    }
    public record Result(List<Segment> segments, Set<String> issues) {
        public Result { segments = List.copyOf(segments); issues = Set.copyOf(issues); }
        public boolean complete() { return issues.isEmpty(); }
        public double length() { return segments.stream().mapToDouble(Segment::length).sum(); }
    }
    public record Saved(long lastChecked, long lastSuccess, String status, Result latest, Result successful) {
        public Saved {
            if (lastChecked < 0 || lastSuccess < 0) throw new IllegalArgumentException("Invalid survey datetime");
            Objects.requireNonNull(status);
            Objects.requireNonNull(latest);
            Objects.requireNonNull(successful);
        }
        public static Saved empty() {
            return new Saved(0, 0, "Not checked", new Result(List.of(), Set.of()), new Result(List.of(), Set.of()));
        }
        public boolean due(long now) {
            return lastChecked == 0 || now >= lastChecked && now - lastChecked >= INTERVAL_MILLIS;
        }
        public Saved begin(long now) {
            if (now <= 0 || !due(now)) throw new IllegalStateException("Survey is not due");
            return new Saved(now, lastSuccess, "In progress / interrupted if server restarted", latest, successful);
        }
        public Saved finish(Result result, long now) {
            return new Saved(lastChecked, result.complete() ? now : lastSuccess,
                    result.complete() ? "Complete" : "Incomplete: " + String.join(", ", new TreeSet<>(result.issues)),
                    result, result.complete() ? result : successful);
        }
    }
    public interface Source {
        Iterator<Point> seeds();
        List<Link> neighbors(Point point);
        boolean inside(Point point);
        Set<String> claims(Point point);
        Set<String> ruins(Point point);
        Set<String> issues();
    }

    private record Pending(Point point, double distance) {}
    private record Info(boolean inside, Set<String> claims, Set<String> ruins) {}
    private record Edge(Point a, Point b) {
        static Edge of(Point a, Point b) { return a.compareTo(b) < 0 ? new Edge(a, b) : new Edge(b, a); }
    }
    private final Source source;
    private final Kind kind;
    private final String identity;
    private final Iterator<Point> seeds;
    private final Executor executor;
    private final Map<Point, Info> info = new HashMap<>();
    private final Set<Point> gapPoints = new HashSet<>();
    private final Map<Point, Map<Point, Double>> graph = new TreeMap<>();
    private final Map<Point, Double> distances = new HashMap<>();
    private final PriorityQueue<Pending> pending = new PriorityQueue<>(Comparator.comparingDouble(Pending::distance));
    private final Set<String> issues = new TreeSet<>();
    private boolean seeded;
    private int seedChecks;
    private Result result;
    private CompletableFuture<Result> measurement;

    public RouteSurvey(Source source, Kind kind, String identity) {
        this(source, kind, identity, Runnable::run);
    }

    public RouteSurvey(Source source, Kind kind, String identity, Executor executor) {
        this.source = source;
        this.kind = kind;
        this.identity = identity;
        this.seeds = source.seeds();
        this.executor = executor;
    }

    /** Each unit processes one seed or route position. World reads stay on the calling server thread. */
    public boolean advance(int budget) {
        if (budget <= 0) throw new IllegalArgumentException("Survey budget must be positive");
        if (measurement != null) {
            if (measurement.isDone()) result = measurement.join();
            return result != null;
        }
        while (budget-- > 0 && result == null) {
            if (!seeded) {
                if (++seedChecks > MAX_SEED_CHECKS) {
                    issues.add("seed work budget");
                    seeded = true;
                    continue;
                }
                if (seeds.hasNext()) {
                    Point p = seeds.next();
                    if (p != null && !distances.containsKey(p) && hasEntryRun(p, new HashSet<>(), 6)) {
                        distances.put(p, 0.0);
                        pending.add(new Pending(p, 0));
                    }
                    if (distances.size() > MAX_POSITIONS) {
                        issues.add("position budget");
                        seeded = true;
                    }
                } else seeded = true;
                continue;
            }
            if (pending.isEmpty()) {
                issues.addAll(source.issues());
                // Everything used by measure() is now an owned snapshot, never a world/database read.
                measurement = CompletableFuture.supplyAsync(this::measure, executor);
                if (measurement.isDone()) result = measurement.join();
                break;
            }
            Pending next = pending.remove();
            if (next.distance > distances.get(next.point)) continue;
            graph.computeIfAbsent(next.point, p -> new TreeMap<>());
            remember(next.point);
            for (Link link : source.neighbors(next.point)) {
                double cost = next.distance + (source.inside(link.point) ? 0 : link.length);
                if (cost > OUTWARD_LIMIT) {
                    issues.add("800-block limit");
                    continue;
                }
                if (!distances.containsKey(link.point) && distances.size() >= MAX_POSITIONS) {
                    issues.add("position budget");
                    continue;
                }
                graph.get(next.point).put(link.point, link.length);
                graph.computeIfAbsent(link.point, p -> new TreeMap<>()).put(next.point, link.length);
                remember(link.point);
                if (kind == Kind.ROAD && Math.max(Math.abs(link.point.x - next.point.x),
                        Math.abs(link.point.z - next.point.z)) == 2) {
                    Point midpoint = new Point(Math.floorDiv(link.point.x + next.point.x, 2),
                            Math.min(link.point.y, next.point.y), Math.floorDiv(link.point.z + next.point.z, 2));
                    gapPoints.add(midpoint);
                    remember(midpoint);
                }
                if (cost < distances.getOrDefault(link.point, Double.POSITIVE_INFINITY)) {
                    distances.put(link.point, cost);
                    pending.add(new Pending(link.point, cost));
                }
            }
        }
        return result != null;
    }

    public Result result() {
        if (result == null) throw new IllegalStateException("Survey has not finished");
        return result;
    }

    private boolean hasEntryRun(Point p, Set<Point> path, int remaining) {
        path.add(p);
        if (remaining == 1) return true;
        for (Link link : source.neighbors(p)) {
            if (!path.contains(link.point) && hasEntryRun(link.point, path, remaining - 1)) return true;
        }
        path.remove(p);
        return false;
    }

    private void remember(Point p) {
        info.computeIfAbsent(p, point -> new Info(source.inside(point),
                Set.copyOf(source.claims(point)), Set.copyOf(source.ruins(point))));
    }

    private Result measure() {
        if (kind == Kind.ROAD) thin();
        Set<Point> seen = new HashSet<>();
        List<Segment> segments = new ArrayList<>();
        for (Point start : List.copyOf(graph.keySet())) {
            checkCancelled();
            if (!graph.containsKey(start) || !seen.add(start)) continue;
            Set<Point> component = new TreeSet<>();
            Deque<Point> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                checkCancelled();
                Point p = queue.removeFirst();
                component.add(p);
                for (Point n : graph.get(p).keySet()) if (seen.add(n)) queue.addLast(n);
            }
            // Width and branch count must not turn a collection of short stubs into a qualifying route.
            if (component.size() < 6 || longestRoute(component) < 48
                    || kind == Kind.ROAD && component.stream().allMatch(p -> info.get(p).inside)) continue;
            pruneShortSpurs(component, 8);
            split(component, segments);
        }
        return new Result(segments, issues);
    }

    private void pruneShortSpurs(Set<Point> component, int maxSpurSteps) {
        boolean changed;
        do {
            changed = false;
            for (Point leaf : List.copyOf(component)) {
                if (!component.contains(leaf) || graph.get(leaf).size() != 1) continue;
                List<Point> branch = new ArrayList<>();
                Point prev = null;
                Point cur = leaf;
                while (cur != null && branch.size() <= maxSpurSteps) {
                    int deg = graph.get(cur).size();
                    if (cur != leaf && deg != 2) break;
                    branch.add(cur);
                    Point next = null;
                    for (Point n : graph.get(cur).keySet()) {
                        if (!n.equals(prev)) {
                            next = n;
                            break;
                        }
                    }
                    prev = cur;
                    cur = next;
                }
                if (cur != null && graph.get(cur).size() >= 3 && branch.size() <= maxSpurSteps) {
                    Info junction = info.get(cur);
                    Set<String> claims = new HashSet<>(junction.claims);
                    Set<String> ruins = new HashSet<>(junction.ruins);
                    for (Point removed : branch) {
                        Info r = info.get(removed);
                        if (r != null) {
                            claims.addAll(r.claims);
                            ruins.addAll(r.ruins);
                        }
                        for (Point n : List.copyOf(graph.get(removed).keySet())) {
                            graph.get(n).remove(removed);
                        }
                        graph.remove(removed);
                        component.remove(removed);
                    }
                    info.put(cur, new Info(junction.inside, Set.copyOf(claims), Set.copyOf(ruins)));
                    changed = true;
                }
            }
        } while (changed);
    }

    private double longestRoute(Set<Point> component) {
        Point start = component.iterator().next();
        Map<Point, Double> first = shortestPaths(start);
        Point farthest = first.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        double diameter = shortestPaths(farthest).values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (component.stream().allMatch(p -> graph.get(p).size() == 2)) {
            return component.stream().flatMap(p -> graph.get(p).values().stream()).mapToDouble(Double::doubleValue).sum() / 2;
        }
        return diameter;
    }

    private Map<Point, Double> shortestPaths(Point start) {
        Map<Point, Double> lengths = new HashMap<>();
        PriorityQueue<Pending> queue = new PriorityQueue<>(Comparator.comparingDouble(Pending::distance));
        lengths.put(start, 0.0);
        queue.add(new Pending(start, 0));
        while (!queue.isEmpty()) {
            Pending item = queue.remove();
            if (item.distance > lengths.get(item.point)) continue;
            for (var edge : graph.get(item.point).entrySet()) {
                double d = item.distance + edge.getValue();
                if (d < lengths.getOrDefault(edge.getKey(), Double.POSITIVE_INFINITY)) {
                    lengths.put(edge.getKey(), d);
                    queue.add(new Pending(edge.getKey(), d));
                }
            }
        }
        return lengths;
    }

    private boolean boundary(Point p) {
        return graph.get(p).size() != 2;
    }

    private void split(Set<Point> component, List<Segment> segments) {
        List<Point> starts = component.stream().filter(this::boundary).toList();
        if (starts.isEmpty()) starts = List.of(component.iterator().next());
        Set<Edge> used = new HashSet<>();
        for (Point start : starts) {
            for (Point next : graph.get(start).keySet()) {
                if (!used.add(Edge.of(start, next))) continue;
                List<Point> points = new ArrayList<>(List.of(start));
                double inside = 0, outside = 0;
                Point previous = start;
                Point p = next;
                while (true) {
                    double length = graph.get(previous).get(p);
                    if (info.get(previous).inside && info.get(p).inside) inside += length;
                    else if (!info.get(previous).inside && !info.get(p).inside) outside += length;
                    else { inside += length / 2; outside += length / 2; }
                    points.add(p);
                    if (p.equals(start) || boundary(p)) break;
                    Point before = previous;
                    Point after = graph.get(p).keySet().stream().filter(n -> !n.equals(before)).findFirst().orElseThrow();
                    if (!used.add(Edge.of(p, after))) break;
                    previous = p;
                    p = after;
                }
                Set<String> claims = new TreeSet<>(), ruins = new TreeSet<>();
                for (Point point : points) {
                    claims.addAll(info.get(point).claims);
                    ruins.addAll(info.get(point).ruins);
                }
                Point firstPt = points.get(0);
                Point lastPt = points.get(points.size() - 1);
                boolean firstIn = info.get(firstPt).inside;
                boolean lastIn = info.get(lastPt).inside;
                if (!firstIn && lastIn || (firstIn == lastIn && firstPt.compareTo(lastPt) > 0)) {
                    Collections.reverse(points);
                }
                String key = identity + "/" + kind + "/" + points;
                String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
                segments.add(new Segment(id, points, inside, outside, claims, ruins));
            }
        }
    }

    private static final int[][] RING = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

    /** Topology-preserving thinning removes paving width before counting distance and junctions. */
    private void thin() {
        Set<Point> pixels = new HashSet<>(graph.keySet());
        pixels.addAll(gapPoints);
        Set<Point> original = Set.copyOf(pixels);
        boolean changed;
        do {
            changed = false;
            for (int pass = 0; pass < 2; pass++) {
                List<Point> remove = new ArrayList<>();
                for (Point p : pixels) {
                    checkCancelled();
                    boolean[] n = new boolean[8];
                    for (int i = 0; i < 8; i++) n[i] = near(pixels, p, RING[i][0], RING[i][1]) != null;
                    int count = 0, transitions = 0;
                    for (int i = 0; i < 8; i++) {
                        if (n[i]) count++;
                        if (!n[i] && n[(i + 1) % 8]) transitions++;
                    }
                    if (count < 2 || count > 6 || transitions != 1) continue;
                    boolean first = pass == 0 ? n[0] && n[2] && n[4] : n[0] && n[2] && n[6];
                    boolean second = pass == 0 ? n[2] && n[4] && n[6] : n[0] && n[4] && n[6];
                    if (!first && !second) remove.add(p);
                }
                if (!remove.isEmpty()) {
                    pixels.removeAll(remove);
                    for (Point p : remove) {
                        for (int[] d : RING) {
                            Point survivor = near(pixels, p, d[0], d[1]);
                            if (survivor == null) continue;
                            Info a = info.get(survivor), b = info.get(p);
                            Set<String> claims = new HashSet<>(a.claims), ruins = new HashSet<>(a.ruins);
                            claims.addAll(b.claims);
                            ruins.addAll(b.ruins);
                            info.put(survivor, new Info(a.inside, Set.copyOf(claims), Set.copyOf(ruins)));
                            break;
                        }
                    }
                    changed = true;
                }
            }
        } while (changed);
        for (Point p : new TreeSet<>(pixels)) {
            List<Point> adjacent = adjacent(pixels, p);
            if (adjacent.size() != 1) continue;
            Point n = adjacent.get(0);
            int dx = p.x - n.x, dz = p.z - n.z;
            if (Math.abs(dx) + Math.abs(dz) != 1) continue;
            Point end = p;
            Point next;
            while ((next = near(original, end, dx, dz)) != null && !pixels.contains(next)) {
                pixels.add(next);
                end = next;
            }
        }
        Map<Point, Map<Point, Double>> skeleton = new TreeMap<>();
        for (Point p : pixels) {
            Map<Point, Double> links = new TreeMap<>();
            for (Point n : adjacent(pixels, p)) links.put(n, p.distance(n));
            skeleton.put(p, links);
        }
        graph.clear();
        graph.putAll(skeleton);
    }

    private static List<Point> adjacent(Set<Point> pixels, Point p) {
        List<Point> neighbors = new ArrayList<>();
        for (int[] d : RING) {
            Point n = near(pixels, p, d[0], d[1]);
            if (n == null) continue;
            if (d[0] != 0 && d[1] != 0
                    && (near(pixels, p, d[0], 0) != null || near(pixels, p, 0, d[1]) != null)) continue;
            neighbors.add(n);
        }
        return neighbors;
    }

    private static Point near(Set<Point> points, Point p, int dx, int dz) {
        for (int dy : new int[]{0, -1, 1}) {
            Point q = p.offset(dx, dy, dz);
            if (points.contains(q)) return q;
        }
        return null;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Survey stopped");
    }
}
