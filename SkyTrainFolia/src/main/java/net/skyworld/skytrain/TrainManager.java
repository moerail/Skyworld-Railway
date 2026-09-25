package net.skyworld.skytrain;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Coordinates train lifecycle and preserves the command/listener facade. */
final class TrainManager implements TrainMotionController.Host {
    private final SkyTrainPlugin plugin;
    private final TrainSettings settings;
    private final TrainPersistence persistence;
    private final DriverControlService drivers;
    private final TrainAudio audio;
    private final ConsistLabels labels;
    private final TrainMemberActuator actuator;
    private final TrainSignActions signActions;
    private final TrainDrivingControls controls;
    private final TrainMotionController motion;
    private final SwitchManager switchManager;
    private final AutomaticSigns automaticSigns;
    private final NamespacedKey trainIdKey;
    private final NamespacedKey trainNameKey;
    private final NamespacedKey memberIndexKey;
    private final ConcurrentMap<UUID, Train> trains = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SavedTrainDefinition> savedTrains = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> cartIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScheduledTask> memberTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Minecart> managedCarts = new ConcurrentHashMap<>();

    String motionSyncStatus() {
        return actuator.motionSyncStatus();
    }

    private final Set<UUID> pendingAutoLinks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> retiringCarts = ConcurrentHashMap.newKeySet();
    private final TrainChunkLoader chunkLoader;
    private final StcsBridge stcsTelemetry;
    private final Object driverLock = new Object();
    private volatile ScheduledTask autosaveTask;

    TrainManager(SkyTrainPlugin plugin, SwitchManager switchManager,
            LineInfrastructureManager infrastructureManager, StationManager stationManager) {
        this.plugin = plugin;
        this.switchManager = switchManager;
        this.automaticSigns = new AutomaticSigns(plugin, this, stationManager);
        this.chunkLoader = new TrainChunkLoader(plugin);
        this.stcsTelemetry = new StcsBridge(plugin);

        this.trainIdKey = new NamespacedKey(plugin, "train_id");
        this.trainNameKey = new NamespacedKey(plugin, "train_name");
        this.memberIndexKey = new NamespacedKey(plugin, "member_index");
        this.settings = new TrainSettings(plugin);
        this.persistence = new TrainPersistence(plugin.getDataFolder(), plugin.getLogger(), settings, trains, nameIndex, cartIndex, savedTrains);
        this.drivers = new DriverControlService(plugin, driverLock, trains::get, this::trainForCart, this::save);
        this.actuator = new TrainMemberActuator(plugin, settings);
        this.audio = new TrainAudio(plugin, settings, managedCarts::get, this::trainForCart);
        this.labels = new ConsistLabels(plugin, settings, managedCarts::get);
        this.controls = new TrainDrivingControls(settings, driverLock, this::requireTrain,
                this::save, this::refreshMemberIndexes);
        this.signActions = new TrainSignActions(plugin, this, settings, stationManager);
        this.motion = new TrainMotionController(plugin, settings, this, switchManager,
                infrastructureManager, chunkLoader, stcsTelemetry, signActions, actuator, audio);
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
            persistence.save();
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
        persistence.load();
    }

    void save() {
        persistence.save();
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
                task -> persistence.save(),
                period * 50L,
                period * 50L,
                TimeUnit.MILLISECONDS);
    }

    void shutdown() {
        automaticSigns.stop();
        if (plugin.displaySync() != null) plugin.displaySync().reset();
        actuator.clearRecoveries();
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
        actuator.clearPending();
        pendingAutoLinks.clear();
        retiringCarts.clear();
        chunkLoader.shutdown();
        drivers.shutdown();
        labels.clear();
    }

    void reloadAll() {
        synchronized (driverLock) {
            if (trains.values().stream().anyMatch(t -> Math.max(t.currentSpeed(), t.maxMemberSpeed()) > 0.001)) {
                throw new IllegalArgumentException("Stop all trains before reloading vehicle performance.");
            }
            long previousTickInterval = settings.tickInterval();
            plugin.reloadVehicleConfiguration();
            persistence.save();
            Runnable finishCabReload = plugin.resetCabForReload();
            try {
                // A config reload is not a server restart. Retain live paths, motion frames,
                // member tasks and display ownership; YAML cannot reconstruct their geometry.
                drivers.shutdown();
                persistence.save();
                if (settings.tickInterval() != previousTickInterval) rescheduleMemberTasks();
                startAutosave();
            } finally {
                finishCabReload.run();
            }
        }
    }

    Train createTrain(String name, List<Minecart> carts) {
        String cleanName = cleanName(name);
        if (nameIndex.containsKey(Train.normalizeName(cleanName))) {
            throw new IllegalArgumentException("列车名已经存在: " + cleanName);
        }
        if (carts.isEmpty()) {
            throw new IllegalArgumentException("附近没有可用矿车");
        }

        Train train = new Train(UUID.randomUUID(), cleanName, settings.defaultSpeed(), settings.maxSpeed(), settings.defaultSpacing());
        trains.put(train.id(), train);
        nameIndex.put(train.key(), train.id());

        appendCarts(train, carts);
        arrangeTrainCarts(train, carts, carts.get(0).getLocation());
        persistence.save();
        return train;
    }

    AutomaticSigns automaticSigns() { return automaticSigns; }

    @Override
    public boolean automaticEligible(Train train) {
        if (train != null && (train.protectionMode == ProtectionMode.ISOLATED || train.protectionMode == ProtectionMode.RECOVERING)) return false;
        return automaticStatus(train).equals("ready");
    }

    String automaticStatus(Train train) {
        synchronized (driverLock) {
            if (train == null || trains.get(train.id()) != train) return "unavailable";
            return automaticStatus(train.properties().conductionMode.automatic(),
                    drivers.hasDriver(train.id()), train.manualTakeover,
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
        double spacing = template == null ? settings.defaultSpacing() : template.spacing;
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
            location.setYaw(actuator.yawFromDirection(facing));
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
                    template == null ? settings.defaultSpeed() : template.targetSpeed,
                    template == null ? settings.maxSpeed() : template.maxSpeed, spacing, properties);
            trains.put(created.id(), created); nameIndex.put(created.key(), created.id());
            appendCarts(created, carts);
            created.reversed = spec.speed() < 0;
            created.reverser = created.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
            created.rememberDirection(created.reversed ? travel.clone().multiply(-1) : travel);
            double speed = Math.min(created.maxSpeed, Math.abs(spec.speed()));
            created.targetSpeed = speed > 0 ? speed : created.targetSpeed;
            created.seedCurrentSpeed(speed);
            created.moving = speed > 0;
            persistence.save();
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
        persistence.save();
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
        if (!settings.autoLinkEnabled() || cart == null || !cart.isValid() || cart.isDead()) {
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
                    settings.autoLinkDelayTicks());
        } catch (RuntimeException ex) {
            pendingAutoLinks.remove(entityId);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule minecart auto-link " + entityId, ex);
        }
    }

    private void autoLink(Minecart cart) {
        if (!settings.autoLinkEnabled() || !cart.isValid() || cart.isDead()) {
            return;
        }

        refreshCart(cart);
        List<Minecart> candidates = new ArrayList<>();
        candidates.add(cart);
        candidates.addAll(nearbyMinecarts(cart, settings.autoLinkRadius()));
        connectCarts(candidates, cart.getLocation(), settings.autoLinkCreateSingleCartTrains());
    }

    int connectCarts(List<Minecart> carts, Location origin, boolean allowSingleCartTrain) {
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
            persistence.save();
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
        var sink = plugin.telemetrySink();
        java.util.function.Consumer<UUID> confirmed = sink == null ? id -> {} : sink.beginRemoval(train);
        UUID driver = drivers.driverId(train.id());
        if (driver != null) drivers.revokeDriver(driver, null, "VEHICLE_REMOVED");
        automaticSigns.forget(train.id());
        trains.remove(train.id());
        stcsTelemetry.remove(train.id());
        switchManager.releaseTrain(train.id());
        chunkLoader.release(train.id());
        drivers.removeTrainDriver(train.id());
        nameIndex.remove(train.key());
        drivers.forgetTrainTargets(train.id());
        for (UUID member : train.members()) {
            cartIndex.remove(member);
            ScheduledTask task = memberTasks.remove(member);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            Minecart cart = managedCarts.remove(member);
            actuator.forgetPending(member);
            if (cart != null) {
                scheduleCartRemoval(cart, confirmed);
            }
        }
        persistence.save();
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
        UUID driver = drivers.driverId(train.id());
        if (driver != null) drivers.revokeDriver(driver, entityId, "VEHICLE_REMOVED");
        train.removeMember(entityId);
        train.clearMemberTargets();
        train.clearTrackPath();
        cartIndex.remove(entityId);
        ScheduledTask task = memberTasks.remove(entityId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        managedCarts.remove(entityId);
        actuator.forgetPending(entityId);

        if (train.memberCount() == 0) {
            trains.remove(train.id());
            nameIndex.remove(train.key());
            switchManager.releaseTrain(train.id());
            chunkLoader.release(train.id());
            drivers.removeTrainDriver(train.id());
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
        UUID driver = drivers.driverId(train.id());
        if (driver != null) drivers.revokeDriver(driver, entityId, "VEHICLE_REMOVED");
        if (!train.removeMember(entityId)) return;
        haltForMissingMember(train);
        actuator.forgetDisplay(entityId);
        train.clearMemberTargets();
        train.clearTrackPath();
        cartIndex.remove(entityId);
        ScheduledTask task = memberTasks.remove(entityId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        managedCarts.remove(entityId);
        actuator.forgetPending(entityId);
        if (train.memberCount() == 0) {
            trains.remove(train.id());
            nameIndex.remove(train.key());
            switchManager.releaseTrain(train.id());
            chunkLoader.release(train.id());
            drivers.removeTrainDriver(train.id());
        }
        persistence.save();
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

    @Override
    public boolean isSameTrain(Minecart minecart, Entity other) {
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
        return drivers.setDrivingTarget(player, train);
    }

    void clearDrivingTarget(Player player) {
        drivers.clearDrivingTarget(player);
    }

    void revokeDriver(UUID playerId, UUID expectedSeat, String reason) {
        drivers.revokeDriver(playerId, expectedSeat, reason);
    }

    Player clearDriver(Train train) {
        return drivers.clearDriver(train);
    }

    boolean isDriver(Player player, Train train) {
        return drivers.isDriver(player, train);
    }

    List<DriverDeskSnapshot> driverDesks() {
        return drivers.driverDesks();
    }

    void requireDriver(Player player, Train train) {
        drivers.requireDriver(player, train);
    }

    @Override
    public String driverName(Train train) {
        return drivers.driverName(train);
    }

    Train train(UUID id) {
        return id == null ? null : trains.get(id);
    }

    Train drivingTarget(Player player) {
        return drivers.drivingTarget(player);
    }

    void start(String name, Double speed) {
        controls.start(name, speed);
    }

    void stop(String name) {
        controls.stop(name);
    }

    void reverse(String name) {
        controls.reverse(name);
    }

    void speed(String name, double speed) {
        controls.speed(name, speed);
    }

    void setMaxSpeed(String name, double speed) {
        controls.setMaxSpeed(name, speed);
    }

    void setSpacing(String name, double spacing) {
        controls.setSpacing(name, spacing);
    }

    void applyPlayerPush(Train train, Minecart cart, Player player) {
        controls.applyPlayerPush(train, cart, player);
    }

    void setReverser(Train train, Reverser reverser) {
        controls.setReverser(train, reverser);
    }

    boolean canChangeReverser(Train train) {
        return controls.canChangeReverser(train);
    }

    Reverser reverseReverserTarget(Train train) {
        return controls.reverseReverserTarget(train);
    }

    Reverser cycleReverserTarget(Train train) {
        return controls.cycleReverserTarget(train);
    }

    void setPowerNotch(Train train, int notch) {
        controls.setPowerNotch(train, notch);
    }

    void setBrakeNotch(Train train, int notch) {
        controls.setBrakeNotch(train, notch);
    }

    void neutralHandle(Train train) {
        controls.neutralHandle(train);
    }

    void emergencyBrake(Train train) {
        controls.emergencyBrake(train);
    }

    String driveStatus(Train train) {
        return controls.driveStatus(train);
    }

    void confirmManualRelease(Train train) {
        controls.confirmManualRelease(train);
    }

    static boolean permanentRemoval(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        return cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD
                && cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT;
    }

    static void haltForMissingMember(Train train) {
        TrainDrivingControls.stopTrain(train);
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
        UUID driver = drivers.driverId(train.id());
        if (driver != null) drivers.revokeDriver(driver, cart.getUniqueId(), "VEHICLE_REMOVED");
        train.recordMemberRemoval(cart.getUniqueId(), permanentRemoval(cause) ? "REMOVED"
                : cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD ? "UNLOADED" : "PLAYER_QUIT",
                System.currentTimeMillis());
        synchronized (driverLock) {
            if (permanentRemoval(cause)) onCartDestroyed(cart);
            else {
                // Preserve the slot for unload/reload, but never keep displaying or commanding old speed.
                haltForMissingMember(train);
                actuator.forgetDisplay(cart.getUniqueId());
            }
        }
        plugin.getLogger().warning("Train " + train.name() + " stopped after member " + cart.getUniqueId()
                + " removal: " + cause + (permanentRemoval(cause) ? " (slot removed)" : " (slot retained)"));
    }

    static boolean mayRelease(UUID requester, UUID driver, UUID lastDriver, boolean admin) {
        return DriverControlService.mayRelease(requester, driver, lastDriver, admin);
    }

    void releaseManualControl(Player player, Train train) {
        drivers.releaseManualControl(player, train);
    }

    static void authorizeAutomatic(Train train, boolean hasDriver, double stopThreshold) {
        TrainDrivingControls.authorizeAutomatic(train, hasDriver, stopThreshold);
    }

    SavedTrainDefinition saveTrainTemplate(String trainName, String savedName) {
        Train train = requireTrain(trainName);
        String cleanName = cleanName(savedName);
        SavedTrainDefinition saved = SavedTrainDefinition.fromTrain(cleanName, train);
        savedTrains.put(Train.normalizeName(cleanName), saved);
        persistence.save();
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
        controls.setTrainMaxSpeed(train, saved.maxSpeed);
        train.spacing = saved.spacing;
        train.properties().copyFrom(saved.properties);
        arrangeTrainCarts(train, carts, base);
        persistence.save();
        return train;
    }

    String setProperty(String trainName, String property, String value) {
        Train train = requireTrain(trainName);
        TrainProperties properties = train.properties();
        String key = normalizeProperty(property);
        String cleanValue = value == null ? "" : value.trim();

        switch (key) {
            case "vtarget" -> {
                properties.setAutomaticTargetSpeed(cleanValue);
                if (train.automaticRun!=null && train.automaticRun.phase==AutomaticRun.Phase.CRUISE)
                    train.automaticRun.inheritTargetSpeed=true;
            }
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
                        TrainDrivingControls.authorizeAutomatic(train, drivers.hasDriver(train.id()), settings.driveDirectionChangeSpeed());
                        automaticSigns.forget(train.id());
                    } else {
                        train.manualTakeover=true;
                        TrainDrivingControls.stopTrain(train);
                    }
                    properties.conductionMode=mode;
                }
            }
            case "gravity" -> properties.gravity = RailMath.clamp(parseDouble(cleanValue, key), 0.0, 4.0);
            case "friction" -> properties.friction = RailMath.clamp(parseDouble(cleanValue, key), 0.0, 4.0);
            case "wait", "waitticks" -> properties.waitTicks = Math.max(0, (int) parseDouble(cleanValue, key));
            case "speed", "targetspeed" -> train.targetSpeed = RailMath.clamp(parseDouble(cleanValue, key), 0.0, train.maxSpeed);
            case "maxspeed" -> controls.setTrainMaxSpeed(train, parseDouble(cleanValue, key));
            case "spacing" -> train.spacing = RailMath.clamp(parseDouble(cleanValue, key), 0.8, 8.0);
            default -> throw new IllegalArgumentException("暂不支持的列车属性: " + property);
        }

        persistence.save();
        return "&a已设置 &e" + train.name() + " &a的属性 &e" + property + " &a= &f" + cleanValue;
    }

    String propertyValue(String trainName, String property) {
        Train train = requireTrain(trainName);
        TrainProperties properties = train.properties();
        return switch (normalizeProperty(property)) {
            case "vtarget" -> properties.automaticTargetSpeed == null ? "default" : Double.toString(properties.automaticTargetSpeed);
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
        persistence.save();
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
        persistence.save();
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
        persistence.save();
        return "&aRoute: &f" + String.join(" -> ", properties.route());
    }

    int activateSignForNearbyTrain(Player player, Block signBlock, String actionLine,
            String valueLine, String modifierLine) {
        return signActions.activateSignForNearbyTrain(player, signBlock, actionLine, valueLine, modifierLine);
    }

    void activateStationsFromRedstone(Block poweredBlock) {
        signActions.activateStationsFromRedstone(poweredBlock);
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

    void ensureTask(Minecart cart, Train train) {
        ensureTask(cart, train, false);
    }

    private void rescheduleMemberTasks() {
        for (Minecart cart : List.copyOf(managedCarts.values())) {
            cart.getScheduler().run(plugin, task -> {
                Train train = trainForCart(cart);
                if (train != null && managedCarts.get(cart.getUniqueId()) == cart)
                    ensureTask(cart, train, true);
            }, () -> {});
        }
    }

    private void ensureTask(Minecart cart, Train train, boolean replace) {
        UUID entityId = cart.getUniqueId();
        ScheduledTask existing = memberTasks.get(entityId);
        if (!replace && existing != null && !existing.isCancelled() && managedCarts.get(entityId) == cart) {
            return;
        }
        if (existing != null && !existing.isCancelled()) existing.cancel();

        try {
            managedCarts.put(entityId, cart);
            ScheduledTask task = cart.getScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> motion.tickMember(train, cart, scheduledTask),
                    () -> {
                        if (managedCarts.remove(entityId, cart)) {
                            memberTasks.remove(entityId);
                            actuator.forgetDisplay(entityId);
                        }
                    },
                    1L,
                    settings.tickInterval());
            if (task != null) {
                memberTasks.put(entityId, task);
            }
        } catch (RuntimeException ex) {
            managedCarts.remove(entityId);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule train cart " + entityId, ex);
        }
    }

    void playLeaderSound(Train train, Sound sound, SoundCategory category, float volume, float pitch) {
        audio.playLeaderSound(train, sound, category, volume, pitch);
    }

    Location activeLeaderLocation(Train train) {
        List<UUID> members = train.members();
        if (members.isEmpty()) {
            return null;
        }
        UUID leaderId = members.get(motion.activeLeaderIndex(train));
        Minecart leader = managedCarts.get(leaderId);
        if (leader != null && leader.isValid() && !leader.isDead()) {
            return leader.getLocation();
        }
        MemberSnapshot snapshot = train.snapshot(leaderId);
        World world = snapshot == null ? null : Bukkit.getWorld(snapshot.worldName);
        return world == null ? null : new Location(world, snapshot.x, snapshot.y, snapshot.z);
    }

    Vector activeLeaderDirection(Train train, Location leaderLocation) {
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

    @Override
    public void refreshMemberIndexes(Train train) {
        List<UUID> members = train.members();
        for (int i = 0; i < members.size(); i++) {
            UUID entityId = members.get(i);
            cartIndex.put(entityId, train.id());
        }
    }

    private void arrangeTrainCarts(Train train, List<Minecart> carts, Location origin) {
        if (!settings.autoArrangeEnabled() || train == null || carts == null || carts.size() < 2) {
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
        double spacing = settings.autoArrangeSpacing();
        if (settings.forceTightSpacing()) {
            spacing = settings.tightSpacing();
        }
        train.spacing = spacing;
        train.clearMemberTargets();
        train.clearTrackPath();
        for (int i = 0; i < ordered.size(); i++) {
            Minecart cart = ordered.get(i);
            markCart(cart, train, i);
            ensureTask(cart, train);
            Location target = anchorLocation.clone().add(direction.clone().multiply(-spacing * i));
            target.setYaw(actuator.yawFromDirection(direction));
            target.setPitch(0.0F);
            actuator.scheduleTeleportAndStop(cart, target);
        }
        labels.showConsistLabels(train, ordered);
    }

    void showConsistLabels(Train train) {
        labels.showConsistLabels(train);
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

    private List<Minecart> nearbyMinecarts(Minecart origin, double radius) {
        List<Minecart> carts = new ArrayList<>();
        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Minecart minecart && minecart.isValid() && !minecart.isDead()) {
                carts.add(minecart);
            }
        }
        return carts;
    }

    List<Minecart> minecartsNear(Player player, Location center, double radius) {
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
                .limit(settings.maxAutoLinkCarts())
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
        actuator.forgetDisplay(cart.getUniqueId());
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

    private void scheduleCartRemoval(Minecart cart, java.util.function.Consumer<UUID> confirmed) {
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
                                if (!cart.isValid()) confirmed.accept(entityId);
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

    String normalizeProperty(String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("属性名不能为空。");
        }
        return property.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean validateMemberTick(Train train, Minecart cart, ScheduledTask task) {
        UUID entityId = cart.getUniqueId();
        if (!cart.isValid() || cart.isDead()) {
            actuator.forgetDisplay(entityId);
            train.memberSpeed(entityId, 0.0);
            memberTasks.remove(entityId);
            managedCarts.remove(entityId);
            actuator.forgetPending(entityId);
            task.cancel();
            return false;
        }

        if (trains.get(train.id()) != train || !train.contains(entityId)) {
            actuator.forgetDisplay(entityId);
            train.memberSpeed(entityId, 0.0);
            clearCartMark(cart);
            cartIndex.remove(entityId);
            memberTasks.remove(entityId);
            managedCarts.remove(entityId);
            actuator.forgetPending(entityId);
            task.cancel();
            return false;
        }

        return true;
    }

    @Override
    public boolean tickAutomaticSigns(Train train, Block rail, Location location, Vector direction, long now) {
        synchronized (driverLock) {
            return automaticSigns.tick(train, rail, location, direction, now);
        }
    }
}
