package net.skyworld.skytrain;

import java.util.EnumMap;

/** Chooses a single safe next step for a stationary manual-train rider. */
final class DriverGuide {
    enum Hint { NONE, CLAIM, RECLAIM, REVERSER, DEMAND, SHADOW_DEMAND,
        SERVICE_UNAVAILABLE, POSITION_WAIT, MA_WAITING, SR_PENDING, BRAKE_HELD,
        ACK_TRIP, RELEASE_OLD_MA }

    record State(boolean manual, boolean stopped, boolean driver, boolean anotherDriver,
            Reverser reverser, OperatingMode operatingMode, ProtectionMode protectionMode,
            boolean formerDriver, boolean serviceAvailable, boolean positionKnown,
            boolean demandAvailable, String authorityReason, boolean emergencyBrake) {}

    private final EnumMap<Hint, Long> lastSent = new EnumMap<>(Hint.class);
    private Hint previous = Hint.NONE;

    static Hint select(State state) {
        if (!state.manual() || !state.stopped()) return Hint.NONE;
        if (state.driver() && state.operatingMode() == OperatingMode.TR) return Hint.ACK_TRIP;
        if (state.driver() && state.operatingMode() == OperatingMode.PT) return Hint.RELEASE_OLD_MA;
        if (!state.driver()) return state.anotherDriver() ? Hint.NONE
                : state.formerDriver() ? Hint.RECLAIM : Hint.CLAIM;
        if (state.reverser() == Reverser.NEUTRAL) return Hint.REVERSER;
        if (state.operatingMode() == OperatingMode.SB) {
            if (!state.serviceAvailable()) return Hint.SERVICE_UNAVAILABLE;
            if (!state.positionKnown()) return Hint.POSITION_WAIT;
            if ("SR_PENDING".equals(state.authorityReason())) return Hint.SR_PENDING;
            if (state.demandAvailable()) return state.protectionMode() == ProtectionMode.ACTIVE
                    ? Hint.DEMAND : state.protectionMode() == ProtectionMode.SHADOW
                            ? Hint.SHADOW_DEMAND : Hint.SERVICE_UNAVAILABLE;
            return Hint.MA_WAITING;
        }
        return state.emergencyBrake() && (state.operatingMode() == OperatingMode.FS
                || state.operatingMode() == OperatingMode.SH || state.operatingMode() == OperatingMode.SR)
                ? Hint.BRAKE_HELD : Hint.NONE;
    }

    Hint next(State state, long now, long repeatMillis) {
        Hint hint = select(state);
        if (hint == Hint.NONE) { previous = Hint.NONE; return Hint.NONE; }
        long elapsed = now - lastSent.getOrDefault(hint, Long.MIN_VALUE / 2);
        long minimum = hint == previous ? repeatMillis : Math.min(repeatMillis, 10_000L);
        previous = hint;
        if (elapsed < minimum) return Hint.NONE;
        lastSent.put(hint, now);
        return hint;
    }
}
