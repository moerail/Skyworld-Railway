package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

/** Owns YAML I/O and its lock; the manager's live registries are replaced only after loading. */
final class TrainPersistence {
    private final File dataFolder;
    private final Logger logger;
    private final TrainSettings settings;
    private final File trainsFile;
    private final File savedTrainsFile;
    private final Map<UUID, Train> trains;
    private final Map<String, UUID> nameIndex;
    private final Map<UUID, UUID> cartIndex;
    private final Map<String, SavedTrainDefinition> savedTrains;
    private final Object dataIoLock = new Object();

    TrainPersistence(File dataFolder, Logger logger, TrainSettings settings, Map<UUID, Train> trains,
            Map<String, UUID> nameIndex, Map<UUID, UUID> cartIndex,
            Map<String, SavedTrainDefinition> savedTrains) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.settings = settings;
        this.trains = trains;
        this.nameIndex = nameIndex;
        this.cartIndex = cartIndex;
        this.savedTrains = savedTrains;
        this.trainsFile = new File(dataFolder, "trains.yml");
        this.savedTrainsFile = new File(dataFolder, "savedtrains.yml");
    }

    void load() {
        synchronized (dataIoLock) {
            Map<String, SavedTrainDefinition> loadedSavedTrains = readSavedTrains();
            Map<UUID, Train> loadedTrains = new LinkedHashMap<>();
            Map<String, UUID> loadedNameIndex = new LinkedHashMap<>();
            Map<UUID, UUID> loadedCartIndex = new LinkedHashMap<>();

            if (trainsFile.exists() && trainsFile.length() > 0L) {
                YamlConfiguration config = loadYaml(trainsFile);
                if (config == null) {
                    return;
                }

                ConfigurationSection root = config.getConfigurationSection("trains");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            ConfigurationSection section = root.getConfigurationSection(key);
                            if (section == null) {
                                continue;
                            }

                            Train train = new Train(
                                    id,
                                    section.getString("name", "train-" + key.substring(0, 8)),
                                    section.getDouble("target-speed", settings.defaultSpeed()),
                                    section.getDouble("max-speed", settings.maxSpeed()),
                                    section.getDouble("spacing", settings.defaultSpacing()),
                                    TrainProperties.load(section.getConfigurationSection("properties")));
                            train.moving = section.getBoolean("moving", false);
                            train.reversed = section.getBoolean("reversed", false);
                            train.driveControlEnabled = section.getBoolean("drive-control-enabled", false);
                            train.protectionMode = ProtectionMode.restore(section.getString("protection-mode", "SHADOW"));
                            train.manualTakeover = section.getBoolean("manual-takeover", train.driveControlEnabled);
                            train.manualReleaseConfirmed = section.getBoolean("manual-release-confirmed", !train.manualTakeover);
                            String lastDriver = section.getString("last-manual-driver", "");
                            try { train.lastManualDriver = lastDriver.isBlank() ? null : UUID.fromString(lastDriver); }
                            catch (IllegalArgumentException ex) { train.lastManualDriver = null; }
                            train.reverser = Reverser.fromStorage(
                                    section.getString("reverser", null),
                                    train.reversed);
                            train.powerNotch = Math.max(0, Math.min(4, section.getInt("power-notch", 0)));
                            train.brakeNotch = Math.max(0, Math.min(7, section.getInt("brake-notch", 0)));
                            train.emergencyBrake = section.getBoolean("emergency-brake", false);
                            train.driverEmergencyHold = section.getBoolean("driver-emergency-hold", false);
                            if (!section.getString("automatic-action", "").isEmpty()
                                    && train.properties().conductionMode.automatic()) {
                                // A prior process's station/route observation must not authorize a restart.
                                train.moving = false;
                                train.powerNotch = 0;
                                train.brakeNotch = 0;
                            }
                            train.pauseUntilMillis = section.getLong("pause-until-millis", 0L);
                            if (train.manualTakeover && !train.manualReleaseConfirmed) DriverSafety.brake(train);
                            train.loadMileage(
                                    section.getString("mileage.line"),
                                    section.getBoolean("mileage.known", false),
                                    section.getDouble("mileage.meters", 0.0),
                                    section.getInt("mileage.travel-sign", 0),
                                    new Vector(
                                            section.getDouble("mileage.travel-direction.x", 0.0),
                                            section.getDouble("mileage.travel-direction.y", 0.0),
                                            section.getDouble("mileage.travel-direction.z", 0.0)),
                                    section.getString("mileage.last-balise"),
                                    section.getDouble("mileage.distance-since-balise-meters",
                                            Double.POSITIVE_INFINITY),
                                    section.getBoolean("mileage.in-signal-range", false));

                            for (String member : section.getStringList("members")) {
                                UUID entityId = UUID.fromString(member);
                                train.addMember(entityId);
                                loadedCartIndex.put(entityId, id);
                            }

                            loadedTrains.put(id, train);
                            loadedNameIndex.put(train.key(), id);
                        } catch (RuntimeException ex) {
                            logger.log(Level.WARNING, "Failed to load train entry " + key, ex);
                        }
                    }
                }
            }

            trains.clear();
            trains.putAll(loadedTrains);
            nameIndex.clear();
            nameIndex.putAll(loadedNameIndex);
            cartIndex.clear();
            cartIndex.putAll(loadedCartIndex);
            savedTrains.clear();
            savedTrains.putAll(loadedSavedTrains);
        }
    }

    void save() {
        synchronized (dataIoLock) {
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                logger.warning("Could not create plugin data folder: " + dataFolder);
                return;
            }

            YamlConfiguration config = new YamlConfiguration();
            for (Train train : trains.values()) {
                String path = "trains." + train.id();
                config.set(path + ".name", train.name());
                config.set(path + ".moving", train.moving);
                config.set(path + ".reversed", train.reversed);
                config.set(path + ".drive-control-enabled", train.driveControlEnabled);
                config.set(path + ".protection-mode", train.protectionMode.name());
                config.set(path + ".manual-takeover", train.manualTakeover);
                config.set(path + ".manual-release-confirmed", train.manualReleaseConfirmed);
                config.set(path + ".last-manual-driver", train.lastManualDriver == null ? "" : train.lastManualDriver.toString());
                config.set(path + ".reverser", train.reverser.name().toLowerCase(Locale.ROOT));
                config.set(path + ".power-notch", train.powerNotch);
                config.set(path + ".brake-notch", train.brakeNotch);
                config.set(path + ".emergency-brake", train.emergencyBrake);
                config.set(path + ".driver-emergency-hold", train.driverEmergencyHold);
                config.set(path + ".automatic-action", train.automaticRun == null ? "" : train.automaticRun.phase.name());
                config.set(path + ".target-speed", train.targetSpeed);
                config.set(path + ".max-speed", train.maxSpeed);
                config.set(path + ".spacing", train.spacing);
                config.set(path + ".pause-until-millis", train.pauseUntilMillis);
                Train.MileagePersistence mileage = train.mileagePersistence();
                config.set(path + ".mileage.line", mileage.lineName());
                config.set(path + ".mileage.known", mileage.known());
                config.set(path + ".mileage.meters", mileage.meters());
                config.set(path + ".mileage.travel-sign", mileage.travelSign());
                config.set(path + ".mileage.travel-direction.x", mileage.travelDirection().getX());
                config.set(path + ".mileage.travel-direction.y", mileage.travelDirection().getY());
                config.set(path + ".mileage.travel-direction.z", mileage.travelDirection().getZ());
                config.set(path + ".mileage.last-balise", mileage.lastBaliseName());
                config.set(path + ".mileage.distance-since-balise-meters",
                        Double.isFinite(mileage.distanceSinceBaliseMeters())
                                ? mileage.distanceSinceBaliseMeters()
                                : null);
                config.set(path + ".mileage.in-signal-range", mileage.inSignalRange());
                config.set(path + ".members", train.members().stream().map(UUID::toString).toList());
                train.properties().save(config, path + ".properties");
            }

            try {
                saveYamlAtomically(config, trainsFile);
                saveSavedTrains();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to save " + trainsFile, ex);
            }
        }
    }

    Map<String, SavedTrainDefinition> readSavedTrains() {
        Map<String, SavedTrainDefinition> loaded = new LinkedHashMap<>();
        if (!savedTrainsFile.exists() || savedTrainsFile.length() == 0L) {
            return loaded;
        }

        YamlConfiguration config = loadYaml(savedTrainsFile);
        if (config == null) {
            return Map.copyOf(savedTrains);
        }
        ConfigurationSection root = config.getConfigurationSection("saved-trains");
        if (root == null) {
            return loaded;
        }

        for (String key : root.getKeys(false)) {
            SavedTrainDefinition saved = SavedTrainDefinition.load(key, root.getConfigurationSection(key));
            if (saved != null) {
                loaded.put(Train.normalizeName(key), saved);
            }
        }
        return loaded;
    }

    void saveSavedTrains() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        for (SavedTrainDefinition saved : savedTrains.values()) {
            saved.save(config, "saved-trains." + saved.name);
        }
        saveYamlAtomically(config, savedTrainsFile);
    }

    YamlConfiguration loadYaml(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = backupUnreadableYaml(file);
            logger.log(Level.WARNING,
                    "Could not load " + file + ". The unreadable file was backed up to " + backup + ".", ex);
            return null;
        }
    }

    File backupUnreadableYaml(File file) {
        File backup = new File(file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveError) {
            logger.log(Level.WARNING, "Failed to move unreadable YAML file " + file + " to backup.", moveError);
        }
        return backup;
    }

    void saveYamlAtomically(YamlConfiguration config, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }

        File temporary = new File(parent, target.getName() + ".tmp");
        config.save(temporary);
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
