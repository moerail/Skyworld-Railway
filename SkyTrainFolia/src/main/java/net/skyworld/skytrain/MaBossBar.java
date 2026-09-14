package net.skyworld.skytrain;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/** One private bar per cab session; never broadcasts to passengers or the server. */
final class MaBossBar {
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private boolean shown;
    private boolean closed;

    synchronized void update(Audience viewer, boolean driver, String title, Double remaining, double range) {
        if (closed) return;
        if (!driver) { hide(viewer); return; }
        boolean valid = remaining != null && Double.isFinite(remaining);
        bar.name(Component.text(title));
        bar.progress(progress(remaining, range));
        bar.color(!valid ? BossBar.Color.WHITE : remaining <= 0 ? BossBar.Color.RED : BossBar.Color.BLUE);
        if (!shown) { viewer.showBossBar(bar); shown = true; }
    }

    synchronized void hide(Audience viewer) {
        if (shown) { viewer.hideBossBar(bar); shown = false; }
    }

    synchronized void close(Audience viewer) {
        closed = true;
        hide(viewer);
    }

    static float progress(Double remaining, double range) {
        if (remaining == null || !Double.isFinite(remaining)) return 0;
        if (!Double.isFinite(range) || range <= 0) range = 300;
        return (float) Math.clamp(remaining / range, 0, 1);
    }
}
