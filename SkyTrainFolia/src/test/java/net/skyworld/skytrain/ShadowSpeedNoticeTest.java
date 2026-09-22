package net.skyworld.skytrain;

import java.util.UUID;

public final class ShadowSpeedNoticeTest {
    public static void main(String[] args) {
        var notice = new ShadowSpeedNotice();
        var settings = new ShadowSpeedNotice.Settings(2, 5, 0.5, 5000);
        var lease = UUID.randomUUID();
        assert !notice.update(lease, 10., 9, 0, settings);
        assert notice.update(lease, 10., 9.5, 1, settings);
        assert !notice.update(lease, 10., 9.4, 2, settings);
        assert !notice.update(lease, 10., 10.1, 3, settings);
        assert !notice.update(lease, null, 10.1, 4, settings);
        assert !notice.update(lease, 10., 10.1, 6000, settings);
        assert !notice.update(lease, 10., 8, 6001, settings);
        assert notice.update(lease, 10., 9.5, 6002, settings);
        assert !notice.update(lease, 10., 8, 6003, settings);
        assert !notice.update(lease, 10., 9.5, 6004, settings);
        assert notice.update(lease, 10., 9.5, 12000, settings);
        assert !notice.update(lease, 0., 0, 13000, settings);
        assert notice.update(UUID.randomUUID(), 0., 1, 14000, settings);
        assert !notice.update(null, 0., 1, 15000, settings);
        assert !notice.update(lease, Double.NaN, 1, 15000, settings);
        System.out.println("PASS shadow speed notice: hysteresis, missing input, cooldown, lease reset, stopped silence");
    }
}
