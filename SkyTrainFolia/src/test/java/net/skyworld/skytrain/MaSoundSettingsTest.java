package net.skyworld.skytrain;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class MaSoundSettingsTest {
    public static void main(String[] args) {
        var c = new YamlConfiguration();
        var errors = new ArrayList<String>();
        var defaults = MaSoundSettings.load(c, errors::add);
        assert defaults.size() == 9 && errors.isEmpty();
        assert defaults.get("OVERSPEED").sound().equals("minecraft:block.note_block.bit");
        assert defaults.get("OVERSPEED").pitch() == 1.5f && defaults.get("OVERSPEED").interval() == 3;
        var near = defaults.get("NEAR_LIMIT");
        assert near.sound().equals("minecraft:block.note_block.flute") && near.notes() == 12;
        for (int i = 0; i < near.notes(); i++)
            assert Math.abs(near.pitchAt(i) - (i % 2 == 0 ? .7071068 : 1.0594631)) < .000001;
        assert defaults.get("SHRINKING").sound().equals("minecraft:block.note_block.bit");
        assert defaults.get("SHRINKING").notes() == 3;
        assert defaults.get("GRANTED").pitch() == 2 && defaults.get("GRANTED").count() == 2;
        assert defaults.get("ATP_SERVICE").sound().equals("minecraft:block.bell.use");
        assert defaults.get("ATP_EMERGENCY").notes() == 3;
        c.set("ma-sounds.low.sound", "skyworld:atp/warning");
        c.set("ma-sounds.low.count", 3);
        c.set("ma-sounds.low.interval-ticks", 10);
        var custom = MaSoundSettings.load(c, errors::add).get("LOW");
        assert custom.sound().equals("skyworld:atp/warning") && custom.count() == 3 && custom.interval() == 10;
        c.set("ma-sounds.low.sound", "block.anvil.land");
        assert MaSoundSettings.load(c, errors::add).get("LOW").sound().equals("minecraft:block.anvil.land");
        c.set("ma-sounds.low.pitch", Double.NaN);
        c.set("ma-sounds.enabled", false);
        assert !MaSoundSettings.load(c, errors::add).get("LOW").enabled();
        assert !errors.isEmpty();
        c.set("ma-sounds.enabled", true);
        c.set("ma-sounds.low.pitch", 1);
        c.set("ma-sounds.low.category", "invalid");
        assert MaSoundSettings.load(c, errors::add).get("LOW").sound().equals("minecraft:block.note_block.pling");
        c.set("ma-sounds.low.category", "master");
        c.set("ma-sounds.low.sound", "Bad ID");
        assert MaSoundSettings.load(c, errors::add).get("LOW").sound().equals("minecraft:block.note_block.pling");
        assert defaults.get("LOW").enabled();
        var legacy = new YamlConfiguration();
        legacy.addDefault("ma-sounds.near-limit.pitch-sequence", List.of(.7071068, 1.0594631));
        legacy.set("ma-sounds.near-limit.pitch", 1.5);
        legacy.set("ma-sounds.near-limit.count", 1);
        assert MaSoundSettings.load(legacy, errors::add).get("NEAR_LIMIT").notes() == 1;
        assert MaSoundSettings.load(legacy, errors::add).get("NEAR_LIMIT").pitchAt(0) == 1.5f;
        legacy.set("ma-sounds.near-limit.pitch-sequence", List.of(.6, 1.2));
        legacy.set("ma-sounds.near-limit.count", 6);
        assert MaSoundSettings.load(legacy, errors::add).get("NEAR_LIMIT").notes() == 12;
        for (Object invalid : List.of(List.of(), List.of(Double.NaN), List.of(2.1), List.of("bad"), "bad")) {
            legacy.set("ma-sounds.near-limit.pitch-sequence", invalid);
            int before = errors.size();
            assert MaSoundSettings.load(legacy, errors::add).get("NEAR_LIMIT").pitches().equals(near.pitches());
            assert errors.size() > before;
        }
        legacy.set("ma-sounds.near-limit.pitch-sequence", List.of(1, 1, 1, 1, 1));
        legacy.set("ma-sounds.near-limit.count", 16);
        assert MaSoundSettings.load(legacy, errors::add).get("NEAR_LIMIT").notes() == 12;
        try { near.pitches().add(1f); throw new AssertionError("mutable pitches"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("PASS MA audio configuration, namespaces, validation, mute and immutable reload snapshot");
    }
}
