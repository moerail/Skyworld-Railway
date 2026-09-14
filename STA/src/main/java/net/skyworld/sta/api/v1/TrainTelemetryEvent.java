package net.skyworld.sta.api.v1;

import java.util.Objects;
import java.util.UUID;

public record TrainTelemetryEvent(Type type, UUID trainId,
        TrainTelemetrySnapshot snapshot, long emittedAtMillis) {
    public enum Type {
        UPDATED,
        REMOVED
    }

    public TrainTelemetryEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(trainId, "trainId");
        if (type == Type.UPDATED && snapshot == null) {
            throw new IllegalArgumentException("UPDATED events require a snapshot.");
        }
        if (snapshot != null && !trainId.equals(snapshot.trainId())) {
            throw new IllegalArgumentException("Event and snapshot train IDs differ.");
        }
    }
}
