package net.skyworld.sta.api.v7;

import java.util.List;

/** Additive, read-only station configuration contract. No route locks or MA are granted. */
public interface StationAreaService {
    int VERSION = 7;
    int MESSAGE = 6502;
    int QUERY_PACKET = 5001, SNAPSHOT_PACKET = 5002, CHANGED_PACKET = 5003, RESULT_PACKET = 5004;
    record Position(String world, int x, int y, int z) {
        public Position { if (world == null || world.isBlank()) throw new IllegalArgumentException("World required"); }
    }
    record Interval(String edgeId, double fromMeters, double toMeters) {
        public Interval {
            if (edgeId == null || edgeId.isBlank() || !Double.isFinite(fromMeters) || !Double.isFinite(toMeters)
                    || fromMeters < 0 || toMeters <= fromMeters) throw new IllegalArgumentException("Invalid graph interval");
        }
    }
    record Boundary(String id, Position rail, String inside, String preference, int penalty, String source) {}
    record Track(String id, String name, Position rail, String state, List<String> endpoints,
                 List<Interval> intervals, List<String> issues) {
        public Track { endpoints = List.copyOf(endpoints); intervals = List.copyOf(intervals); issues = List.copyOf(issues); }
    }
    record Area(String id, String station, String yard, String state, List<Boundary> boundaries,
                List<Track> tracks, List<Interval> intervals, List<String> issues) {
        public Area { boundaries = List.copyOf(boundaries); tracks = List.copyOf(tracks);
            intervals = List.copyOf(intervals); issues = List.copyOf(issues); }
    }
    record Snapshot(int version, int messageId, int packetId, String status, String producerSession,
                    long configurationRevision, long graphRevision, long emittedAtMillis, List<Area> areas) {
        public Snapshot {
            if (version != VERSION || messageId != MESSAGE || packetId != SNAPSHOT_PACKET)
                throw new IllegalArgumentException("Unsupported station-area envelope");
            if (status == null || producerSession == null || producerSession.isBlank()
                    || configurationRevision < 0 || graphRevision < 0 || emittedAtMillis < 0)
                throw new IllegalArgumentException("Invalid station-area metadata");
            areas = List.copyOf(areas);
        }
    }
    default int areaProtocolVersion() { return VERSION; }
    /** An immutable cached snapshot; safe to query off the world thread. */
    Snapshot stationAreas();
}
