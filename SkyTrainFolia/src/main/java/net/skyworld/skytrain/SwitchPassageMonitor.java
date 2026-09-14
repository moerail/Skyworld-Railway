package net.skyworld.skytrain;

import java.util.*;
import java.util.function.Consumer;

/** Best-effort actual member observations, never predicted placements or an ATP proof. */
final class SwitchPassageMonitor {
    private final Map<UUID, MemberSnapshot> previous = new HashMap<>();
    private final Set<UUID> reported = new HashSet<>();
    record Suspect(SkyTrainSwitch railwaySwitch, SwitchPort entry, SwitchState physical) { }

    synchronized void observe(Collection<MemberSnapshot> samples, Collection<SkyTrainSwitch> switches,
            long now, Consumer<Suspect> notify) {
        for (SkyTrainSwitch sw : switches) {
            if (!samples.isEmpty() && samples.stream().allMatch(s -> fresh(s, now)
                    && (!s.worldName.equals(sw.pivot.worldName())
                    || Math.hypot(s.x - sw.pivot.x() - .5, s.z - sw.pivot.z() - .5) > 2.25))) {
                reported.remove(sw.id);
            }
        }
        for (MemberSnapshot current : samples) {
            if (!fresh(current, now)) continue;
            MemberSnapshot before = previous.put(current.entityId, current);
            if (before == null || current.timeMillis <= before.timeMillis
                    || current.timeMillis - before.timeMillis > 500 || !before.worldName.equals(current.worldName)) continue;
            for (SkyTrainSwitch sw : switches) {
                if (!current.worldName.equals(sw.pivot.worldName()) || (int)Math.floor(current.x) != sw.pivot.x()
                        || (int)Math.floor(current.z) != sw.pivot.z() || Math.abs(current.y - sw.pivot.y()) > 1) continue;
                for (var port : sw.nodeSnapshot().ports().entrySet()) {
                    if (port.getKey() == SwitchPort.COMMON) continue;
                    var face = port.getValue();
                    if ((int)Math.floor(before.x) != sw.pivot.x() + face.getModX()
                            || (int)Math.floor(before.z) != sw.pivot.z() + face.getModZ()
                            || Math.abs(before.y - sw.pivot.y()) > 1) continue;
                    SwitchState physical = sw.physicalState();
                    SwitchState required = port.getKey() == SwitchPort.STRAIGHT ? SwitchState.STRAIGHT : SwitchState.DIVERGING;
                    if (physical != null && physical != required && reported.add(sw.id)) {
                        notify.accept(new Suspect(sw, port.getKey(), physical));
                    }
                }
            }
        }
        Set<UUID> present = new HashSet<>();
        samples.forEach(s -> present.add(s.entityId));
        previous.keySet().retainAll(present);
        reported.removeIf(id -> switches.stream().noneMatch(sw -> sw.id.equals(id)));
    }

    private static boolean fresh(MemberSnapshot s, long now) {
        return now >= s.timeMillis && now - s.timeMillis <= 1000;
    }
}
