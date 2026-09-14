package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class VehicleProfiles {
    static final List<String> BUNDLED = List.of("crh380b", "cr400bf", "keikyu-n1000", "minecraft-comfort");
    private VehicleProfiles() { }

    static YamlConfiguration read(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.load(file);
        return c;
    }

    static VehicleProfile load(File directory, String id) throws IOException, InvalidConfigurationException {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid settings.default-vehicle-profile: " + id);
        }
        File file = new File(directory, id + ".yml");
        if (!file.isFile()) throw new IllegalArgumentException("Vehicle profile not found: " + file);
        return VehicleProfile.parse(id, read(file));
    }

    static boolean oldPhysicsKey(String key) {
        return key.startsWith("drive-") || List.of("max-speed", "max-allowed-speed",
                "acceleration-per-tick", "deceleration-per-tick", "emergency-deceleration-per-tick").contains(key);
    }

    static void migrate(File configFile, File directory) throws IOException, InvalidConfigurationException {
        YamlConfiguration c = read(configFile);
        if (c.getInt("settings.config-version", 0) >= 3) return;
        // Keep the exact original before writing either the migrated config or legacy profile.
        File backup = new File(configFile.getParentFile(), "config.pre-1.5.0." + System.nanoTime() + ".yml");
        Files.copy(configFile.toPath(), backup.toPath());
        if (!c.contains("settings.default-vehicle-profile")) {
            File legacy = new File(directory, "legacy.yml");
            YamlConfiguration converted = legacy(c);
            VehicleProfile.parse("legacy", converted);
            if (legacy.exists()) {
                if (!read(legacy).saveToString().equals(converted.saveToString())) {
                    throw new IllegalArgumentException("Refusing to overwrite " + legacy);
                }
            } else {
                converted.save(legacy);
            }
            c.set("settings.default-vehicle-profile", "legacy");
        }
        if (!c.contains("settings.server-speed-limit-kmh")) {
            double old = Math.max(0.05, Math.min(4, c.getDouble("settings.max-allowed-speed", 4)));
            c.set("settings.server-speed-limit-kmh", old * 72);
        }
        var settings = c.getConfigurationSection("settings");
        if (settings != null) for (String key : List.copyOf(settings.getKeys(false))) {
            if (oldPhysicsKey(key)) settings.set(key, null);
        }
        c.set("settings.config-version", 3);
        c.save(configFile);
    }

    static YamlConfiguration legacy(YamlConfiguration old) {
        YamlConfiguration c = new YamlConfiguration();
        boolean force = "force".equalsIgnoreCase(old.getString("settings.drive-physics-mode", "acceleration"));
        double mass = clamp(old.getDouble("settings.drive-force-reference-mass",
                old.getDouble("settings.drive-train-min-mass", 90)), 1, 5000);
        c.set("schema-version", 1);
        c.set("name", "Migrated legacy (fixed reference consist)");
        c.set("physics-mode", force ? "force" : "acceleration");
        c.set("mass-tonnes", mass);
        double defaultMax = clamp(old.getDouble("settings.max-speed", .9), .05, 4);
        c.set("max-speed-kmh", clamp(old.getDouble("settings.max-allowed-speed", 4), defaultMax, 4) * 72);
        c.set("default-max-speed-kmh", defaultMax * 72);
        double[] powers = { .0035, .0065, .01, .014 };
        double[] forces = { .45, .82, 1.18, 1.55 };
        double[] brakes = { .006, .01, .015, .021, .028, .036, .046 };
        double[] brakeForces = { .55, .90, 1.35, 1.90, 2.60, 3.45, 4.60 };
        for (int i = 1; i <= 4; i++) c.set("traction.acceleration-mps2.p" + i, 400 * (force
                ? forceAcceleration(old, "acceleration-p" + i, "tractive-force-p" + i, forces[i-1], mass)
                : old.getDouble("settings.drive-power-acceleration-p" + i, powers[i-1])));
        for (int i = 1; i <= 7; i++) c.set("brake.deceleration-mps2.b" + i, 400 * (force
                ? forceAcceleration(old, "deceleration-b" + i, "brake-force-b" + i, brakeForces[i-1], mass)
                : old.getDouble("settings.drive-brake-deceleration-b" + i, brakes[i-1])));
        double emergency = force ? forceAcceleration(old, "emergency-deceleration", "emergency-brake-force", 7.8, mass)
                : old.getDouble("settings.emergency-deceleration-per-tick", .08);
        c.set("brake.emergency-mps2", 400 * emergency);
        c.set("resistance.rolling-mps2", 400 * (force
                ? old.getDouble("settings.drive-rolling-resistance-force", .06) / mass
                : old.getDouble("settings.drive-rolling-resistance-per-tick", .0007)));
        c.set("resistance.air-mps2-at-100-kmh", 400 * Math.pow(100.0 / 72, 2) * (force
                ? old.getDouble("settings.drive-force-air-resistance-factor", .018) / mass
                : old.getDouble("settings.drive-air-resistance-factor", .002)));
        c.set("resistance.grade-mps2", 400 * old.getDouble("settings.drive-grade-resistance-factor", .012));
        double base = clamp(old.getDouble("settings.drive-base-speed", .28), .001, 4);
        c.set("traction.base-speed-kmh", base * 72);
        c.set("traction.field-weakening-speed-kmh", clamp(old.getDouble("settings.drive-field-weakening-speed", .58), base, 4) * 72);
        c.set("traction.minimum-ratio", old.getDouble("settings.drive-min-high-speed-traction-ratio", .18));
        c.set("control.direction-change-speed-kmh", old.getDouble("settings.drive-direction-change-speed", .018) * 72);
        c.set("automatic.acceleration-mps2", 400 * old.getDouble("settings.acceleration-per-tick", .02));
        c.set("automatic.deceleration-mps2", 400 * old.getDouble("settings.deceleration-per-tick", .035));
        c.set("automatic.emergency-mps2", 400 * old.getDouble("settings.emergency-deceleration-per-tick", .08));
        return c;
    }

    private static double forceAcceleration(YamlConfiguration c, String referenceKey, String forceKey,
            double fallback, double mass) {
        double reference = c.getDouble("settings.drive-force-" + referenceKey, -1);
        return reference >= 0 ? clamp(reference, 0, 1) : c.getDouble("settings.drive-" + forceKey, fallback) / mass;
    }

    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
}
