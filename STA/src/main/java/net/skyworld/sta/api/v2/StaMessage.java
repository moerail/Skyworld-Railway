package net.skyworld.sta.api.v2;

import java.util.Objects;
import java.util.UUID;
import net.skyworld.sta.api.v1.TrainTelemetrySnapshot;
import net.skyworld.sta.api.v1.TrackPositionSnapshot;

/** STA application messages. IDs are private STA IDs, NOT ETCS wire identifiers. */
public record StaMessage(Header header, Physical physical, Tracking tracking) {
    public static final int VERSION = 2;
    public enum Kind {
        TELEMETRY_REPORT(1001), TRACK_REPORT(1002), TRAIN_REMOVED(1003);
        public final int id;
        Kind(int id) { this.id = id; }
        public static Kind of(int id) {
            for (Kind kind : values()) if (kind.id == id) return kind;
            throw new IllegalArgumentException("Unknown STA message: " + id);
        }
    }
    public enum Source { STF, STCS }
    public enum Quality { VALID, UNLOCATED, STALE, EXPIRED, GRAPH_CHANGED, SOURCE_UNAVAILABLE, SCALE_MISMATCH }
    public record Header(int version, Kind kind, Source source, UUID sessionId,
            long sequence, long emittedAtMillis, UUID trainId) {
        public Header {
            Objects.requireNonNull(kind); Objects.requireNonNull(source);
            Objects.requireNonNull(sessionId); Objects.requireNonNull(trainId);
            if (version != VERSION || sequence < 0 || emittedAtMillis < 0)
                throw new IllegalArgumentException("Unsupported version or invalid header");
        }
    }
    /** Position is the active leading cart centre, NOT a safe train-front envelope.
     * lengthMeters is nominal centre-to-centre consist span; no train integrity claim.
     * motionXYZ is a dimensionless unit direction, XYZ and railXYZ are Minecraft blocks.
     * Speed uses nominal 20 ticks/second; it is not wall-clock speed under server lag. */
    public record Physical(TrainTelemetrySnapshot state, double blocksPerMeter) {
        public Physical {
            Objects.requireNonNull(state);
            if (!Double.isFinite(blocksPerMeter) || blocksPerMeter <= 0)
                throw new IllegalArgumentException("blocksPerMeter must be positive and finite");
            double norm = state.motionX()*state.motionX()+state.motionY()*state.motionY()+state.motionZ()*state.motionZ();
            if (norm > 0.0001 && Math.abs(norm-1.0) > 0.001)
                throw new IllegalArgumentException("Motion is a unit direction, not a velocity");
        }
    }
    /** Provenance binds logical position to exactly one STF session and sequence. */
    public record Tracking(UUID telemetrySessionId, long telemetrySequence,
            long receivedAtMillis, long resolvedAtMillis, long staleAfterMillis,
            long expireAfterMillis, long graphRevision, Quality quality,
            TrackPositionSnapshot position, String reason) {
        public Tracking {
            Objects.requireNonNull(telemetrySessionId); Objects.requireNonNull(quality);
            reason = Objects.requireNonNullElse(reason, "");
            if (telemetrySequence < 0 || receivedAtMillis < 0 || resolvedAtMillis < 0
                    || staleAfterMillis < 1 || expireAfterMillis < staleAfterMillis || graphRevision < 0)
                throw new IllegalArgumentException("Invalid tracking metadata");
            if (quality == Quality.VALID && (position == null || !position.graphCurrent()
                    || position.stale() || position.graphRevision() != graphRevision))
                throw new IllegalArgumentException("VALID requires a current resolved position");
        }
    }
    public StaMessage {
        Objects.requireNonNull(header);
        if (header.kind() == Kind.TRAIN_REMOVED) {
            if (physical != null || tracking != null) throw new IllegalArgumentException("Removal has no packets");
        } else {
            Objects.requireNonNull(physical, "Physical packet required");
            if (!header.trainId().equals(physical.state().trainId()))
                throw new IllegalArgumentException("Train identity mismatch");
            if (physical.state().observedAtMillis() > header.emittedAtMillis())
                throw new IllegalArgumentException("Emission cannot precede observation");
            if (header.kind() == Kind.TELEMETRY_REPORT && (header.source() != Source.STF
                    || tracking != null || header.sequence() != physical.state().sequence()))
                throw new IllegalArgumentException("Invalid STF report");
            if (header.kind() == Kind.TRACK_REPORT && (header.source() != Source.STCS || tracking == null))
                throw new IllegalArgumentException("Invalid STCS report");
            if (tracking != null && tracking.telemetrySequence() != physical.state().sequence())
                throw new IllegalArgumentException("Telemetry provenance mismatch");
            if (tracking != null && tracking.position() != null
                    && tracking.position().measuredAtMillis() != physical.state().observedAtMillis())
                throw new IllegalArgumentException("Logical position must reference the physical observation time");
        }
    }
}
