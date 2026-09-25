package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;
import net.skyworld.sta.api.v1.TrainMode;
import net.skyworld.sta.api.v1.TrainTelemetrySnapshot;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

/** Optional STA v6 boundary; this class is loaded only by StaTelemetryPublisher. */
final class StaOperationalIntegration {
    static OperationalPermission select(OperationalAuthorityService.Snapshot snapshot,
            UUID train, UUID lease, long graphRevision, long now, UUID session,
            List<TrainTelemetrySnapshot> history, boolean reversed, String reverser) {
        UUID controlSession = snapshot == null ? null : snapshot.session();
        if (snapshot == null || snapshot.version() != OperationalAuthorityService.VERSION
                || !snapshot.status().equals("AVAILABLE") || snapshot.graphRevision() != graphRevision
                || now < snapshot.emittedAtMillis() || now - snapshot.emittedAtMillis() > 1500)
            return OperationalPermission.unavailable("STALE", graphRevision, controlSession);
        if (lease == null) return OperationalPermission.unavailable("NO_DRIVER", graphRevision, controlSession);
        var grants = snapshot.grants().stream().filter(g -> g.trainId().equals(train)).toList();
        if (grants.size() != 1) return OperationalPermission.unavailable("NO_GRANT", graphRevision, controlSession);
        var grant = grants.getFirst();
        if (!grant.executable() || !grant.driverLeaseId().equals(lease))
            return OperationalPermission.unavailable("NOT_EXECUTABLE", graphRevision, controlSession);
        if (!grant.telemetrySession().equals(session))
            return OperationalPermission.unavailable("SESSION_CHANGED", graphRevision, controlSession);
        var source = history.stream().filter(s -> s.sequence() == grant.telemetrySequence()).findFirst().orElse(null);
        if (source == null || !source.trainId().equals(train) || source.mode() != TrainMode.MANUAL
                || now < source.observedAtMillis() || now - source.observedAtMillis() > 1500)
            return OperationalPermission.unavailable("SOURCE_UNAVAILABLE", graphRevision, controlSession);
        if (source.reversed() != reversed || source.cab() == null
                || !source.cab().reverser().equals(reverser)
                || history.stream().anyMatch(s -> s.sequence() > source.sequence()
                        && (s.reversed() != reversed || !s.world().equals(source.world())
                            || s.cab() == null || !s.cab().reverser().equals(reverser))))
            return OperationalPermission.unavailable("DIRECTION_CHANGED", graphRevision, controlSession);
        if (grant.path().isEmpty() || !grant.path().getLast().edgeId().equals(grant.eoaEdgeId())
                || grant.ceilingKmh() <= 0 || !Double.isFinite(grant.remainingMeters()))
            return OperationalPermission.unavailable("INVALID_GRANT", graphRevision, controlSession);
        return new OperationalPermission(grant.id(), lease, controlSession, grant.mode(), grant.remainingMeters(),
                grant.ceilingKmh() / 3.6, grant.graphRevision(), "VALID");
    }
}
