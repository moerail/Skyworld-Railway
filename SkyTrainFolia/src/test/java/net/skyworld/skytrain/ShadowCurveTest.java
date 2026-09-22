package net.skyworld.skytrain;

public final class ShadowCurveTest {
    public static void main(String[] args) {
        var settings = new ShadowCurve.Settings(true, false, 1, 5, 5 / 3.6, 1, 0.8);
        double last = 0;
        for (int i = 0; i <= 20000; i++) {
            double distance = i / 100.0;
            var result = ShadowCurve.calculate(settings, new ShadowCurve.Input(distance, 0, "SHADOW"), 0, 30, 1);
            double speed = result.permittedMps();
            assert speed >= last && speed <= 30;
            if (distance <= 1) assert speed == 0;
            if (distance <= 5) assert speed <= 5 / 3.6;
            assert speed - last < 0.03 : "Discontinuous curve";
            last = speed;
        }
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(-1., 0, "SHADOW"), 0, 30, 1)
                .state().equals("EOA_OVERRUN");
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(1., 0, "SHADOW"), 3, 30, 1)
                .state().equals("OVERSPEED");
        var fresh = ShadowCurve.calculate(settings, new ShadowCurve.Input(50., 0, "SHADOW"), 0, 30, 1);
        var aged = ShadowCurve.calculate(settings, new ShadowCurve.Input(50., 1, "SHADOW", 10), 0, 30, 1);
        assert aged.permittedMps() < fresh.permittedMps();
        // A stationary train must not lose fictitious distance as its report ages.
        for (int i = 0; i <= 150; i++) {
            var stopped = ShadowCurve.calculate(settings, new ShadowCurve.Input(4., i / 100., "SHADOW", 0), 0, 36, 1);
            var baseline = ShadowCurve.calculate(settings, new ShadowCurve.Input(4., 0, "SHADOW", 0), 0, 36, 1);
            assert stopped.permittedMps().equals(baseline.permittedMps());
            // Same physical position represented by old vs refreshed MA must yield the same speed.
            double age = i / 100.;
            double distance = 4 - 0.5 * age;
            var old = ShadowCurve.calculate(settings, new ShadowCurve.Input(4., age, "SHADOW", 0.5), 0.5, 36, 1);
            var renewed = ShadowCurve.calculate(settings, new ShadowCurve.Input(distance, 0, "SHADOW", 0.5), 0.5, 36, 1);
            assert Math.abs(old.permittedMps() - renewed.permittedMps()) < 1e-9;
        }
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(1., 0, "SHADOW"), 1, 36, 1).permittedMps() == 0;
        String[] lines = new String[14];
        for (int i = 0; i < lines.length; i++) lines[i] = "line" + i;
        ShadowCurveDisplay.limitSecond(lines);
        assert lines[0].equals("line0") && lines[1].equals("line12") && lines[13].equals("line13");
        for (int i = 2; i <= 12; i++) assert lines[i].equals("line" + (i - 1));
        for (var language : UiLanguage.values()) {
            assert ShadowCurveDisplay.bossTitle("MA", language, 5 / 3.6).endsWith("5.0 km/h");
            assert ShadowCurveDisplay.bossTitle("MA", language, null).endsWith("--");
        }
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(50., 1.501, "SHADOW"), 0, 30, 1).permittedMps() == null;
        assert ShadowCurve.calculate(settings, ShadowCurve.Input.unavailable("NO_DRIVER"), 0, 30, 1).permittedMps() == null;
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(Double.NaN, 0, "SHADOW"), 0, 30, 1).permittedMps() == null;
        assert ShadowCurve.calculate(settings, new ShadowCurve.Input(50., 0, "SHADOW"), 0, 30, 0).permittedMps() == null;
        try {
            new ShadowCurve.Settings(true, true, 1, 5, 1, 1, 0.8);
            throw new AssertionError("Unsupported FS activation accepted");
        } catch (IllegalArgumentException expected) { }
        assert new TelemetrySink() {}.shadowCurveInput(null, null, 0, false, "FORWARD").remainingMeters() == null;
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        assert ShadowCurveSettings.load(config).enabled();
        config.set("shadow-atp.enforcement-enabled", true);
        try { ShadowCurveSettings.load(config); throw new AssertionError("FS config accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS shadow curve: monotonicity, approach cap, stop, stale/invalid inputs, optional STA, FS gate");
    }
}
