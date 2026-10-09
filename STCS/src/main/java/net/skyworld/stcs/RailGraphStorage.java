package net.skyworld.stcs;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Disk-only geometry codec. The public graph JSON retains its full path. */
final class RailGraphStorage {
    static final int SCHEMA_VERSION = 4;
    private static final Gson GSON = new Gson();

    private RailGraphStorage() { }

    static JsonObject encode(RailGraph graph) {
        JsonObject root = GSON.toJsonTree(graph).getAsJsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray edges = root.getAsJsonArray("edges");
        for (int i = 0; i < graph.edges.size(); i++) {
            RailGraph.Edge edge = graph.edges.get(i);
            JsonObject straight = straightPath(edge.path(), graph.blocksPerMeter);
            if (straight == null) continue;
            JsonObject encoded = edges.get(i).getAsJsonObject();
            encoded.remove("path");
            encoded.add("straightPath", straight);
        }
        return root;
    }

    static Decoded decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("schemaVersion")) throw new IllegalArgumentException("Missing RailGraph schemaVersion");
        int version = root.get("schemaVersion").getAsInt();
        if (version < 2 || version > SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported RailGraph schemaVersion " + version);
        if (version == SCHEMA_VERSION) {
            double blocksPerMeter = root.get("blocksPerMeter").getAsDouble();
            for (JsonElement element : root.getAsJsonArray("edges")) {
                JsonObject edge = element.getAsJsonObject();
                if (!edge.has("straightPath")) continue;
                if (edge.has("path")) throw new IllegalArgumentException("Edge has two path representations");
                edge.add("path", expand(edge.getAsJsonObject("straightPath"), blocksPerMeter));
                edge.remove("straightPath");
            }
        }
        RailGraph graph = GSON.fromJson(root, RailGraph.class);
        if (graph == null || graph.nodes == null || graph.edges == null || graph.unresolved == null)
            throw new IllegalArgumentException("Incomplete RailGraph");
        return new Decoded(graph, version < SCHEMA_VERSION);
    }

    private static JsonObject straightPath(List<RailGraph.Point> path, double blocksPerMeter) {
        if (path == null || path.size() < 2 || !Double.isFinite(blocksPerMeter) || blocksPerMeter <= 0) return null;
        RailGraph.Point first = path.getFirst();
        RailGraph.Point second = path.get(1);
        int dx = second.x() - first.x(), dy = second.y() - first.y(), dz = second.z() - first.z();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) return null;
        double stepMeters = 1.0 / blocksPerMeter;
        for (int i = 1; i < path.size(); i++) {
            RailGraph.Point before = path.get(i - 1), point = path.get(i);
            if (!first.world().equals(point.world()) || point.x() - before.x() != dx
                    || point.y() - before.y() != dy || point.z() - before.z() != dz
                    || Double.compare(point.distanceMeters(), before.distanceMeters() + stepMeters) != 0) return null;
        }
        JsonObject encoded = new JsonObject();
        encoded.addProperty("world", first.world());
        encoded.addProperty("x", first.x());
        encoded.addProperty("y", first.y());
        encoded.addProperty("z", first.z());
        encoded.addProperty("distanceMeters", first.distanceMeters());
        encoded.addProperty("dx", dx);
        encoded.addProperty("dy", dy);
        encoded.addProperty("dz", dz);
        encoded.addProperty("steps", path.size() - 1);
        return encoded;
    }

    private static JsonArray expand(JsonObject encoded, double blocksPerMeter) {
        if (!Double.isFinite(blocksPerMeter) || blocksPerMeter <= 0) throw new IllegalArgumentException("Invalid RailGraph scale");
        String world = encoded.get("world").getAsString();
        int x = encoded.get("x").getAsInt(), y = encoded.get("y").getAsInt(), z = encoded.get("z").getAsInt();
        int dx = encoded.get("dx").getAsInt(), dy = encoded.get("dy").getAsInt(), dz = encoded.get("dz").getAsInt();
        int steps = encoded.get("steps").getAsInt();
        double distance = encoded.get("distanceMeters").getAsDouble();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1 || steps < 1 || steps > 1_000_000
                || !Double.isFinite(distance)) throw new IllegalArgumentException("Invalid straight path");
        List<RailGraph.Point> points = new ArrayList<>(steps + 1);
        points.add(new RailGraph.Point(world, x, y, z, distance));
        double stepMeters = 1.0 / blocksPerMeter;
        for (int i = 1; i <= steps; i++) {
            distance += stepMeters;
            points.add(new RailGraph.Point(world, Math.addExact(x, Math.multiplyExact(i, dx)),
                    Math.addExact(y, Math.multiplyExact(i, dy)), Math.addExact(z, Math.multiplyExact(i, dz)), distance));
        }
        return GSON.toJsonTree(points).getAsJsonArray();
    }

    record Decoded(RailGraph graph, boolean legacy) { }
}
