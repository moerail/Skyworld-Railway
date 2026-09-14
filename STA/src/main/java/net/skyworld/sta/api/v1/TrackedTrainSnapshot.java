package net.skyworld.sta.api.v1;

import java.util.Objects;

public record TrackedTrainSnapshot(TrainTelemetrySnapshot telemetry,
        TrackPositionSnapshot trackPosition) {
    public TrackedTrainSnapshot {
        Objects.requireNonNull(telemetry, "telemetry");
        Objects.requireNonNull(trackPosition, "trackPosition");
    }
}
