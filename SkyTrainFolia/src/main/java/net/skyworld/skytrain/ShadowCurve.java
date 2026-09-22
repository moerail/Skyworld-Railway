package net.skyworld.skytrain;

/** Experimental, SI-unit, read-only model. Never a traction/brake command. */
final class ShadowCurve {
    record Input(Double remainingMeters, double ageSeconds, String reason, double sourceSpeedMps) {
        Input(Double remainingMeters, double ageSeconds, String reason) { this(remainingMeters, ageSeconds, reason, 0); }
        static Input unavailable(String reason) { return new Input(null, 0, reason); }
    }
    record Result(Double permittedMps, String state) {
        static Result unavailable(String reason) { return new Result(null, reason); }
    }
    record Settings(boolean enabled, boolean enforcementEnabled, double stopMargin,
            double approachDistance, double approachMps, double reactionSeconds, double brakeFactor) {
        Settings {
            if (enforcementEnabled) throw new IllegalArgumentException(
                    "shadow-atp.enforcement-enabled=true is unsupported: no validated FS brake execution chain");
            if (!finite(stopMargin, approachDistance, approachMps, reactionSeconds, brakeFactor)
                    || stopMargin < 0 || approachDistance <= stopMargin || approachMps <= 0
                    || reactionSeconds < 0 || brakeFactor <= 0 || brakeFactor > 1)
                throw new IllegalArgumentException("Invalid shadow-atp curve settings");
        }
    }
    static Result calculate(Settings settings, Input input, double actualMps, double ceilingMps,
            double serviceDecelerationMps2) {
        if (!settings.enabled()) return Result.unavailable("DISABLED");
        if (input == null || input.remainingMeters() == null)
            return Result.unavailable(input == null ? "UNAVAILABLE" : input.reason());
        if (!finite(input.remainingMeters(), input.ageSeconds(), input.sourceSpeedMps(), actualMps, ceilingMps, serviceDecelerationMps2)
                || input.ageSeconds() < 0 || input.ageSeconds() > 1.5 || actualMps < 0
                || input.sourceSpeedMps() < 0 || ceilingMps < 0 || serviceDecelerationMps2 <= 0)
            return Result.unavailable("INVALID_INPUT");
        double deceleration = serviceDecelerationMps2 * settings.brakeFactor();
        // Shadow-only extrapolation from measured speeds, not the vehicle's theoretical ceiling.
        // A certified execution chain would need continuous odometry and uncertainty bounds.
        double distance = input.remainingMeters() - Math.max(actualMps, input.sourceSpeedMps()) * input.ageSeconds();
        double stop = speedForDistance(distance - settings.stopMargin(), 0, deceleration, settings.reactionSeconds());
        double approach = distance <= settings.approachDistance() ? settings.approachMps()
                : speedForDistance(distance - settings.approachDistance(), settings.approachMps(),
                        deceleration, settings.reactionSeconds());
        double permitted = Math.min(ceilingMps, Math.min(stop, approach));
        return new Result(permitted, input.remainingMeters() < 0 ? "EOA_OVERRUN"
                : actualMps > permitted + 0.05 ? "OVERSPEED" : permitted == 0 ? "STOP" : "SHADOW");
    }
    private static double speedForDistance(double distance, double target, double deceleration, double delay) {
        if (distance <= 0) return target;
        // Solve v*t + (v*v-target*target)/(2*a) <= distance.
        double at = deceleration * delay;
        return Math.max(target, Math.sqrt(at * at + target * target + 2 * deceleration * distance) - at);
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
