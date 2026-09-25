package net.skyworld.sta.api.v6;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OperationalAuthorityContractTest {
    public static void main(String[] args) {
        var path = new ArrayList<OperationalAuthorityService.Segment>();
        path.add(new OperationalAuthorityService.Segment("edge", 0, 10));
        var grant = new OperationalAuthorityService.Grant(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "SR", false, 2, "edge", 10, 10,
                40, path, 1003, 1015);
        path.clear();
        assert grant.path().size() == 1;
        rejects(() -> new OperationalAuthorityService.Grant(grant.id(), grant.trainId(),
                grant.driverLeaseId(), grant.telemetrySession(), 1, "FS", true, 2,
                "edge", 10, 10, 40, List.of(new OperationalAuthorityService.Segment("edge", 0, 10)),
                3, 15));
        var snapshot = new OperationalAuthorityService.Snapshot(6, UUID.randomUUID(), 1,
                1000, 2, "AVAILABLE", List.of(grant), List.of());
        assert snapshot.grants().size() == 1 && !snapshot.grants().getFirst().executable();
        System.out.println("STA v6 private message IDs and immutable operational snapshots passed");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid operational authority to be rejected");
    }
}
