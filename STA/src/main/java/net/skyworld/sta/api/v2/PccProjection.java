package net.skyworld.sta.api.v2;

import java.util.*;

/** Compatibility view only. Never feed browser extrapolation back into tracking/control. */
public final class PccProjection {
    private PccProjection() {}
    public static Map<String, Object> train(StaMessage m, long now) {
        if (m.header().kind() != StaMessage.Kind.TRACK_REPORT) throw new IllegalArgumentException("Tracking report required");
        var s = m.physical().state(); var t = m.tracking(); var p = t.position();
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("trainId", s.trainId().toString()); r.put("name", s.trainName());
        r.put("world", s.world()); r.put("x", s.x()); r.put("y", s.y()); r.put("z", s.z());
        r.put("speedMetersPerSecond", s.speedMetersPerSecond()); r.put("lengthMeters", s.lengthMeters());
        r.put("memberCount", s.memberCount()); r.put("mode", s.mode().name().toLowerCase(Locale.ROOT));
        r.put("driver", s.driverName()); r.put("moving", s.moving()); r.put("reversed", s.reversed());
        r.put("direction", s.reversed() ? "reverse" : "forward"); r.put("observedAtMillis", s.observedAtMillis());
        r.put("cab", s.cab());
        r.put("trainNumber", s.trainNumber());
        r.put("ageMillis", Math.max(0, now - s.observedAtMillis())); r.put("graphRevision", t.graphRevision());
        r.put("quality", t.quality().name()); r.put("reason", t.reason());
        boolean valid = t.quality() == StaMessage.Quality.VALID && now - s.observedAtMillis() < t.staleAfterMillis();
        r.put("stale", !valid); r.put("graphCurrent", valid);
        if (p != null) {
            r.put("edgeId", p.edgeId()); r.put("edgeFromNodeId", p.edgeFromNodeId()); r.put("edgeToNodeId", p.edgeToNodeId());
            r.put("edgeOffsetMeters", p.edgeOffsetMeters()); r.put("edgeLengthMeters", p.edgeLengthMeters());
            r.put("line", p.line()); if (p.mileageMeters() != null) r.put("currentMileageMeters", p.mileageMeters());
        }
        return Collections.unmodifiableMap(r);
    }
}
