package net.skyworld.stcs;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RailGraphSelfTest {
    private RailGraphSelfTest() {
    }

    public static void main(String[] args) {
        RailGraph.Node origin = node("origin", "origin", "ORIGIN", 0);
        RailGraph.Node balise = node("balise", "balise", "BG0001", 10);
        RailGraph.Node signal = node("signal", "signal", "S01", 20);
        RailGraph.Edge first = edge("origin", "balise", 0, 10);
        RailGraph.Edge second = edge("balise", "signal", 10, 20);
        RailGraph.Edge reverse = new RailGraph.Edge("reverse", "balise", "origin", "west", "east", 10.0,
                java.util.stream.IntStream.iterate(10, x -> x >= 0, x -> x - 1)
                        .mapToObj(x -> point(x, 10 - x)).toList());
        RailGraph graph = new RailGraph(7L, 256.0, 1.0,
                List.of(origin, balise, signal), List.of(first, second, reverse), List.of());

        Map<String, Object> eastbound = graph.query("world", 5, 64, 0, 1.0, 0.0, 0.0);
        require("BG0001".equals(eastbound.get("nextMarkerName")), "eastbound target");
        require(Math.abs(((Number) eastbound.get("distanceMeters")).doubleValue() - 5.0) < 0.001,
                "eastbound distance");
        require(Math.abs(((Number) eastbound.get("currentMileageMeters")).doubleValue() - 5.0) < 0.001,
                "current mileage");
        require("origin-balise".equals(eastbound.get("edgeId")), "directed edge id");
        require(Math.abs(((Number) eastbound.get("edgeOffsetMeters")).doubleValue() - 5.0) < 0.001,
                "edge offset");
        require(Math.abs(((Number) eastbound.get("edgeLengthMeters")).doubleValue() - 10.0) < 0.001,
                "edge length");

        Map<String, Object> atBalise = graph.query("world", 10, 64, 0, 1.0, 0.0, 0.0);
        require("S01".equals(atBalise.get("nextMarkerName")), "target handover at marker rail");

        Map<String, Object> westbound = graph.query("world", 5, 64, 0, -1.0, 0.0, 0.0);
        require("ORIGIN".equals(westbound.get("nextMarkerName")), "westbound target");

        RailGraph.Node railwaySwitch = new RailGraph.Node("switch", "switch", "SW-001", "", null,
                new RailGraph.Position("world", 10, 64, 0),
                Map.of("common", "west", "straight", "east", "diverging", "south"),
                List.of("common>straight", "common>diverging", "straight>common", "diverging>common"),
                "straight", null, null);
        RailGraph.Node afterSwitch = node("after", "balise", "BG0002", 20);
        RailGraph.Node afterDiverging = new RailGraph.Node("after-diverging", "balise", "BG1002",
                "line_a:branch", null, new RailGraph.Position("world", 10, 64, 10),
                Map.of(), List.of(), null, null, null);
        RailGraph.Edge toSwitch = new RailGraph.Edge("to-switch", "origin", "switch",
                "east", "common", 10.0,
                java.util.stream.IntStream.rangeClosed(0, 10).mapToObj(x -> point(x, x)).toList());
        RailGraph.Edge fromSwitch = new RailGraph.Edge("from-switch", "switch", "after",
                "straight", "west", 10.0,
                java.util.stream.IntStream.rangeClosed(10, 20).mapToObj(x -> point(x, x - 10)).toList());
        RailGraph.Edge divergingFromSwitch = new RailGraph.Edge("diverging-from-switch", "switch",
                "after-diverging", "diverging", "north", 10.0,
                java.util.stream.IntStream.rangeClosed(0, 10)
                        .mapToObj(z -> new RailGraph.Point("world", 10, 64, z, z)).toList());
        RailGraph switchGraph = new RailGraph(8L, 256.0, 1.0,
                List.of(origin, railwaySwitch, afterSwitch, afterDiverging),
                List.of(toSwitch, fromSwitch, divergingFromSwitch), List.of());
        Map<String, Object> beforeSwitch = switchGraph.query("world", 5, 64, 0, 1.0, 0.0, 0.0);
        require("BG0002".equals(beforeSwitch.get("nextMarkerName")), "switch traversal target");
        require(Math.abs(((Number) beforeSwitch.get("distanceMeters")).doubleValue() - 15.0) < 0.001,
                "switch traversal distance");
        require("switch".equals(beforeSwitch.get("nextSwitchId")), "switch infrastructure target");
        Map<String, Object> liveDiverging = switchGraph.query(
                "world", 5, 64, 0, 1.0, 0.0, 0.0, ignored -> "diverging");
        require("BG1002".equals(liveDiverging.get("nextMarkerName")), "live diverging switch target");
        Map<String, Object> liveStraight = switchGraph.query(
                "world", 5, 64, 0, 1.0, 0.0, 0.0, ignored -> "straight");
        require("BG0002".equals(liveStraight.get("nextMarkerName")), "live straight switch target");

        RailGraph.Node sidingBalise = blankNode("siding", "balise", "SD01", 30);
        RailGraph.Edge namedToSwitch = edge("balise", "switch", 10, 20);
        RailGraph.Edge switchToSiding = edge("switch", "siding", 20, 30);
        RailGraph sidingGraph = new RailGraph(9L, 256.0, 1.0,
                List.of(origin, balise, railwaySwitch, sidingBalise),
                List.of(first, namedToSwitch, switchToSiding), List.of());
        RailGraph.Node resolvedSiding = sidingGraph.nodes.stream()
                .filter(node -> node.id().equals("siding")).findFirst().orElseThrow();
        require(resolvedSiding.line().isBlank(), "unanchored siding must not inherit main line");
        require(resolvedSiding.lineReferenceId() == null && resolvedSiding.mileageMeters() == null,
                "unanchored siding must not invent main line mileage");
        Map<String, Object> distantSwitch = sidingGraph.query("world", 5, 64, 0, 1.0, 0.0, 0.0);
        require("SW-001".equals(distantSwitch.get("nextSwitchName")), "next switch beyond balise");
        require(Math.abs(((Number) distantSwitch.get("distanceToSwitchMeters")).doubleValue() - 15.0) < 0.001,
                "next switch accumulated distance");

        RailGraph.Node isolatedSwitch = blankNode("isolated-switch", "switch", "Y01", 0);
        RailGraph.Node isolatedBalise = blankNode("isolated-balise", "balise", "SD02", 5);
        RailGraph isolatedGraph = new RailGraph(10L, 256.0, 1.0,
                List.of(isolatedSwitch, isolatedBalise), List.of(edge("isolated-switch", "isolated-balise", 0, 5)),
                List.of());
        RailGraph.Node fallbackSiding = isolatedGraph.nodes.stream()
                .filter(node -> node.id().equals("isolated-balise")).findFirst().orElseThrow();
        require(fallbackSiding.lineReferenceId() == null && fallbackSiding.mileageMeters() == null,
                "an isolated switch is not a mileage origin");

        RailGraph.Node station = blankNode("station", "station", "STATION@12345678", 15);
        RailGraph stationGraph = new RailGraph(11L, 256.0, 1.0,
                List.of(origin, balise, station, signal),
                List.of(first, edge("balise", "station", 10, 15), edge("station", "signal", 15, 20)),
                List.of());
        RailGraph.Node resolvedStation = stationGraph.nodes.stream()
                .filter(node -> node.id().equals("station")).findFirst().orElseThrow();
        require("line_a:up".equals(resolvedStation.line()), "station inherited line");
        require(Math.abs(resolvedStation.mileageMeters() - 15.0) < 0.001, "station inherited mileage");

        RailGraph.Edge retained = edge("origin", "balise", 0, 10);
        List<RailGraph.Edge> partial = RailGraphManager.preserveIncompleteEdges(
                List.of(retained), List.of(),
                List.of(new RailGraph.Issue("origin", "east", "chunk_not_loaded", 4.0)),
                Set.of("origin", "balise"));
        require(partial.size() == 1 && partial.getFirst().id().equals(retained.id()),
                "retain edge across unloaded chunk");
        List<RailGraph.Edge> confirmedMissing = RailGraphManager.preserveIncompleteEdges(
                List.of(retained), List.of(),
                List.of(new RailGraph.Issue("origin", "east", "rail_ended", 4.0)),
                Set.of("origin", "balise"));
        require(confirmedMissing.isEmpty(), "remove edge after loaded rail end");
        java.util.concurrent.atomic.AtomicLong restarted = new java.util.concurrent.atomic.AtomicLong();
        require(RailGraphManager.nextBuildId(restarted, 150) == 151, "revision continues after restart");
        require(RailGraphManager.nextBuildId(restarted, 150) == 152, "overlapping requests have unique revisions");
        require(!RailGraphManager.currentExport(151, 152), "old asynchronous export cannot overwrite newer graph");
        require(RailGraphManager.currentExport(152, 152), "current graph can be exported");
        for (String reason : List.of("source_schedule_failed", "scan_schedule_failed", "world_not_loaded", "chunk_not_loaded")) {
            require(RailGraphManager.preserveIncompleteEdges(List.of(retained), List.of(),
                    List.of(new RailGraph.Issue("origin", "none", reason, 0)), Set.of("origin", "balise")).size() == 1,
                    "unavailable source preserves topology: " + reason);
        }
        System.out.println("RailGraphSelfTest passed");
    }

    private static RailGraph.Node node(String id, String type, String name, int x) {
        RailGraph.Position position = new RailGraph.Position("world", x, 64, 0);
        return new RailGraph.Node(id, type, name, "line_a:up", position, position,
                Map.of(), List.of(), null, null, null);
    }

    private static RailGraph.Node blankNode(String id, String type, String name, int x) {
        RailGraph.Position position = new RailGraph.Position("world", x, 64, 0);
        return new RailGraph.Node(id, type, name, "", null, position,
                Map.of(), List.of(), "switch".equals(type) ? "straight" : null, null, null);
    }

    private static RailGraph.Edge edge(String from, String to, int start, int end) {
        return new RailGraph.Edge(from + '-' + to, from, to, "east", "west", end - start,
                java.util.stream.IntStream.rangeClosed(start, end)
                        .mapToObj(x -> point(x, x - start)).toList());
    }

    private static RailGraph.Point point(int x, double distance) {
        return new RailGraph.Point("world", x, 64, 0, distance);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
