package net.skyworld.sta.api.v1;

import java.util.Objects;
import java.util.UUID;

public record TrainTrackingEvent(Type type, UUID trainId,
        TrackedTrainSnapshot snapshot, long emittedAtMillis) {
    public enum Type {
        UPDATED,
        REMOVED
    }

    public TrainTrackingEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(trainId, "trainId");
        if (type == Type.UPDATED && snapshot == null) {
            throw new IllegalArgumentException("UPDATED events require a snapshot.");
        }
        if (snapshot != null && !trainId.equals(snapshot.telemetry().trainId())) {
            throw new IllegalArgumentException("Event and snapshot train IDs differ.");
        }
    }
}
