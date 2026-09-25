package net.skyworld.stcs;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SrRouteTest {
    public static void main(String[] args) {
        var a = ShadowPlannerTest.node("a", "balise", 0, 0);
        var b = ShadowPlannerTest.node("b", "balise", 100, 0);
        var c = ShadowPlannerTest.node("c", "balise", 200, 0);
        var target = ShadowPlannerTest.node("target", "end", 300, 0);
        var ab = ShadowPlannerTest.edge(a, b, "east", "west");
        var bc = ShadowPlannerTest.edge(b, c, "east", "west");
        var ct = ShadowPlannerTest.edge(c, target, "east", "west");
        var graph = ShadowPlannerTest.graph(List.of(a, b, c, target), List.of(ab, bc, ct));
        var start = new ShadowGraph.Start(ab.id(), 20);
        var route = SrRoute.find(graph, start, target.id(), 5000);
        assert route.reachable() && route.edges().equals(List.of(ab.id(), bc.id(), ct.id()));
        assert route.distanceMeters() == 280;
        var local = ShadowPlanner.plan(graph, ab.id(), 20, 10, 120, 0, Map.of(), Set.of());
        assert local.remaining() != null && local.remaining() > 0 && local.remaining() <= 120;
        assert local.path().stream().map(p -> p.edgeId()).toList().equals(List.of(ab.id(), bc.id()));
        assert SrRoute.find(graph, start, target.id(), 5000,
                local.path().stream().map(p -> p.edgeId()).toList()).reachable();
        assert SrRoute.find(graph, start, c.id(), 5000, List.of(ab.id(), bc.id())).reachable();
        assert SrRoute.find(graph, start, target.id(), 200).reason().equals("SR_TARGET_TOO_FAR");

        var branch = ShadowPlannerTest.node("branch", "end", 200, 100);
        var sb = ShadowPlannerTest.edge(b, branch, "south", "north");
        var junction = ShadowPlannerTest.graph(List.of(a, b, c, target, branch), List.of(ab, bc, ct, sb));
        assert SrRoute.find(junction, start, target.id(), 5000, List.of(ab.id(), sb.id()))
                .reason().equals("TARGET_UNREACHABLE");

        var gap = new ShadowGraph(new RailGraph(1, 256, 1, List.of(a, b, c, target),
                List.of(ab, bc, ct), List.of(new RailGraph.Issue("c", "east", "chunk_not_loaded", 0))));
        assert SrRoute.find(gap, start, target.id(), 5000).reason().equals("GRAPH_GAP");
        assert SrRoute.find(ShadowPlannerTest.graph(List.of(a, b, c, target), List.of(ab, bc)),
                start, target.id(), 5000).reason().equals("TARGET_UNREACHABLE");
        System.out.println("SR target topology, local grant window, branch and graph-gap checks passed");
    }
}
