package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Driver-only warning priority with hysteresis; missing input suspends but does not re-arm it. */
final class ShadowSpeedNotice {
    record Settings(double enterGapKmh, double clearGapKmh, double minimumSpeedKmh,
            double overspeedEnterMarginKmh, double overspeedClearGapKmh,
            double lowSpeedLimitKmh, double lowSpeedEnterGapKmh, double lowSpeedClearGapKmh) {
        Settings {
            if (!Double.isFinite(enterGapKmh) || !Double.isFinite(clearGapKmh)
                    || !Double.isFinite(minimumSpeedKmh) || enterGapKmh < 0 || clearGapKmh <= enterGapKmh
                    || minimumSpeedKmh <= 0 || !Double.isFinite(overspeedEnterMarginKmh)
                    || !Double.isFinite(overspeedClearGapKmh) || overspeedEnterMarginKmh < 0
                    || overspeedClearGapKmh < 0 || overspeedClearGapKmh >= clearGapKmh
                    || !Double.isFinite(lowSpeedLimitKmh) || lowSpeedLimitKmh < 0
                    || !Double.isFinite(lowSpeedEnterGapKmh) || lowSpeedEnterGapKmh < 0
                    || !Double.isFinite(lowSpeedClearGapKmh) || lowSpeedClearGapKmh <= lowSpeedEnterGapKmh
                    || overspeedClearGapKmh >= lowSpeedClearGapKmh)
                throw new IllegalArgumentException("Invalid shadow-atp warning hysteresis");
        }
        static Settings load(ConfigurationSection config) {
            return new Settings(config.getDouble("shadow-atp.warning.enter-gap-kmh", 15),
                    config.getDouble("shadow-atp.warning.clear-gap-kmh", 18),
                    config.getDouble("shadow-atp.warning.minimum-speed-kmh", 0.5),
                    config.getDouble("shadow-atp.warning.overspeed-enter-margin-kmh", 0),
                    config.getDouble("shadow-atp.warning.overspeed-clear-gap-kmh", 1),
                    config.getDouble("shadow-atp.warning.low-speed-limit-kmh", 40),
                    config.getDouble("shadow-atp.warning.low-speed-enter-gap-kmh", 5),
                    config.getDouble("shadow-atp.warning.low-speed-clear-gap-kmh", 8));
        }
        double enterGap(double limitKmh) { return limitKmh <= lowSpeedLimitKmh ? lowSpeedEnterGapKmh : enterGapKmh; }
        double clearGap(double limitKmh) { return limitKmh <= lowSpeedLimitKmh ? lowSpeedClearGapKmh : clearGapKmh; }
    }
    private UUID lease;
    private boolean latched;
    private boolean overspeed;

    String update(UUID currentLease, Double limitMps, double actualMps, Settings settings) {
        if (!java.util.Objects.equals(lease, currentLease)) {
            lease = currentLease;
            latched = false;
            overspeed = false;
        }
        if (lease == null || limitMps == null || !Double.isFinite(limitMps)
                || !Double.isFinite(actualMps) || actualMps < 0 || limitMps < 0) return null;
        double speed = actualMps * 3.6, limit = limitMps * 3.6;
        if (!aboveClearThreshold(limitMps, actualMps, settings)) {
            latched = false;
            overspeed = false;
            return null;
        }
        if (speed > limit + settings.overspeedEnterMarginKmh()) overspeed = true;
        else if (speed <= Math.max(0, limit - settings.overspeedClearGapKmh())) overspeed = false;
        if (speed >= limit - settings.enterGap(limit)) latched = true;
        return overspeed ? "OVERSPEED" : latched ? "NEAR_LIMIT" : null;
    }

    static boolean aboveClearThreshold(Double limitMps, double actualMps, Settings settings) {
        return limitMps != null && Double.isFinite(limitMps) && limitMps >= 0
                && Double.isFinite(actualMps) && actualMps * 3.6 >= settings.minimumSpeedKmh()
                && actualMps * 3.6 > Math.max(0, limitMps * 3.6 - settings.clearGap(limitMps * 3.6));
    }
}
