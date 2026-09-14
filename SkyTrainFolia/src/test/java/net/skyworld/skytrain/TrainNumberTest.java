package net.skyworld.skytrain;

import org.bukkit.configuration.file.YamlConfiguration;

public final class TrainNumberTest {
    public static void main(String[] args) throws Exception {
        var properties = TrainProperties.defaults();
        assert properties.trainNumber.isEmpty();
        properties.setTrainNumber(" G001 ");
        assert properties.trainNumber.equals("G001");
        var yaml = new YamlConfiguration();
        properties.save(yaml,"train");
        var restored = new YamlConfiguration();
        restored.loadFromString(yaml.saveToString());
        assert TrainProperties.load(restored.getConfigurationSection("train")).trainNumber.equals("G001");
        assert properties.copy().trainNumber.equals("G001");
        for (String bad : new String[]{"x".repeat(33),"a|b","a\nb"}) {
            boolean rejected=false;
            try { properties.setTrainNumber(bad); } catch (IllegalArgumentException ex) {rejected=true;}
            assert rejected && properties.trainNumber.equals("G001");
        }
        properties.setTrainNumber("clear");
        assert properties.trainNumber.isEmpty();
        properties.save(yaml,"train");
        assert TrainProperties.load(yaml.getConfigurationSection("train")).trainNumber.isEmpty();
        assert TrainProperties.load(null).trainNumber.isEmpty();
        System.out.println("PASS train number: set, copy, persistence, legacy defaults, validation and clear");
    }
}
