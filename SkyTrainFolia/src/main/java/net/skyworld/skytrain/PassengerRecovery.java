package net.skyworld.skytrain;

/** Hysteresis for recovery teleports; accessed only by the cart's existing entity task. */
final class PassengerRecovery {
    private long outsideSince;
    private boolean outside;

    boolean shouldRecover(double distance, double softLimit, double hardLimit,
                          long graceNanos, long nowNanos) {
        if (!Double.isFinite(distance)) { reset(); return false; }
        if (distance >= hardLimit) { reset(); return true; }
        if (distance <= softLimit * 0.75) { reset(); return false; }
        if (distance > softLimit && !outside) {
            outside = true;
            outsideSince = nowNanos;
        }
        // A brief excursion which is already recovering must not trigger a teleport.
        return outside && distance > softLimit && nowNanos - outsideSince >= graceNanos;
    }

    void reset() { outside = false; outsideSince = 0L; }
}
