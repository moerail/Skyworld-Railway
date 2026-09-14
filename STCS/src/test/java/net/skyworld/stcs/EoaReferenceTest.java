package net.skyworld.stcs;

import java.util.List;
import java.util.Map;

public final class EoaReferenceTest {
    static RailGraph.Node node(String id, String type, String line, int x) {
        var p = new RailGraph.Position("world", x, 64, 0);
        return new RailGraph.Node(id, type, id, line, p, p, Map.of(), List.of(), null, null, null);
    }
    static RailGraph.Edge edge(String id, String from, String to, double length) {
        return new RailGraph.Edge(id, from, to, id.equals("ba") ? "west" : "east",
                id.equals("ba") ? "east" : "west", length, List.of());
    }
    public static void main(String[] args) {
        var graph = new RailGraph(42, 256, 1,
                List.of(node("a", "origin", "A", 0), node("b", "balise", "A", 100),
                        node("c", "origin", "B", 200)),
                List.of(edge("ab", "a", "b", 100), edge("ba", "b", "a", 100),
                        edge("bc", "b", "c", 100)), List.of());
        assert graph.edgePosition(42, "ab", 25).mileageMeters() == 25;
        assert graph.edgePosition(42, "ba", 25).mileageMeters() == 75 : "Reverse edges decrease mileage";
        assert graph.edgePosition(42, "bc", 0).line().equals("A");
        assert graph.edgePosition(42, "bc", 100).line().equals("B");
        assert graph.edgePosition(42, "bc", 50).mileageMeters() == null : "Do not interpolate across line origins";
        assert graph.edgePosition(43, "ab", 25) == null;
        assert graph.edgePosition(42, "missing", 25) == null;
        assert graph.edgePosition(42, null, 25) == null;
        assert graph.edgePosition(42, "ab", -1) == null;
        assert graph.edgePosition(42, "ab", 101) == null;
        assert graph.edgePosition(42, "ab", Double.NaN) == null;
        assert graph.edgePosition(42, "ab", Double.POSITIVE_INFINITY) == null;
        assert graph.edgePosition(42, "ab", 100).mileageMeters() == 100;
        System.out.println("PASS EoA edge references: revision, reverse mileage, line boundaries, invalid offsets");
    }
}
