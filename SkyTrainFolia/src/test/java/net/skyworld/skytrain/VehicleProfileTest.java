package net.skyworld.skytrain;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

public final class VehicleProfileTest {
    public static void main(String[] args) throws Exception {
        for (String id : VehicleProfiles.BUNDLED) {
            VehicleProfile p = VehicleProfile.parse(id, preset(id));
            near(p.tractionForce(0), 0, "Neutral traction");
            near(p.brakeForce(0), 0, "Released brake");
            near(p.tractionForce(4) / p.mass(), p.powerAcceleration(4), "Force conversion");
            check(p.powerAcceleration(1) < p.powerAcceleration(4), "Notch progression");
            check(p.brakeAcceleration(7) <= p.emergency(), "EB must exceed service braking");
            near(p.speedLimit(1, 2), 1, "Server ceiling");
            near(p.speedLimit(8, .5), .5, "Stored train ceiling");
            near(p.speedLimit(8, 8), p.maxSpeed(), "Vehicle ceiling");
            double baseline = 0;
            for (int count : new int[] {1, 4, 8, 16}) {
                Train train = train(count);
                for (int t = 1; t <= 400; t++) step(train, p, t, 4, 0, false);
                if (count == 1) baseline = train.currentSpeed();
                near(train.currentSpeed(), baseline, id + " must ignore member count " + count);
            }
            Train run = train(8);
            int elapsed = 0;
            while (run.currentSpeed() < p.maxSpeed() - .000001 && elapsed < 72000) {
                step(run, p, ++elapsed, 4, 0, false);
            }
            check(elapsed < 72000, id + " must reach advertised cap within one hour on level track");
            check(run.currentSpeed() <= p.maxSpeed(), "No overshoot");
            System.out.printf("%s: 0-%.0f km/h %.1f s%n", id, p.maxSpeed() * 72, elapsed / 20.0);
            double beforeBrake = run.currentSpeed();
            step(run, p, ++elapsed, 0, 7, false);
            check(run.currentSpeed() < beforeBrake, "B7 slows down");
            for (int i = 0; i < 10000; i++) step(run, p, ++elapsed, 0, 0, true);
            near(run.currentSpeed(), 0, "EB stops without reversing");
        }
        YamlConfiguration invalid = preset("crh380b");
        invalid.set("mass-tonnes", Double.NaN);
        rejects(() -> VehicleProfile.parse("bad", invalid));
        invalid.set("mass-tonnes", 0);
        rejects(() -> VehicleProfile.parse("bad", invalid));
        invalid.set("mass-tonnes", 460);
        invalid.set("traction.acceleration-mps2.p4", null);
        rejects(() -> VehicleProfile.parse("bad", invalid));
        invalid.set("traction.acceleration-mps2.p4", "fast");
        rejects(() -> VehicleProfile.parse("bad", invalid));
        var root = Files.createTempDirectory("stf-profile-test").toFile();
        rejects(() -> VehicleProfiles.load(root, "../crh380b"));
        rejects(() -> VehicleProfiles.load(root, "missing"));
        migration(root);
    }

    private static void migration(java.io.File root) throws Exception {
        var folder = new java.io.File(root, "vehicles");
        check(folder.mkdir(), "Create fixture directory");
        var file = new java.io.File(root, "config.yml");
        YamlConfiguration old = new YamlConfiguration();
        old.set("settings.config-version", 2);
        old.set("settings.drive-physics-mode", "force");
        old.set("settings.drive-force-reference-mass", 360.0);
        old.set("settings.drive-force-acceleration-p4", .002);
        old.set("settings.tick-interval", 2);
        old.set("messages.prefix", "keep-me");
        old.save(file);
        String original = Files.readString(file.toPath());
        VehicleProfiles.migrate(file, folder);
        var migrated = VehicleProfiles.read(file);
        check(migrated.getInt("settings.config-version") == 3, "Config version");
        check("legacy".equals(migrated.getString("settings.default-vehicle-profile")), "Legacy selection");
        check(migrated.getInt("settings.tick-interval") == 2, "Unrelated settings preserved");
        check("keep-me".equals(migrated.getString("messages.prefix")), "Messages preserved");
        check(!migrated.contains("settings.drive-physics-mode"), "Old settings removed");
        var backups = root.listFiles((dir, name) -> name.startsWith("config.pre-1.5.0."));
        check(backups != null && backups.length == 1, "One backup");
        check(Files.readString(backups[0].toPath()).equals(original), "Exact original backup");
        VehicleProfile legacy = VehicleProfiles.load(folder, "legacy");
        near(legacy.mass(), 360, "Fixed migrated mass");
        near(legacy.defaultMaxSpeed(), .9, "Legacy new-train default limit");
        near(legacy.maxSpeed(), 4, "Legacy adjustable upper limit");
        near(legacy.tractionForce(4), .002 * 360, "Reference force retained");
        near(legacy.tractionForce(1), .45, "Legacy direct force retained");
        near(legacy.rollingForce(), .06, "Legacy rolling force retained");
        near(legacy.airForceFactor(), .018, "Legacy drag retained");
        String once = Files.readString(file.toPath());
        VehicleProfiles.migrate(file, folder);
        check(Files.readString(file.toPath()).equals(once), "Migration idempotent");
        old.set("settings.drive-physics-mode", "acceleration");
        VehicleProfile acceleration = VehicleProfile.parse("legacy", VehicleProfiles.legacy(old));
        check(!acceleration.forceMode(), "Acceleration mode preserved");
        near(acceleration.powerAcceleration(1), .0035, "Acceleration conversion");
        near(acceleration.air(), .002, "Acceleration-mode drag conversion");
        var bundled = preset("minecraft-comfort");
        bundled.save(new java.io.File(folder, "custom.yml"));
        check(VehicleProfiles.load(folder, "custom").id().equals("custom"), "Custom profiles load");
    }

    private static Train train(int count) {
        Train t = new Train(UUID.randomUUID(), "test", 0, 8, 1.1);
        for (int i = 0; i < count; i++) t.addMember(UUID.randomUUID());
        return t;
    }

    private static void step(Train t, VehicleProfile p, int tick, int power, int brake, boolean eb) {
        t.updateDrivenForceSpeed(tick * 50L, p.maxSpeed(), p.tractionForce(power), p.brakeForce(brake),
                p.mass(), p.rollingForce(), p.airForceFactor(), p.grade(), 0,
                p.baseSpeed(), p.weakeningSpeed(), p.minimumRatio(), p.autoDeceleration(),
                p.emergencyForce(), eb, power > 0 && brake == 0 && !eb);
    }

    private static YamlConfiguration preset(String id) throws Exception {
        var resource = VehicleProfileTest.class.getResourceAsStream("/vehicles/" + id + ".yml");
        check(resource != null, "Bundled resource " + id);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            YamlConfiguration c = new YamlConfiguration();
            c.load(reader);
            return c;
        }
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-8, message + ": " + actual + " != " + expected);
    }
}
