package net.skyworld.sta.api.v3;

import java.util.*;

public final class RailwayEventLog implements RailwayEventService {
    private final UUID session = UUID.randomUUID();
    private final int capacity;
    private final ArrayDeque<RailwayEvent> events = new ArrayDeque<>();
    private long sequence, evicted;
    public RailwayEventLog(int capacity) {
        if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("Invalid event capacity");
        this.capacity = capacity;
    }
    public synchronized RailwayEvent publish(String source, RailwayEvent.Type type, UUID trainId,
            String trainName, UUID driverId, String driverName, String reason) {
        return publishDetailed(source, type, trainId, trainName, driverId, driverName, reason, Map.of());
    }
    public synchronized RailwayEvent publishDetailed(String source, RailwayEvent.Type type, UUID trainId,
            String trainName, UUID driverId, String driverName, String reason, Map<String, String> details) {
        var event = new RailwayEvent(session, sequence + 1, System.currentTimeMillis(), source, type,
                trainId, trainName, driverId, driverName, reason, details);
        if (events.size() == capacity) { events.removeFirst(); evicted++; }
        events.addLast(event); sequence++;
        return event;
    }
    public synchronized History history() { return new History(session, sequence, evicted, List.copyOf(events)); }
}
