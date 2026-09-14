package net.skyworld.skytrain;

import java.util.Map;
import java.util.UUID;

final class TrainMotionFrame {
    private static final TrainMotionFrame EMPTY = new TrainMotionFrame(Map.of(), 0L, Long.MAX_VALUE);

    private final Map<UUID, TrainMemberTarget> targets;
    final long updatedAtMillis;
    final long applyTick;

    TrainMotionFrame(Map<UUID, TrainMemberTarget> targets, long updatedAtMillis, long applyTick) {
        this.targets = Map.copyOf(targets);
        this.updatedAtMillis = updatedAtMillis;
        this.applyTick = applyTick;
    }

    static TrainMotionFrame empty() {
        return EMPTY;
    }

    TrainMemberTarget target(UUID entityId) {
        return targets.get(entityId);
    }

    boolean isEmpty() {
        return targets.isEmpty();
    }
}
