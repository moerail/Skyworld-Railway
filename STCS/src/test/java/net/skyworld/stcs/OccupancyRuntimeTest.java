package net.skyworld.stcs;

import java.nio.file.Files;
import java.util.*;

public final class OccupancyRuntimeTest {
    public static void main(String[] args) throws Exception {
        var path = List.of(new RailGraph.Point("world", -5, 64, 0, 0),
                new RailGraph.Point("world", -4, 64, 0, 1), new RailGraph.Point("world", -3, 64, 0, 2));
        var reverse = new ArrayList<>(path); Collections.reverse(reverse);
        var graph = new RailGraph(1, 256, 1, List.of(), List.of(
                new RailGraph.Edge("forward", "a", "b", "east", "west", 2, path),
                new RailGraph.Edge("reverse", "b", "a", "west", "east", 2, reverse)), List.of());
        var index = new PhysicalResources(graph);
        var footprint = index.candidates("WORLD", -4.5, 64.06, 0.5);
        assert footprint.size() == 3 : footprint;
        assert footprint.contains(PhysicalResources.key("world", -3, 64, 0));
        assert index.candidates("other", -4.5, 64, 0.5).isEmpty();
        assert index.candidates("world", -4.5, 64, 8).isEmpty();
        var newGraph = new RailGraph(2, 256, 1, List.of(), List.of(
                new RailGraph.Edge("new-id", "b", "a", "west", "east", 2, reverse)), List.of());
        assert footprint.equals(new PhysicalResources(newGraph).candidates("world", -4.5, 64.06, 0.5));
        var file = Files.createTempDirectory("m1-runtime-test").resolve("ledger.json");
        try {
            UUID train = UUID.randomUUID();
            var ledger = new ProtectionLedger(file);
            OccupancyAccumulator.retain(ledger, train, footprint);
            byte[] bytes = Files.readAllBytes(file);
            OccupancyAccumulator.retain(ledger, train, footprint);
            assert Arrays.equals(bytes, Files.readAllBytes(file)) : "unchanged polls must not rewrite";
            OccupancyAccumulator.retain(ledger, train,
                    new PhysicalResources(RailGraph.empty(256, 1)).candidates("world", -4.5, 64, 0.5));
            assert ledger.snapshot().get(train).occupied().equals(footprint) : "graph loss cleared occupancy";
            assert ledger.snapshot().get(train).quality() == ProtectionLedger.Quality.UNCERTAIN;
            assert !ledger.reserve(train, ledger.snapshot().get(train).sequence(), footprint, Map.of());
            var restored = new ProtectionLedger(file);
            OccupancyAccumulator.retain(restored, train, Set.of());
            assert restored.snapshot().get(train).quality() == ProtectionLedger.Quality.RECOVERING;
            assert restored.snapshot().get(train).occupied().equals(footprint);
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.getParent()); }
    }
}
