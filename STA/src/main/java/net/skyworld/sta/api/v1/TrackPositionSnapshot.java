package net.skyworld.sta.api.v1;

/** Immutable logical position resolved against one STCS RailGraph revision. */
public record TrackPositionSnapshot(
        long graphRevision,
        String edgeId,
        String edgeFromNodeId,
        String edgeToNodeId,
        double edgeOffsetMeters,
        double edgeLengthMeters,
        String line,
        Double mileageMeters,
        long measuredAtMillis,
        boolean graphCurrent,
        boolean stale) {

    public TrackPositionSnapshot {
        edgeId = required(edgeId, "edgeId");
        edgeFromNodeId = required(edgeFromNodeId, "edgeFromNodeId");
        edgeToNodeId = required(edgeToNodeId, "edgeToNodeId");
        line = line == null ? "" : line.trim();
        if (graphRevision < 0 || measuredAtMillis < 0
                || !Double.isFinite(edgeOffsetMeters) || !Double.isFinite(edgeLengthMeters)
                || edgeOffsetMeters < 0 || edgeLengthMeters < 0
                || edgeOffsetMeters > edgeLengthMeters + 0.001
                || (mileageMeters != null && !Double.isFinite(mileageMeters))) {
            throw new IllegalArgumentException("Invalid track position.");
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        return normalized;
    }
}
