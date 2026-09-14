package net.skyworld.sta.api.v3;

import java.util.List;
import java.util.UUID;

/** Bounded, in-memory operational history owned by STA. Safe on region and async threads. */
public interface RailwayEventService {
    record History(UUID session, long latestSequence, long evictedCount, List<RailwayEvent> events) {
        public History { events = List.copyOf(events); }
    }
    RailwayEvent publish(String source, RailwayEvent.Type type, UUID trainId, String trainName,
            UUID driverId, String driverName, String reason);
    History history();
    default RailwayEvent publishDetailed(String source, RailwayEvent.Type type, UUID trainId, String trainName,
            UUID driverId, String driverName, String reason, java.util.Map<String, String> details) {
        return publish(source, type, trainId, trainName, driverId, driverName, reason);
    }
}
