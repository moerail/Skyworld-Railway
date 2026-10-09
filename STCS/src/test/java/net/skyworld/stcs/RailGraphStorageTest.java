package net.skyworld.stcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public final class RailGraphStorageTest {
    private RailGraphStorageTest() { }

    public static void main(String[] args) {
        List<RailGraph.Point> straight = new ArrayList<>();
        for (int x = 0; x <= 20; x++) straight.add(new RailGraph.Point("world", x, 64, 0, x));
        List<RailGraph.Point> corner = List.of(
                new RailGraph.Point("world", 20, 64, 0, 0),
                new RailGraph.Point("world", 21, 64, 0, 1),
                new RailGraph.Point("world", 21, 64, 1, 2));
        List<RailGraph.Point> slope = List.of(
                new RailGraph.Point("world", 21, 64, 1, 0),
                new RailGraph.Point("world", 22, 65, 1, Math.sqrt(2)));
        RailGraph graph = new RailGraph(12, 256, 1,
                List.of(node("a", 0, 64, 0), node("b", 20, 64, 0),
                        node("c", 21, 64, 1), node("d", 22, 65, 1)),
                List.of(edge("straight", "a", "b", straight), edge("corner", "b", "c", corner),
                        edge("slope", "c", "d", slope)), List.of(), Map.of());
        JsonObject encoded = RailGraphStorage.encode(graph);
        check(encoded.get("schemaVersion").getAsInt() == 4, "schema version");
        check(new Gson().toJsonTree(graph).getAsJsonObject().get("schemaVersion").getAsInt() == 3,
                "public graph format remains compatible");
        check(encoded.getAsJsonArray("edges").get(0).getAsJsonObject().has("straightPath"), "straight compressed");
        check(!encoded.getAsJsonArray("edges").get(0).getAsJsonObject().has("path"), "no duplicate path");
        check(encoded.getAsJsonArray("edges").get(1).getAsJsonObject().has("path"), "corner retained");
        check(encoded.getAsJsonArray("edges").get(2).getAsJsonObject().has("path"), "slope retained");
        check(encoded.toString().length() < new Gson().toJson(graph).length(), "straight path reduces storage");
        RailGraphStorage.Decoded decoded = RailGraphStorage.decode(encoded.toString());
        check(!decoded.legacy(), "new graph");
        for (int i = 0; i < graph.edges.size(); i++)
            check(graph.edges.get(i).path().equals(decoded.graph().edges.get(i).path()), "lossless edge " + i);
        RailGraph restored = new RailGraph(decoded.graph().revision, decoded.graph().maxScanDistanceMeters,
                decoded.graph().blocksPerMeter, decoded.graph().nodes, decoded.graph().edges,
                decoded.graph().unresolved, decoded.graph().lineDefinitions);
        check(restored.query("world", 10, 64, 0, 1, 0, 0).get("edgeId").equals("straight"), "block query");

        JsonObject old = new Gson().toJsonTree(graph).getAsJsonObject();
        old.addProperty("schemaVersion", 3);
        RailGraphStorage.Decoded legacy = RailGraphStorage.decode(old.toString());
        check(legacy.legacy(), "old graph detected");
        check(legacy.graph().edges.get(0).path().equals(straight), "old path loaded");

        JsonObject invalid = RailGraphStorage.encode(graph);
        invalid.getAsJsonArray("edges").get(0).getAsJsonObject()
                .getAsJsonObject("straightPath").addProperty("dx", 2);
        try {
            RailGraphStorage.decode(invalid.toString());
            throw new AssertionError("invalid path accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static RailGraph.Node node(String id, int x, int y, int z) {
        return new RailGraph.Node(id, "balise", id, "", null,
                new RailGraph.Position("world", x, y, z), Map.of(), List.of(), null, null, null);
    }

    private static RailGraph.Edge edge(String id, String from, String to, List<RailGraph.Point> path) {
        return new RailGraph.Edge(id, from, to, "east", "west", path.getLast().distanceMeters(), path);
    }

    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
    }
}
