package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;
import net.skyworld.sta.api.v1.TrainMode;
import net.skyworld.sta.api.v1.TrainTelemetrySnapshot;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

public final class StaOperationalIntegrationTest {
    public static void main(String[] args) {
        UUID train = UUID.randomUUID(), lease = UUID.randomUUID(), session = UUID.randomUUID();
        var source = new TrainTelemetrySnapshot(train, "test", 7, 1000, "world", 0, 0, 0,
                0, 0, 0, 1, 0, 0, 1, 10, 2, true, false, TrainMode.MANUAL, "driver",
                new TrainTelemetrySnapshot.CabState("ACTIVE", "FORWARD", 0, 0, false, false));
        var segment = new OperationalAuthorityService.Segment("edge", 1, 31);
        var grant = new OperationalAuthorityService.Grant(UUID.randomUUID(), train, lease, session, 7,
                "FS", true, 3, "edge", 31, 30, 80, List.of(segment), 1003, 1015);
        var snapshot = new OperationalAuthorityService.Snapshot(6, UUID.randomUUID(), 1, 1000,
                3, "AVAILABLE", List.of(grant), List.of());
        var valid = StaOperationalIntegration.select(snapshot, train, lease, 3, 1100, session,
                List.of(source), false, "FORWARD");
        assert valid.available() && valid.remainingMeters() == 30 && valid.ceilingMps() == 80 / 3.6;
        assert !StaOperationalIntegration.select(snapshot, train, lease, 4, 1100, session,
                List.of(source), false, "FORWARD").available();
        assert !StaOperationalIntegration.select(snapshot, train, lease, 3, 2501, session,
                List.of(source), false, "FORWARD").available();
        assert !StaOperationalIntegration.select(snapshot, train, UUID.randomUUID(), 3, 1100,
                session, List.of(source), false, "FORWARD").available();
        assert !StaOperationalIntegration.select(snapshot, train, lease, 3, 1100,
                UUID.randomUUID(), List.of(source), false, "FORWARD").available();
        assert !StaOperationalIntegration.select(snapshot, train, lease, 3, 1100,
                session, List.of(source), true, "REVERSE").available();
        var nonExecutable = new OperationalAuthorityService.Grant(grant.id(), train, lease, session,
                7, "FS", false, 3, "edge", 31, 30, 80, List.of(segment), 1003, 1015);
        var pending = new OperationalAuthorityService.Snapshot(6, UUID.randomUUID(), 1, 1000,
                3, "AVAILABLE", List.of(nonExecutable), List.of());
        assert !StaOperationalIntegration.select(pending, train, lease, 3, 1100,
                session, List.of(source), false, "FORWARD").available();
        System.out.println("STA v6 permission freshness, provenance, lease, direction and executable gate passed");
    }
}
