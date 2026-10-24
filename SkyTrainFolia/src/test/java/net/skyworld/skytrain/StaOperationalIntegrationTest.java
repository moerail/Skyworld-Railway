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
        var shGrant = new OperationalAuthorityService.Grant(UUID.randomUUID(), train, lease, session,
                7, "SH", true, 3, "edge", 31, 30, 40, List.of(segment), 1003, 1015);
        var shSnapshot = new OperationalAuthorityService.Snapshot(6, snapshot.session(), 2, 1000,
                3, "AVAILABLE", List.of(shGrant), List.of());
        assert StaOperationalIntegration.select(shSnapshot, train, lease, 3, 1100,
                session, List.of(source), false, "NEUTRAL").available()
                : "SH neutral handle keeps the same-direction grant";
        assert !StaOperationalIntegration.select(shSnapshot, train, lease, 3, 1100,
                session, List.of(source), true, "BACKWARD").available()
                : "An old forward SH grant cannot authorise reverse travel";
        var reverseSource = new TrainTelemetrySnapshot(train, "test", 8, 1100, "world", 0, 0, 0,
                0, 0, 0, -1, 0, 0, 0, 10, 2, false, true, TrainMode.MANUAL, "driver",
                new TrainTelemetrySnapshot.CabState("ACTIVE", "BACKWARD", 0, 7, false, false));
        assert !StaOperationalIntegration.select(shSnapshot, train, lease, 3, 1100,
                session, List.of(source, reverseSource), false, "FORWARD").available()
                : "A later direction change invalidates old telemetry provenance";
        var reverseGrant = new OperationalAuthorityService.Grant(UUID.randomUUID(), train, lease, session,
                8, "SH", true, 3, "reverse-edge", 20, 20, 40,
                List.of(new OperationalAuthorityService.Segment("reverse-edge", 0, 20)), 1003, 1015);
        var reverseSnapshot = new OperationalAuthorityService.Snapshot(6, snapshot.session(), 3, 1100,
                3, "AVAILABLE", List.of(reverseGrant), List.of());
        assert StaOperationalIntegration.select(reverseSnapshot, train, lease, 3, 1150,
                session, List.of(source, reverseSource), true, "BACKWARD").available()
                : "Fresh reverse SH grant resumes without a driver demand";
        System.out.println("STA v6 permission freshness, provenance, lease, direction and executable gate passed");
    }
}
