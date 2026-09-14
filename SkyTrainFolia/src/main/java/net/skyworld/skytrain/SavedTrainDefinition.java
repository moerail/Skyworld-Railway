package net.skyworld.skytrain;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class SavedTrainDefinition {
    final String name;
    final int memberCount;
    final double targetSpeed;
    final double maxSpeed;
    final double spacing;
    final TrainProperties properties;

    SavedTrainDefinition(String name, int memberCount, double targetSpeed, double maxSpeed, double spacing, TrainProperties properties) {
        this.name = name;
        this.memberCount = memberCount;
        this.targetSpeed = targetSpeed;
        this.maxSpeed = maxSpeed;
        this.spacing = spacing;
        this.properties = properties.copy();
    }

    static SavedTrainDefinition fromTrain(String name, Train train) {
        return new SavedTrainDefinition(
                name,
                Math.max(1, train.memberCount()),
                train.targetSpeed,
                train.maxSpeed,
                train.spacing,
                train.properties());
    }

    static SavedTrainDefinition load(String name, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new SavedTrainDefinition(
                name,
                Math.max(1, section.getInt("member-count", 1)),
                section.getDouble("target-speed", 0.35),
                section.getDouble("max-speed", 0.9),
                section.getDouble("spacing", 1.10),
                TrainProperties.load(section.getConfigurationSection("properties")));
    }

    void save(YamlConfiguration config, String path) {
        config.set(path + ".member-count", memberCount);
        config.set(path + ".target-speed", targetSpeed);
        config.set(path + ".max-speed", maxSpeed);
        config.set(path + ".spacing", spacing);
        properties.save(config, path + ".properties");
    }
}
