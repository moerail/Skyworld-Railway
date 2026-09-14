package net.skyworld.skytrain;

/** Per-train sound transitions, independent of the driver input source. */
final class TrainSoundState {
    private int previousBrake = -1;
    private long lastApply = Long.MIN_VALUE;

    synchronized int brakeEvent(int brake, long now, long cooldown, boolean enabled) {
        brake = Math.max(0, Math.min(8, brake));
        int old = previousBrake;
        previousBrake = brake;
        if (!enabled || old < 0 || old == brake) return 0;
        if (old > 0 && brake == 0) return -1;
        if (brake > old && (lastApply == Long.MIN_VALUE || now - lastApply >= cooldown)) {
            lastApply = now;
            return 1;
        }
        return 0;
    }

    static double level(double kmh, double start, double full) {
        double x = Math.max(0, Math.min(1, (kmh - start) / Math.max(1, full - start)));
        return x * x * (3 - 2 * x);
    }
}
