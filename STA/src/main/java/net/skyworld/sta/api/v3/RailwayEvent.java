package net.skyworld.sta.api.v3;

import java.util.Objects;
import java.util.UUID;

/** Operational notification, not a safety command or an acknowledgement. */
public record RailwayEvent(UUID session, long sequence, long emittedAtMillis, String source,
        Type type, UUID trainId, String trainName, UUID driverId, String driverName, String reason,
        java.util.Map<String, String> details) {
    public enum Type { DRIVER_UNAVAILABLE, DRIVER_RELEASED, DRIVER_ACQUIRED,
        SWITCH_CHANGED, SWITCH_RUN_THROUGH_SUSPECTED, EMERGENCY_BRAKE_APPLIED,
        MA_REQUESTED, MA_RELEASED, MA_UNAVAILABLE, SR_GRANTED, ATP_MODE_CHANGED }
    public RailwayEvent(UUID session, long sequence, long emittedAtMillis, String source,
            Type type, UUID trainId, String trainName, UUID driverId, String driverName, String reason) {
        this(session, sequence, emittedAtMillis, source, type, trainId, trainName, driverId, driverName, reason, java.util.Map.of());
    }
    public RailwayEvent {
        Objects.requireNonNull(session); Objects.requireNonNull(type);
        if (type != Type.SWITCH_CHANGED) Objects.requireNonNull(trainId);
        details = details == null ? java.util.Map.of() : java.util.Map.copyOf(details);
        if (details.size() > 16) throw new IllegalArgumentException("Too many event details");
        details.forEach((key, value) -> { bounded(key); bounded(value); });
        if (sequence < 1 || emittedAtMillis < 0) throw new IllegalArgumentException("Invalid event header");
        source = bounded(source); trainName = bounded(trainName); driverName = bounded(driverName); reason = bounded(reason);
    }
    private static String bounded(String value) {
        value = Objects.requireNonNullElse(value, "");
        if (value.length() > 256) throw new IllegalArgumentException("Event field too long");
        return value;
    }
}
