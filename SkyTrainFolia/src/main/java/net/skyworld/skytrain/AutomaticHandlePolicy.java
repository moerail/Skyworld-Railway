package net.skyworld.skytrain;

import org.bukkit.configuration.ConfigurationSection;

/** Normal cab handles only: authority, ATP and vehicle physics remain external constraints. */
final class AutomaticHandlePolicy {
    record Settings(int maxPower, int serviceBrake) {
        static Settings read(ConfigurationSection c) {
            return new Settings(notch(c, "automatic-driving.max-power-notch", 4, 1, 4),
                    notch(c, "automatic-driving.service-brake-notch", 6, 1, 7));
        }
        private static int notch(ConfigurationSection c, String key, int fallback, int min, int max) {
            Object value = c.get(key);
            if (!(value instanceof Number n)) return fallback;
            double d = n.doubleValue();
            return Double.isFinite(d) && d == Math.rint(d) && d >= min && d <= max ? (int) d : fallback;
        }
    }
    record Handles(int power, int brake, double target) {}

    static Handles select(VehicleProfile vehicle, Settings settings, double speed, double cruise,
            Double atpLimit, double remaining, boolean stop) {
        if (!Double.isFinite(speed) || !Double.isFinite(cruise) || !Double.isFinite(remaining)
                || speed < 0 || cruise < 0 || remaining < 0
                || (atpLimit != null && (!Double.isFinite(atpLimit) || atpLimit < 0)))
            return new Handles(0, 7, 0);
        double target = Math.min(cruise, vehicle.maxSpeed());
        if (atpLimit != null) target = Math.min(target, atpLimit);
        if (stop) {
            // Conservative service envelope, with two physics ticks for handle reaction.
            // No invented minimum deceleration when the selected profile has no braking.
            double a = .85 * vehicle.brakeAcceleration(settings.serviceBrake());
            double distance = Math.max(0, remaining - .04);
            double approach = a <= 0 ? 0 : Math.sqrt(4 * a * a + 2 * a * distance) - 2 * a;
            target = Math.min(target, approach);
        }
        if (target < .001) return new Handles(0, 7, target);
        double error = target - speed;
        if (error < -.002) return new Handles(0, settings.serviceBrake(), target);
        int power = error > .04 ? 4 : error > .02 ? 3 : error > .008 ? 2 : error > .004 ? 1 : 0;
        return new Handles(Math.min(power, settings.maxPower()), 0, target);
    }
}
