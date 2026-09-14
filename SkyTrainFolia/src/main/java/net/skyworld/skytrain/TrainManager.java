package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.papermc.paper.entity.TeleportFlag;

final class TrainManager {
    private final SkyTrainPlugin plugin;
    private final SwitchManager switchManager;
    private final LineInfrastructureManager infrastructureManager;
    private final StationManager stationManager;
    private final AutomaticSigns automaticSigns;
    private final File trainsFile;
    private final File savedTrainsFile;
    private final NamespacedKey trainIdKey;
    private final NamespacedKey trainNameKey;
    private final NamespacedKey memberIndexKey;
    private final ConcurrentMap<UUID, Train> trains = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SavedTrainDefinition> savedTrains = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> cartIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScheduledTask> memberTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Minecart> managedCarts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Boolean>> pendingTeleports = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PassengerRecovery> passengerRecoveries = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.LongAdder passengerRecoveryTeleports = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedMoves = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedPassengerMoves = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedMoveFallbacks = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder physicalTeleports = new java.util.concurrent.atomic.LongAdder();
    private volatile boolean ownedMoverFailed;

    String motionSyncStatus() {
        return (plugin.displaySync() == null ? "display=vanilla" : plugin.displaySync().status())
                + ", passenger-recoveries=" + passengerRecoveryTeleports.sum()
                + ", owned-moves=" + ownedMoves.sum() + ", owned-passenger-moves=" + ownedPassengerMoves.sum()
                + ", owned-fallbacks=" + ownedMoveFallbacks.sum() + ", physical-teleports=" + physicalTeleports.sum()
                + ", pending-teleports=" + pendingTeleports.size();
    }

    private void forgetDisplay(UUID entityId) {
        passengerRecoveries.remove(entityId);
        if (plugin.displaySync() != null) plugin.displaySync().forget(entityId);
    }
    private final ConcurrentMap<UUID, UUID> driverTargets = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> trainDrivers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> driverSeats = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> driverNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> driverLeaseIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScheduledTask> driverChecks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> consistLabelVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConsistLabelState> consistLabelStates = new ConcurrentHashMap<>();
    private final Set<UUID> pendingAutoLinks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> retiringCarts = ConcurrentHashMap.newKeySet();
    private final TrainChunkLoader chunkLoader;
    private final StcsBridge stcsTelemetry;
    private final Object dataIoLock = new Object();
    private final Object driverLock = new Object();
    private volatile ScheduledTask autosaveTask;

    TrainManager(SkyTrainPlugin plugin, SwitchManager switchManager,
            LineInfrastructureManager infrastructureManager, StationManager stationManager) {
        this.plugin = plugin;
        this.switchManager = switchManager;
        this.infrastructureManager = infrastructureManager;
        this.stationManager = stationManager;
        this.automaticSigns = new AutomaticSigns(plugin, this, stationManager);
        this.chunkLoader = new TrainChunkLoader(plugin);
        this.stcsTelemetry = new StcsBridge(plugin);
        this.trainsFile = new File(plugin.getDataFolder(), "trains.yml");
        this.savedTrainsFile = new File(plugin.getDataFolder(), "savedtrains.yml");
        this.trainIdKey = new NamespacedKey(plugin, "train_id");
        this.trainNameKey = new NamespacedKey(plugin, "train_name");
        this.memberIndexKey = new NamespacedKey(plugin, "member_index");
    }

    int trainCount() {
        return trains.size();
    }

    SkyTrainPlugin plugin() {
        return plugin;
    }

    Collection<Train> trains() {
        return List.copyOf(trains.values());
    }

    Train train(String name) {
        if (name == null) {
            return null;
        }
        UUID id = nameIndex.get(Train.normalizeName(name));
        return id == null ? null : trains.get(id);
    }

    List<String> trainNames() {
        return trains.values().stream()
                .map(Train::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    int clearLineMileage(String lineName) {
        String lineKey = InfrastructureMarker.normalizeLine(lineName);
        int cleared = 0;
        for (Train train : trains.values()) {
            if (train.clearMileageForLine(lineKey)) {
                cleared++;
            }
        }
        if (cleared > 0) {
            save();
        }
        return cleared;
    }

    List<String> savedTrainNames() {
        return savedTrains.values().stream()
                .map(saved -> saved.name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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
                                    section.getDouble("target-speed", defaultSpeed()),
                                    section.getDouble("max-speed", maxSpeed()),
                                    section.getDouble("spacing", defaultSpacing()),
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
                            plugin.getLogger().log(Level.WARNING, "Failed to load train entry " + key, ex);
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
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder: " + plugin.getDataFolder());
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
                plugin.getLogger().log(Level.WARNING, "Failed to save " + trainsFile, ex);
            }
        }
    }

    private Map<String, SavedTrainDefinition> readSavedTrains() {
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

    private void saveSavedTrains() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        for (SavedTrainDefinition saved : savedTrains.values()) {
            saved.save(config, "saved-trains." + saved.name);
        }
        saveYamlAtomically(config, savedTrainsFile);
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = backupUnreadableYaml(file);
            plugin.getLogger().log(Level.WARNING,
                    "Could not load " + file + ". The unreadable file was backed up to " + backup + ".", ex);
            return null;
        }
    }

    private File backupUnreadableYaml(File file) {
        File backup = new File(file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveError) {
            plugin.getLogger().log(Level.WARNING, "Failed to move unreadable YAML file " + file + " to backup.", moveError);
        }
        return backup;
    }

    private void saveYamlAtomically(YamlConfiguration config, File target) throws IOException {
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

    void startAutosave() {
        automaticSigns.start();
        ScheduledTask previous = autosaveTask;
        if (previous != null && !previous.isCancelled()) {
            previous.cancel();
        }

        long period = Math.max(200L, plugin.getConfig().getLong("settings.autosave-interval-ticks", 6000L));
        autosaveTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> save(),
                period * 50L,
                period * 50L,
                TimeUnit.MILLISECONDS);
    }

    void shutdown() {
        automaticSigns.stop();
        if (plugin.displaySync() != null) plugin.displaySync().reset();
        passengerRecoveries.clear();
        ScheduledTask autosave = autosaveTask;
        if (autosave != null && !autosave.isCancelled()) {
            autosave.cancel();
        }
        autosaveTask = null;

        for (Train train : trains.values()) {
            stcsTelemetry.remove(train.id());
        }
        stcsTelemetry.clear();

        for (ScheduledTask task : memberTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        memberTasks.clear();
        managedCarts.clear();
        pendingTeleports.clear();
        pendingAutoLinks.clear();
        retiringCarts.clear();
        chunkLoader.shutdown();
        synchronized (driverLock) {
            for (UUID driver : List.copyOf(driverTargets.keySet())) revokeDriver(driver, null, "SERVER_STOP");
            driverTargets.clear();
            trainDrivers.clear();
            driverSeats.clear();
            driverNames.clear();
            driverLeaseIds.clear();
            driverChecks.values().forEach(ScheduledTask::cancel);
            driverChecks.clear();
        }
        consistLabelVersions.clear();
        consistLabelStates.clear();
    }

    void reloadAll() {
        if (trains.values().stream().anyMatch(t -> Math.max(t.currentSpeed(), t.maxMemberSpeed()) > 0.001)) {
            throw new IllegalArgumentException("Stop all trains before reloading vehicle performance.");
        }
        plugin.reloadVehicleConfiguration();
        save();
        shutdown();
        load();
        startAutosave();
    }

    Train createTrain(String name, List<Minecart> carts) {
        String cleanName = cleanName(name);
        if (nameIndex.containsKey(Train.normalizeName(cleanName))) {
            throw new IllegalArgumentException("列车名已经存在: " + cleanName);
        }
        if (carts.isEmpty()) {
            throw new IllegalArgumentException("附近没有可用矿车");
        }

        Train train = new Train(UUID.randomUUID(), cleanName, defaultSpeed(), maxSpeed(), defaultSpacing());
        trains.put(train.id(), train);
        nameIndex.put(train.key(), train.id());

        appendCarts(train, carts);
        arrangeTrainCarts(train, carts, carts.get(0).getLocation());
        save();
        return train;
    }

    AutomaticSigns automaticSigns() { return automaticSigns; }

    boolean automaticEligible(Train train) {
        if (train != null && (train.protectionMode == ProtectionMode.ISOLATED || train.protectionMode == ProtectionMode.RECOVERING)) return false;
        return automaticStatus(train).equals("ready");
    }

    String automaticStatus(Train train) {
        synchronized (driverLock) {
            if (train == null || trains.get(train.id()) != train) return "unavailable";
            return automaticStatus(train.properties().conductionMode.automatic(),
                    trainDrivers.containsKey(train.id()), train.manualTakeover,
                    train.manualReleaseConfirmed, train.driveControlEnabled, train.emergencyBrake);
        }
    }

    static String automaticStatus(boolean auto, boolean driver, boolean takeover,
            boolean released, boolean handle, boolean eb) {
        if (driver) return "driver";
        if (takeover && !released) return "release";
        if (takeover) return "rearm";
        if (eb) return "eb";
        if (handle) return "handle";
        return auto ? "ready" : "manual";
    }

    static boolean automaticAllowed(boolean auto, boolean driver, boolean takeover, boolean handle, boolean eb) {
        return auto && !driver && !takeover && !handle && !eb;
    }

    boolean destroyAutomatic(Train train) {
        synchronized (driverLock) {
            if (!automaticEligible(train)) return false;
            retireTrain(train);
            return true;
        }
    }

    void stationOutput(String signKey,java.util.function.Consumer<Boolean> update) {
        synchronized(driverLock) {
            boolean waiting=trains.values().stream().anyMatch(train -> {
                AutomaticRun run=train.automaticRun;
                return run!=null && run.signKey.startsWith(signKey+":")
                        && run.phase==AutomaticRun.Phase.WAIT && automaticEligible(train);
            });
            update.accept(waiting);
        }
    }

    boolean switchLever(Block block) {
        var position=SwitchBlockPosition.of(block);
        for(var sw:switchManager.switchesSnapshot()) {
            if(position.equals(sw.actuator())) return true;
            for(var center:List.of(sw.sign,sw.support)) if(position.worldName().equals(center.worldName())
                    && Math.abs(position.x()-center.x())<=1 && Math.abs(position.y()-center.y())<=1
                    && Math.abs(position.z()-center.z())<=1) return true;
        }
        return false;
    }

    void validateSpawnPattern(String pattern) {
        SpawnPattern.Parsed parsed = SpawnPattern.parse(pattern,
                savedTrains.values().stream().map(s -> s.name).toList());
        validateSpawnParts(parsed.parts());
    }

    private void validateSpawnParts(List<SpawnPattern.Part> parts) {
        for (var part : parts) {
            if (part.name() == null) { validateSpawnParts(part.children()); continue; }
            if (part.name().length() == 1 && "mspht".contains(part.name())) continue;
            SavedTrainDefinition saved = savedTrains.get(Train.normalizeName(part.name()));
            if (saved == null || !saved.properties.conductionMode.automatic()) {
                throw new IllegalArgumentException("Spawn template must be explicitly saved in auto mode: " + part.name());
            }
        }
    }

    void spawnAutomatic(AutomaticSignSpec spec, Block rail, Vector preferred, boolean centered) {
        validateSpawnPattern(spec.pattern());
        int limit = Math.max(1, Math.min(1024, plugin.getConfig().getInt("settings.sign-spawn-max-carts", 64)));
        var parsed = SpawnPattern.parse(spec.pattern(), savedTrains.values().stream().map(s -> s.name).toList());
        var tokens = parsed.expand(java.util.concurrent.ThreadLocalRandom.current(), limit);
        List<Class<? extends Minecart>> types = new ArrayList<>();
        SavedTrainDefinition template = null;
        for (String token : tokens) {
            Class<? extends Minecart> type = switch (token) {
                case "m" -> Minecart.class;
                case "s" -> org.bukkit.entity.minecart.StorageMinecart.class;
                case "p" -> org.bukkit.entity.minecart.PoweredMinecart.class;
                case "h" -> org.bukkit.entity.minecart.HopperMinecart.class;
                case "t" -> org.bukkit.entity.minecart.ExplosiveMinecart.class;
                default -> null;
            };
            if (type != null) types.add(type);
            else {
                SavedTrainDefinition saved = savedTrains.get(Train.normalizeName(token));
                if (saved == null || !saved.properties.conductionMode.automatic()) throw new IllegalArgumentException("Template no longer automatic");
                if (template == null) template = saved;
                if (types.size() + saved.memberCount > limit) throw new IllegalArgumentException("Spawn cart limit exceeded");
                for (int i = 0; i < saved.memberCount; i++) types.add(Minecart.class);
            }
        }
        if (types.isEmpty() || types.size() > limit) throw new IllegalArgumentException("Invalid spawn length");
        double spacing = template == null ? defaultSpacing() : template.spacing;
        double length = spacing * (types.size() - 1);
        VanillaRailWalker walker = VanillaRailWalker.at(rail.getLocation().add(.5,.0625,.5), preferred);
        if (walker == null) throw new IllegalArgumentException("No spawn rail");
        double forward = switch (parsed.center()) {
            case MIDDLE -> length * .5;
            case LEFT -> 0;
            case RIGHT -> length;
            case NONE -> centered ? length * .5 : length;
        };
        if (!walker.move(forward)) throw new IllegalArgumentException("Insufficient loaded rail ahead");
        List<Location> locations = new ArrayList<>();
        Vector travel = walker.direction();
        walker.invert();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0 && !walker.move(spacing)) throw new IllegalArgumentException("Insufficient loaded rail behind");
            Location location = walker.position();
            for (int dx : new int[]{-2,2}) for (int dz : new int[]{-2,2}) {
                if (!RailSignAccess.readable(location.getBlock().getRelative(dx,0,dz)))
                    throw new IllegalArgumentException("Spawn area crosses unavailable region");
            }
            if (!location.getWorld().getNearbyEntities(location, 1, 1, 1, e -> e instanceof Minecart).isEmpty())
                throw new IllegalArgumentException("Spawn track occupied");
            Vector facing = walker.direction().multiply(-1);
            location.setYaw(yawFromDirection(facing));
            locations.add(location);
        }
        List<Minecart> carts = new ArrayList<>();
        Train created = null;
        try {
            for (int i = 0; i < types.size(); i++) {
                Minecart cart = locations.get(i).getWorld().spawn(locations.get(i), types.get(i));
                if (!cart.isValid()) throw new IllegalArgumentException("Minecart spawn was cancelled");
                carts.add(cart);
            }
            TrainProperties properties = template == null ? TrainProperties.defaults() : template.properties.copy();
            properties.conductionMode = ConductionMode.AUTOMATIC;
            created = new Train(UUID.randomUUID(), generateAutoTrainName(),
                    template == null ? defaultSpeed() : template.targetSpeed,
                    template == null ? maxSpeed() : template.maxSpeed, spacing, properties);
            trains.put(created.id(), created); nameIndex.put(created.key(), created.id());
            appendCarts(created, carts);
            created.reversed = spec.speed() < 0;
            created.reverser = created.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
            created.rememberDirection(created.reversed ? travel.clone().multiply(-1) : travel);
            double speed = Math.min(created.maxSpeed, Math.abs(spec.speed()));
            created.targetSpeed = speed > 0 ? speed : created.targetSpeed;
            created.seedCurrentSpeed(speed);
            created.moving = speed > 0;
            save();
        } catch (RuntimeException ex) {
            if (created != null) retireTrain(created);
            else for (Minecart cart : carts) cart.remove();
            throw ex;
        }
    }

    int appendCarts(String name, List<Minecart> carts) {
        Train train = requireTrain(name);
        int added = appendCarts(train, carts);
        if (added > 0 && !carts.isEmpty()) {
            arrangeTrainCarts(train, carts, carts.get(0).getLocation());
        }
        save();
        return added;
    }

    private int appendCarts(Train train, List<Minecart> carts) {
        int added = 0;
        Set<UUID> seen = new HashSet<>();
        for (Minecart cart : carts) {
            if (cart == null || !cart.isValid() || cart.isDead() || !seen.add(cart.getUniqueId())) {
                continue;
            }

            removeCart(cart, false);
            if (train.addMember(cart.getUniqueId())) {
                markCart(cart, train, train.memberCount() - 1);
                ensureTask(cart, train);
                added++;
            }
        }
        if (added > 0) {
            train.clearMemberTargets();
            train.clearTrackPath();
        }
        return added;
    }

    int connectNearby(Player player, double radius) {
        List<Minecart> carts = scanNearby(player, radius);
        return connectCarts(carts, player.getLocation(), true);
    }

    void scheduleAutoLink(Minecart cart) {
        if (!autoLinkEnabled() || cart == null || !cart.isValid() || cart.isDead()) {
            return;
        }

        UUID entityId = cart.getUniqueId();
        if (!pendingAutoLinks.add(entityId)) {
            return;
        }

        try {
            cart.getScheduler().runDelayed(
                    plugin,
                    task -> {
                        try {
                            autoLink(cart);
                        } finally {
                            pendingAutoLinks.remove(entityId);
                        }
                    },
                    () -> pendingAutoLinks.remove(entityId),
                    autoLinkDelayTicks());
        } catch (RuntimeException ex) {
            pendingAutoLinks.remove(entityId);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule minecart auto-link " + entityId, ex);
        }
    }

    private void autoLink(Minecart cart) {
        if (!autoLinkEnabled() || !cart.isValid() || cart.isDead()) {
            return;
        }

        refreshCart(cart);
        List<Minecart> candidates = new ArrayList<>();
        candidates.add(cart);
        candidates.addAll(nearbyMinecarts(cart, autoLinkRadius()));
        connectCarts(candidates, cart.getLocation(), autoLinkCreateSingleCartTrains());
    }

    private int connectCarts(List<Minecart> carts, Location origin, boolean allowSingleCartTrain) {
        List<Minecart> unique = uniqueValidCarts(carts, origin);
        if (unique.isEmpty()) {
            return 0;
        }

        for (Minecart cart : unique) {
            refreshCart(cart);
        }

        Train target = null;
        for (Minecart cart : unique) {
            Train train = trainForCart(cart);
            if (train != null) {
                target = train;
                break;
            }
        }

        if (target == null) {
            if (unique.size() == 1 && !allowSingleCartTrain) {
                return 0;
            }
            Train created = createTrain(generateAutoTrainName(), unique);
            return created.memberCount();
        }

        Train chosenTarget = target;
        List<Minecart> toAppend = unique.stream()
                .filter(cart -> trainForCart(cart) != chosenTarget)
                .toList();
        int added = appendCarts(chosenTarget, toAppend);
        if (added > 0) {
            arrangeTrainCarts(chosenTarget, unique, origin);
            save();
        }
        return added;
    }

    boolean removeTrain(String name) {
        Train train = train(name);
        if (train == null) {
            return false;
        }
        retireTrain(train);
        return true;
    }

    private void retireTrain(Train train) {
        UUID driver = trainDrivers.get(train.id());
        if (driver != null) revokeDriver(driver, null, "VEHICLE_REMOVED");
        automaticSigns.forget(train.id());
        trains.remove(train.id());
        stcsTelemetry.remove(train.id());
        switchManager.releaseTrain(train.id());
        chunkLoader.release(train.id());
        trainDrivers.remove(train.id());
        nameIndex.remove(train.key());
        driverTargets.entrySet().removeIf(entry -> train.id().equals(entry.getValue()));
        for (UUID member : train.members()) {
            cartIndex.remove(member);
            ScheduledTask task = memberTasks.remove(member);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            Minecart cart = managedCarts.remove(member);
            pendingTeleports.remove(member);
            if (cart != null) {
                scheduleCartRemoval(cart);
            }
        }
        save();
    }

    boolean isProtectedTrainCart(Minecart cart) {
        return cart != null && trainForCart(cart) != null && !retiringCarts.contains(cart.getUniqueId());
    }

    Train removeCart(Minecart cart, boolean save) {
        Train train = trainForCart(cart);
        clearCartMark(cart);
        if (train == null) {
            return null;
        }

        UUID entityId = cart.getUniqueId();
        UUID driver = trainDrivers.get(train.id());
        if (driver != null) revokeDriver(driver, entityId, "VEHICLE_REMOVED");
        train.removeMember(entityId);
        train.clearMemberTargets();
        train.clearTrackPath();
        cartIndex.remove(entityId);
        ScheduledTask task = memberTasks.remove(entityId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        managedCarts.remove(entityId);
        pendingTeleports.remove(entityId);

        if (train.memberCount() == 0) {
            trains.remove(train.id());
            nameIndex.remove(train.key());
            switchManager.releaseTrain(train.id());
            chunkLoader.release(train.id());
            trainDrivers.remove(train.id());
        }
        if (save) {
            this.save();
        }
        return train;
    }

    void onCartDestroyed(Minecart cart) {
        Train train = trainForCart(cart);
        if (train == null) {
            return;
        }
        UUID entityId = cart.getUniqueId();
        UUID driver = trainDrivers.get(train.id());
        if (driver != null) revokeDriver(driver, entityId, "VEHICLE_REMOVED");
        if (!train.removeMember(entityId)) return;
        haltForMissingMember(train);
        forgetDisplay(entityId);
        train.clearMemberTargets();
        train.clearTrackPath();
        cartIndex.remove(entityId);
        ScheduledTask task = memberTasks.remove(entityId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        managedCarts.remove(entityId);
        pendingTeleports.remove(entityId);
        if (train.memberCount() == 0) {
            trains.remove(train.id());
            nameIndex.remove(train.key());
            switchManager.releaseTrain(train.id());
            chunkLoader.release(train.id());
            trainDrivers.remove(train.id());
        }
        save();
    }

    boolean refreshCart(Minecart cart) {
        if (!plugin.isRailInfrastructureReady()) return false;
        UUID trainId = readTrainId(cart);
        if (trainId == null) {
            return false;
        }

        Train train = trains.get(trainId);
        if (train == null || !train.contains(cart.getUniqueId())) {
            clearCartMark(cart);
            cartIndex.remove(cart.getUniqueId());
            return false;
        }

        cartIndex.put(cart.getUniqueId(), train.id());
        ensureTask(cart, train);
        return true;
    }

    Train trainForCart(Minecart cart) {
        UUID trainId = readTrainId(cart);
        if (trainId == null) {
            trainId = cartIndex.get(cart.getUniqueId());
        }
        return trainId == null ? null : trains.get(trainId);
    }

    boolean isSameTrain(Minecart minecart, Entity other) {
        if (!(other instanceof Minecart otherMinecart)) {
            return false;
        }
        UUID first = readTrainId(minecart);
        UUID second = readTrainId(otherMinecart);
        if (first == null) {
            first = cartIndex.get(minecart.getUniqueId());
        }
        if (second == null) {
            second = cartIndex.get(otherMinecart.getUniqueId());
        }
        return first != null && first.equals(second);
    }

    List<Minecart> scanNearby(Player player, double radius) {
        double safeRadius = RailMath.clamp(radius, 1.0, 32.0);
        List<Minecart> carts = new ArrayList<>();

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart minecart) {
            carts.add(minecart);
        }

        for (Entity entity : player.getNearbyEntities(safeRadius, safeRadius, safeRadius)) {
            if (entity instanceof Minecart minecart && !minecart.isDead() && minecart.isValid()) {
                carts.add(minecart);
            }
        }

        Location origin = player.getLocation();
        return carts.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(cart -> cart.getLocation().distanceSquared(origin)))
                .peek(this::refreshCart)
                .toList();
    }

    Train nearestTrain(Location center, double radius) {
        if (center == null || center.getWorld() == null) {
            return null;
        }

        double safeRadius = RailMath.clamp(radius, 1.0, 64.0);
        Train nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : center.getWorld().getNearbyEntities(center, safeRadius, safeRadius, safeRadius)) {
            if (!(entity instanceof Minecart minecart) || !minecart.isValid() || minecart.isDead()) {
                continue;
            }
            refreshCart(minecart);
            Train train = trainForCart(minecart);
            if (train == null) {
                continue;
            }

            double distance = minecart.getLocation().distanceSquared(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = train;
            }
        }
        return nearest;
    }

    boolean setDrivingTarget(Player player, Train train) {
        if (player == null || train == null) {
            return false;
        }
        synchronized (driverLock) {
            UUID playerId = player.getUniqueId();
            if (!player.isOnline() || player.isDead() || !(player.getVehicle() instanceof Minecart seat)
                    || trainForCart(seat) != train) throw new IllegalArgumentException("error.drive-board");
            if (trains.get(train.id()) != train) return false;
            UUID currentDriver = trainDrivers.get(train.id());
            if (currentDriver != null && !currentDriver.equals(playerId)) {
                Player currentPlayer = Bukkit.getPlayer(currentDriver);
                if (currentPlayer != null && currentPlayer.isOnline()) {
                    return false;
                }
                revokeDriver(currentDriver, null, "DISCONNECTED");
            }
            if (isDriver(player, train)) return true;
            revokeDriver(playerId, null, "SEAT_CHANGED");
            driverTargets.put(playerId, train.id());
            trainDrivers.put(train.id(), playerId);
            driverSeats.put(playerId, seat.getUniqueId());
            driverNames.put(playerId, player.getName());
            UUID leaseId = UUID.randomUUID();
            driverLeaseIds.put(playerId, leaseId);
            train.lastManualDriver = playerId;
            // A fresh declaration never inherits traction from an earlier driver/session.
            DriverSafety.brake(train);
            ScheduledTask check = player.getScheduler().runAtFixedRate(plugin, task -> {
                synchronized (driverLock) {
                    if (!DriverSafety.sameSession(leaseId, driverLeaseIds.get(playerId))) { task.cancel(); return; }
                    if (!isDriver(player, train)) revokeDriver(playerId, null, "SEAT_LOST");
                }
            }, () -> revokeDriverLease(playerId, leaseId), 1L, 1L);
            if (check == null) {
                revokeDriver(playerId, null, "DISCONNECTED");
                throw new IllegalArgumentException("error.drive-board");
            }
            driverChecks.put(playerId, check);
            publishDriverEvent(train, playerId, player.getName(), "DRIVER_ACQUIRED", "EXPLICIT_DRIVE");
            train.driverEmergencyHold = false;
            save();
            return true;
        }
    }

    void clearDrivingTarget(Player player) {
        if (player != null) revokeDriver(player.getUniqueId(), null, "SEAT_LOST");
    }

    void revokeDriver(UUID playerId, UUID expectedSeat, String reason) {
        synchronized (driverLock) {
            if (expectedSeat != null && !expectedSeat.equals(driverSeats.get(playerId))) return;
            UUID trainId = driverTargets.remove(playerId);
            driverSeats.remove(playerId);
            cancelDriverCheck(playerId);
            String name = driverNames.remove(playerId);
            if (trainId == null || !trainDrivers.remove(trainId, playerId)) return;
            Train train = trains.get(trainId);
            if (train == null) return;
            DriverSafety.brake(train);
            publishDriverEvent(train, playerId, name, "DRIVER_UNAVAILABLE", reason);
            save();
        }
    }

    private void revokeDriverLease(UUID playerId, UUID leaseId) {
        synchronized (driverLock) {
            if (DriverSafety.sameSession(leaseId, driverLeaseIds.get(playerId))) revokeDriver(playerId, null, "DISCONNECTED");
        }
    }

    private void cancelDriverCheck(UUID playerId) {
        driverLeaseIds.remove(playerId);
        ScheduledTask task = driverChecks.remove(playerId);
        if (task != null) task.cancel();
    }

    private void publishDriverEvent(Train train, UUID driver, String name, String type, String reason) {
        plugin.getLogger().info(type + " train=" + train.name() + " driver=" + driver + " reason=" + reason);
        TelemetrySink sink = plugin.telemetrySink();
        if (sink != null) sink.driverEvent(train, driver, name, type, reason);
    }

    Player clearDriver(Train train) {
        if (train == null) {
            return null;
        }
        synchronized (driverLock) {
            UUID playerId = trainDrivers.remove(train.id());
            if (playerId == null) {
                return null;
            }
            driverTargets.remove(playerId, train.id());
            driverSeats.remove(playerId);
            cancelDriverCheck(playerId);
            String name = driverNames.remove(playerId);
            DriverSafety.brake(train);
            publishDriverEvent(train, playerId, name, "DRIVER_RELEASED", "ADMIN_RELEASE");
            return Bukkit.getPlayer(playerId);
        }
    }

    boolean isDriver(Player player, Train train) {
        if (player == null || train == null) return false;
        Entity seat = player.getVehicle();
        return DriverSafety.ownsSeat(trainDrivers.get(train.id()), player.getUniqueId(),
                driverSeats.get(player.getUniqueId()), seat == null ? null : seat.getUniqueId(),
                player.isOnline() && !player.isDead());
    }

    List<net.skyworld.sta.api.v4.DriverDeskService.Desk> driverDesks() {
        synchronized (driverLock) {
            List<net.skyworld.sta.api.v4.DriverDeskService.Desk> result = new ArrayList<>();
            trainDrivers.forEach((trainId, driverId) -> {
                Train train = trains.get(trainId);
                UUID lease = driverLeaseIds.get(driverId);
                if (train != null && lease != null) result.add(new net.skyworld.sta.api.v4.DriverDeskService.Desk(
                        trainId, driverId, lease, train.protectionMode.name()));
            });
            return List.copyOf(result);
        }
    }

    void requireDriver(Player player, Train train) {
        if (isDriver(player, train)) return;
        Train old = drivingTarget(player);
        if (old != null && !isDriver(player, old)) clearDrivingTarget(player);
        throw new IllegalArgumentException("error.drive-declare");
    }

    String driverName(Train train) {
        if (train == null) {
            return null;
        }
        synchronized (driverLock) {
            UUID playerId = trainDrivers.get(train.id());
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                if (playerId != null) {
                    revokeDriver(playerId, null, "DISCONNECTED");
                }
                return null;
            }
            return player.getName();
        }
    }

    Train train(UUID id) {
        return id == null ? null : trains.get(id);
    }

    Train drivingTarget(Player player) {
        if (player == null) {
            return null;
        }
        UUID trainId = driverTargets.get(player.getUniqueId());
        Train train = trainId == null ? null : trains.get(trainId);
        if (train == null && trainId != null) {
            driverTargets.remove(player.getUniqueId());
            driverSeats.remove(player.getUniqueId());
            cancelDriverCheck(player.getUniqueId());
            driverNames.remove(player.getUniqueId());
        }
        return train;
    }

    void start(String name, Double speed) {
        Train train = requireTrain(name);
        train.driverEmergencyHold = false;
        startTrain(train, speed);
        save();
    }

    void stop(String name) {
        Train train = requireTrain(name);
        stopTrain(train);
        save();
    }

    void reverse(String name) {
        Train train = requireTrain(name);
        reverseTrain(train, System.currentTimeMillis());
        save();
    }

    void speed(String name, double speed) {
        Train train = requireTrain(name);
        train.targetSpeed = RailMath.clamp(speed, 0.0, train.maxSpeed);
        save();
    }

    void setMaxSpeed(String name, double speed) {
        Train train = requireTrain(name);
        setTrainMaxSpeed(train, speed);
        save();
    }

    private void setTrainMaxSpeed(Train train, double speed) {
        double newMaxSpeed = RailMath.clamp(speed, 0.05, maxAllowedSpeed());
        double currentPhysicalSpeed = Math.max(train.currentSpeed(), train.maxMemberSpeed());
        train.maxSpeed = newMaxSpeed;
        train.targetSpeed = RailMath.clamp(train.targetSpeed, 0.0, train.maxSpeed);
        train.seedEffectiveMaxSpeed(train.moving || currentPhysicalSpeed > 0.001
                ? Math.max(0.05, Math.min(maxAllowedSpeed(), currentPhysicalSpeed + 0.10))
                : newMaxSpeed);
    }

    void setSpacing(String name, double spacing) {
        Train train = requireTrain(name);
        train.spacing = RailMath.clamp(spacing, 0.8, 8.0);
        save();
    }

    void applyPlayerPush(Train train, Minecart cart, Player player) {
        double currentSpeed = train == null ? 0.0 : Math.max(train.currentSpeed(), train.maxMemberSpeed());
        if (train == null || cart == null || player == null || !train.properties().pushable
                || train.moving || train.driveControlEnabled
                || (!train.playerPushActive && currentSpeed > playerPushActivationSpeed())) {
            return;
        }

        RailInfo rail = RailMath.findRail(cart.getLocation());
        if (rail == null) {
            return;
        }

        Vector playerVelocity = player.getVelocity().clone().setY(0.0);
        Vector pushPreference = playerVelocity.lengthSquared() > 0.0004
                ? playerVelocity
                : cart.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (pushPreference.lengthSquared() < 0.0001) {
            pushPreference = player.getLocation().getDirection().setY(0.0);
        }
        Vector direction = RailMath.direction(rail.rail.getShape(), pushPreference);
        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        Vector previousDirection = train.rememberedDirection();
        boolean oppositeDirection = previousDirection.lengthSquared() >= 0.0001
                && previousDirection.dot(direction) < 0.0;
        if (oppositeDirection && currentSpeed > playerPushActivationSpeed()) {
            return;
        }
        if (oppositeDirection) {
            train.reversed = !train.reversed;
            train.reverseMileageDirection();
            refreshMemberIndexes(train);
        }

        double movementBonus = Math.min(playerPushMovementBonus(), horizontalSpeed(playerVelocity));
        train.rememberDirection(direction);
        train.clearSnapshots();
        train.clearMemberSpeeds();
        train.clearMemberTargets();
        train.clearTrackPath();
        train.applyPlayerPush(playerPushImpulse() + movementBonus, playerPushMaxSpeed());
    }

    void setReverser(Train train, Reverser reverser) {
        Reverser target = reverser == null ? Reverser.NEUTRAL : reverser;
        if (target != Reverser.NEUTRAL
                && train.reverser != target
                && !canChangeReverser(train)) {
            throw new IllegalArgumentException("列车未停稳，不能切换换向器。");
        }
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.reverser = target;
        if (train.reverser == Reverser.NEUTRAL) {
            train.powerNotch = 0;
        }
        train.moving = true;
        save();
    }

    boolean canChangeReverser(Train train) {
        return Math.max(train.currentSpeed(), train.maxMemberSpeed()) <= driveDirectionChangeSpeed();
    }

    Reverser reverseReverserTarget(Train train) {
        if (train.reverser == Reverser.FORWARD) {
            return Reverser.BACKWARD;
        }
        if (train.reverser == Reverser.BACKWARD) {
            return Reverser.FORWARD;
        }
        return train.reversed ? Reverser.FORWARD : Reverser.BACKWARD;
    }

    Reverser cycleReverserTarget(Train train) {
        if (train.reverser != Reverser.NEUTRAL) {
            return Reverser.NEUTRAL;
        }
        return train.reversed ? Reverser.FORWARD : Reverser.BACKWARD;
    }

    void setPowerNotch(Train train, int notch) {
        train.protectionMode.requireTraction();
        if (train.driverEmergencyHold) throw new IllegalArgumentException("error.drive-declare");
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = Math.max(0, Math.min(4, notch));
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.moving = true;
        save();
    }

    void setBrakeNotch(Train train, int notch) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = Math.max(0, Math.min(7, notch));
        train.emergencyBrake = false;
        train.moving = true;
        save();
    }

    void neutralHandle(Train train) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.moving = true;
        save();
    }

    void emergencyBrake(Train train) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = 7;
        train.emergencyBrake = true;
        train.moving = true;
        save();
    }

    String driveStatus(Train train) {
        return "&e" + train.name()
                + " &7| 换向器: &f" + train.reverser.displayName()
                + " &7| 牵引: &fP" + train.powerNotch
                + " &7| 制动: &f" + (train.emergencyBrake ? "EB" : "B" + train.brakeNotch)
                + " &7| 速度: &f" + trim(Math.max(train.currentSpeed(), train.maxMemberSpeed()));
    }

    private void takeManualControl(Train train) {
        synchronized (driverLock) {
            train.manualTakeover = true;
            train.manualReleaseConfirmed = false;
            cancelStationMotion(train);
        }
    }

    void confirmManualRelease(Train train) {
        if (train == null) return;
        synchronized (driverLock) { train.manualReleaseConfirmed = true; }
        save();
    }

    static boolean permanentRemoval(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        return cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD
                && cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT;
    }

    static void haltForMissingMember(Train train) {
        stopTrain(train);
        train.seedCurrentSpeed(0);
        train.clearMemberSpeeds();
        train.clearSnapshots();
        train.clearMemberTargets();
        train.clearTrackPath();
        train.emergencyBrake = true;
        train.brakeNotch = 7;
    }

    void onCartRemoved(Minecart cart, org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        if (!plugin.isRailInfrastructureReady()) return;
        if (retiringCarts.contains(cart.getUniqueId())) return;
        Train train = trainForCart(cart);
        if (train == null) return;
        UUID driver = trainDrivers.get(train.id());
        if (driver != null) revokeDriver(driver, cart.getUniqueId(), "VEHICLE_REMOVED");
        train.recordMemberRemoval(cart.getUniqueId(), permanentRemoval(cause) ? "REMOVED"
                : cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD ? "UNLOADED" : "PLAYER_QUIT",
                System.currentTimeMillis());
        synchronized (driverLock) {
            if (permanentRemoval(cause)) onCartDestroyed(cart);
            else {
                // Preserve the slot for unload/reload, but never keep displaying or commanding old speed.
                haltForMissingMember(train);
                forgetDisplay(cart.getUniqueId());
            }
        }
        plugin.getLogger().warning("Train " + train.name() + " stopped after member " + cart.getUniqueId()
                + " removal: " + cause + (permanentRemoval(cause) ? " (slot removed)" : " (slot retained)"));
    }

    static boolean mayRelease(UUID requester, UUID driver, UUID lastDriver, boolean admin) {
        if (driver != null) return driver.equals(requester);
        return admin || requester.equals(lastDriver);
    }

    void releaseManualControl(Player player, Train train) {
        if (train == null) throw new IllegalArgumentException("没有找到要释放的列车，请靠近列车后重试。");
        synchronized (driverLock) {
            if (trains.get(train.id()) != train) throw new IllegalArgumentException("列车已不存在。");
            UUID playerId = player.getUniqueId();
            UUID driver = trainDrivers.get(train.id());
            if (!mayRelease(playerId, driver, train.lastManualDriver, player.hasPermission("skytrain.admin"))) {
                throw new IllegalArgumentException(driver != null
                        ? "这列车由其他司机控制；管理员请使用 /st admin release <列车名>。"
                        : "无法确认你是上一位司机；请重新 /st drive 后 /st release，或由管理员释放列车。");
            }
            DriverSafety.brake(train);
            train.manualReleaseConfirmed = true;
            trainDrivers.remove(train.id(), playerId);
            driverTargets.remove(playerId, train.id());
            driverSeats.remove(playerId);
            driverNames.remove(playerId);
            cancelDriverCheck(playerId);
            if (driver != null) publishDriverEvent(train, playerId, player.getName(), "DRIVER_RELEASED", "EXPLICIT_RELEASE");
        }
        save();
    }

    static void authorizeAutomatic(Train train, boolean hasDriver, double stopThreshold) {
        if (hasDriver) throw new IllegalArgumentException("请先 /st release 释放司机控制权，再设置 auto。");
        if (train.manualTakeover && !train.manualReleaseConfirmed)
            throw new IllegalArgumentException("手动接管尚未明确释放。请先 /st release，或 /st admin release <列车名>。");
        if (Math.max(train.currentSpeed(), train.maxMemberSpeed()) > stopThreshold)
            throw new IllegalArgumentException("列车尚未停稳，不能启用 auto。");
        stopTrain(train);
        train.driverEmergencyHold = false;
        train.manualTakeover = false;
        train.reverser = train.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
        train.properties().conductionMode = ConductionMode.AUTOMATIC;
    }

    private void startTrain(Train train, Double speed) {
        if (speed != null) {
            train.targetSpeed = RailMath.clamp(speed, 0.0, train.maxSpeed);
        }
        cancelStationMotion(train);
        train.driveControlEnabled = false;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.reverser = train.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
        train.pauseUntilMillis = 0L;
        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        train.reversePending = false;
        train.clearPlayerPush();
        train.moving = true;
    }

    private static void stopTrain(Train train) {
        cancelStationMotion(train);
        train.driveControlEnabled = false;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.pauseUntilMillis = 0L;
        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        train.reversePending = false;
        train.clearPlayerPush();
        train.moving = false;
    }

    private static void cancelStationMotion(Train train) {
        train.automaticRun = null;
        train.clearStationMotion();
        train.pauseUntilMillis = 0L;
    }

    private void reverseTrain(Train train, long now) {
        setReverser(train, reverseReverserTarget(train));
    }

    SavedTrainDefinition saveTrainTemplate(String trainName, String savedName) {
        Train train = requireTrain(trainName);
        String cleanName = cleanName(savedName);
        SavedTrainDefinition saved = SavedTrainDefinition.fromTrain(cleanName, train);
        savedTrains.put(Train.normalizeName(cleanName), saved);
        save();
        return saved;
    }

    Train spawnSavedTrain(Player player, String savedName, String trainName) {
        SavedTrainDefinition saved = savedTrains.get(Train.normalizeName(savedName));
        if (saved == null) {
            throw new IllegalArgumentException("找不到保存的列车模板: " + savedName);
        }

        String actualTrainName = trainName == null || trainName.isBlank()
                ? uniqueTrainName(saved.name)
                : cleanName(trainName);
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("无法在当前位置生成列车。");
        }

        RailInfo rail = RailMath.findRail(base);
        Vector direction = rail == null
                ? RailMath.yawDirection(base.getYaw())
                : RailMath.direction(rail.rail.getShape(), RailMath.yawDirection(base.getYaw()));
        if (direction.lengthSquared() < 0.0001) {
            direction = RailMath.yawDirection(base.getYaw());
        }

        List<Minecart> carts = new ArrayList<>();
        for (int i = 0; i < saved.memberCount; i++) {
            Location spawnAt = base.clone().add(direction.clone().multiply(-saved.spacing * i));
            Entity entity = world.spawnEntity(spawnAt, EntityType.MINECART);
            if (entity instanceof Minecart minecart) {
                carts.add(minecart);
            }
        }
        if (carts.isEmpty()) {
            throw new IllegalArgumentException("没有成功生成任何矿车。");
        }

        Train train = createTrain(actualTrainName, carts);
        train.targetSpeed = saved.targetSpeed;
        setTrainMaxSpeed(train, saved.maxSpeed);
        train.spacing = saved.spacing;
        train.properties().copyFrom(saved.properties);
        arrangeTrainCarts(train, carts, base);
        save();
        return train;
    }

    String setProperty(String trainName, String property, String value) {
        Train train = requireTrain(trainName);
        TrainProperties properties = train.properties();
        String key = normalizeProperty(property);
        String cleanValue = value == null ? "" : value.trim();

        switch (key) {
            case "name" -> renameTrain(train, cleanValue);
            case "displayname" -> properties.displayName = cleanValue;
            case "trainnumber" -> properties.setTrainNumber(cleanValue);
            case "destination", "dest" -> properties.destination = cleanValue;
            case "collision", "collisionmode" -> properties.collisionMode = cleanValue.toLowerCase(Locale.ROOT);
            case "playerenter", "playersenter", "enter" -> properties.playersEnter = parseBoolean(cleanValue, key);
            case "playerexit", "playersexit", "exit" -> properties.playersExit = parseBoolean(cleanValue, key);
            case "pushable", "playerpush", "playerpushable" -> properties.pushable = parseBoolean(cleanValue, key);
            case "pickup", "pickupitems" -> properties.pickupItems = parseBoolean(cleanValue, key);
            case "invincible" -> properties.invincible = parseBoolean(cleanValue, key);
            case "allowplayertake", "playertake" -> properties.allowPlayerTake = parseBoolean(cleanValue, key);
            case "requirepoweredcart", "poweredcart" -> properties.requirePoweredCart = parseBoolean(cleanValue, key);
            case "sound", "soundenabled" -> properties.soundEnabled = parseBoolean(cleanValue, key);
            case "keepchunks", "keepchunksloaded" -> properties.keepChunksLoaded = parseBoolean(cleanValue, key);
            case "conduction", "conductionmode", "operationmode", "controlmode", "mode" -> {
                ConductionMode mode=ConductionMode.parse(cleanValue);
                synchronized(driverLock) {
                    if(mode.automatic()) {
                        authorizeAutomatic(train, trainDrivers.containsKey(train.id()), driveDirectionChangeSpeed());
                        automaticSigns.forget(train.id());
                    } else {
                        train.manualTakeover=true;
                        stopTrain(train);
                    }
                    properties.conductionMode=mode;
                }
            }
            case "gravity" -> properties.gravity = RailMath.clamp(parseDouble(cleanValue, key), 0.0, 4.0);
            case "friction" -> properties.friction = RailMath.clamp(parseDouble(cleanValue, key), 0.0, 4.0);
            case "wait", "waitticks" -> properties.waitTicks = Math.max(0, (int) parseDouble(cleanValue, key));
            case "speed", "targetspeed" -> train.targetSpeed = RailMath.clamp(parseDouble(cleanValue, key), 0.0, train.maxSpeed);
            case "maxspeed" -> setTrainMaxSpeed(train, parseDouble(cleanValue, key));
            case "spacing" -> train.spacing = RailMath.clamp(parseDouble(cleanValue, key), 0.8, 8.0);
            default -> throw new IllegalArgumentException("暂不支持的列车属性: " + property);
        }

        save();
        return "&a已设置 &e" + train.name() + " &a的属性 &e" + property + " &a= &f" + cleanValue;
    }

    String propertyValue(String trainName, String property) {
        Train train = requireTrain(trainName);
        TrainProperties properties = train.properties();
        return switch (normalizeProperty(property)) {
            case "name" -> train.name();
            case "displayname" -> properties.displayName;
            case "trainnumber" -> properties.trainNumber;
            case "destination", "dest" -> properties.destination;
            case "collision", "collisionmode" -> properties.collisionMode;
            case "playerenter", "playersenter", "enter" -> Boolean.toString(properties.playersEnter);
            case "playerexit", "playersexit", "exit" -> Boolean.toString(properties.playersExit);
            case "pushable", "playerpush", "playerpushable" -> Boolean.toString(properties.pushable);
            case "pickup", "pickupitems" -> Boolean.toString(properties.pickupItems);
            case "invincible" -> Boolean.toString(properties.invincible);
            case "allowplayertake", "playertake" -> Boolean.toString(properties.allowPlayerTake);
            case "requirepoweredcart", "poweredcart" -> Boolean.toString(properties.requirePoweredCart);
            case "sound", "soundenabled" -> Boolean.toString(properties.soundEnabled);
            case "keepchunks", "keepchunksloaded" -> Boolean.toString(properties.keepChunksLoaded);
            case "conduction", "conductionmode", "operationmode", "controlmode", "mode" ->
                properties.conductionMode.storageName();
            case "gravity" -> Double.toString(properties.gravity);
            case "friction" -> Double.toString(properties.friction);
            case "wait", "waitticks" -> Integer.toString(properties.waitTicks);
            case "speed", "targetspeed" -> Double.toString(train.targetSpeed);
            case "maxspeed" -> Double.toString(train.maxSpeed);
            case "spacing" -> Double.toString(train.spacing);
            case "tags" -> String.join(",", properties.tags());
            case "owners" -> String.join(",", properties.owners());
            case "route" -> String.join("->", properties.route());
            default -> throw new IllegalArgumentException("暂不支持的列车属性: " + property);
        };
    }

    String updateTag(String trainName, String action, String tag) {
        TrainProperties properties = requireTrain(trainName).properties();
        String mode = action.toLowerCase(Locale.ROOT);
        if ("list".equals(mode)) {
            return "&eTags: &f" + String.join(", ", properties.tags());
        }
        if ("add".equals(mode)) {
            properties.addTag(tag);
        } else if ("remove".equals(mode) || "del".equals(mode)) {
            properties.removeTag(tag);
        } else {
            throw new IllegalArgumentException("用法: /st tag <列车> add|remove|list <标签>");
        }
        save();
        return "&aTags: &f" + String.join(", ", properties.tags());
    }

    String updateOwner(String trainName, String action, String owner) {
        TrainProperties properties = requireTrain(trainName).properties();
        String mode = action.toLowerCase(Locale.ROOT);
        if ("list".equals(mode)) {
            return "&eOwners: &f" + String.join(", ", properties.owners());
        }
        if ("add".equals(mode)) {
            properties.addOwner(owner);
        } else if ("remove".equals(mode) || "del".equals(mode)) {
            properties.removeOwner(owner);
        } else {
            throw new IllegalArgumentException("用法: /st owner <列车> add|remove|list <玩家>");
        }
        save();
        return "&aOwners: &f" + String.join(", ", properties.owners());
    }

    String updateRoute(String trainName, String action, List<String> destinations) {
        TrainProperties properties = requireTrain(trainName).properties();
        String mode = action.toLowerCase(Locale.ROOT);
        if ("list".equals(mode)) {
            return "&eRoute: &f" + String.join(" -> ", properties.route());
        }
        if ("clear".equals(mode)) {
            properties.clearRoute();
        } else if ("set".equals(mode)) {
            properties.setRoute(destinations);
        } else if ("add".equals(mode)) {
            for (String destination : destinations) {
                properties.addRouteDestination(destination);
            }
        } else {
            throw new IllegalArgumentException("用法: /st route <列车> set|add|clear|list <目的地...>");
        }
        save();
        return "&aRoute: &f" + String.join(" -> ", properties.route());
    }

    int activateSignForNearbyTrain(Player player, Block signBlock, String actionLine,
            String valueLine, String modifierLine) {
        String action = firstToken(actionLine);
        if (!isSignAction(action)) {
            return 0;
        }

        Location signLocation = signBlock.getLocation().add(0.5, 0.5, 0.5);
        double radius = signActivationRadius();
        List<Minecart> nearby = minecartsNear(player, signLocation, radius);
        if (nearby.isEmpty()) {
            return 0;
        }

        for (Minecart minecart : nearby) {
            refreshCart(minecart);
        }

        Train target = null;
        for (Minecart minecart : nearby) {
            target = trainForCart(minecart);
            if (target != null) {
                break;
            }
        }
        if (target == null) {
            connectCarts(nearby, signLocation, true);
            for (Minecart minecart : nearby) {
                target = trainForCart(minecart);
                if (target != null) {
                    break;
                }
            }
        }
        if (target == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        Location leaderLocation = activeLeaderLocation(target);
        Vector travelDirection = activeLeaderDirection(target, leaderLocation);
        triggerSign(target, actionLine, valueLine, modifierLine, signBlock, signKey(signBlock),
                leaderLocation, travelDirection, now);
        for (Minecart minecart : nearby) {
            if (trainForCart(minecart) == target) {
                ensureTask(minecart, target);
            }
        }
        save();
        return 1;
    }

    void activateStationsFromRedstone(Block poweredBlock) {
        if (poweredBlock == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block candidate = poweredBlock.getRelative(x, y, z);
                    if (!(candidate.getState() instanceof Sign sign)) {
                        continue;
                    }
                    String header = plain(sign.getLine(0));
                    if (!SignHeaders.isSkyTrain(header) || !isHeaderActive(header, candidate)
                            || !"station".equals(firstToken(sign.getLine(1)))) {
                        continue;
                    }
                    String key = signKey(candidate);
                    if (visited.add(key)) {
                        activateStationForNearbyTrain(candidate, sign);
                    }
                }
            }
        }
    }

    private void activateStationForNearbyTrain(Block signBlock, Sign sign) {
        Location center = signBlock.getLocation().add(0.5, 0.5, 0.5);
        double radius = signActivationRadius();
        Collection<Entity> nearby;
        try {
            nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius,
                    entity -> entity instanceof Minecart);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to scan station redstone area.", ex);
            return;
        }
        Train target = nearby.stream().filter(Minecart.class::isInstance).map(Minecart.class::cast)
                .peek(this::refreshCart).map(this::trainForCart).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Location leaderLocation = activeLeaderLocation(target);
        Vector travelDirection = activeLeaderDirection(target, leaderLocation);
        triggerSign(target, sign.getLine(1), sign.getLine(2), sign.getLine(3), signBlock,
                signKey(signBlock), leaderLocation, travelDirection, now);
        save();
    }

    private boolean isHeaderActive(String header, Block signBlock) {
        if (SignHeaders.isNeverActive(header)) {
            return false;
        }
        if (SignHeaders.isAlwaysActive(header)) {
            return true;
        }
        boolean powered = readSignPowered(signBlock);
        return SignHeaders.isInverted(header) ? !powered : powered;
    }

    private boolean readSignPowered(Block signBlock) {
        if (signBlock == null) {
            return false;
        }
        Block support = null;
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            support = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        return signBlock.isBlockPowered() || signBlock.isBlockIndirectlyPowered() || signBlock.getBlockPower() > 0
                || (support != null && (support.isBlockPowered()
                || support.isBlockIndirectlyPowered() || support.getBlockPower() > 0));
    }

    private boolean signPropertyAllowed(String property) {
        return switch (normalizeProperty(property)) {
            case "name", "destination", "dest", "route", "tags", "owners" -> false;
            default -> true;
        };
    }

    Train requireTrain(String name) {
        Train train = train(name);
        if (train == null) {
            throw new IllegalArgumentException("找不到列车: " + name);
        }
        return train;
    }

    String describe(Train train) {
        String display = train.properties().displayName == null || train.properties().displayName.isBlank()
                ? train.name()
                : train.properties().displayName;
        return "&e" + display
                + " &8(" + train.name() + ")"
                + " &7| 车厢: &f" + train.memberCount()
                + " &7| 状态: " + (train.moving ? "&a运行" : "&c停止")
                + " &7| 方向: " + (train.reversed ? "&d反向" : "&b正向")
                + " &7| 速度: &f" + trim(train.targetSpeed)
                + " &7/ &f" + trim(plugin.trainSpeedLimit(train))
                + " &7| 间距: &f" + trim(train.spacing)
                + " &7| 驾驶: &f" + (train.driveControlEnabled || train.automaticRun != null
                        ? train.reverser.displayName() + " P" + train.powerNotch + " "
                                + (train.emergencyBrake ? "EB" : "B" + train.brakeNotch)
                        : "自动")
                + " " + train.properties().summary()
                + " &7| Auto armed: &f" + automaticEligible(train)
                + " &7| Release confirmed: &f" + train.manualReleaseConfirmed
                + " &7| Protection: &f" + train.protectionMode + " (ATP unavailable in alpha)"
                + (train.automaticRun == null ? "" : " &7| " + train.automaticRun.reason
                        + " (" + trim(train.automaticRun.remaining) + " blocks)");
    }

    private void ensureTask(Minecart cart, Train train) {
        UUID entityId = cart.getUniqueId();
        ScheduledTask existing = memberTasks.get(entityId);
        if (existing != null && !existing.isCancelled() && managedCarts.get(entityId) == cart) {
            return;
        }
        if (existing != null && !existing.isCancelled()) existing.cancel();

        try {
            managedCarts.put(entityId, cart);
            ScheduledTask task = cart.getScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> tickMember(train, cart, scheduledTask),
                    () -> {
                        if (managedCarts.remove(entityId, cart)) {
                            memberTasks.remove(entityId);
                            forgetDisplay(entityId);
                        }
                    },
                    1L,
                    tickInterval());
            if (task != null) {
                memberTasks.put(entityId, task);
            }
        } catch (RuntimeException ex) {
            managedCarts.remove(entityId);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule train cart " + entityId, ex);
        }
    }

    private void tickMember(Train train, Minecart cart, ScheduledTask task) {
        UUID entityId = cart.getUniqueId();
        if (!cart.isValid() || cart.isDead()) {
            forgetDisplay(entityId);
            train.memberSpeed(entityId, 0.0);
            memberTasks.remove(entityId);
            managedCarts.remove(entityId);
            pendingTeleports.remove(entityId);
            task.cancel();
            return;
        }

        if (trains.get(train.id()) != train || !train.contains(entityId)) {
            forgetDisplay(entityId);
            train.memberSpeed(entityId, 0.0);
            clearCartMark(cart);
            cartIndex.remove(entityId);
            memberTasks.remove(entityId);
            managedCarts.remove(entityId);
            pendingTeleports.remove(entityId);
            task.cancel();
            return;
        }

        // Never mutate a cart while its asynchronous relocation is still in flight.
        CompletableFuture<Boolean> pendingMove = pendingTeleports.get(entityId);
        if (pendingMove != null && !pendingMove.isDone()) return;
        if (plugin.displaySync() != null) plugin.displaySync().observe(train.id(), cart);
        long now = System.currentTimeMillis();
        long currentTick = Bukkit.getCurrentTick();
        if (train.driverEmergencyHold) DriverSafety.maintainBrake(train);
        if (train.protectionMode == ProtectionMode.RECOVERING) {
            train.automaticRun = null;
            train.powerNotch = 0;
            train.brakeNotch = 7;
            train.emergencyBrake = true;
            train.driveControlEnabled = true;
        }
        applyForcedSpacing(train);
        int index = train.indexOf(entityId);
        if (index == activeLeaderIndex(train)) {
            if (chunkLoadingEnabled()) {
                chunkLoader.update(train, cart.getLocation(), chunkLoadingRadius());
            } else {
                chunkLoader.release(train.id());
            }
        }
        cart.setSlowWhenEmpty(false);
        removeNonPlayerPassengers(cart);
        boolean playerPassenger = hasPlayerPassenger(cart);
        boolean smoothPassenger = playerPassenger
                && (trainPhysicsHardLock() || passengerSmoothingEnabled());
        double trainSpeedLimit = plugin.trainSpeedLimit(train);
        double entityMaxSpeed = train.updateEffectiveMaxSpeed(now, trainSpeedLimit, maxSpeedChangePerTick());
        double passengerMotionFactor = passengerVanillaMotionFactor();
        double controlledEntityMaxSpeed = smoothPassenger
                ? RailMath.clamp(
                        Math.max(0.4, (entityMaxSpeed + passengerMaxCorrectionPerTick()) / passengerMotionFactor),
                        0.05,
                        VehicleProfile.MAX_SPEED * 2)
                : 0.0;
        cart.setMaxSpeed(smoothPassenger
                ? controlledEntityMaxSpeed
                : (trainPhysicsHardLock() ? 0.0 : RailMath.clamp(entityMaxSpeed, 0.05, VehicleProfile.MAX_SPEED)));
        cart.setGravity(!trainPhysicsHardLock() && train.properties().gravity > 0.0);
        cart.setInvulnerable(train.properties().invincible);
        cart.setSilent(!train.properties().soundEnabled);

        Location location = cart.getLocation();
        RailInfo rail = RailMath.findRail(location);

        if (rail == null) {
            TrainMemberTarget target = train.memberTarget(entityId, currentTick);
            if (trainPhysicsHardLock()) {
                if (target != null && now - target.updatedAtMillis <= 1000L) {
                    applyTrainTarget(train, cart, entityId, target, location, now);
                } else {
                    cart.setVelocity(new Vector());
                    train.memberSpeed(entityId, 0.0);
                    train.snapshot(entityId, new MemberSnapshot(entityId, location, new Vector(), now));
                }
                return;
            }

            Vector velocity = cart.getVelocity().multiply(0.7 * RailMath.clamp(train.properties().friction, 0.0, 4.0));
            if (velocity.lengthSquared() < 0.001) {
                velocity.zero();
            }
            cart.setVelocity(velocity);
            train.memberSpeed(entityId, horizontalSpeed(velocity));
            train.snapshot(entityId, new MemberSnapshot(entityId, location, velocity, now));
            return;
        }

        applyReverserDirection(train, now);
        Vector preference = cart.getVelocity();
        if (preference.lengthSquared() < 0.0001) {
            preference = train.rememberedDirection();
        }
        if (preference.lengthSquared() < 0.0001) {
            preference = RailMath.yawDirection(location.getYaw());
            if (train.reversed) {
                preference.multiply(-1.0);
            }
        }
        Vector direction = RailMath.direction(rail.rail.getShape(), preference);

        if (index == activeLeaderIndex(train)) {
            refreshStationLatches(train);
            synchronized (driverLock) {
                if (automaticSigns.tick(train, rail.block, location, direction, now)) return;
            }
            if (handleSignActions(train, rail.block, location, direction, now)) {
                return;
            }
            prepareStationDeparture(train, now);
        }

        StationMotion stationMotion = train.stationMotion();
        boolean stationMoving = stationMotion != null && stationMotion.isMoving();
        boolean stationWaiting = stationMotion != null && stationMotion.isWaiting();
        boolean waiting = stationWaiting || train.pauseUntilMillis > now;
        if (!waiting && train.pauseUntilMillis > 0L) {
            train.pauseUntilMillis = 0L;
        }
        boolean settlingReverse = reverseBraking(train, now);

        boolean controlledStop = (!train.driveControlEnabled && !train.moving) || waiting || settlingReverse
                || train.emergencyBrake;
        boolean playerPushCoasting = controlledStop && train.playerPushActive;
        boolean automaticHandle = train.automaticRun != null && automaticEligible(train);
        double desiredTrainSpeed = controlledStop ? 0.0
                : stationMoving ? stationMotion.commandedSpeed(stationMotionMinimumSpeed(stationMotion))
                : (train.driveControlEnabled || automaticHandle ? train.maxSpeed : train.targetSpeed);

        if (!controlledStop && !stationMoving && index == activeLeaderIndex(train) && rail.poweredRail && rail.powered) {
            desiredTrainSpeed = Math.max(desiredTrainSpeed, poweredRailBoostSpeed());
        }
        if (!controlledStop && !stationMoving && index == activeLeaderIndex(train)
                && rail.poweredRail && !rail.powered && brakeOnUnpoweredRail()) {
            desiredTrainSpeed *= 0.25;
        }
        desiredTrainSpeed *= RailMath.clamp(train.properties().friction, 0.0, 4.0);
        desiredTrainSpeed = RailMath.clamp(desiredTrainSpeed, 0.0, trainSpeedLimit);
        SpeedLimit obstacleLimit = index == activeLeaderIndex(train)
                ? frontMinecartSpeedLimit(train, cart, location, direction, desiredTrainSpeed)
                : new SpeedLimit(desiredTrainSpeed, false);
        VehicleProfile vehicle = plugin.vehicleProfile();
        boolean speedController = index == activeLeaderIndex(train) && !train.speedControlledRecently(now);
        double speed;
        if (speedController && stationMoving && !controlledStop) {
            speed = Math.min(obstacleLimit.speed,
                    stationMotion.commandedSpeed(stationMotionMinimumSpeed(stationMotion)));
            train.seedCurrentSpeed(speed);
        } else if (speedController && (train.driveControlEnabled || automaticHandle)) {
            boolean tractionAllowed = !controlledStop
                    && train.moving
                    && train.reverser != Reverser.NEUTRAL
                    && train.reverser.wantsBackward() == train.reversed
                    && train.powerNotch > 0
                    && train.brakeNotch == 0
                    && !train.emergencyBrake
                    && train.currentSpeed() <= obstacleLimit.speed + 0.001;
            if (vehicle.forceMode()) {
                speed = train.updateDrivenForceSpeed(
                        now,
                        obstacleLimit.speed,
                        vehicle.tractionForce(train.powerNotch),
                        vehicle.brakeForce(train.brakeNotch),
                        vehicle.mass(),
                        vehicle.rollingForce(),
                        vehicle.airForceFactor(),
                        vehicle.grade(),
                        direction.getY(),
                        vehicle.baseSpeed(),
                        vehicle.weakeningSpeed(),
                        vehicle.minimumRatio(),
                        vehicle.autoDeceleration(),
                        vehicle.emergencyForce(),
                        obstacleLimit.emergencyBrake || settlingReverse,
                        tractionAllowed);
            } else {
                speed = train.updateDrivenSpeed(
                        now,
                        obstacleLimit.speed,
                        vehicle.powerAcceleration(train.powerNotch),
                        vehicle.brakeAcceleration(train.brakeNotch),
                        vehicle.rolling(),
                        vehicle.air(),
                        vehicle.autoDeceleration(),
                        vehicle.autoEmergency(),
                        obstacleLimit.emergencyBrake || settlingReverse,
                        tractionAllowed);
            }
        } else if (speedController) {
            speed = train.updateCurrentSpeed(
                    now,
                    obstacleLimit.speed,
                    vehicle.autoAcceleration(),
                    playerPushCoasting ? playerPushDecelerationPerTick() : vehicle.autoDeceleration(),
                    vehicle.autoEmergency(),
                    obstacleLimit.emergencyBrake || settlingReverse);
            if (playerPushCoasting && speed <= 0.001) {
                train.clearPlayerPush();
            }
        } else {
            speed = train.currentSpeed();
        }

        if (index == activeLeaderIndex(train)) {
            playTracksideRunningSound(train, cart, speed, currentTick);
            playBrakeSound(train, cart, now);
        }

        if (index == activeLeaderIndex(train) && !train.targetLayoutUpdatedRecently(now)) {
            layoutTrainTargets(train, location, direction, speed, now, currentTick);
        }

        TrainMemberTarget target = train.memberTarget(entityId, currentTick);
        if (target == null || now - target.updatedAtMillis > 1000L) {
            if (trainPhysicsHardLock()) {
                cart.setVelocity(new Vector());
                train.memberSpeed(entityId, 0.0);
                train.snapshot(entityId, new MemberSnapshot(entityId, location, new Vector(), now));
                return;
            }
            Vector velocity = direction.lengthSquared() < 0.0001 || speed <= 0.001
                    ? new Vector()
                    : direction.clone().multiply(speed);
            cart.setVelocity(velocity);
            train.memberSpeed(entityId, speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, location, velocity, now));
            return;
        }

        applyTrainTarget(train, cart, entityId, target, location, now);
    }

    private void layoutTrainTargets(Train train, Location leaderLocation, Vector direction, double speed,
            long now, long currentTick) {
        List<UUID> members = train.members();
        if (members.isEmpty()) {
            return;
        }

        double consistLength = Math.max(0.0, train.spacing * (members.size() - 1));
        TrainRailPath trackPath = train.trackPath();
        if (trackPath == null || !trackPath.isCompatible(leaderLocation, train.reversed, consistLength)) {
            trackPath = TrainRailPath.create(leaderLocation, direction, train.reversed, consistLength);
            if (trackPath == null) {
                train.seedCurrentSpeed(0.0);
                return;
            }
            train.trackPath(trackPath);
        }

        double elapsedTicks = train.beginMotionFrame(now);
        double plannedDistance = speed * elapsedTicks;
        AutomaticRun automaticRun = train.automaticRun;
        if (automaticRun != null && automaticRun.phase == AutomaticRun.Phase.APPROACH) {
            plannedDistance = Math.min(plannedDistance, automaticRun.remaining);
        }
        double movedThisFrame = 0.0;
        StationMotion stationMotion = train.stationMotion();
        if (stationMotion != null && stationMotion.isMoving()) {
            plannedDistance = Math.min(plannedDistance, stationMotion.remainingDistance());
        }
        if (speed > 0.001 && !switchManager.prepareTrain(
                train, trackPath, leaderLocation, plannedDistance)) {
            train.seedCurrentSpeed(0.0);
            speed = 0.0;
        }
        if (speed > 0.001) {
            if (!trackPath.move(plannedDistance, train.reversed)) {
                train.seedCurrentSpeed(0.0);
                speed = 0.0;
            }
            movedThisFrame = trackPath.lastMoveDistance();
            if (automaticRun != null && train.automaticRun == automaticRun) {
                automaticRun.moved(movedThisFrame);
                if (automaticRun.phase == AutomaticRun.Phase.APPROACH && automaticRun.remaining <= 0.02) {
                    automaticRun.arrived(now);
                    train.seedCurrentSpeed(0);
                    speed = 0;
                }
            }
            infrastructureManager.observeTrainMovement(
                    train, trackPath.lastMoveProbes(), trackPath.lastMoveDistance());
            if (stationMotion != null && train.stationMotion() == stationMotion) {
                StationMotion.AdvanceResult result = stationMotion.advance(trackPath.lastMoveDistance(), now);
                if (result == StationMotion.AdvanceResult.DOCKED) {
                    train.seedCurrentSpeed(0.0);
                    train.pauseUntilMillis = stationMotion.dwellUntilMillis();
                    speed = 0.0;
                } else if (result == StationMotion.AdvanceResult.COMPLETE) {
                    train.clearStationMotion(stationMotion);
                    train.pauseUntilMillis = 0L;
                }
            }
        } else {
            infrastructureManager.observeTrainMovement(
                    train, trackPath.probeAhead(0.0, train.reversed), 0.0);
        }
        Map<UUID, TrainMemberTarget> targets = new LinkedHashMap<>();
        List<TrainRailPath.MemberPlacement> placements = trackPath.placements(
                members.size(), train.spacing, train.reversed);
        switchManager.observeTrainLayout(train, placements, now);
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            TrainRailPath.MemberPlacement placement = placements.get(memberIndex);
            Location targetLocation = placement.location();
            Vector targetDirection = placement.direction();
            targetLocation.setYaw(yawFromDirection(targetDirection));
            targetLocation.setPitch(0.0F);
            targets.put(members.get(memberIndex),
                    new TrainMemberTarget(targetLocation, targetDirection, speed, now));
        }
        train.publishMotionFrame(targets, now, currentTick);
        if (plugin.displaySync() != null) plugin.displaySync().publish(train.id(), targets);
        if (speed > 0.001) {
            int leaderIndex = activeLeaderIndex(train);
            train.rememberDirection(placements.get(leaderIndex).direction());
        }
        if (plugin.getConfig().getBoolean("settings.stcs-telemetry-enabled", true)) {
            long publishTicks = Math.max(1L,
                    plugin.getConfig().getLong("settings.stcs-telemetry-publish-interval-ticks", 5L));
            long reconcileTicks = Math.max(20L,
                    plugin.getConfig().getLong("settings.stcs-telemetry-reconcile-interval-ticks",
                            plugin.getConfig().getLong("settings.stcs-telemetry-heartbeat-ticks", 100L)));
            stcsTelemetry.observe(train, driverName(train), movedThisFrame,
                    now, publishTicks * 50L, reconcileTicks * 50L);
        }
    }

    private void applyTrainTarget(Train train, Minecart cart, UUID entityId, TrainMemberTarget target,
            Location currentLocation, long now) {
        if (currentLocation.getWorld() == null || !currentLocation.getWorld().equals(target.location.getWorld())) {
            return;
        }

        Vector trackVelocity = target.direction.lengthSquared() < 0.0001
                ? new Vector()
                : target.direction.clone().normalize().multiply(target.speed);
        if (trainPhysicsHardLock() && tryOwnedMove(cart, target.location)) {
            passengerRecoveries.remove(entityId);
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, cart.getLocation(), trackVelocity, now));
            return;
        }
        if (hasPlayerPassenger(cart) && (trainPhysicsHardLock() || passengerSmoothingEnabled())) {
            applyPassengerTarget(train, cart, entityId, target, currentLocation, trackVelocity, now);
            return;
        }
        if (trainPhysicsHardLock()) {
            Location lockedLocation = target.location.clone();
            boolean completed = teleportTrainMember(cart, lockedLocation, false);
            if (completed) cart.setVelocity(new Vector());
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId,
                    completed ? cart.getLocation() : currentLocation, trackVelocity, now));
            return;
        }

        double distance = currentLocation.distance(target.location);
        if (distance > trainPhysicsTeleportDistance() || target.speed <= 0.001) {
            try {
                cart.teleport(target.location);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to move train member to rail target.", ex);
            }
            cart.setVelocity(new Vector());
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, target.location, new Vector(), now));
            return;
        }

        Vector velocity = trackVelocity;
        Vector correction = target.location.toVector().subtract(currentLocation.toVector())
                .multiply(trainPhysicsPositionCorrection());
        double maxCorrection = trainPhysicsMaxCorrectionPerTick();
        correction.setX(RailMath.clamp(correction.getX(), -maxCorrection, maxCorrection));
        correction.setY(RailMath.clamp(correction.getY(), -maxCorrection, maxCorrection));
        correction.setZ(RailMath.clamp(correction.getZ(), -maxCorrection, maxCorrection));
        velocity.add(correction);

        cart.setVelocity(velocity);
        train.memberSpeed(entityId, horizontalSpeed(velocity));
        train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, velocity, now));
    }

    private void applyPassengerTarget(Train train, Minecart cart, UUID entityId, TrainMemberTarget target,
            Location currentLocation, Vector trackVelocity, long now) {
        // Zero commanded speed is a hold, not permission to drive backwards toward an unreachable
        // arc target. This also prevents recovery teleports after EB has already stopped the train.
        if (target.speed <= 0.001) {
            passengerRecoveries.remove(entityId);
            cart.setMaxSpeed(0.0);
            cart.setVelocity(new Vector());
            train.memberSpeed(entityId, 0.0);
            train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, new Vector(), now));
            return;
        }
        double distance = currentLocation.distance(target.location);
        PassengerRecovery recovery = passengerRecoveries.computeIfAbsent(entityId, ignored -> new PassengerRecovery());
        boolean guarded = plugin.getConfig().getBoolean("settings.passenger-recovery-guard-enabled", true);
        double hardLimit = Math.max(passengerTeleportDistance(),
                plugin.getConfig().getDouble("settings.passenger-recovery-hard-distance", 4.0));
        double softLimit = Math.min(hardLimit * 0.75,
                Math.max(passengerTeleportDistance(), target.speed * 2.0));
        long graceNanos = Math.max(0L, Math.min(40L,
                plugin.getConfig().getLong("settings.passenger-recovery-grace-ticks", 6L))) * 50_000_000L;
        boolean recover = guarded
                ? recovery.shouldRecover(distance, softLimit, hardLimit, graceNanos, System.nanoTime())
                : distance > passengerTeleportDistance();
        if (recover) {
            recovery.reset();
            passengerRecoveryTeleports.increment();
            boolean completed = teleportTrainMember(cart, target.location, true);
            Vector compensatedVelocity = trackVelocity.clone().multiply(1.0 / passengerVanillaMotionFactor());
            if (completed) cart.setVelocity(compensatedVelocity);
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId,
                    completed ? cart.getLocation() : currentLocation, compensatedVelocity, now));
            return;
        }

        Vector correction = target.location.toVector().subtract(currentLocation.toVector())
                .multiply(passengerPositionCorrection());
        double maxCorrection = passengerMaxCorrectionPerTick();
        correction.setX(RailMath.clamp(correction.getX(), -maxCorrection, maxCorrection));
        correction.setY(RailMath.clamp(correction.getY(), -maxCorrection, maxCorrection));
        correction.setZ(RailMath.clamp(correction.getZ(), -maxCorrection, maxCorrection));

        // The previous per-axis cap allowed sqrt(3) times the configured correction on diagonals.
        if (guarded && correction.lengthSquared() > maxCorrection * maxCorrection) {
            correction.normalize().multiply(maxCorrection);
        }

        Vector velocity = trackVelocity.clone().add(correction);
        if (velocity.lengthSquared() > 0.0000001) {
            velocity.multiply(1.0 / passengerVanillaMotionFactor());
        }
        cart.setVelocity(velocity);
        train.memberSpeed(entityId, target.speed);
        train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, velocity, now));
    }

    private boolean tryOwnedMove(Minecart cart, Location target) {
        if (ownedMoverFailed || !plugin.getConfig().getBoolean("settings.owned-region-movement-enabled", true)
                || !Bukkit.getMinecraftVersion().equals("26.2")) return false;
        try {
            boolean passenger = hasPlayerPassenger(cart);
            if (OwnedTrainMover.move(cart, target, passenger)) {
                ownedMoves.increment();
                if (passenger) ownedPassengerMoves.increment();
                return true;
            }
            ownedMoveFallbacks.increment();
        } catch (RuntimeException | LinkageError ex) {
            ownedMoverFailed = true;
            plugin.getLogger().log(Level.WARNING,
                    "Owned-region movement disabled after failure; retaining original Folia relocation path.", ex);
        }
        return false;
    }

    private boolean teleportTrainMember(Minecart cart, Location target, boolean preserveView) {
        UUID entityId = cart.getUniqueId();
        CompletableFuture<Boolean> pending = pendingTeleports.get(entityId);
        if (pending != null && !pending.isDone()) {
            return false;
        }
        if (pending != null) {
            pendingTeleports.remove(entityId, pending);
        }

        Location destination = target.clone();
        physicalTeleports.increment();
        if (preserveView) {
            destination.setYaw(cart.getYaw());
            destination.setPitch(cart.getPitch());
        }

        try {
            if (cart.teleport(destination, TeleportFlag.EntityState.RETAIN_PASSENGERS)) {
                return true;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINEST,
                    "Synchronous train-member teleport crossed a region boundary; using async teleport.", ex);
        }

        CompletableFuture<Boolean> reservation = new CompletableFuture<>();
        if (pendingTeleports.putIfAbsent(entityId, reservation) != null) {
            return false;
        }
        try {
            cart.teleportAsync(destination, TeleportFlag.EntityState.RETAIN_PASSENGERS)
                    .whenComplete((success, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.FINE,
                                    "Failed to move train member " + entityId + " across a region boundary.", error);
                            reservation.completeExceptionally(error);
                        } else {
                            reservation.complete(Boolean.TRUE.equals(success));
                        }
                        pendingTeleports.remove(entityId, reservation);
                    });
        } catch (RuntimeException ex) {
            pendingTeleports.remove(entityId, reservation);
            reservation.completeExceptionally(ex);
            plugin.getLogger().log(Level.FINE, "Failed to schedule train-member teleport.", ex);
        }
        return false;
    }

    private Vector bindVelocityToRail(Minecart cart, RailInfo rail, Location location, Vector velocity, boolean stopped) {
        if (!railBindEnabled() || rail == null || location == null) {
            return velocity;
        }

        double centerX = rail.block.getX() + 0.5;
        double centerZ = rail.block.getZ() + 0.5;
        double offsetX = centerX - location.getX();
        double offsetZ = centerZ - location.getZ();
        double distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
        if (distanceSquared < 0.0004) {
            return velocity;
        }
        if (stopped) {
            return new Vector();
        }

        double teleportDistance = railBindTeleportDistance();
        if (distanceSquared > teleportDistance * teleportDistance) {
            Location target = location.clone();
            target.setX(centerX);
            target.setZ(centerZ);
            target.setYaw(yawFromDirection(velocity.lengthSquared() < 0.0001 ? trainDirectionFallback(cart) : velocity));
            try {
                cart.teleportAsync(target);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to bind minecart to rail center.", ex);
            }
            return velocity;
        }

        double strength = railBindStrength();
        double maxCorrection = railBindMaxCorrectionPerTick();
        double correctionX = RailMath.clamp(offsetX * strength, -maxCorrection, maxCorrection);
        double correctionZ = RailMath.clamp(offsetZ * strength, -maxCorrection, maxCorrection);
        return velocity.clone().add(new Vector(correctionX, 0.0, correctionZ));
    }

    private void applyReverserDirection(Train train, long now) {
        if (!train.driveControlEnabled || train.reverser == Reverser.NEUTRAL) {
            return;
        }
        boolean desiredReversed = train.reverser.wantsBackward();
        if (train.reversed == desiredReversed) {
            return;
        }
        if (Math.max(train.currentSpeed(), train.maxMemberSpeed()) > driveDirectionChangeSpeed()) {
            return;
        }

        train.reversed = desiredReversed;
        refreshMemberIndexes(train);
        train.reverseRememberedDirection();
        train.reverseMileageDirection();
        train.clearSnapshots();
        train.clearMemberSpeeds();
        train.clearMemberTargets();
        train.seedCurrentSpeed(0.0);
        train.reverseSettleUntilMillis = Math.max(train.reverseSettleUntilMillis, now + reverseSettleTicks() * 50L);
    }

    private void removeNonPlayerPassengers(Minecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (!(passenger instanceof Player)) {
                cart.removePassenger(passenger);
            }
        }
    }

    private boolean hasPlayerPassenger(Minecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private boolean reverseBraking(Train train, long now) {
        if (!train.reversePending && train.reverseSettleUntilMillis <= 0L && train.reverseBrakeDeadlineMillis <= 0L) {
            return false;
        }
        if (now < train.reverseSettleUntilMillis) {
            return true;
        }
        if (now < train.reverseBrakeDeadlineMillis
                && Math.max(train.currentSpeed(), train.maxMemberSpeed()) > reverseReleaseSpeed()) {
            return true;
        }

        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        if (train.reversePending) {
            train.reversePending = false;
            train.reversed = !train.reversed;
            refreshMemberIndexes(train);
            train.reverseRememberedDirection();
            train.reverseMileageDirection();
            train.clearSnapshots();
            train.clearMemberSpeeds();
            train.clearMemberTargets();
            train.seedCurrentSpeed(0.0);
            return true;
        }
        return false;
    }

    private SpeedLimit frontMinecartSpeedLimit(Train train, Minecart cart, Location location, Vector direction,
            double desiredSpeed) {
        if (!frontMinecartDetectionEnabled() || desiredSpeed <= 0.001 || direction.lengthSquared() < 0.0001) {
            return new SpeedLimit(desiredSpeed, false);
        }

        Vector forward = direction.clone().setY(0.0);
        if (forward.lengthSquared() < 0.0001) {
            return new SpeedLimit(desiredSpeed, false);
        }
        forward.normalize();

        double detectionDistance = frontMinecartDetectionDistance();
        double stopDistance = Math.min(frontMinecartStopDistance(), detectionDistance - 0.1);
        double lateralDistance = frontMinecartLateralDistance();
        double closest = Double.MAX_VALUE;

        for (Entity entity : cart.getNearbyEntities(detectionDistance, 2.0, detectionDistance)) {
            if (!(entity instanceof Minecart other) || !other.isValid() || other.isDead()) {
                continue;
            }
            if (other.getUniqueId().equals(cart.getUniqueId()) || train.contains(other.getUniqueId())
                    || isSameTrain(cart, other)) {
                continue;
            }
            if (!other.getWorld().equals(cart.getWorld())) {
                continue;
            }

            Vector offset = other.getLocation().toVector().subtract(location.toVector());
            if (Math.abs(offset.getY()) > 1.75) {
                continue;
            }
            Vector flat = offset.clone().setY(0.0);
            double aheadDistance = flat.dot(forward);
            if (aheadDistance <= 0.0 || aheadDistance > detectionDistance) {
                continue;
            }
            double lateralSquared = Math.max(0.0, flat.lengthSquared() - aheadDistance * aheadDistance);
            if (lateralSquared > lateralDistance * lateralDistance) {
                continue;
            }
            closest = Math.min(closest, aheadDistance);
        }

        if (closest == Double.MAX_VALUE) {
            return new SpeedLimit(desiredSpeed, false);
        }
        if (closest <= stopDistance) {
            return new SpeedLimit(0.0, true);
        }

        double range = Math.max(0.1, detectionDistance - stopDistance);
        double ratio = RailMath.clamp((closest - stopDistance) / range, 0.0, 1.0);
        boolean emergencyBrake = closest <= stopDistance + 0.75;
        return new SpeedLimit(Math.min(desiredSpeed, desiredSpeed * ratio), emergencyBrake);
    }

    private double coupledMemberSpeed(Train train, double baseSpeed, MemberSnapshot target, double followDistance,
            boolean controlledStop) {
        if (target == null || followDistance < 0.0) {
            return baseSpeed;
        }

        double spacing = Math.max(0.1, train.spacing);
        double targetSpeed = horizontalSpeed(target.velocity);
        double hardStopDistance = spacing * coupledHardStopRatio();

        if (followDistance <= hardStopDistance) {
            return 0.0;
        }
        if (controlledStop || train.reversePending) {
            return RailMath.clamp(Math.min(baseSpeed, targetSpeed + coupledBrakeSpeedBuffer()), 0.0, train.maxSpeed);
        }

        return RailMath.clamp(baseSpeed, 0.0, train.maxSpeed);
    }

    private Vector trainDirectionFallback(Minecart cart) {
        Vector velocity = cart.getVelocity();
        if (velocity.lengthSquared() > 0.0001) {
            return velocity;
        }
        return RailMath.yawDirection(cart.getLocation().getYaw());
    }

    private double horizontalSpeed(Vector velocity) {
        if (velocity == null) {
            return 0.0;
        }
        return Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
    }

    private int activeLeaderIndex(Train train) {
        return train.reversed ? Math.max(0, train.memberCount() - 1) : 0;
    }

    void playLeaderSound(Train train, Sound sound, SoundCategory category, float volume, float pitch) {
        List<UUID> members = train.members();
        if (members.isEmpty()) return;
        UUID leaderId = members.get(train.reversed ? members.size() - 1 : 0);
        Minecart leader = managedCarts.get(leaderId);
        if (leader == null) return;
        // Resolve world state only on the selected emitter's owning region.
        leader.getScheduler().run(plugin, task -> {
            if (leader.isValid() && !leader.isDead() && trainForCart(leader) == train
                    && train.properties().soundEnabled) {
                leader.getWorld().playSound(leader, sound, category, volume, pitch);
            }
        }, null);
    }

    private Location activeLeaderLocation(Train train) {
        List<UUID> members = train.members();
        if (members.isEmpty()) {
            return null;
        }
        UUID leaderId = members.get(activeLeaderIndex(train));
        Minecart leader = managedCarts.get(leaderId);
        if (leader != null && leader.isValid() && !leader.isDead()) {
            return leader.getLocation();
        }
        MemberSnapshot snapshot = train.snapshot(leaderId);
        World world = snapshot == null ? null : Bukkit.getWorld(snapshot.worldName);
        return world == null ? null : new Location(world, snapshot.x, snapshot.y, snapshot.z);
    }

    private Vector activeLeaderDirection(Train train, Location leaderLocation) {
        TrainRailPath trackPath = train.trackPath();
        TrainTrackPosition trackPosition = trackPath == null
                ? null : trackPath.activeLeaderTrackPosition(train.reversed);
        if (trackPosition != null && trackPosition.motion().lengthSquared() >= 0.0001) {
            return trackPosition.motion();
        }
        Vector remembered = train.rememberedDirection();
        if (remembered.lengthSquared() >= 0.0001) {
            return remembered;
        }
        return leaderLocation == null ? new Vector(0.0, 0.0, 1.0)
                : RailMath.yawDirection(leaderLocation.getYaw());
    }

    private void refreshMemberIndexes(Train train) {
        List<UUID> members = train.members();
        for (int i = 0; i < members.size(); i++) {
            UUID entityId = members.get(i);
            cartIndex.put(entityId, train.id());
        }
    }

    private boolean handleSignActions(Train train, Block railBlock, Location leaderLocation,
            Vector travelDirection, long now) {
        for (Block block : nearbySignBlocks(railBlock)) {
            if (!RailSignAccess.readable(block)) continue;
            BlockState state = block.getState();
            if (!(state instanceof Sign sign)) {
                continue;
            }

            String header = plain(sign.getLine(0));
            if (!SignHeaders.isSkyTrain(header) || !isHeaderActive(header, block)) {
                continue;
            }

            String key = signKey(block);
            String actionLine = plain(sign.getLine(1));
            if (AutomaticSigns.action(actionLine)) continue;
            if ("station".equals(firstToken(actionLine)) && train.stationLatched(key)) {
                continue;
            }
            if (!train.canTriggerSign(key, now, signCooldownMillis())) {
                continue;
            }

            if (triggerSign(train, actionLine, plain(sign.getLine(2)), plain(sign.getLine(3)),
                    block, key, leaderLocation, travelDirection, now)) {
                return true;
            }
        }
        return false;
    }

    private List<Block> nearbySignBlocks(Block railBlock) {
        List<Block> blocks = new ArrayList<>(27);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    blocks.add(railBlock.getRelative(x, y, z));
                }
            }
        }
        return blocks;
    }

    private boolean triggerSign(Train train, String actionLine, String valueLine,
            String modifierLine, Block signBlock, String signKey, Location leaderLocation,
            Vector travelDirection, long now) {
        String[] tokens = actionLine.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return false;
        }

        String action = tokens[0].toLowerCase(Locale.ROOT);
        String value = tokens.length >= 2 ? tokens[1] : valueLine;
        if ("station".equals(action) && train.stationLatched(signKey)) {
            return false;
        }
        if (AutomaticSigns.action(actionLine)) return false;
        switch (action) {
            case "station":
                train.latchStation(signKey);
                if (!train.properties().conductionMode.automatic()) {
                    break;
                }
                long waitTicks = stationWaitTicks(value, train);
                double dockingDistance = stationDockingDistance(
                        train, signKey, leaderLocation, travelDirection);
                boolean reverseOnDeparture = stationDepartureReverses(
                        train, signBlock, modifierLine, travelDirection);
                train.clearPlayerPush();
                train.driveControlEnabled = false;
                train.moving = true;
                train.pauseUntilMillis = 0L;
                train.beginStationMotion(signKey, dockingDistance,
                        Math.max(train.currentSpeed(), train.maxMemberSpeed()),
                        waitTicks * 50L, reverseOnDeparture, now);
                StationMotion stationMotion = train.stationMotion();
                if (stationMotion != null && stationMotion.isWaiting()) {
                    train.seedCurrentSpeed(0.0);
                    train.pauseUntilMillis = stationMotion.dwellUntilMillis();
                }
                break;
            case "property":
                if (value != null && !value.isBlank() && signPropertyAllowed(value)) {
                    setProperty(train.name(), value, modifierLine);
                }
                break;
            default:
                break;
        }
        return false;
    }

    private boolean stationDepartureReverses(Train train, Block signBlock, String modifierLine,
            Vector travelDirection) {
        String departure = modifierLine == null ? "continue" : modifierLine.trim().toLowerCase(Locale.ROOT);
        boolean reverse = "reverse".equals(departure);
        if ("left".equals(departure) || "right".equals(departure)) {
            Vector wanted = stationManager.departureDirection(signBlock, departure);
            Vector current = travelDirection == null || travelDirection.lengthSquared() < 0.0001
                    ? train.rememberedDirection() : travelDirection;
            reverse = wanted.lengthSquared() >= 0.0001 && current.lengthSquared() >= 0.0001
                    && wanted.dot(current) < 0.0;
        }
        return reverse;
    }

    private double stationDockingDistance(Train train, String signKey, Location leaderLocation,
            Vector travelDirection) {
        double consistCenterOffset = Math.max(0.0, train.spacing * (train.memberCount() - 1) * 0.5);
        double stationOffset = 0.0;
        Location stopLocation = stationManager.stopLocation(signKey, travelDirection);
        if (stopLocation != null && leaderLocation != null && leaderLocation.getWorld() != null
                && leaderLocation.getWorld().equals(stopLocation.getWorld())
                && travelDirection != null && travelDirection.lengthSquared() >= 0.0001) {
            Vector direction = travelDirection.clone().normalize();
            stationOffset = stopLocation.toVector().subtract(leaderLocation.toVector()).dot(direction);
        }
        return RailMath.clamp(
                stationOffset + consistCenterOffset + stationDockingStopOffset(),
                0.0,
                stationDockingMaxDistance());
    }

    private void prepareStationDeparture(Train train, long now) {
        StationMotion stationMotion = train.stationMotion();
        if (stationMotion == null || !stationMotion.dwellExpired(now)) {
            return;
        }
        if (stationMotion.needsDepartureReverse(train.reversed)) {
            if (!train.reversePending) {
                train.reversePending = true;
                train.reverseBrakeDeadlineMillis = Math.max(train.reverseBrakeDeadlineMillis,
                        now + reverseMaxBrakeTicks() * 50L);
            }
            return;
        }
        if (train.reversePending || train.reverseSettleUntilMillis > now) {
            return;
        }
        train.pauseUntilMillis = 0L;
        stationMotion.beginDeparture(train.targetSpeed, stationDepartureDistance());
        if (stationMotion.phase() == StationMotion.Phase.COMPLETE) {
            train.clearStationMotion(stationMotion);
        }
    }

    private double stationMotionMinimumSpeed(StationMotion stationMotion) {
        return stationMotion.phase() == StationMotion.Phase.DOCKING
                ? stationDockingMinSpeed() : stationDepartureMinSpeed();
    }

    private void refreshStationLatches(Train train) {
        if (train.stationLatches().isEmpty()) {
            return;
        }
        double releaseDistanceSquared = stationReleaseDistance() * stationReleaseDistance();
        List<MemberSnapshot> snapshots = train.snapshots();
        for (String key : train.stationLatches()) {
            Location sign = stationManager.signLocation(key);
            if (sign == null) {
                train.releaseStation(key);
                continue;
            }
            boolean occupied = snapshots.stream().anyMatch(snapshot ->
                    snapshot.worldName.equals(sign.getWorld().getName())
                            && square(snapshot.x - sign.getX())
                                    + square(snapshot.y - sign.getY())
                                    + square(snapshot.z - sign.getZ()) <= releaseDistanceSquared);
            if (!occupied) {
                train.releaseStation(key);
            }
        }
    }

    private void nextRouteDestination(Train train) {
        List<String> route = train.properties().route();
        if (route.isEmpty()) {
            return;
        }
        String current = train.properties().destination;
        int nextIndex = 0;
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equalsIgnoreCase(current)) {
                nextIndex = (i + 1) % route.size();
                break;
            }
        }
        train.properties().destination = route.get(nextIndex);
    }

    private String signKey(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    private void arrangeTrainCarts(Train train, List<Minecart> carts, Location origin) {
        if (!autoArrangeEnabled() || train == null || carts == null || carts.size() < 2) {
            return;
        }

        List<Minecart> valid = uniqueValidCarts(carts, origin).stream()
                .filter(train::containsCart)
                .toList();
        if (valid.size() < 2) {
            return;
        }
        if (valid.size() != train.memberCount()) {
            return;
        }

        Location base = origin == null ? valid.get(0).getLocation() : origin;
        Vector preference = directionPreference(base, valid);
        RailInfo rail = RailMath.findRail(base);
        Vector direction = rail == null
                ? preference
                : RailMath.direction(rail.rail.getShape(), preference);
        if (direction.lengthSquared() < 0.0001) {
            direction = RailMath.yawDirection(base.getYaw());
        }

        Vector sortDirection = direction.clone();
        List<Minecart> ordered = valid.stream()
                .sorted(Comparator.<Minecart>comparingDouble(
                        cart -> cart.getLocation().toVector().subtract(base.toVector()).dot(sortDirection)).reversed())
                .toList();
        train.reorderMembers(ordered.stream().map(Minecart::getUniqueId).toList());
        train.rememberDirection(direction);

        Minecart anchor = ordered.get(0);
        Location anchorLocation = anchor.getLocation();
        double spacing = autoArrangeSpacing();
        if (forceTightSpacing()) {
            spacing = tightSpacing();
        }
        train.spacing = spacing;
        train.clearMemberTargets();
        train.clearTrackPath();
        for (int i = 0; i < ordered.size(); i++) {
            Minecart cart = ordered.get(i);
            markCart(cart, train, i);
            ensureTask(cart, train);
            Location target = anchorLocation.clone().add(direction.clone().multiply(-spacing * i));
            target.setYaw(yawFromDirection(direction));
            target.setPitch(0.0F);
            scheduleTeleportAndStop(cart, target);
        }
        showConsistLabels(train, ordered);
    }

    void showConsistLabels(Train train) {
        if (train == null) {
            return;
        }
        List<Minecart> carts = train.members().stream()
                .map(managedCarts::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        showConsistLabels(train, carts);
    }

    private void showConsistLabels(Train train, List<Minecart> carts) {
        if (train == null || carts == null || carts.isEmpty()) {
            return;
        }

        long visibleTicks = consistLabelVisibleTicks();
        if (visibleTicks <= 0L) {
            return;
        }

        for (Minecart cart : carts) {
            if (cart == null || !cart.isValid() || cart.isDead()) {
                continue;
            }
            int index = train.indexOf(cart.getUniqueId());
            if (index < 0) {
                continue;
            }
            try {
                cart.getScheduler().run(
                        plugin,
                        task -> {
                            if (!cart.isValid() || cart.isDead()) {
                                return;
                            }
                            ConsistLabelState original = consistLabelStates.computeIfAbsent(
                                    cart.getUniqueId(),
                                    ignored -> new ConsistLabelState(cart.getCustomName(), cart.isCustomNameVisible()));
                            long version = consistLabelVersions.merge(cart.getUniqueId(), 1L, Long::sum);
                            cart.setCustomName(ChatColor.AQUA + Integer.toString(index + 1) + "车");
                            cart.setCustomNameVisible(true);
                            scheduleConsistLabelReset(cart, original, version, visibleTicks);
                        },
                        () -> {
                            consistLabelStates.remove(cart.getUniqueId());
                            consistLabelVersions.remove(cart.getUniqueId());
                        });
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to show train consist label.", ex);
            }
        }
    }

    private void scheduleConsistLabelReset(Minecart cart, ConsistLabelState original, long version,
            long visibleTicks) {
        cart.getScheduler().runDelayed(
                plugin,
                task -> {
                    if (!cart.isValid() || cart.isDead()
                            || consistLabelVersions.getOrDefault(cart.getUniqueId(), 0L) != version) {
                        return;
                    }
                    cart.setCustomName(original.name());
                    cart.setCustomNameVisible(original.visible());
                    consistLabelVersions.remove(cart.getUniqueId(), version);
                    consistLabelStates.remove(cart.getUniqueId(), original);
                },
                () -> {
                    consistLabelStates.remove(cart.getUniqueId(), original);
                    consistLabelVersions.remove(cart.getUniqueId(), version);
                },
                visibleTicks);
    }

    private Vector directionPreference(Location anchor, List<Minecart> carts) {
        Minecart farthest = null;
        double bestDistance = 0.0;
        for (Minecart cart : carts) {
            if (cart.getWorld().equals(anchor.getWorld())) {
                double distance = cart.getLocation().distanceSquared(anchor);
                if (distance > bestDistance) {
                    bestDistance = distance;
                    farthest = cart;
                }
            }
        }
        if (farthest != null && bestDistance > 0.04) {
            return farthest.getLocation().toVector().subtract(anchor.toVector()).multiply(-1.0);
        }
        return RailMath.yawDirection(anchor.getYaw());
    }

    private void scheduleTeleportAndStop(Minecart cart, Location target) {
        try {
            cart.getScheduler().run(
                    plugin,
                    task -> {
                        if (!cart.isValid() || cart.isDead()) {
                            return;
                        }

                        cart.teleportAsync(target).whenComplete((success, error) -> {
                            if (error != null) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Failed to arrange train cart " + cart.getUniqueId(), error);
                                return;
                            }
                            if (!Boolean.TRUE.equals(success)) {
                                return;
                            }
                            try {
                                cart.getScheduler().run(
                                        plugin,
                                        followUp -> {
                                            if (cart.isValid() && !cart.isDead()) {
                                                cart.setVelocity(new Vector());
                                            }
                                        },
                                        () -> {
                                        });
                            } catch (RuntimeException ex) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Failed to stop arranged train cart " + cart.getUniqueId(), ex);
                            }
                        });
                    },
                    () -> {
                    });
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to arrange train cart " + cart.getUniqueId(), ex);
        }
    }

    private float yawFromDirection(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private List<Minecart> nearbyMinecarts(Minecart origin, double radius) {
        List<Minecart> carts = new ArrayList<>();
        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Minecart minecart && minecart.isValid() && !minecart.isDead()) {
                carts.add(minecart);
            }
        }
        return carts;
    }

    private List<Minecart> minecartsNear(Player player, Location center, double radius) {
        List<Minecart> carts = new ArrayList<>();
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart minecart && isNear(minecart, center, radius)) {
            carts.add(minecart);
        }

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Minecart minecart && isNear(minecart, center, radius)) {
                carts.add(minecart);
            }
        }
        return uniqueValidCarts(carts, center);
    }

    private boolean isNear(Minecart minecart, Location center, double radius) {
        World world = center.getWorld();
        return minecart.isValid()
                && !minecart.isDead()
                && world != null
                && minecart.getWorld().equals(world)
                && minecart.getLocation().distanceSquared(center) <= radius * radius;
    }

    private List<Minecart> uniqueValidCarts(List<Minecart> carts, Location origin) {
        Map<UUID, Minecart> unique = new LinkedHashMap<>();
        for (Minecart cart : carts) {
            if (cart != null && cart.isValid() && !cart.isDead()) {
                unique.putIfAbsent(cart.getUniqueId(), cart);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparingDouble(cart -> distanceSquared(origin, cart)))
                .limit(maxAutoLinkCarts())
                .toList();
    }

    private double distanceSquared(Location origin, Minecart cart) {
        if (origin == null || cart.getWorld() == null || origin.getWorld() == null || !cart.getWorld().equals(origin.getWorld())) {
            return Double.MAX_VALUE;
        }
        return cart.getLocation().distanceSquared(origin);
    }

    private String generateAutoTrainName() {
        String prefix = plugin.getConfig().getString("settings.auto-train-name-prefix", "auto");
        if (prefix == null || prefix.isBlank()) {
            prefix = "auto";
        }
        prefix = prefix.trim().replace(' ', '-');

        for (int i = 0; i < 1000; i++) {
            String suffix = Integer.toString(ThreadLocalRandom.current().nextInt(0x100000), 36);
            String candidate = prefix + "-" + suffix;
            if (!nameIndex.containsKey(Train.normalizeName(candidate))) {
                return candidate;
            }
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String firstToken(String line) {
        if (line == null) {
            return "";
        }
        String clean = plain(line).trim();
        if (clean.isEmpty()) {
            return "";
        }
        return clean.split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private boolean isSignAction(String action) {
        return switch (action) {
            case "start", "go", "launch", "stop", "halt", "reverse", "back", "speed", "setspeed", "maxspeed",
                    "station", "wait", "destination", "dest", "clear-destination", "cleardestination",
                    "next", "next-destination", "nextdestination", "route" -> true;
            default -> false;
        };
    }

    private java.util.OptionalDouble parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return java.util.OptionalDouble.empty();
        }
        try {
            return java.util.OptionalDouble.of(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ex) {
            return java.util.OptionalDouble.empty();
        }
    }

    private long stationWaitTicks(String value, Train train) {
        java.util.OptionalLong parsed = parseDurationTicks(value);
        if (parsed.isPresent()) {
            return parsed.getAsLong();
        }
        if (train.properties().waitTicks > 0) {
            return train.properties().waitTicks;
        }
        return defaultStationWaitTicks();
    }

    private java.util.OptionalLong parseDurationTicks(String value) {
        if (value == null || value.isBlank()) {
            return java.util.OptionalLong.empty();
        }

        String clean = plain(value).trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            return java.util.OptionalLong.empty();
        }

        DurationUnit unit = DurationUnit.AUTO;
        String number = clean;
        if (number.endsWith("milliseconds")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "milliseconds".length());
        } else if (number.endsWith("millis")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "millis".length());
        } else if (number.endsWith("ms")) {
            unit = DurationUnit.MILLISECONDS;
            number = number.substring(0, number.length() - "ms".length());
        } else if (number.endsWith("ticks")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - "ticks".length());
        } else if (number.endsWith("tick")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - "tick".length());
        } else if (number.endsWith("t")) {
            unit = DurationUnit.TICKS;
            number = number.substring(0, number.length() - 1);
        } else if (number.endsWith("seconds")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "seconds".length());
        } else if (number.endsWith("second")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "second".length());
        } else if (number.endsWith("secs")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "secs".length());
        } else if (number.endsWith("sec")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - "sec".length());
        } else if (number.endsWith("秒")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - 1);
        } else if (number.endsWith("s")) {
            unit = DurationUnit.SECONDS;
            number = number.substring(0, number.length() - 1);
        }

        try {
            double amount = Double.parseDouble(number.trim());
            if (amount <= 0.0) {
                return java.util.OptionalLong.of(0L);
            }
            long ticks = switch (unit) {
                case MILLISECONDS -> Math.round(amount / 50.0);
                case SECONDS -> Math.round(amount * 20.0);
                case TICKS -> Math.round(amount);
                case AUTO -> amount <= 20.0 ? Math.round(amount * 20.0) : Math.round(amount);
            };
            return java.util.OptionalLong.of(Math.max(0L, ticks));
        } catch (NumberFormatException ex) {
            return java.util.OptionalLong.empty();
        }
    }

    private double parseDouble(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " 需要数字值。");
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(property + " 需要数字值。");
        }
    }

    private boolean parseBoolean(String value, String property) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "true", "yes", "on", "1", "allow", "enabled" -> true;
            case "false", "no", "off", "0", "deny", "disabled" -> false;
            default -> throw new IllegalArgumentException(property + " 需要 true/false。");
        };
    }

    private void renameTrain(Train train, String newName) {
        String clean = cleanName(newName);
        String newKey = Train.normalizeName(clean);
        UUID existing = nameIndex.get(newKey);
        if (existing != null && !existing.equals(train.id())) {
            throw new IllegalArgumentException("列车名已经存在: " + clean);
        }
        nameIndex.remove(train.key());
        train.rename(clean);
        nameIndex.put(train.key(), train.id());
    }

    private String uniqueTrainName(String baseName) {
        String cleanBase = cleanName(baseName);
        if (!nameIndex.containsKey(Train.normalizeName(cleanBase))) {
            return cleanBase;
        }
        for (int i = 2; i < 10000; i++) {
            String candidate = cleanBase + "-" + i;
            if (!nameIndex.containsKey(Train.normalizeName(candidate))) {
                return candidate;
            }
        }
        return cleanBase + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void markCart(Minecart cart, Train train, int index) {
        PersistentDataContainer data = cart.getPersistentDataContainer();
        data.set(trainIdKey, PersistentDataType.STRING, train.id().toString());
        data.set(trainNameKey, PersistentDataType.STRING, train.name());
        data.set(memberIndexKey, PersistentDataType.INTEGER, index);
        cartIndex.put(cart.getUniqueId(), train.id());
    }

    private void clearCartMark(Minecart cart) {
        forgetDisplay(cart.getUniqueId());
        PersistentDataContainer data = cart.getPersistentDataContainer();
        data.remove(trainIdKey);
        data.remove(trainNameKey);
        data.remove(memberIndexKey);
        restoreVanillaCartPhysics(cart);
    }

    private void scheduleCartRelease(Minecart cart) {
        try {
            cart.getScheduler().run(
                    plugin,
                    task -> {
                        if (cart.isValid() && !cart.isDead()) {
                            clearCartMark(cart);
                        }
                    },
                    () -> {
                    });
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to release train cart " + cart.getUniqueId(), ex);
        }
    }

    private void scheduleCartRemoval(Minecart cart) {
        UUID entityId = cart.getUniqueId();
        retiringCarts.add(entityId);
        try {
            cart.getScheduler().run(
                    plugin,
                    task -> {
                        try {
                            if (cart.isValid() && !cart.isDead()) {
                                cart.eject();
                                cart.remove();
                            }
                        } finally {
                            retiringCarts.remove(entityId);
                        }
                    },
                    () -> retiringCarts.remove(entityId));
        } catch (RuntimeException ex) {
            retiringCarts.remove(entityId);
            plugin.getLogger().log(Level.WARNING, "Failed to remove train cart " + entityId, ex);
        }
    }

    private void restoreVanillaCartPhysics(Minecart cart) {
        cart.setMaxSpeed(0.4);
        cart.setGravity(true);
        cart.setVelocity(new Vector());
    }

    private void applyForcedSpacing(Train train) {
        if (forceTightSpacing()) {
            train.spacing = tightSpacing();
        }
    }

    private UUID readTrainId(Minecart cart) {
        String id = cart.getPersistentDataContainer().get(trainIdKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean sameWorld(MemberSnapshot snapshot, Location location) {
        return location.getWorld() != null && snapshot.worldName.equals(location.getWorld().getName());
    }

    private static String plain(String text) {
        return ChatColor.stripColor(text == null ? "" : text).trim();
    }

    private String cleanName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("列车名不能为空");
        }
        if (clean.length() > 32) {
            throw new IllegalArgumentException("列车名不能超过 32 个字符");
        }
        return clean;
    }

    private String normalizeProperty(String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("属性名不能为空。");
        }
        return property.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private double defaultSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.default-speed", 0.35), 0.0, maxSpeed());
    }

    private double maxSpeed() {
        return Math.min(plugin.vehicleProfile().defaultMaxSpeed(), plugin.serverSpeedLimit());
    }

    private double maxAllowedSpeed() {
        return Math.min(plugin.vehicleProfile().maxSpeed(), plugin.serverSpeedLimit());
    }

    private double maxSpeedChangePerTick() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.max-speed-change-per-tick", 0.035), 0.001, 0.5);
    }

    private double poweredRailBoostSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.powered-rail-boost-speed", 0.6), 0.0, maxSpeed());
    }

    private double defaultSpacing() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.default-spacing", 1.10), 0.8, 8.0);
    }

    private double spacingCorrection() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.spacing-correction", 0.045), 0.0, 0.5);
    }

    private double spacingFollowCorrection() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.spacing-follow-correction", 0.35), 0.0, 2.0);
    }

    private boolean forceTightSpacing() {
        return plugin.getConfig().getBoolean("settings.force-tight-spacing", true);
    }

    private double tightSpacing() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.tight-spacing", 1.10), 0.6, 2.0);
    }

    private long reverseSettleTicks() {
        return Math.max(1L, plugin.getConfig().getLong("settings.reverse-settle-ticks", 12L));
    }

    private long reverseMaxBrakeTicks() {
        return Math.max(reverseSettleTicks(), plugin.getConfig().getLong("settings.reverse-max-brake-ticks", 40L));
    }

    private double reverseReleaseSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.reverse-release-speed", 0.04), 0.0, maxSpeed());
    }

    private double minimumFollowDistanceRatio() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.minimum-follow-distance-ratio", 0.72), 0.25, 0.95);
    }

    private boolean railBindEnabled() {
        return plugin.getConfig().getBoolean("settings.rail-bind-enabled", true);
    }

    private double railBindStrength() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.rail-bind-strength", 0.45), 0.0, 1.0);
    }

    private double railBindMaxCorrectionPerTick() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.rail-bind-max-correction-per-tick", 0.035),
                0.0,
                0.2);
    }

    private double railBindTeleportDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.rail-bind-teleport-distance", 0.55), 0.1, 2.0);
    }

    private boolean trainPhysicsHardLock() {
        return plugin.getConfig().getBoolean("settings.track-coordinate-physics", true);
    }

    private double trainPhysicsPositionCorrection() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.train-physics-position-correction", 0.75), 0.0, 2.0);
    }

    private double trainPhysicsMaxCorrectionPerTick() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.train-physics-max-correction-per-tick", 0.20), 0.0, 1.0);
    }

    private double trainPhysicsTeleportDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.train-physics-teleport-distance", 0.45), 0.05, 2.0);
    }

    private boolean passengerSmoothingEnabled() {
        return plugin.getConfig().getBoolean("settings.passenger-smoothing-enabled", true);
    }

    private double passengerPositionCorrection() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.passenger-position-correction", 0.30), 0.0, 1.0);
    }

    private double passengerMaxCorrectionPerTick() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.passenger-max-correction-per-tick", 0.10), 0.0, 0.5);
    }

    private double passengerTeleportDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.passenger-teleport-distance", 1.25), 0.25, 4.0);
    }

    private double passengerSettleDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.passenger-settle-distance", 0.04), 0.005, 0.25);
    }

    private double passengerVanillaMotionFactor() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.passenger-vanilla-motion-factor", 0.75), 0.25, 1.0);
    }

    private void playTracksideRunningSound(Train train, Minecart cart, double speed, long currentTick) {
        if (!train.properties().soundEnabled
                || !plugin.getConfig().getBoolean("settings.trackside-running-sound-enabled", true)
                || speed < tracksideRunningSoundMinSpeed()) {
            return;
        }

        int interval = tracksideRunningSoundIntervalTicks();
        if (Math.floorMod(currentTick + train.id().hashCode(), interval) != 0) {
            return;
        }

        double start = RailMath.clamp(plugin.getConfig().getDouble("settings.trackside-running-sound-start-kmh", 10), 0, 400);
        double full = RailMath.clamp(plugin.getConfig().getDouble("settings.trackside-running-sound-full-kmh", 120), start + 1, 1000);
        double speedRatio = TrainSoundState.level(Math.abs(speed) * 72, start, full);
        if (speedRatio <= 0) return;
        double minPitch = tracksideRunningSoundMinPitch();
        float pitch = (float) (minPitch
                + (tracksideRunningSoundMaxPitch() - minPitch) * speedRatio);
        cart.getWorld().playSound(
                cart,
                Sound.ENTITY_MINECART_RIDING,
                SoundCategory.NEUTRAL,
                (float) (tracksideRunningSoundVolume() * speedRatio),
                pitch);
    }

    private void playBrakeSound(Train train, Minecart cart, long now) {
        boolean enabled = train.properties().soundEnabled
                && plugin.getConfig().getBoolean("settings.brake-sound-enabled", true);
        long cooldown = Math.max(0, Math.min(5000, plugin.getConfig().getLong("settings.brake-sound-cooldown-ms", 300)));
        int event = train.soundState.brakeEvent(train.emergencyBrake ? 8 : train.brakeNotch, now, cooldown, enabled);
        if (event == 0) return;
        String type = event > 0 ? "apply" : "release";
        float volume = (float) RailMath.clamp(plugin.getConfig().getDouble("settings.brake-sound-" + type + "-volume", event > 0 ? .45 : .55), 0, 1);
        float pitch = (float) RailMath.clamp(plugin.getConfig().getDouble("settings.brake-sound-" + type + "-pitch", event > 0 ? 1.5 : .7), .5, 2);
        cart.getWorld().playSound(cart, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, volume, pitch);
    }

    private int tracksideRunningSoundIntervalTicks() {
        return Math.max(4, Math.min(100,
                plugin.getConfig().getInt("settings.trackside-running-sound-interval-ticks", 16)));
    }

    private double tracksideRunningSoundMinSpeed() {
        return RailMath.clamp(
                plugin.getConfig().getDouble("settings.trackside-running-sound-min-speed", 0.025),
                0.0,
                1.0);
    }

    private double tracksideRunningSoundVolume() {
        return RailMath.clamp(
                plugin.getConfig().getDouble("settings.trackside-running-sound-volume", 0.85),
                0.0,
                4.0);
    }

    private double tracksideRunningSoundMinPitch() {
        return RailMath.clamp(
                plugin.getConfig().getDouble("settings.trackside-running-sound-min-pitch", 0.75),
                0.5,
                2.0);
    }

    private double tracksideRunningSoundMaxPitch() {
        double minPitch = tracksideRunningSoundMinPitch();
        return RailMath.clamp(
                plugin.getConfig().getDouble("settings.trackside-running-sound-max-pitch", 1.20),
                minPitch,
                2.0);
    }


    private double playerPushImpulse() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.player-push-impulse", 0.055), 0.005, 0.5);
    }

    private double playerPushMovementBonus() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.player-push-movement-bonus", 0.045), 0.0, 0.5);
    }

    private double playerPushMaxSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.player-push-max-speed", 0.22), 0.01, 1.0);
    }

    private double playerPushActivationSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.player-push-activation-speed", 0.035), 0.0, 0.25);
    }

    private double playerPushDecelerationPerTick() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.player-push-deceleration-per-tick", 0.004), 0.0001, 0.1);
    }

    private double driveDirectionChangeSpeed() { return plugin.vehicleProfile().reverseSpeed(); }

    private boolean frontMinecartDetectionEnabled() {
        return plugin.getConfig().getBoolean("settings.front-minecart-detection-enabled", true);
    }

    private double frontMinecartDetectionDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.front-minecart-detection-distance", 8.0), 2.0, 32.0);
    }

    private double frontMinecartStopDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.front-minecart-stop-distance", 1.65), 0.6,
                Math.max(0.7, frontMinecartDetectionDistance() - 0.1));
    }

    private double frontMinecartLateralDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.front-minecart-lateral-distance", 1.15), 0.3, 4.0);
    }

    private double coupledHardStopRatio() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.coupled-hard-stop-ratio", 0.78), 0.3, 0.98);
    }

    private double coupledSlowRatio() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.coupled-slow-ratio", 1.08), coupledHardStopRatio() + 0.02,
                1.6);
    }

    private double coupledBrakeSpeedBuffer() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.coupled-brake-speed-buffer", 0.015), 0.0, 0.2);
    }

    private boolean brakeOnUnpoweredRail() {
        return plugin.getConfig().getBoolean("settings.unpowered-powered-rail-brake", true);
    }

    private long signCooldownMillis() {
        return Math.max(100L, plugin.getConfig().getLong("settings.sign-cooldown-ms", 1000L));
    }

    private long reverseSignCooldownMillis() {
        return Math.max(signCooldownMillis(), plugin.getConfig().getLong("settings.reverse-sign-cooldown-ms", 3000L));
    }

    private long reverseSignBlockMillis() {
        return reverseSignCooldownMillis() + reverseMaxBrakeTicks() * 50L;
    }

    private long defaultStationWaitTicks() {
        return Math.max(0L, plugin.getConfig().getLong("settings.default-station-wait-ticks", 100L));
    }

    private double stationDockingStopOffset() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-docking-stop-offset", 0.0),
                -8.0, 8.0);
    }

    private double stationDockingMaxDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-docking-max-distance", 64.0),
                1.0, 256.0);
    }

    private double stationDockingMinSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-docking-min-speed", 0.020),
                0.001, 0.20);
    }

    private double stationDepartureDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-departure-distance", 8.0),
                0.25, 128.0);
    }

    private double stationDepartureMinSpeed() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-departure-min-speed", 0.012),
                0.001, 0.20);
    }

    private double stationReleaseDistance() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.station-release-distance", 4.0), 2.0, 16.0);
    }

    private static double square(double value) {
        return value * value;
    }

    private long tickInterval() {
        return Math.max(1L, plugin.getConfig().getLong("settings.tick-interval", 1L));
    }

    private boolean autoLinkEnabled() {
        return plugin.getConfig().getBoolean("settings.auto-link-enabled", true);
    }

    private double autoLinkRadius() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.auto-link-radius", 2.75), 0.8, 8.0);
    }

    private long autoLinkDelayTicks() {
        return Math.max(1L, plugin.getConfig().getLong("settings.auto-link-delay-ticks", 2L));
    }

    private boolean autoLinkCreateSingleCartTrains() {
        return plugin.getConfig().getBoolean("settings.auto-link-create-single-cart-trains", false);
    }

    private long maxAutoLinkCarts() {
        return Math.max(2L, plugin.getConfig().getLong("settings.auto-link-max-carts-per-pass", 16L));
    }

    private boolean autoArrangeEnabled() {
        return plugin.getConfig().getBoolean("settings.auto-arrange-enabled", true);
    }

    private double autoArrangeSpacing() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.auto-arrange-spacing", 1.10), 0.8, 4.0);
    }

    private long consistLabelVisibleTicks() {
        return Math.max(0L, plugin.getConfig().getLong("settings.consist-label-visible-ticks", 120L));
    }

    private double signActivationRadius() {
        return RailMath.clamp(plugin.getConfig().getDouble("settings.sign-activation-radius", 6.0), 1.0, 16.0);
    }

    private boolean chunkLoadingEnabled() {
        return plugin.getConfig().getBoolean("settings.chunk-loading-enabled", true);
    }

    private int chunkLoadingRadius() {
        return Math.max(0, Math.min(2, plugin.getConfig().getInt("settings.chunk-loading-radius", 1)));
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private enum DurationUnit {
        AUTO,
        TICKS,
        SECONDS,
        MILLISECONDS
    }

    private record ConsistLabelState(String name, boolean visible) {
    }

    private static final class SpeedLimit {
        private final double speed;
        private final boolean emergencyBrake;

        private SpeedLimit(double speed, boolean emergencyBrake) {
            this.speed = speed;
            this.emergencyBrake = emergencyBrake;
        }
    }
}
