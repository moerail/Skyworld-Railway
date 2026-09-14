package net.skyworld.skytrain;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class MaSoundSettingsTest {
    public static void main(String[] args) {
        var c = new YamlConfiguration();
        var errors = new ArrayList<String>();
        var defaults = MaSoundSettings.load(c, errors::add);
        assert defaults.size() == 5 && errors.isEmpty();
        assert defaults.get("GRANTED").pitch() == 2 && defaults.get("GRANTED").count() == 2;
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
        System.out.println("PASS MA audio configuration, namespaces, validation, mute and immutable reload snapshot");
    }
}
