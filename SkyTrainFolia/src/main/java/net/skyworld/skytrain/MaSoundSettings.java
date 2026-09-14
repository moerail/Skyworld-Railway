package net.skyworld.skytrain;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;

final class MaSoundSettings {
    record Tone(boolean enabled, String sound, SoundCategory category, float volume, float pitch, int count, long interval) {}
    static Map<String, Tone> load(ConfigurationSection config, Consumer<String> warning) {
        Map<String, Tone> tones = new HashMap<>();
        add(tones, config, warning, "GRANTED", "granted", "block.anvil.land", 1, 2, 2);
        add(tones, config, warning, "CHANGED", "changed", "block.anvil.land", 1, 2, 1);
        add(tones, config, warning, "RELEASED", "released", "block.iron_trapdoor.close", .6f, 1.2f, 1);
        add(tones, config, warning, "SHRINKING", "shrinking", "entity.experience_orb.pickup", 1, 1, 1);
        add(tones, config, warning, "LOW", "low", "block.note_block.pling", 1, 1, 1);
        return Map.copyOf(tones);
    }
    private static void add(Map<String, Tone> result, ConfigurationSection c, Consumer<String> warning,
            String cue, String key, String sound, float volume, float pitch, int count) {
        String p = "ma-sounds." + key + ".";
        Tone fallback = new Tone(true, "minecraft:" + sound, SoundCategory.MASTER, volume, pitch, count, 5);
        try {
            String id = c.getString(p + "sound", fallback.sound()).trim();
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("invalid sound ID");
            double v = c.getDouble(p + "volume", volume), f = c.getDouble(p + "pitch", pitch);
            int n = c.getInt(p + "count", count);
            long interval = c.getLong(p + "interval-ticks", 5);
            if (!Double.isFinite(v) || v < 0 || v > 4 || !Double.isFinite(f) || f < .5 || f > 2
                    || n < 1 || n > 5 || interval < 1 || interval > 200) throw new IllegalArgumentException("value out of range");
            result.put(cue, new Tone(c.getBoolean("ma-sounds.enabled", true) && c.getBoolean(p + "enabled", true), id,
                    SoundCategory.valueOf(c.getString(p + "category", "MASTER").toUpperCase(Locale.ROOT)),
                    (float)v, (float)f, n, interval));
        } catch (IllegalArgumentException ex) {
            warning.accept(p + ex.getMessage() + "; using default sound settings");
            result.put(cue, new Tone(c.getBoolean("ma-sounds.enabled", true) && c.getBoolean(p + "enabled", true),
                    fallback.sound(), fallback.category(), volume, pitch, count, 5));
        }
    }
}
