package net.skyworld.stcs;

import org.bukkit.configuration.ConfigurationSection;

/** Distances along the directed route, in metres. These are shadow-model limits, not ATP settings. */
record MaSettings(double lookAheadMeters, double lockDistanceMeters, double maxAuthorityDistanceMeters,
        double marginMeters, boolean checkExitCapacity) {
    MaSettings(double lookAheadMeters, double lockDistanceMeters, double maxAuthorityDistanceMeters, double marginMeters) {
        this(lookAheadMeters, lockDistanceMeters, maxAuthorityDistanceMeters, marginMeters, true);
    }
    MaSettings {
        validate("ma.look-ahead-meters", lookAheadMeters, 10, 10000);
        validate("ma.lock-distance-meters", lockDistanceMeters, 0, 10000);
        validate("ma.max-authority-distance-meters", maxAuthorityDistanceMeters, 1, 10000);
        validate("ma.margin-meters", marginMeters, 0, 100);
    }

    static MaSettings load(ConfigurationSection config) {
        boolean legacy = config.contains("ma.horizon-meters", true);
        double oldHorizon = legacy ? read(config, "ma.horizon-meters", 600) : 600;
        return new MaSettings(
                read(config, "ma.look-ahead-meters", oldHorizon),
                read(config, "ma.lock-distance-meters", legacy ? oldHorizon : 150),
                read(config, "ma.max-authority-distance-meters", legacy ? oldHorizon : 300),
                read(config, "ma.margin-meters", 2),
                readBoolean(config, "ma.check-exit-capacity", true));
    }

    static MaSettings legacy(double horizon, double margin) {
        return legacy(horizon, margin, true);
    }

    static MaSettings legacy(double horizon, double margin, boolean checkExitCapacity) {
        return new MaSettings(horizon, horizon, horizon, margin, checkExitCapacity);
    }

    private static boolean readBoolean(ConfigurationSection config, String path, boolean fallback) {
        if (!config.contains(path, true)) return fallback;
        Object value = config.get(path);
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(path + " must be a boolean");
        return flag;
    }

    private static double read(ConfigurationSection config, String path, double fallback) {
        if (!config.contains(path, true)) return fallback;
        Object value = config.get(path);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(path + " must be a number");
        return number.doubleValue();
    }

    private static void validate(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be finite and between " + min + " and " + max + " metres");
    }
}
