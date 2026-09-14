package net.skyworld.skytrain;

import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;

public final class MaBossBarTest {
    static final class Viewer implements Audience {
        int shows, hides;
        BossBar visible;
        @Override public void showBossBar(BossBar bar) { shows++; visible = bar; }
        @Override public void hideBossBar(BossBar bar) { hides++; assert visible == bar; visible = null; }
    }

    public static void main(String[] args) {
        var viewer = new Viewer();
        var bar = new MaBossBar();
        UUID driver = UUID.randomUUID(), passenger = UUID.randomUUID(), seat = UUID.randomUUID();
        bar.update(viewer, DriverSafety.ownsSeat(driver, passenger, seat, seat, true), "Passenger", 80., 300);
        assert viewer.shows == 0;
        bar.update(viewer, DriverSafety.ownsSeat(null, driver, null, seat, true), "Reboarded", 80., 300);
        assert viewer.shows == 0 : "Reboarding alone is not a new driving declaration";
        bar.update(viewer, DriverSafety.ownsSeat(driver, driver, seat, seat, true), "MA 150 m", 150., 300);
        assert viewer.shows == 1 && viewer.visible.progress() == .5f;
        bar.update(viewer, true, "MA 75 m", 75., 300);
        assert viewer.shows == 1 && viewer.visible.progress() == .25f;
        bar.update(viewer, true, "MA 400 m", 400., 300);
        assert viewer.visible.progress() == 1;
        bar.update(viewer, true, "EoA", 0., 300);
        assert viewer.visible.progress() == 0 && viewer.visible.color() == BossBar.Color.RED;
        bar.update(viewer, true, "EoA passed", -1., 300);
        assert viewer.visible.progress() == 0 && viewer.visible.color() == BossBar.Color.RED;
        bar.update(viewer, true, "Stale / released / isolated", null, 300);
        assert viewer.visible.progress() == 0 && viewer.visible.color() == BossBar.Color.WHITE;
        bar.update(viewer, false, "Released", null, 300);
        assert viewer.visible == null && viewer.hides == 1;
        bar.update(viewer, true, "New lease", 300., 300);
        assert viewer.shows == 2;
        bar.close(viewer);
        bar.update(viewer, true, "Late old-session refresh", 300., 300);
        assert viewer.visible == null && viewer.shows == 2;
        bar.close(viewer);
        assert viewer.hides == 2;
        assert MaBossBar.progress(Double.NaN, 300) == 0;
        assert MaBossBar.progress(150., Double.NaN) == .5f;
        assert MaBossBar.progress(150., 0) == .5f;
        var position = new net.skyworld.sta.api.v1.TrackPositionSnapshot(1, "e", "a", "b", 20, 100,
                "Branch", 1234.5, 1000, true, false);
        assert ShadowMaDisplay.eoaLocation(position).equals("Branch / K1+234.50");
        assert ShadowMaDisplay.eoaLocation(null) == null;
        position = new net.skyworld.sta.api.v1.TrackPositionSnapshot(1, "e", "a", "b", 20, 100,
                "Branch", null, 1000, true, false);
        assert ShadowMaDisplay.eoaLocation(position) == null;
        var ui = new UiMessages(null);
        for (var language : UiLanguage.values()) {
            for (String key : new String[]{"protection.speedLimit", "ma.remaining", "ma.overrun", "ma.locationUnknown"}) {
                assert !ui.text(language, key).equals(key);
            }
        }
        System.out.println("PASS driver-only MA bar, lifecycle, fixed scale, unavailable/overrun and four languages");
    }
}
