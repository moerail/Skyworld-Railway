package net.skyworld.stcs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Topological SR destination check; it never proves occupancy or physical point alignment. */
final class SrRoute {
    record Result(List<String> edges, double distanceMeters, String reason) {
        boolean reachable() { return reason.equals("REACHABLE"); }
    }
    private record Candidate(RailGraph.Edge edge, double distance, List<String> path) { }

    static Result find(ShadowGraph graph, ShadowGraph.Start start, String target, double maxDistance) {
        return find(graph, start, target, maxDistance, List.of());
    }

    static Result find(ShadowGraph graph, ShadowGraph.Start start, String target, double maxDistance,
            List<String> requiredPrefix) {
        var first = graph.edges.get(start.edge());
        if (first == null || start.offset() < 0 || start.offset() > first.distanceMeters())
            return new Result(List.of(), 0, "POSITION_UNCERTAIN");
        if (graph.unresolved.contains(target) || graph.unresolved.contains(first.from()))
            return new Result(List.of(), 0, "GRAPH_GAP");
        if (!requiredPrefix.isEmpty() && !first.id().equals(requiredPrefix.getFirst()))
            return new Result(List.of(), 0, "ROUTE_MISMATCH");
        var queue = new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::distance));
        queue.add(new Candidate(first, first.distanceMeters() - start.offset(), List.of(first.id())));
        Map<String, Double> best = new HashMap<>();
        boolean graphGap = false, tooFar = false;
        int explored = 0;
        while (!queue.isEmpty() && explored++ < 4096) {
            var current = queue.remove();
            if (current.distance() > maxDistance + 1e-6) { tooFar = true; continue; }
            if (current.distance() >= best.getOrDefault(current.edge().id(), Double.POSITIVE_INFINITY)) continue;
            best.put(current.edge().id(), current.distance());
            if (current.edge().to().equals(target) && current.path().size() >= requiredPrefix.size())
                return new Result(current.path(), current.distance(), "REACHABLE");
            if (current.path().size() >= 256) { tooFar = true; continue; }
            var node = graph.nodes.get(current.edge().to());
            if (node == null || graph.unresolved.contains(node.id())) { graphGap = true; continue; }
            for (var next : graph.outgoing.getOrDefault(node.id(), List.of())) {
                if (next.sourcePort().equals(current.edge().targetPort())) continue;
                if (current.path().size() < requiredPrefix.size()
                        && !next.id().equals(requiredPrefix.get(current.path().size()))) continue;
                if ("switch".equals(node.type()) && (node.allowedTransitions() == null
                        || !node.allowedTransitions().contains(current.edge().targetPort() + ">" + next.sourcePort())))
                    continue;
                double distance = current.distance() + next.distanceMeters();
                if (distance >= best.getOrDefault(next.id(), Double.POSITIVE_INFINITY)) continue;
                var path = new ArrayList<>(current.path());
                path.add(next.id());
                queue.add(new Candidate(next, distance, List.copyOf(path)));
            }
        }
        return new Result(List.of(), 0, graphGap ? "GRAPH_GAP" : tooFar ? "SR_TARGET_TOO_FAR" : "TARGET_UNREACHABLE");
    }
}
