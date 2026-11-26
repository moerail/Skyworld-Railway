package net.skyworld.skytrain;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AutomaticHandlePolicyTest {
    public static void main(String[] args) throws Exception {
        var config = new YamlConfiguration();
        var defaults = AutomaticHandlePolicy.Settings.read(config);
        check(defaults.maxPower() == 4 && defaults.serviceBrake() == 6, "Existing configs adopt P4/B6");
        config.set("automatic-driving.max-power-notch", 2);
        config.set("automatic-driving.service-brake-notch", 4);
        var gentle = AutomaticHandlePolicy.Settings.read(config);
        check(gentle.maxPower() == 2 && gentle.serviceBrake() == 4, "Configurable handle ceilings");
        for (Object bad : new Object[] {0, 9, 2.5, Double.NaN, "4"}) {
            config.set("automatic-driving.max-power-notch", bad);
            config.set("automatic-driving.service-brake-notch", bad);
            check(AutomaticHandlePolicy.Settings.read(config).equals(defaults), "Invalid settings fall back");
        }
        for (String id : VehicleProfiles.BUNDLED) {
            VehicleProfile p;
            try (var reader = new InputStreamReader(AutomaticHandlePolicyTest.class.getResourceAsStream("/vehicles/" + id + ".yml"), StandardCharsets.UTF_8)) {
                var yaml = new YamlConfiguration(); yaml.load(reader); p = VehicleProfile.parse(id, yaml);
            }
            var start = AutomaticHandlePolicy.select(p, defaults, 0, .6, null, 100, false);
            check(start.power() == 4 && start.brake() == 0, "Full normal traction at departure");
            check(AutomaticHandlePolicy.select(p, gentle, 0, .6, null, 100, false).power() == 2, "Power ceiling respected");
            check(AutomaticHandlePolicy.select(p, defaults, .595, .6, null, 100, false).power() == 1, "Taper near cruise");
            var limit = AutomaticHandlePolicy.select(p, defaults, .6, 1, .3, 100, false);
            check(limit.power() == 0 && limit.brake() == 6 && limit.target() == .3, "ATP cap cuts traction and brakes");
            check(AutomaticHandlePolicy.select(p, defaults, .2, .6, null, 0, true).brake() == 7, "Final holding brake");
            check(AutomaticHandlePolicy.select(p, defaults, Double.NaN, .6, null, 10, true).brake() == 7, "Invalid telemetry holds");
            check(clearance(p, defaults) < clearance(p, gentle), id + " clears 30 blocks faster at P4");
            stopRun(p, defaults);
        }
    }
    private static Train train() { return new Train(UUID.randomUUID(), "auto", 0, 8, 1.1); }
    private static int clearance(VehicleProfile p, AutomaticHandlePolicy.Settings settings) {
        Train t = train(); double distance = 0;
        for (int tick = 1; tick < 10000; tick++) {
            var h = AutomaticHandlePolicy.select(p, settings, t.currentSpeed(), .6, null, 100, false);
            distance += step(t, p, h, tick);
            if (distance >= 30) return tick;
        }
        throw new AssertionError("Departure never clears");
    }
    private static void stopRun(VehicleProfile p, AutomaticHandlePolicy.Settings settings) {
        Train t = train(); double remaining = 300; boolean braked = false;
        for (int tick = 1; tick < 10000; tick++) {
            var h = AutomaticHandlePolicy.select(p, settings, t.currentSpeed(), .6, null, remaining, true);
            check(h.power() == 0 || h.brake() == 0, "Never power against brakes");
            braked |= h.brake() == 6;
            double movement = step(t, p, h, tick);
            if (movement > remaining) check(t.currentSpeed() < .025, "No high-speed arrival at movement fence");
            remaining = Math.max(0, remaining - Math.min(movement, remaining));
            if (t.currentSpeed() == 0 && remaining <= .1) {
                check(braked, "Uses stronger service braking before final hold"); return;
            }
        }
        throw new AssertionError(p.id() + " failed to dock: " + remaining + " remaining");
    }
    private static double step(Train t, VehicleProfile p, AutomaticHandlePolicy.Handles h, int tick) {
        return t.updateDrivenForceSpeed(tick * 50L, p.maxSpeed(), p.tractionForce(h.power()), p.brakeForce(h.brake()),
                p.mass(), p.rollingForce(), p.airForceFactor(), p.grade(), 0, p.baseSpeed(), p.weakeningSpeed(),
                p.minimumRatio(), p.autoDeceleration(), p.emergencyForce(), false, h.power() > 0 && h.brake() == 0);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
