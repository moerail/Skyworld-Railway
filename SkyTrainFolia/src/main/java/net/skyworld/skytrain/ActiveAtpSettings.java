package net.skyworld.skytrain;

import org.bukkit.configuration.ConfigurationSection;

final class ActiveAtpSettings {
    static ActiveAtpState.Settings load(ConfigurationSection config, VehicleProfile vehicle, double blocksPerMeter) {
        String profile = config.getString("active-atp.profile", "relaxed");
        if (!"relaxed".equals(profile) && !"strict".equals(profile))
            throw new UiException("active-atp.profile must be relaxed or strict", "active-atp.profile должен быть relaxed или strict");
        String root = "active-atp.profiles." + profile + ".";
        double brakeFactor = config.getDouble(root + "service-brake-factor", .8);
        if (!Double.isFinite(brakeFactor) || brakeFactor <= 0 || brakeFactor > 1)
            throw new UiException("Invalid active ATP brake factor", "Недопустимый коэффициент торможения активной ATP");
        double deceleration = vehicle.brakeAcceleration(7) * 400 / blocksPerMeter * brakeFactor;
        return new ActiveAtpState.Settings(
                config.getDouble(root + "stop-margin-meters", 1),
                config.getDouble(root + "approach-distance-meters", 5),
                config.getDouble(root + "approach-speed-kmh", 5) / 3.6,
                deceleration,
                config.getDouble(root + "reaction-seconds", 1),
                config.getDouble(root + "overspeed-tolerance-kmh", 10) / 3.6,
                Math.round(config.getDouble(root + "overspeed-grace-seconds", 10) * 1000),
                Math.round(config.getDouble(root + "service-to-emergency-seconds", 5) * 1000));
    }
}
