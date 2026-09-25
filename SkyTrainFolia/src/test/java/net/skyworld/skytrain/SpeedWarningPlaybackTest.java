package net.skyworld.skytrain;

import org.bukkit.configuration.file.YamlConfiguration;

public final class SpeedWarningPlaybackTest {
    public static void main(String[] args) {
        var config = new YamlConfiguration();
        var tones = MaSoundSettings.load(config, message -> { throw new AssertionError(message); });
        var near = tones.get("NEAR_LIMIT");
        var over = tones.get("OVERSPEED");
        var playback = new SpeedWarningPlayback();
        int emitted = 0;
        for (int tick = 0; tick < 240; tick++) {
            Float pitch = playback.tick("NEAR_LIMIT", near);
            if (tick % 5 == 0) {
                assert pitch != null && pitch == near.pitchAt(emitted++);
            } else assert pitch == null;
        }
        assert emitted == 48 : "four twelve-note cycles, no inter-burst gap";
        assert playback.tick("OVERSPEED", over) == over.pitchAt(0);
        for (int tick = 1; tick <= 30; tick++) {
            Float pitch = playback.tick("OVERSPEED", over);
            assert (pitch != null) == (tick % 3 == 0);
        }
        assert playback.tick("NEAR_LIMIT", near) == near.pitchAt(0);
        for (int tick = 0; tick < 100; tick++) assert playback.tick(null, null) == null;
        assert playback.tick("NEAR_LIMIT", near) == near.pitchAt(0);
        config.set("ma-sounds.overspeed.enabled", false);
        var muted = MaSoundSettings.load(config, message -> { throw new AssertionError(message); }).get("OVERSPEED");
        for (int tick = 0; tick < 20; tick++) assert playback.tick("OVERSPEED", muted) == null;
        assert playback.tick("NEAR_LIMIT", near) == near.pitchAt(0);
        System.out.println("PASS gap-free warning loops, immediate priority switching, stop, mute and restart");
    }
}
