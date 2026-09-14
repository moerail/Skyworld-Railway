package net.skyworld.stcs;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Physical rail cells, independent of graph edge IDs, scan direction and revision. */
final class PhysicalResources {
    private final Map<String, List<Set<String>>> index = new HashMap<>();
    PhysicalResources(RailGraph graph) {
        for (var edge : graph.edges) {
            Set<String> cells = new HashSet<>();
            for (var p : edge.path()) cells.add(key(p.world(), p.x(), p.y(), p.z()));
            Set<String> footprint = Set.copyOf(cells);
            for (String cell : cells) index.computeIfAbsent(cell, unused -> new ArrayList<>()).add(footprint);
        }
        for (var node : graph.nodes) if (node.rail() != null) {
            var p = node.rail();
            String cell = key(p.world(), p.x(), p.y(), p.z());
            index.computeIfAbsent(cell, unused -> new ArrayList<>()).add(Set.of(cell));
        }
    }
    static String key(String world, int x, int y, int z) {
        return "rail:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                world.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)) + ":" + x + ":" + y + ":" + z;
    }
    /** Candidate footprints, not exact localisation or integrity proof. Include adjacent ambiguity. */
    Set<String> candidates(String world, double x, double y, double z) {
        Set<String> result = new HashSet<>();
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            for (var footprint : index.getOrDefault(key(world, bx + dx, by + dy, bz + dz), List.of()))
                result.addAll(footprint);
        return Set.copyOf(result);
    }
}
