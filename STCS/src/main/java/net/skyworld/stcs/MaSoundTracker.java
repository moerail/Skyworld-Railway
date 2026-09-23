package net.skyworld.stcs;

import java.util.*;
import net.skyworld.sta.api.v5.ShadowAuthorityService.Authority;

/** Notification heuristic only; never supplies control or reservation decisions. */
final class MaSoundTracker {
    private final Map<UUID, Authority> previous = new HashMap<>();
    private final Set<UUID> granted = new HashSet<>();
    private final Map<UUID, Long> lastChange = new HashMap<>();
    private static final class WarningState {
        double shrinking, extending;
        boolean warnedShrink, warnedLow;
    }
    private final Map<UUID, WarningState> warnings = new HashMap<>();
    void reset(UUID train) { previous.remove(train); granted.remove(train); lastChange.remove(train); warnings.remove(train); }
    void retain(Set<UUID> trains) { previous.keySet().retainAll(trains); granted.retainAll(trains); lastChange.keySet().retainAll(trains); warnings.keySet().retainAll(trains); }
    String update(Authority next, long now, double threshold, long cooldown, double lowMeters) {
        Authority old = previous.get(next.trainId());
        String base = update(next, now, threshold, cooldown);
        if (!next.state().equals("ALLOCATED_SHADOW") || next.signedRemainingMeters() == null) return base;
        var w = warnings.computeIfAbsent(next.trainId(), id -> new WarningState());
        boolean comparable = old != null
                && Objects.equals(old.driverLeaseId(), next.driverLeaseId())
                && Objects.equals(old.telemetrySession(), next.telemetrySession());
        List<String> cues = new ArrayList<>();
        if (!base.isEmpty()) cues.add(base);
        double remaining = next.signedRemainingMeters();
        if (lowMeters > 0 && remaining < lowMeters && !w.warnedLow) {
            w.warnedLow = true; cues.add("LOW");
        } else if (remaining >= lowMeters + 5) w.warnedLow = false;
        if (comparable && !next.path().isEmpty()) {
            var head = next.path().getFirst();
            double travelled = 0;
            boolean found = false;
            for (var part : old.path()) {
                if (part.edgeId().equals(head.edgeId())) {
                    travelled += head.fromMeters() - part.fromMeters(); found = true; break;
                }
                travelled += part.toMeters() - part.fromMeters();
            }
            double delta = remaining - old.signedRemainingMeters();
            if (found && travelled >= 0) {
                if (delta > 0) {
                    w.extending += delta; w.shrinking = 0;
                    if (w.extending >= 5) w.warnedShrink = false;
                } else if (delta < 0 && travelled > 0.001) {
                    w.extending = 0; w.shrinking -= delta;
                    if (w.shrinking >= 1 && !w.warnedShrink) {
                        w.warnedShrink = true; cues.add("SHRINKING");
                    }
                }
            }
        } else { w.shrinking = 0; w.extending = 0; }
        return String.join("+", cues);
    }
    String update(Authority next, long now, double threshold, long cooldown) {
        UUID id = next.trainId();
        if (!next.state().equals("ALLOCATED_SHADOW") || next.signedRemainingMeters() == null) {
            previous.remove(id); return "";
        }
        Authority old = previous.put(id, next);
        if (granted.add(id)) return "GRANTED";
        if (old == null || !Objects.equals(old.driverLeaseId(), next.driverLeaseId())
                || !Objects.equals(old.telemetrySession(), next.telemetrySession())) return "";
        double creditDelta = next.signedRemainingMeters() - old.signedRemainingMeters();
        if (Math.abs(creditDelta) < threshold || next.path().isEmpty()) return "";
        var head = next.path().getFirst();
        double travelled = 0;
        boolean found = false;
        for (var part : old.path()) {
            if (part.edgeId().equals(head.edgeId())) {
                travelled += head.fromMeters() - part.fromMeters(); found = true; break;
            }
            travelled += part.toMeters() - part.fromMeters();
        }
        // Fixed EoA: credit decreases by travelled distance. Rolling horizon: credit stays constant.
        if (!found || travelled < -0.5 || Math.abs(creditDelta + travelled) < threshold) return "";
        if (now - lastChange.getOrDefault(id, Long.MIN_VALUE / 2) < cooldown) return "";
        lastChange.put(id, now); return "CHANGED";
    }
}
