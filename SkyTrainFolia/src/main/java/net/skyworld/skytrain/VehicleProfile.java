package net.skyworld.skytrain;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable, whole-vehicle performance. Minecraft member count is deliberately absent. */
record VehicleProfile(String id, boolean forceMode, double mass, double maxSpeed, double defaultMaxSpeed,
        List<Double> power, List<Double> brakes, double emergency, double rolling,
        double air, double grade, double baseSpeed, double weakeningSpeed, double minimumRatio,
        double reverseSpeed, double autoAcceleration, double autoDeceleration, double autoEmergency) {
    static final double MAX_SPEED = 8.0;
    static final double SPEED_CONVERSION = 72.0;
    static final double ACCELERATION_CONVERSION = 400.0;

    static VehicleProfile parse(String id, ConfigurationSection c) {
        if (number(c, "schema-version", 1, 1) != 1) throw invalid("schema-version");
        String mode = c.getString("physics-mode", "");
        if (!mode.equals("force") && !mode.equals("acceleration")) throw invalid("physics-mode");
        double base = number(c, "traction.base-speed-kmh", 0.072, 576) / SPEED_CONVERSION;
        double weakening = number(c, "traction.field-weakening-speed-kmh", base * SPEED_CONVERSION, 576)
                / SPEED_CONVERSION;
        double max = number(c, "max-speed-kmh", 3.6, 576) / SPEED_CONVERSION;
        double defaultMax = c.contains("default-max-speed-kmh")
                ? number(c, "default-max-speed-kmh", 3.6, max * SPEED_CONVERSION) / SPEED_CONVERSION : max;
        return new VehicleProfile(id, mode.equals("force"), number(c, "mass-tonnes", 1, 5000), max, defaultMax,
                notches(c, "traction.acceleration-mps2.p", 4),
                notches(c, "brake.deceleration-mps2.b", 7),
                acceleration(c, "brake.emergency-mps2"),
                acceleration(c, "resistance.rolling-mps2"),
                acceleration(c, "resistance.air-mps2-at-100-kmh") / Math.pow(100.0 / SPEED_CONVERSION, 2),
                acceleration(c, "resistance.grade-mps2"), base, weakening,
                number(c, "traction.minimum-ratio", 0, 1),
                number(c, "control.direction-change-speed-kmh", 0, 5) / SPEED_CONVERSION,
                acceleration(c, "automatic.acceleration-mps2"),
                acceleration(c, "automatic.deceleration-mps2"),
                acceleration(c, "automatic.emergency-mps2"));
    }

    private static List<Double> notches(ConfigurationSection c, String prefix, int count) {
        java.util.ArrayList<Double> result = new java.util.ArrayList<>();
        result.add(0.0);
        for (int i = 1; i <= count; i++) result.add(acceleration(c, prefix + i));
        return List.copyOf(result);
    }

    private static double acceleration(ConfigurationSection c, String key) {
        return number(c, key, 0, 10000) / ACCELERATION_CONVERSION;
    }

    static double number(ConfigurationSection c, String key, double min, double max) {
        Object value = c.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() < min || n.doubleValue() > max) throw invalid(key);
        return n.doubleValue();
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Invalid/missing vehicle value: " + key);
    }

    double powerAcceleration(int notch) { return power.get(Math.max(0, Math.min(4, notch))); }
    double brakeAcceleration(int notch) { return brakes.get(Math.max(0, Math.min(7, notch))); }
    double tractionForce(int notch) { return mass * powerAcceleration(notch); }
    double brakeForce(int notch) { return mass * brakeAcceleration(notch); }
    double emergencyForce() { return mass * emergency; }
    double rollingForce() { return mass * rolling; }
    double airForceFactor() { return mass * air; }

    double speedLimit(double serverLimit, double trainLimit) {
        return Math.min(maxSpeed, Math.min(serverLimit, trainLimit));
    }
}
