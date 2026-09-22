package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Per-driver-session Schmitt trigger; a missing curve does not re-arm an active warning. */
final class ShadowSpeedNotice {
    record Settings(double enterGapKmh, double clearGapKmh, double minimumSpeedKmh, long cooldownMillis) {
        Settings {
            if (!Double.isFinite(enterGapKmh) || !Double.isFinite(clearGapKmh)
                    || !Double.isFinite(minimumSpeedKmh) || enterGapKmh < 0 || clearGapKmh <= enterGapKmh
                    || minimumSpeedKmh <= 0 || cooldownMillis < 0)
                throw new IllegalArgumentException("Invalid shadow-atp warning hysteresis");
        }
        static Settings load(ConfigurationSection config) {
            return new Settings(config.getDouble("shadow-atp.warning.enter-gap-kmh", 2),
                    config.getDouble("shadow-atp.warning.clear-gap-kmh", 5),
                    config.getDouble("shadow-atp.warning.minimum-speed-kmh", 0.5),
                    config.getLong("shadow-atp.warning.cooldown-millis", 5000));
        }
    }
    private UUID lease;
    private boolean latched;
    private long lastNotice = Long.MIN_VALUE;

    boolean update(UUID currentLease, Double limitMps, double actualMps, long now, Settings settings) {
        if (!java.util.Objects.equals(lease, currentLease)) {
            lease = currentLease;
            latched = false;
            lastNotice = Long.MIN_VALUE;
        }
        if (lease == null || limitMps == null || !Double.isFinite(limitMps)
                || !Double.isFinite(actualMps) || actualMps < 0 || limitMps < 0) return false;
        double speed = actualMps * 3.6, limit = limitMps * 3.6;
        if (speed < settings.minimumSpeedKmh() || speed <= Math.max(0, limit - settings.clearGapKmh())) {
            latched = false;
            return false;
        }
        if (latched || speed < limit - settings.enterGapKmh()) return false;
        if (lastNotice != Long.MIN_VALUE && (now < lastNotice || now - lastNotice < settings.cooldownMillis()))
            return false;
        latched = true;
        lastNotice = now;
        return true;
    }
}
