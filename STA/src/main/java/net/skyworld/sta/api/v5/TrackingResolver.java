package net.skyworld.sta.api.v5;

import java.util.*;
import net.skyworld.sta.api.v1.TrackPositionSnapshot;

/** Pure logical resolver: no entity access, callbacks or scheduler calls. Single async owner. */
public final class TrackingResolver {
    private final UUID session;
    private final long staleAfter, expireAfter;
    private final Map<UUID, Input> inputs = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSequence = new HashMap<>();
    private UUID sourceSession;
    private long sequence;
    private record Input(StaMessage message, long receivedAt) {}
    public TrackingResolver(UUID session, long staleAfter, long expireAfter) {
        this.session = Objects.requireNonNull(session);
        if (staleAfter < 1 || expireAfter < staleAfter) throw new IllegalArgumentException("timeouts");
        this.staleAfter = staleAfter; this.expireAfter = expireAfter;
    }
    public List<StaMessage> accept(UUID expectedSession, Collection<StaMessage> messages, long now) {
        List<StaMessage> removed = new ArrayList<>();
        if (!Objects.equals(sourceSession, expectedSession)) {
            for (UUID id : inputs.keySet()) removed.add(removal(id, now));
            inputs.clear(); lastSequence.clear(); sourceSession = expectedSession;
        }
        Set<UUID> present = new HashSet<>();
        for (StaMessage m : messages) {
            var h = m.header();
            if (!Objects.equals(expectedSession, h.sessionId()) || h.source() != StaMessage.Source.STF
                    || h.kind() == StaMessage.Kind.TRACK_REPORT || h.emittedAtMillis() > now + 5000) continue;
            UUID id = h.trainId(); present.add(id);
            if (h.sequence() <= lastSequence.getOrDefault(id, -1L)) continue;
            if (!lastSequence.containsKey(id) && lastSequence.size() >= 16384) continue;
            lastSequence.put(id, h.sequence());
            if (h.kind() == StaMessage.Kind.TRAIN_REMOVED) {
                inputs.remove(id); removed.add(removal(id, now));
            } else if (m.physical().state().observedAtMillis() <= now + 5000) {
                inputs.put(id, new Input(m, now));
            }
        }
        // snapshots() is a complete current store: absence after tombstone retention means removed.
        for (UUID id : new ArrayList<>(inputs.keySet())) if (!present.contains(id)) {
            inputs.remove(id); lastSequence.remove(id); removed.add(removal(id, now));
        }
        lastSequence.keySet().removeIf(id -> !present.contains(id));
        return removed;
    }
    public List<StaMessage> resolve(RailNetworkService graph, boolean available, long now) {
        List<StaMessage> result = new ArrayList<>();
        for (var e : inputs.entrySet()) {
            var m = e.getValue().message(); var p = m.physical(); var s = p.state();
            long age = Math.max(0, now - Math.min(s.observedAtMillis(), e.getValue().receivedAt()));
            long rev = graph.graphRevision();
            StaMessage.Quality quality;
            TrackPositionSnapshot position = null;
            if (!available) quality = StaMessage.Quality.SOURCE_UNAVAILABLE;
            else if (age >= expireAfter) quality = StaMessage.Quality.EXPIRED;
            else if (age >= staleAfter) quality = StaMessage.Quality.STALE;
            else if (Math.abs(graph.blocksPerMeter() - p.blocksPerMeter()) > 0.000001)
                quality = StaMessage.Quality.SCALE_MISMATCH;
            else {
                var navigation = graph.query(new RailNetworkService.RailQuery(s.world(), s.railX(), s.railY(), s.railZ(),
                        s.motionX(), s.motionY(), s.motionZ()));
                position = navigation == null ? null : navigation.position();
                quality = position == null ? StaMessage.Quality.UNLOCATED
                        : position.graphRevision() != rev || graph.graphRevision() != rev
                        ? StaMessage.Quality.GRAPH_CHANGED : StaMessage.Quality.VALID;
            }
            if (position != null) position = new TrackPositionSnapshot(position.graphRevision(), position.edgeId(),
                    position.edgeFromNodeId(), position.edgeToNodeId(), position.edgeOffsetMeters(), position.edgeLengthMeters(),
                    position.line(), position.mileageMeters(), s.observedAtMillis(), quality == StaMessage.Quality.VALID, false);
            var track = new StaMessage.Tracking(m.header().sessionId(), m.header().sequence(), e.getValue().receivedAt(),
                    now, staleAfter, expireAfter, rev, quality, position, quality == StaMessage.Quality.VALID ? "" : quality.name());
            result.add(new StaMessage(new StaMessage.Header(StaMessage.VERSION, StaMessage.Kind.TRACK_REPORT, StaMessage.Source.STCS,
                    session, ++sequence, now, e.getKey()), p, track));
        }
        return result;
    }
    private StaMessage removal(UUID id, long now) {
        return new StaMessage(new StaMessage.Header(StaMessage.VERSION, StaMessage.Kind.TRAIN_REMOVED, StaMessage.Source.STCS,
                session, ++sequence, now, id), null, null);
    }
}
