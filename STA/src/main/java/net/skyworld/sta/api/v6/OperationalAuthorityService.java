package net.skyworld.sta.api.v6;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** SkyRail operational permissions. Identifiers are ETCS-inspired private numbers, not ETCS telegrams. */
public interface OperationalAuthorityService {
    int VERSION = 6;
    int MA_MESSAGE = 1003;
    int MA_PACKET = 1015;

    Snapshot operationalSnapshot();

    /** Called by an authenticated dispatcher after an SR request. No Bukkit entity access is required. */
    String approveSr(UUID trainId, UUID targetNodeId, String actor);

    /** Onboard receipt acknowledgement, not proof of train integrity or route clearance. */
    default boolean acknowledgeGrant(UUID trainId, UUID driverLeaseId, UUID grantId, long graphRevision) {
        return false;
    }

    /** An onboard protection-channel change invalidates old requests and forward reservations, not body occupancy. */
    default void revokeForChannelChange(UUID trainId) { }

    record Segment(String edgeId, double fromMeters, double toMeters) {
        public Segment {
            Objects.requireNonNull(edgeId);
            if (!Double.isFinite(fromMeters) || !Double.isFinite(toMeters)
                    || fromMeters < 0 || toMeters <= fromMeters)
                throw new IllegalArgumentException("Invalid route segment");
        }
    }

    record Grant(UUID id, UUID trainId, UUID driverLeaseId, UUID telemetrySession,
            long telemetrySequence, String mode, boolean executable, long graphRevision,
            String eoaEdgeId, double eoaOffsetMeters, double remainingMeters,
            double ceilingKmh, List<Segment> path, int NID_MESSAGE, int NID_PACKET) {
        public Grant {
            Objects.requireNonNull(id); Objects.requireNonNull(trainId);
            Objects.requireNonNull(driverLeaseId); Objects.requireNonNull(telemetrySession);
            Objects.requireNonNull(eoaEdgeId);
            if (!Set.of("FS", "SH", "SR").contains(mode) || graphRevision < 0
                    || telemetrySequence < 0 || !Double.isFinite(eoaOffsetMeters)
                    || !Double.isFinite(remainingMeters) || !Double.isFinite(ceilingKmh)
                    || eoaOffsetMeters < 0 || ceilingKmh <= 0 || path == null || path.isEmpty()
                    || path.size() > 256 || NID_MESSAGE != MA_MESSAGE || NID_PACKET != MA_PACKET)
                throw new IllegalArgumentException("Invalid operational permission");
            path = List.copyOf(path);
        }
    }

    record PendingSr(UUID trainId, String trainName, UUID driverId, UUID driverLeaseId,
            long requestedAtMillis) {
        public PendingSr {
            Objects.requireNonNull(trainId); Objects.requireNonNull(trainName);
            Objects.requireNonNull(driverId); Objects.requireNonNull(driverLeaseId);
            if (requestedAtMillis < 0) throw new IllegalArgumentException("Invalid request time");
        }
    }

    record Snapshot(int version, UUID session, long sequence, long emittedAtMillis,
            long graphRevision, String status, List<Grant> grants, List<PendingSr> pendingSr) {
        public Snapshot {
            if (version != VERSION || sequence < 0 || emittedAtMillis < 0 || graphRevision < 0)
                throw new IllegalArgumentException("Invalid operational snapshot");
            Objects.requireNonNull(session); Objects.requireNonNull(status);
            grants = List.copyOf(grants);
            pendingSr = List.copyOf(pendingSr);
        }
    }
}
