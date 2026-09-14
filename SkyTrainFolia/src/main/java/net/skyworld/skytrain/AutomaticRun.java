package net.skyworld.skytrain;

/** One virtual driver's station action. No Bukkit state crosses this boundary. */
final class AutomaticRun {
    enum Phase { APPROACH, WAIT, DEPART, CRUISE, HOLD }
    final String signKey;
    final AutomaticSignSpec spec;
    final boolean reverse;
    final boolean initialReversed;
    volatile Phase phase = Phase.APPROACH;
    volatile double remaining;
    volatile double departureTravel;
    volatile long until;
    volatile String reason = "Station approach";
    double desiredSpeed;
    boolean graphApproach;
    double initialSpeed;
    boolean coasting;
    private boolean brakingAnnounced;
    private long lastNotchChange;
    private double brakingEntrySpeed;
    private boolean speedHolding;
    private double lastCruiseTarget;
    AutomaticRun(String key, AutomaticSignSpec spec, double distance, boolean reverse, boolean reversed) {
        this.signKey=key; this.spec=spec; this.remaining=Math.max(0,distance);
        this.reverse=reverse; this.initialReversed=reversed;
    }
    void moved(double distance) {
        if(phase==Phase.APPROACH) remaining=Math.max(0,remaining-Math.max(0,distance));
        if(phase==Phase.DEPART) departureTravel+=Math.max(0,distance);
    }
    void hold(String reason) { phase=Phase.HOLD; this.reason=reason; }
    void arrived(long now) {
        phase=Phase.WAIT;
        speedHolding=false;
        lastCruiseTarget=0;
        brakingEntrySpeed=0;
        initialSpeed=0;
        until=spec.direction().isEmpty() && !spec.route() ? Long.MAX_VALUE : now+spec.waitMillis();
        reason="Station wait";
    }
    static double brakingTarget(double distance, double serviceDeceleration, double cap) {
        if(distance<=0.02) return 0;
        return Math.min(cap, Math.max(0.006,Math.sqrt(Math.max(0, 2 * serviceDeceleration * Math.max(0,distance-0.15)))));
    }
    boolean beginBrakingNotice(double speed, int notch) {
        if (brakingAnnounced || phase != Phase.APPROACH || coasting || speed <= 0.003 || notch >= 0) {
            return false;
        }
        brakingAnnounced = true;
        return true;
    }
    static double taperedStopDistance(double speed, double[] brakes, double resistance) {
        double distance = 0;
        for (int b = 1; b <= 7; b++) {
            double high = speed * b / 7, low = speed * (b - 1) / 7;
            distance += (high * high - low * low) / (2 * Math.max(.000001, brakes[b] + resistance));
        }
        return distance;
    }

    /** Full-power departure, P1/coast cruising and speed-banded station braking. */
    int notch(double speed, double target, double[] powers, double[] brakes, double resistance, long now, int previous) {
        if (phase == Phase.WAIT || phase == Phase.HOLD || remaining <= .02 && phase == Phase.APPROACH) return -7;
        if (phase == Phase.APPROACH) {
            if (brakingEntrySpeed == 0 && speed > .003
                    && remaining <= taperedStopDistance(speed, brakes, resistance) + speed * 2 + .15) {
                brakingEntrySpeed = speed;
                lastNotchChange = now;
                return -7;
            }
            if (brakingEntrySpeed > 0) {
                // Recover a small undershoot without jumping back to full traction.
                if (speed < .006 && remaining > .02) return 1;
                int brake = Math.max(1, Math.min(7, (int)Math.ceil(7 * speed / brakingEntrySpeed)));
                double required = speed * speed / (2 * Math.max(.02, remaining - .02));
                while (brake < 7 && brakes[brake] + resistance < required) brake++;
                int old = Math.max(0, -previous);
                if (old > brake && speed > .03) {
                    brake = now - lastNotchChange < 200 ? old : Math.max(brake, old - 1);
                }
                if (brake != old) lastNotchChange = now;
                return -brake;
            }
        }
        if (target <= .001) return -7;
        if (target > lastCruiseTarget + .003) speedHolding = false;
        lastCruiseTarget = target;
        double tolerance = Math.max(.003, Math.min(.02, target * .01));
        if (speed >= target - Math.max(tolerance, powers[4] - resistance)) speedHolding = true;
        // A reduced limit or downhill overspeed still needs service braking.
        if (speed > target + tolerance) {
            double required = (speed - target) / 5;
            for (int b = 1; b <= 7; b++) if (brakes[b] + resistance >= required) return -b;
            return -7;
        }
        if (!speedHolding) return 4;
        if (speed >= target || speed + Math.max(0, powers[1] - resistance) > target) return 0;
        return speed < target - tolerance || previous == 1 ? 1 : 0;
    }
}
