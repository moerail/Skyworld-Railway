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
    boolean inheritTargetSpeed;
    double initialSpeed;
    boolean coasting;
    private boolean brakingAnnounced;
    private double brakingEntrySpeed;
    private boolean docking;
    private boolean dockingPower;
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
        docking=false;
        dockingPower=false;
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

    // Integrate a deceleration that tapers from B4 towards B1 over the last eight blocks.
    static double approachSpeed(double distance, double[] brakes, double resistance) {
        double d=Math.max(0,distance);
        double low=Math.max(.000001,brakes[1]+resistance);
        double high=Math.max(low,brakes[4]+resistance);
        return Math.sqrt(2*(low*d+(high-low)*(d-8*Math.log1p(d/8))));
    }

    /** Full-power departure, P1/coast cruising and distance-feedback station braking. */
    int notch(double speed, double target, double[] powers, double[] brakes, double resistance, long now, int previous) {
        if (phase == Phase.WAIT || phase == Phase.HOLD || remaining <= .02 && phase == Phase.APPROACH) return -7;
        if (phase == Phase.APPROACH) {
            if (brakingEntrySpeed == 0 && speed > .003
                    && speed >= approachSpeed(remaining-speed*2,brakes,resistance)) {
                brakingEntrySpeed = speed;
            }
            if (brakingEntrySpeed > 0) {
                if(speed<.001 && remaining>.02) docking=true;
                double horizon=4;
                double desired=approachSpeed(remaining-speed*horizon,brakes,resistance);
                if(docking) {
                    double creep=Math.min(.04,approachSpeed(remaining,brakes,resistance));
                    if(speed<creep*.55) dockingPower=true;
                    if(speed>=creep*.9) dockingPower=false;
                    if(dockingPower && (speed<.001 || speed+Math.max(0,powers[1]-resistance)<desired)) return 1;
                }
                // Coast below the curve, rather than braking to a standstill and reapplying P1.
                double required=Math.max(0,(speed-desired)/horizon-resistance);
                int brake=0;
                double error=required;
                for(int b=1;b<=7;b++) {
                    double candidate=Math.abs(brakes[b]-required);
                    if(candidate<error) { error=candidate; brake=b; }
                }
                int old=Math.max(0,Math.min(7,-previous));
                if(Math.abs(brakes[old]-required)<=error+.00005) brake=old;
                // Late detection/overspeed must not be hidden by notch hysteresis.
                double stopping=speed*speed/(2*Math.max(.001,remaining-.02));
                if(stopping>brakes[7]+resistance) brake=7;
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
