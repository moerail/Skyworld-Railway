package net.skyworld.sta.api.v2;

import net.skyworld.sta.api.v1.TrackPositionSnapshot;

/** Read-only STCS topology/navigation service; no Bukkit objects cross the boundary.
 * Graph JSON keeps its existing schemaVersion=3; this is distinct from STA protocol v2. */
public interface RailNetworkService {
    record RailQuery(String world, int railX, int railY, int railZ,
            double directionX, double directionY, double directionZ) {
        public RailQuery {
            if (world == null || world.isBlank() || !Double.isFinite(directionX)
                    || !Double.isFinite(directionY) || !Double.isFinite(directionZ))
                throw new IllegalArgumentException("Invalid navigation query");
        }
    }
    record Navigation(TrackPositionSnapshot position, String nextType, String nextName,
            Double nextMileageMeters, Double distanceMeters, String nextSwitchName,
            Double nextSwitchMileageMeters, String nextSwitchPosition, Double nextSwitchDistanceMeters) {}
    default int protocolVersion() { return StaMessage.VERSION; }
    long graphRevision();
    double blocksPerMeter();
    String graphJson();
    Navigation query(RailQuery query);
    /** Read-only edge reference (e.g. EoA), not a live train observation. Returns null
     * for unsupported providers, revision mismatch, unknown edges or invalid offsets.
     * Unknown/ambiguous line mileage remains null; never loads world chunks. */
    default TrackPositionSnapshot edgePosition(long revision, String edgeId, double offsetMeters) { return null; }
    /** Advisory stopping target, not MA or ATP. Distance is from the queried rail centre. */
    record StationApproach(long graphRevision, long observedAtMillis, String stationId,
            String world, int signX, int signY, int signZ, double distanceMeters,
            double blocksPerMeter) {
        public StationApproach {
            if(graphRevision<0 || observedAtMillis<0 || stationId==null || stationId.isBlank()
                    || world==null || world.isBlank() || !Double.isFinite(distanceMeters) || distanceMeters<0
                    || !Double.isFinite(blocksPerMeter) || blocksPerMeter<=0)
                throw new IllegalArgumentException("Invalid advisory station target");
        }
    }
    /** Returns null for unknown/ambiguous paths or providers without this capability. */
    default StationApproach stationAhead(RailQuery query, double maximumDistanceMeters) { return null; }
}
