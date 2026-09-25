package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ShadowSpeedNoticeTest {
    public static void main(String[] args) {
        var notice = new ShadowSpeedNotice();
        var settings = new ShadowSpeedNotice.Settings(2, 5, 0.5, 0, 1, 40, 2, 5);
        var lease = UUID.randomUUID();
        assert notice.update(lease, 10., 9, settings) == null;
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9.5, settings));
        for (int i = 0; i < 200; i++)
            assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9.4, settings));
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 10., settings));
        assert "OVERSPEED".equals(notice.update(lease, 10., 10.01, settings));
        for (int i = 0; i < 200; i++)
            assert "OVERSPEED".equals(notice.update(lease, 10., 10.1, settings));
        assert "OVERSPEED".equals(notice.update(lease, 10., 9.9, settings));
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9.7, settings));
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9., settings));
        assert notice.update(lease, null, 9, settings) == null;
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9., settings));
        assert notice.update(lease, 10., 8, settings) == null;
        assert notice.update(lease, 10., 9, settings) == null;
        // No cooldown after a genuine clear and re-entry.
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9.5, settings));
        assert notice.update(UUID.randomUUID(), 10., 9, settings) == null;
        assert notice.update(null, 0., 1, settings) == null;
        assert notice.update(lease, Double.NaN, 1, settings) == null;
        assert notice.update(lease, 10., Double.NaN, settings) == null;
        assert notice.update(lease, 0., 0, settings) == null;
        assert "OVERSPEED".equals(notice.update(lease, 0., 1, settings));
        assert notice.update(lease, 0., 0.1, settings) == null;

        var config = new YamlConfiguration();
        config.set("shadow-atp.warning.enter-gap-kmh", 4.0);
        config.set("shadow-atp.warning.clear-gap-kmh", 7.0);
        config.set("shadow-atp.warning.overspeed-enter-margin-kmh", 2.0);
        config.set("shadow-atp.warning.overspeed-clear-gap-kmh", 0.5);
        config.set("shadow-atp.warning.cooldown-millis", 999999);
        var custom = ShadowSpeedNotice.Settings.load(config);
        notice = new ShadowSpeedNotice();
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9., custom));
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 10.4, custom));
        assert "OVERSPEED".equals(notice.update(lease, 10., 10.6, custom));
        assert "NEAR_LIMIT".equals(notice.update(lease, 10., 9.8, custom));
        for (double invalid : new double[] {-1, 7, Double.NaN}) {
            config.set("shadow-atp.warning.overspeed-clear-gap-kmh", invalid);
            try { ShadowSpeedNotice.Settings.load(config); throw new AssertionError("invalid hysteresis accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        var defaults=ShadowSpeedNotice.Settings.load(new YamlConfiguration());
        notice=new ShadowSpeedNotice();
        assert notice.update(lease,40/3.6,25/3.6,defaults)==null;
        assert notice.update(lease,40/3.6,34/3.6,defaults)==null;
        assert "NEAR_LIMIT".equals(notice.update(lease,40/3.6,35/3.6,defaults));
        assert "NEAR_LIMIT".equals(notice.update(lease,40/3.6,33/3.6,defaults));
        assert notice.update(lease,40/3.6,32/3.6,defaults)==null;
        assert "NEAR_LIMIT".equals(notice.update(lease,80/3.6,65/3.6,defaults));
        assert "NEAR_LIMIT".equals(notice.update(lease,80/3.6,63/3.6,defaults));
        assert notice.update(lease,80/3.6,62/3.6,defaults)==null;
        assert "NEAR_LIMIT".equals(notice.update(lease,41/3.6,26/3.6,defaults));
        // A descending limit changes bands: the old high-speed latch must clear.
        assert notice.update(lease,40/3.6,26/3.6,defaults)==null;
        assert "OVERSPEED".equals(notice.update(lease,40/3.6,41/3.6,defaults));
        assert "NEAR_LIMIT".equals(notice.update(lease,40/3.6,39/3.6,defaults));
        assert notice.update(lease,5/3.6,0.,defaults)==null;
        assert "NEAR_LIMIT".equals(notice.update(lease,5/3.6,1/3.6,defaults));
        var lowConfig=new YamlConfiguration();
        lowConfig.set("shadow-atp.warning.low-speed-limit-kmh",50);
        lowConfig.set("shadow-atp.warning.low-speed-enter-gap-kmh",3);
        lowConfig.set("shadow-atp.warning.low-speed-clear-gap-kmh",6);
        var low=ShadowSpeedNotice.Settings.load(lowConfig);
        assert low.enterGap(50)==3 && low.clearGap(50)==6 && low.enterGap(51)==15;
        for(double invalid:new double[]{-1,3,Double.NaN,Double.POSITIVE_INFINITY}) {
            lowConfig.set("shadow-atp.warning.low-speed-clear-gap-kmh",invalid);
            try { ShadowSpeedNotice.Settings.load(lowConfig); throw new AssertionError("invalid low-speed gap accepted"); }
            catch(IllegalArgumentException expected) { }
        }
        System.out.println("PASS continuous warnings, low-speed band including 40, hysteresis, band changes, overspeed priority and configuration");
    }
}
