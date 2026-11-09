package com.falazar.farmupcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;

/** Persistent discovery ledger and per-member inbox, independent of changing segment IDs. */
public record RouteDiscovery(Set<Point> known, Set<String> connections, Map<String, List<String>> pending) {
    private static final Gson GSON = new Gson();
    public RouteDiscovery {
        known = Set.copyOf(known);
        connections = Set.copyOf(connections);
        Map<String, List<String>> copy = new TreeMap<>();
        pending.forEach((id, messages) -> copy.put(id, List.copyOf(messages)));
        pending = Map.copyOf(copy);
    }
    public static RouteDiscovery empty() { return new RouteDiscovery(Set.of(), Set.of(), Map.of()); }
    public record Update(RouteDiscovery state, int additions, int newConnections) {}

    public Update discover(Result result, String villageName, Kind kind, Collection<String> members, long now) {
        Set<Point> geometry = new HashSet<>();
        Set<String> contacts = new TreeSet<>();
        for (Segment s : result.segments()) {
            geometry.addAll(s.points());
            contacts.addAll(s.claims());
            for (String ruin : s.ruins()) contacts.add("ruin:" + ruin);
        }
        Set<Point> added = new HashSet<>();
        for (Point p : geometry) {
            boolean old = false;
            // A one-block centreline shift or re-segmentation is not a new road.
            for (int dx = -1; dx <= 1 && !old; dx++)
                for (int dy = -1; dy <= 1 && !old; dy++)
                    for (int dz = -1; dz <= 1 && !old; dz++) old = known.contains(p.offset(dx, dy, dz));
            if (!old) added.add(p);
        }
        int additions = 0;
        while (!added.isEmpty()) {
            Deque<Point> queue = new ArrayDeque<>();
            Point first = added.iterator().next();
            added.remove(first);
            queue.add(first);
            int count = 0;
            while (!queue.isEmpty()) {
                Point p = queue.removeFirst();
                count++;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Point n = p.offset(dx, dy, dz);
                    if (added.remove(n)) queue.addLast(n);
                }
            }
            if (count >= 6) additions++;
        }
        Set<String> newContacts = new HashSet<>(contacts);
        newContacts.removeAll(connections);
        Map<String, List<String>> inboxes = new HashMap<>(pending);
        inboxes.keySet().retainAll(new HashSet<>(members));
        if (additions > 0 || !newContacts.isEmpty()) {
            String message = "[" + villageName + "] New " + (kind == Kind.ROAD ? "road" : "railway")
                    + " discovery: " + additions + " new route(s)/extension(s), " + newContacts.size()
                    + " new connection(s). Surveyed total: " + Math.round(result.length()) + " blocks"
                    + (result.complete() ? "." : " (partial survey).")
                    + " Checked " + java.time.Instant.ofEpochMilli(now) + ". Use /village "
                    + (kind == Kind.ROAD ? "roads" : "railroads") + " for details.";
            for (String member : new TreeSet<>(members)) {
                List<String> messages = new ArrayList<>(inboxes.getOrDefault(member, List.of()));
                messages.add(message);
                inboxes.put(member, messages);
            }
        }
        Set<Point> all = new HashSet<>(known);
        all.addAll(geometry);
        Set<String> allContacts = new HashSet<>(connections);
        allContacts.addAll(contacts);
        return new Update(new RouteDiscovery(all, allContacts, inboxes), additions, newContacts.size());
    }

    public RouteDiscovery delivered(String member) {
        Map<String, List<String>> inboxes = new HashMap<>(pending);
        inboxes.remove(member);
        return new RouteDiscovery(known, connections, inboxes);
    }

    public static List<String> encode(RouteDiscovery state) {
        String json = GSON.toJson(state);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < json.length(); i += 16000) chunks.add(json.substring(i, Math.min(json.length(), i + 16000)));
        return chunks;
    }
    public static RouteDiscovery decode(List<String> chunks) {
        RouteDiscovery state = GSON.fromJson(String.join("", chunks), RouteDiscovery.class);
        if (state == null) throw new JsonParseException("Missing route discovery state");
        return state;
    }
}
