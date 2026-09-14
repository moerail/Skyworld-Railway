package net.skyworld.stcs;

import java.util.Map;

public final class TrainPositionRegistryTest {
    private TrainPositionRegistryTest() {
    }

    public static void main(String[] args) {
        TrainPositionRegistry registry = new TrainPositionRegistry(15_000L, 60_000L);
        registry.report(Map.of(
                "trainId", "train-1",
                "name", "Local 01",
                "edgeId", "a:east:b:west",
                "graphRevision", 12L,
                "edgeOffsetMeters", 25.0,
                "speedMetersPerSecond", 8.0), 12L, 1_000L);

        Map<String, Object> fresh = registry.positions(12L, 5_000L).getFirst();
        require(Boolean.FALSE.equals(fresh.get("stale")), "fresh position");
        require(Boolean.TRUE.equals(fresh.get("graphCurrent")), "matching graph revision");

        Map<String, Object> stale = registry.positions(13L, 20_000L).getFirst();
        require(Boolean.TRUE.equals(stale.get("stale")), "stale position");
        require(Boolean.FALSE.equals(stale.get("graphCurrent")), "outdated graph revision");

        require(registry.positions(13L, 61_000L).isEmpty(), "expired position removed");
        System.out.println("TrainPositionRegistryTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
