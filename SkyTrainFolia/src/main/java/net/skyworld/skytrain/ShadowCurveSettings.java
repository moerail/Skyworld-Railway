package net.skyworld.skytrain;

import org.bukkit.configuration.ConfigurationSection;

final class ShadowCurveSettings {
    static ShadowCurve.Settings load(ConfigurationSection config) {
        return new ShadowCurve.Settings(config.getBoolean("shadow-atp.enabled", true),
                config.getBoolean("shadow-atp.enforcement-enabled", false),
                config.getDouble("shadow-atp.stop-margin-meters", 1),
                config.getDouble("shadow-atp.approach-distance-meters", 5),
                config.getDouble("shadow-atp.approach-speed-kmh", 5) / 3.6,
                config.getDouble("shadow-atp.reaction-seconds", 1),
                config.getDouble("shadow-atp.service-brake-factor", 0.8));
    }
}
