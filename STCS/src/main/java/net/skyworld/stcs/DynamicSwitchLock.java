package net.skyworld.stcs;

import org.bukkit.configuration.ConfigurationSection;

/** FS point eligibility derived from current speed; route search and MA caps remain independent. */
record DynamicSwitchLock(boolean enabled, double leadSeconds, double decelerationMps2, double bufferMeters) {
    static final DynamicSwitchLock DEFAULT = new DynamicSwitchLock(true, 5, 1, 25);
    private static final String ROOT = "ma.dynamic-lock.";

    DynamicSwitchLock {
        validate("lead-seconds", leadSeconds, 0, 60);
        validate("deceleration-mps2", decelerationMps2, .01, 100);
        validate("buffer-meters", bufferMeters, 0, 1000);
    }

    static DynamicSwitchLock load(ConfigurationSection config) {
        Object enabled = config.get(ROOT + "enabled", DEFAULT.enabled());
        if (!(enabled instanceof Boolean flag))
            throw new IllegalArgumentException(ROOT + "enabled must be a boolean");
        return new DynamicSwitchLock(flag,
                read(config, "lead-seconds", DEFAULT.leadSeconds()),
                read(config, "deceleration-mps2", DEFAULT.decelerationMps2()),
                read(config, "buffer-meters", DEFAULT.bufferMeters()));
    }

    MaSettings forSpeed(MaSettings base, double speedMps) {
        if (!enabled || !Double.isFinite(speedMps)) return base;
        double v = Math.abs(speedMps);
        double distance = Math.max(base.lockDistanceMeters(),
                Math.min(10000, v * v / (2 * decelerationMps2) + v * leadSeconds + bufferMeters));
        return new MaSettings(base.lookAheadMeters(), distance, base.maxAuthorityDistanceMeters(), base.marginMeters());
    }

    private static double read(ConfigurationSection config, String key, double fallback) {
        Object value = config.get(ROOT + key, fallback);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(ROOT + key + " must be a number");
        return number.doubleValue();
    }

    private static void validate(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(ROOT + key + " must be finite and between " + min + " and " + max);
    }
}
