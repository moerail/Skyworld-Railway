package net.skyworld.skytrain;

import java.util.UUID;

/** STA-neutral, validated permission for the manual-train ATP executor. */
record OperationalPermission(UUID id, UUID lease, UUID controlSession, String mode,
        double remainingMeters, double ceilingMps, long graphRevision, String reason) {
    static OperationalPermission unavailable(String reason) {
        return new OperationalPermission(null, null, null, "", 0, 0, -1, reason);
    }

    static OperationalPermission unavailable(String reason, long graphRevision, UUID controlSession) {
        return new OperationalPermission(null, null, controlSession, "", 0, 0, graphRevision, reason);
    }

    boolean available() { return id != null; }

    OperationalPermission cappedAt(double vehicleMps) {
        if (!available() || !Double.isFinite(vehicleMps) || vehicleMps <= 0) return this;
        return new OperationalPermission(id, lease, controlSession, mode, remainingMeters,
                Math.min(ceilingMps, vehicleMps), graphRevision, reason);
    }
}
