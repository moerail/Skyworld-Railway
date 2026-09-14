package net.skyworld.stcs;

import org.bukkit.configuration.file.YamlConfiguration;

public final class MaSettingsTest {
    static MaSettings load(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return MaSettings.load(config);
    }

    static void reject(String yaml, String key) throws Exception {
        try { load(yaml); throw new AssertionError("Accepted invalid " + key); }
        catch (IllegalArgumentException ex) { assert ex.getMessage().contains(key) : ex; }
    }

    public static void main(String[] args) throws Exception {
        assert load("").equals(new MaSettings(600, 150, 300, 2));
        assert load("ma: {horizon-meters: 240, margin-meters: 3}").equals(new MaSettings(240, 240, 240, 3));
        assert load("ma: {horizon-meters: 240, look-ahead-meters: 500, lock-distance-meters: 60, max-authority-distance-meters: 120}")
                .equals(new MaSettings(500, 60, 120, 2));
        assert load("ma: {horizon-meters: 240, lock-distance-meters: 60}").equals(new MaSettings(240, 60, 240, 2));
        assert load("ma: {lock-distance-meters: 0, max-authority-distance-meters: 1}").lockDistanceMeters() == 0;

        var old = new YamlConfiguration();
        old.loadFromString("ma: {horizon-meters: 240}");
        var defaults = new YamlConfiguration();
        defaults.loadFromString("ma: {look-ahead-meters: 600, lock-distance-meters: 150, max-authority-distance-meters: 300}");
        old.setDefaults(defaults);
        old.options().copyDefaults(true);
        assert MaSettings.load(old).equals(new MaSettings(240, 240, 240, 2)) : "Bundled defaults must not hide explicit legacy values";
        old.set("ma.lock-distance-meters", 75);
        assert MaSettings.load(old).equals(new MaSettings(240, 75, 240, 2));

        reject("ma: {look-ahead-meters: 9}", "ma.look-ahead-meters");
        reject("ma: {look-ahead-meters: .nan}", "ma.look-ahead-meters");
        reject("ma: {lock-distance-meters: -1}", "ma.lock-distance-meters");
        reject("ma: {lock-distance-meters: .inf}", "ma.lock-distance-meters");
        reject("ma: {max-authority-distance-meters: 0}", "ma.max-authority-distance-meters");
        reject("ma: {max-authority-distance-meters: 10001}", "ma.max-authority-distance-meters");
        reject("ma: {max-authority-distance-meters: '100'}", "ma.max-authority-distance-meters");
        reject("ma: {margin-meters: 101}", "ma.margin-meters");
        System.out.println("PASS distance defaults, legacy precedence with Bukkit defaults, and invalid configuration");
    }
}
