package net.skyworld.sta.api.v4;

import java.util.*;

/** STCS publishes replaceable display snapshots. NOT an executable grant/ACK channel. */
public interface ShadowAuthorityService {
    Snapshot snapshot();
    record PathPart(String edgeId, double fromMeters, double toMeters) {
        public PathPart { Objects.requireNonNull(edgeId); finite(fromMeters); finite(toMeters);
            if (fromMeters < 0 || toMeters < fromMeters) throw new IllegalArgumentException("Invalid path interval"); }
    }
    record Authority(UUID trainId, UUID driverLeaseId, UUID telemetrySession, long telemetrySequence,
            String state, String reason, List<PathPart> path, String eoaEdgeId, Double eoaOffsetMeters,
            Double signedRemainingMeters) {
        public Authority {
            Objects.requireNonNull(trainId); Objects.requireNonNull(state); Objects.requireNonNull(reason);
            path = List.copyOf(path);
            if (path.size()>256 || telemetrySequence<0) throw new IllegalArgumentException("Invalid authority");
            if(eoaOffsetMeters!=null) { finite(eoaOffsetMeters); if(eoaOffsetMeters<0) throw new IllegalArgumentException(); }
            if(signedRemainingMeters!=null) finite(signedRemainingMeters);
            if((eoaEdgeId==null)!=(eoaOffsetMeters==null)) throw new IllegalArgumentException("Incomplete EoA");
            if(!Set.of("ALLOCATED_SHADOW","WAITING","INACTIVE").contains(state)) throw new IllegalArgumentException("Unknown authority state");
            if(state.equals("ALLOCATED_SHADOW")) {
                if(driverLeaseId==null||telemetrySession==null||eoaEdgeId==null||signedRemainingMeters==null)
                    throw new IllegalArgumentException("Allocated shadow requires driver, provenance and EoA");
            } else if(eoaEdgeId!=null||signedRemainingMeters!=null||!path.isEmpty()) throw new IllegalArgumentException("Inactive authority has a path");
        }
        public double creditMeters() { return signedRemainingMeters == null ? 0 : Math.max(0,signedRemainingMeters); }
    }
    record Section(String edgeId, String resourceId, String state, Set<UUID> occupants, Set<UUID> reservations,
            Double fromMeters, Double toMeters) {
        public Section(String edgeId,String resourceId,String state,Set<UUID> occupants,Set<UUID> reservations) {
            this(edgeId,resourceId,state,occupants,reservations,null,null);
        }
        public Section { Objects.requireNonNull(edgeId); Objects.requireNonNull(resourceId); Objects.requireNonNull(state);
            if((fromMeters==null)!=(toMeters==null))throw new IllegalArgumentException("Incomplete interval");
            if(fromMeters!=null) { finite(fromMeters);finite(toMeters);if(fromMeters<0||toMeters<fromMeters)throw new IllegalArgumentException("Invalid interval"); }
            if(!Set.of("OCCUPIED","UNCERTAIN","RESERVED_SHADOW","UNALLOCATED").contains(state)) throw new IllegalArgumentException("Unknown section state");
            occupants=Set.copyOf(occupants); reservations=Set.copyOf(reservations); }
    }
    record Snapshot(int version, boolean simulationOnly, boolean executable, UUID session, long sequence,
            long emittedAtMillis, long graphRevision, String status, List<Authority> authorities, List<Section> sections) {
        public Snapshot {
            if(version!=4 || !simulationOnly || executable || sequence<0 || emittedAtMillis<0 || graphRevision<0)
                throw new IllegalArgumentException("Only non-executable shadow snapshots are supported");
            Objects.requireNonNull(session); Objects.requireNonNull(status);
            authorities=List.copyOf(authorities); sections=List.copyOf(sections);
        }
    }
    private static void finite(double value) { if(!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite distance"); }
}
