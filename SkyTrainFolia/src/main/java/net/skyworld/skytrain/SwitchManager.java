package net.skyworld.skytrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

final class SwitchManager {
    private static final double EPSILON = 0.0001;

    private final SkyTrainPlugin plugin;
    private final File switchesFile;
    private final ConcurrentMap<UUID, SkyTrainSwitch> switches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> signIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> pivotIndex = new ConcurrentHashMap<>();

    SkyTrainSwitch switchAt(String worldName, int x, int y, int z) {
        UUID id = pivotIndex.get(new SwitchBlockPosition(worldName, x, y, z).key());
        return id == null ? null : switches.get(id);
    }

    List<SkyTrainSwitch> switchesSnapshot() {
        return List.copyOf(switches.values());
    }
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private final AtomicBoolean saveDirty = new AtomicBoolean();
    private final Object dataIoLock = new Object();
    private volatile boolean shuttingDown;
    private ScheduledTask recoveryTask;
    private final ConcurrentMap<UUID, SkyTrainSwitch> recoveryPending = new ConcurrentHashMap<>();

    SwitchManager(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        this.switchesFile = new File(plugin.getDataFolder(), "switches.yml");
    }

    int switchCount() {
        return switches.size();
    }

    Collection<SwitchNodeSnapshot> graphNodes() {
        return switches.values().stream()
                .map(SkyTrainSwitch::nodeSnapshot)
                .sorted(Comparator.comparing(node -> node.id().toString()))
                .toList();
    }

    void load() {
        synchronized (dataIoLock) {
            Map<UUID, SkyTrainSwitch> loaded = new LinkedHashMap<>();
            if (switchesFile.exists() && switchesFile.length() > 0L) {
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(switchesFile);
                } catch (IOException | InvalidConfigurationException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to read switches.yml; existing runtime data was kept.", ex);
                    return;
                }

                ConfigurationSection root = config.getConfigurationSection("switches");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            ConfigurationSection section = root.getConfigurationSection(key);
                            if (section == null) {
                                continue;
                            }
                            String world = section.getString("world");
                            SwitchGeometry geometry = SwitchGeometry.parse(section.getString("geometry"));
                            if (world == null || geometry == null) {
                                throw new IllegalArgumentException("Missing world or geometry");
                            }

                            SwitchBlockPosition sign = readPosition(section, "sign", world);
                            SwitchBlockPosition pivot = readPosition(section, "pivot", world);
                            BlockFace legacyFront = parseHorizontalFace(section.getString("front"));
                            BlockFace mountFace = parseBlockFace(section.getString("mount-face"));
                            BlockFace commonFace = parseHorizontalFace(section.getString("ports.common"));
                            BlockFace straightFace = parseHorizontalFace(section.getString("ports.straight"));
                            BlockFace divergingFace = parseHorizontalFace(section.getString("ports.diverging"));
                            if (commonFace == null || straightFace == null || divergingFace == null) {
                                if (legacyFront == null || !geometry.legacyFrontFacing()) {
                                    throw new IllegalArgumentException("Missing switch port directions");
                                }
                                straightFace = legacyFront;
                                commonFace = legacyFront.getOppositeFace();
                                divergingFace = geometry.frontDivergingFace(legacyFront);
                            }
                            if (mountFace == null) {
                                mountFace = legacyFront == null ? straightFace : legacyFront;
                            }
                            SwitchBlockPosition support = section.contains("support.x")
                                    ? readPosition(section, "support", world)
                                    : offset(sign, mountFace, 1);
                            Rail.Shape straightShape = shapeFor(commonFace, straightFace);
                            Rail.Shape divergingShape = shapeFor(commonFace, divergingFace);
                            SwitchState commanded = SwitchState.parse(
                                    section.getString("commanded-state"), SwitchState.STRAIGHT);
                            String localName = normalizeLocalName(section.getString("local-name"));
                            SwitchBlockPosition actuator = section.contains("actuator.x")
                                    ? readPosition(section, "actuator", world)
                                    : null;
                            loaded.put(id, new SkyTrainSwitch(id, sign, support, pivot, mountFace,
                                    commonFace, straightFace, divergingFace, geometry,
                                    straightShape, divergingShape, commanded, null, localName,
                                    actuator, actuator == null ? 0 : 1));
                        } catch (RuntimeException ex) {
                            plugin.getLogger().log(Level.WARNING, "Skipping invalid switch entry " + key, ex);
                        }
                    }
                }
            }

            switches.clear();
            switches.putAll(loaded);
            rebuildIndexes();
        }
    }

    void start() {
        if (recoveryTask != null) recoveryTask.cancel();
        recoveryPending.clear();
        shuttingDown = false;
        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            scheduleActuatorRefresh(railwaySwitch, 1L);
            scheduleRedstoneRefresh(railwaySwitch, 2L);
        }
        recoveryTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> recoverLoadedSwitches(), 20L, 20L);
    }

    void shutdown() {
        shuttingDown = true;
        if (recoveryTask != null) recoveryTask.cancel();
        recoveryPending.clear();
    }

    private void recoverLoadedSwitches() {
        if (shuttingDown) return;
        for (SkyTrainSwitch sw : switches.values()) {
            if (sw.observationVersion() < 0 || !loaded(sw.pivot) || !loaded(sw.sign)
                    || recoveryPending.putIfAbsent(sw.id, sw) != null) continue;
            try {
                Bukkit.getRegionScheduler().run(plugin, sw.pivot.location(), task -> {
                    try {
                        if (shuttingDown || switches.get(sw.id) != sw
                                || !readable(sw.pivot) || !readable(sw.sign)) return;
                        long version = sw.observationVersion();
                        // Availability is not presence: never delete or switch a point during recovery.
                        if (version < 0 || !isPhysicallyPresent(sw)) return;
                        BlockData data = sw.pivot.block().getBlockData();
                        if (data instanceof Rail rail) sw.confirmObservedShape(version, rail.getShape());
                    } catch (RuntimeException ex) {
                        plugin.getLogger().log(Level.FINE, "Switch observation deferred " + sw.id, ex);
                    } finally {
                        recoveryPending.remove(sw.id, sw);
                    }
                });
            } catch (RuntimeException ex) {
                recoveryPending.remove(sw.id, sw);
                plugin.getLogger().log(Level.FINE, "Switch observation scheduling deferred " + sw.id, ex);
            }
        }
    }

    private static boolean loaded(SwitchBlockPosition position) {
        Location location = position.location();
        return location != null && location.getWorld().isChunkLoaded(position.x() >> 4, position.z() >> 4);
    }

    void reload() {
        save();
        load();
        start();
    }

    void save() {
        synchronized (dataIoLock) {
            YamlConfiguration config = new YamlConfiguration();
            for (SkyTrainSwitch railwaySwitch : switches.values()) {
                String path = "switches." + railwaySwitch.id;
                config.set(path + ".world", railwaySwitch.pivot.worldName());
                writePosition(config, path + ".sign", railwaySwitch.sign);
                writePosition(config, path + ".support", railwaySwitch.support);
                writePosition(config, path + ".pivot", railwaySwitch.pivot);
                config.set(path + ".mount-face", railwaySwitch.mountFace.name().toLowerCase(Locale.ROOT));
                config.set(path + ".ports.common", railwaySwitch.commonFace.name().toLowerCase(Locale.ROOT));
                config.set(path + ".ports.straight", railwaySwitch.straightFace.name().toLowerCase(Locale.ROOT));
                config.set(path + ".ports.diverging", railwaySwitch.divergingFace.name().toLowerCase(Locale.ROOT));
                config.set(path + ".geometry", railwaySwitch.geometry.code);
                config.set(path + ".commanded-state", railwaySwitch.commandedState().storageName());
                config.set(path + ".local-name", railwaySwitch.localName);
                SwitchBlockPosition actuator = railwaySwitch.actuator();
                if (actuator != null) {
                    writePosition(config, path + ".actuator", actuator);
                }
            }
            try {
                saveYamlAtomically(config, switchesFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save switches.yml", ex);
            }
        }
    }

    SwitchRegistration registerSign(Block signBlock, String geometryText, String localNameText) {
        SwitchGeometry geometry = SwitchGeometry.parseNewDefinition(geometryText);
        if (geometry == null) {
            throw new IllegalArgumentException("道岔方向必须写 left 或 right。");
        }

        SwitchSignMount signMount = switchSignMount(signBlock);
        BlockFace front = signMount.frontFace();
        BlockFace mountFace = signMount.attachedFace();
        Block support = signMount.support();
        SwitchLayout layout = switchLayout(signMount, geometry);
        Block pivot = layout.pivot();
        BlockFace common = layout.commonFace();
        BlockFace straight = layout.straightFace();
        BlockFace diverging = layout.divergingFace();
        Rail pivotRail = (Rail) pivot.getBlockData();
        Rail.Shape straightShape = shapeFor(common, straight);
        Rail.Shape divergingShape = shapeFor(common, diverging);
        if (!pivotRail.getShapes().contains(straightShape) || !pivotRail.getShapes().contains(divergingShape)) {
            throw new IllegalArgumentException("当前铁轨类型不支持该道岔几何。");
        }

        SwitchBlockPosition signPosition = SwitchBlockPosition.of(signBlock);
        SwitchBlockPosition supportPosition = SwitchBlockPosition.of(support);
        SwitchBlockPosition pivotPosition = SwitchBlockPosition.of(pivot);
        UUID existingAtSign = signIndex.get(signPosition.key());
        UUID existingAtPivot = pivotIndex.get(pivotPosition.key());
        if (existingAtPivot != null && !existingAtPivot.equals(existingAtSign)) {
            throw new IllegalArgumentException("这根尖轨已经绑定了另一个 SkyTrain 道岔牌。");
        }

        UUID id = existingAtSign == null ? UUID.randomUUID() : existingAtSign;
        SkyTrainSwitch old = existingAtSign == null ? null : switches.get(existingAtSign);
        SwitchState commanded = readPowered(signBlock, support)
                ? SwitchState.DIVERGING
                : SwitchState.STRAIGHT;
        SwitchState physical = stateForShape(pivotRail.getShape(), straightShape, divergingShape);
        String localName = normalizeLocalName(localNameText);
        SkyTrainSwitch railwaySwitch = new SkyTrainSwitch(id, signPosition, supportPosition,
                pivotPosition, mountFace, common, straight, diverging, geometry,
                straightShape, divergingShape, commanded, physical, localName, null, 0);
        refreshActuator(railwaySwitch);

        if (old != null) {
            pivotIndex.remove(old.pivot.key(), old.id);
        }
        switches.put(id, railwaySwitch);
        signIndex.put(signPosition.key(), id);
        pivotIndex.put(pivotPosition.key(), id);
        requestPhysicalState(railwaySwitch, commanded);
        queueSave();
        return new SwitchRegistration(id, geometry, commanded, pivotPosition, railwaySwitch.actuatorStatus());
    }

    boolean removeSign(Block signBlock) {
        UUID id = signIndex.remove(SwitchBlockPosition.of(signBlock).key());
        if (id == null) {
            return false;
        }
        SkyTrainSwitch removed = switches.remove(id);
        if (removed != null) {
            pivotIndex.remove(removed.pivot.key(), id);
        }
        queueSave();
        return removed != null;
    }

    boolean removeBrokenComponent(Block brokenBlock) {
        SwitchBlockPosition broken = SwitchBlockPosition.of(brokenBlock);
        UUID id = signIndex.get(broken.key());
        if (id != null) {
            return remove(switches.get(id));
        }

        id = pivotIndex.get(broken.key());
        if (id != null) {
            SkyTrainSwitch railwaySwitch = switches.get(id);
            if (railwaySwitch != null) {
                railwaySwitch.invalidatePhysicalState();
            }
            return false;
        }

        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            if (railwaySwitch.support.key().equals(broken.key())) {
                return remove(railwaySwitch);
            }
        }
        return false;
    }

    void onBlockPlaced(Block placedBlock) {
        UUID id = pivotIndex.get(SwitchBlockPosition.of(placedBlock).key());
        if (id == null) {
            return;
        }
        SkyTrainSwitch railwaySwitch = switches.get(id);
        if (railwaySwitch == null) {
            refreshActuatorsNear(placedBlock);
            return;
        }
        railwaySwitch.invalidatePhysicalState();
        refreshActuator(railwaySwitch);
        requestPhysicalState(railwaySwitch, railwaySwitch.effectiveState());
        refreshActuatorsNear(placedBlock);
    }

    void onRedstoneChanged(Block changedBlock) {
        String worldName = changedBlock.getWorld().getName();
        boolean knownSwitchNearby = false;
        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            if (!railwaySwitch.sign.worldName().equals(worldName) || !redstoneCanAffect(railwaySwitch, changedBlock)) {
                continue;
            }
            knownSwitchNearby = true;
            scheduleRedstoneRefresh(railwaySwitch, 1L);
        }
        if (!knownSwitchNearby && discoverCopiedSignsNear(changedBlock, 3) > 0) {
            onRedstoneChanged(changedBlock);
        }
    }

    void onBlockBroken(Block brokenBlock) {
        if (brokenBlock == null || shuttingDown) {
            return;
        }
        try {
            Bukkit.getRegionScheduler().runDelayed(plugin, brokenBlock.getLocation(), task ->
                    refreshActuatorsNear(brokenBlock), 1L);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to schedule switch actuator refresh.", ex);
        }
    }

    void scanAround(Location center, int radius, Consumer<SwitchScanResult> completion) {
        if (center == null || center.getWorld() == null || completion == null) {
            return;
        }

        World world = center.getWorld();
        int boundedRadius = Math.max(4, Math.min(128, radius));
        int minChunkX = (center.getBlockX() - boundedRadius) >> 4;
        int maxChunkX = (center.getBlockX() + boundedRadius) >> 4;
        int minChunkZ = (center.getBlockZ() - boundedRadius) >> 4;
        int maxChunkZ = (center.getBlockZ() + boundedRadius) >> 4;
        List<ChunkCoordinate> loadedChunks = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    loadedChunks.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        if (loadedChunks.isEmpty()) {
            completion.accept(new SwitchScanResult(0, 0, 0, 0));
            return;
        }

        AtomicInteger remaining = new AtomicInteger(loadedChunks.size());
        AtomicInteger registered = new AtomicInteger();
        AtomicInteger existing = new AtomicInteger();
        AtomicInteger invalid = new AtomicInteger();
        double radiusSquared = (double) boundedRadius * boundedRadius;
        for (ChunkCoordinate coordinate : loadedChunks) {
            Location chunkLocation = new Location(world,
                    (coordinate.x() << 4) + 8.0, center.getY(), (coordinate.z() << 4) + 8.0);
            Bukkit.getRegionScheduler().run(plugin, chunkLocation, task -> {
                try {
                    Chunk chunk = world.getChunkAt(coordinate.x(), coordinate.z());
                    for (BlockState state : chunk.getTileEntities()) {
                        Location location = state.getLocation();
                        double dx = location.getX() - center.getX();
                        double dy = location.getY() - center.getY();
                        double dz = location.getZ() - center.getZ();
                        if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                            continue;
                        }
                        ScanOutcome outcome = registerCopiedSign(state);
                        switch (outcome) {
                            case REGISTERED -> registered.incrementAndGet();
                            case EXISTING -> existing.incrementAndGet();
                            case INVALID -> invalid.incrementAndGet();
                            case NOT_SWITCH -> {
                            }
                        }
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        completion.accept(new SwitchScanResult(
                                registered.get(), existing.get(), invalid.get(), loadedChunks.size()));
                    }
                }
            });
        }
    }

    private int discoverCopiedSignsNear(Block center, int radius) {
        int registered = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (registerCopiedSign(center.getRelative(x, y, z).getState()) == ScanOutcome.REGISTERED) {
                        registered++;
                    }
                }
            }
        }
        return registered;
    }

    private ScanOutcome registerCopiedSign(BlockState state) {
        if (!(state instanceof Sign sign)) {
            return ScanOutcome.NOT_SWITCH;
        }
        String header = normalizedSignLine(sign.getLine(0));
        String action = normalizedSignLine(sign.getLine(1));
        if (!SignHeaders.isSkyTrain(header) || !"switch".equalsIgnoreCase(action)) {
            return ScanOutcome.NOT_SWITCH;
        }

        SwitchBlockPosition signPosition = SwitchBlockPosition.of(state.getBlock());
        UUID existingId = signIndex.get(signPosition.key());
        if (existingId != null) {
            SkyTrainSwitch existing = switches.get(existingId);
            if (existing != null) {
                existing.invalidatePhysicalState();
                requestPhysicalState(existing, existing.effectiveState());
            }
            return ScanOutcome.EXISTING;
        }
        try {
            registerSign(state.getBlock(), normalizedSignLine(sign.getLine(2)),
                    normalizedSignLine(sign.getLine(3)));
            return ScanOutcome.REGISTERED;
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.FINE,
                    "Could not import copied switch sign at " + signPosition.key() + ": " + ex.getMessage());
            return ScanOutcome.INVALID;
        }
    }

    private static String normalizedSignLine(String line) {
        String stripped = ChatColor.stripColor(line == null ? "" : line);
        return stripped == null ? "" : stripped.trim();
    }

    private static String normalizeLocalName(String value) {
        String normalized = normalizedSignLine(value);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    boolean prepareTrain(Train train, TrainRailPath trackPath, Location leaderLocation, double plannedDistance) {
        if (train == null || trackPath == null || leaderLocation == null || leaderLocation.getWorld() == null) {
            return true;
        }

        double approachDistance = approachDistance() + Math.max(0.0, plannedDistance) * 2.0;
        SwitchApproach approach = findTrackApproach(
                trackPath.probeAhead(approachDistance, train.reversed), leaderLocation.getWorld().getName());
        if (approach == null) {
            return true;
        }

        SkyTrainSwitch railwaySwitch = approach.railwaySwitch();
        // World validation and mutation run at the pivot, never on a remote leader's region.
        double distanceSquared = approach.distance() * approach.distance();
        if (railwaySwitch.isLockedBy(train.id())) {
            SwitchState route = railwaySwitch.lockedRoute(train.id());
            requestPhysicalState(railwaySwitch, route, train.id(), train.name(), "AUTO_APPROACH");
            return railwaySwitch.routeReady(train.id())
                    || mayApproachWhileSwitching(distanceSquared, plannedDistance);
        }

        SwitchPort entry = approach.entry();
        SwitchState route = switch (entry) {
            case COMMON -> railwaySwitch.commandedState();
            case STRAIGHT -> SwitchState.STRAIGHT;
            case DIVERGING -> SwitchState.DIVERGING;
        };
        SkyTrainSwitch.Reservation reservation = railwaySwitch.reserve(
                train.id(), entry, route, System.currentTimeMillis());
        if (reservation == SkyTrainSwitch.Reservation.BUSY) {
            return false;
        }
        requestPhysicalState(railwaySwitch, route, train.id(), train.name(), "AUTO_APPROACH");
        return railwaySwitch.routeReady(train.id())
                || mayApproachWhileSwitching(distanceSquared, plannedDistance);
    }

    private SwitchApproach findTrackApproach(List<TrainRailPath.RailProbe> probes, String worldName) {
        for (TrainRailPath.RailProbe probe : probes) {
            TrainTrackPosition position = probe.trackPosition();
            for (SkyTrainSwitch railwaySwitch : switches.values()) {
                if (!railwaySwitch.pivot.worldName().equals(worldName)) {
                    continue;
                }
                for (Map.Entry<SwitchPort, BlockFace> port : railwaySwitch.nodeSnapshot().ports().entrySet()) {
                    BlockFace face = port.getValue();
                    if (position.railX != railwaySwitch.pivot.x() + face.getModX()
                            || position.railZ != railwaySwitch.pivot.z() + face.getModZ()
                            || Math.abs(position.railY - railwaySwitch.pivot.y()) > 1) {
                        continue;
                    }

                    if (VanillaRailWalker.exitFace(position.railShape, position.motion()) == face.getOppositeFace()) {
                        return new SwitchApproach(railwaySwitch, port.getKey(), probe.distance());
                    }
                }
            }
        }
        return null;
    }

    private boolean mayApproachWhileSwitching(double distanceSquared, double plannedDistance) {
        double holdDistance = occupiedDistance() + Math.max(0.0, plannedDistance);
        return distanceSquared > holdDistance * holdDistance;
    }

    void observeTrainLayout(Train train, List<TrainRailPath.MemberPlacement> placements, long nowMillis) {
        if (train == null || placements == null || placements.isEmpty()) {
            return;
        }
        train.switchPassages.observe(train.memberEvidence().values().stream()
                .filter(e -> "OBSERVED".equals(e.state())).map(Train.MemberEvidence::position).toList(),
                switches.values(), nowMillis, suspect -> {
                    if (plugin.telemetrySink() != null) plugin.telemetrySink().switchEvent(train.id(), train.name(),
                            suspect.railwaySwitch(), "SWITCH_RUN_THROUGH_SUSPECTED", "OBSERVED_TRAILING_ENTRY",
                            suspect.physical().name(), "", suspect.entry().name());
                });
        double occupied = occupiedDistance();
        double release = releaseDistance();
        double abandoned = approachDistance() + 1.0;
        double occupiedSquared = occupied * occupied;
        double releaseSquared = release * release;
        double abandonedSquared = abandoned * abandoned;

        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            if (!railwaySwitch.isLockedBy(train.id())) {
                continue;
            }
            double nearest = Double.MAX_VALUE;
            for (TrainRailPath.MemberPlacement placement : placements) {
                Location location = placement.location();
                if (location.getWorld() == null
                        || !railwaySwitch.pivot.worldName().equals(location.getWorld().getName())) {
                    continue;
                }
                nearest = Math.min(nearest, horizontalDistanceSquared(location, railwaySwitch.pivot));
            }
            if (railwaySwitch.observe(train.id(), nearest, nowMillis,
                    occupiedSquared, releaseSquared, abandonedSquared)) {
                SwitchState restore = railwaySwitch.release(train.id());
                requestPhysicalState(railwaySwitch, restore);
            }
        }
    }

    void releaseTrain(UUID trainId) {
        if (trainId == null) {
            return;
        }
        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            SwitchState restore = railwaySwitch.release(trainId);
            if (restore != null) {
                requestPhysicalState(railwaySwitch, restore);
            }
        }
    }

    List<String> descriptions() {
        return switches.values().stream()
                .sorted(Comparator.comparing(sw -> sw.id.toString()))
                .map(SkyTrainSwitch::status)
                .toList();
    }

    SkyTrainSwitch nearest(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double radiusSquared = radius * radius;
        return switches.values().stream()
                .filter(sw -> sw.pivot.worldName().equals(location.getWorld().getName()))
                .filter(sw -> horizontalDistanceSquared(location, sw.pivot) <= radiusSquared)
                .min(Comparator.comparingDouble(sw -> horizontalDistanceSquared(location, sw.pivot)))
                .orElse(null);
    }

    java.util.concurrent.CompletionStage<String> changeById(UUID id, SwitchState expected, SwitchState target,
            SwitchBlockPosition position,java.util.function.BooleanSupplier guard,Consumer<String> progress) {
        var job=new SwitchChangeJob(()->System.nanoTime()/1_000_000,30_000);
        SkyTrainSwitch sw=switches.get(id);
        if(shuttingDown || sw==null || expected==null || target==null || expected==target || !sw.pivot.equals(position)) {
            job.finish("REJECTED_POSITION_OR_STATE");return job.result;
        }
        Location location=sw.pivot.location();
        if(location==null || !sw.sign.worldName().equals(position.worldName())
                || Math.abs((sw.sign.x()>>4)-(position.x()>>4))>1 || Math.abs((sw.sign.z()>>4)-(position.z()>>4))>1) {
            job.finish("REJECTED_POSITION_OR_WORLD");return job.result;
        }
        java.util.concurrent.CompletableFuture.delayedExecutor(30,java.util.concurrent.TimeUnit.SECONDS).execute(job::expire);
        loadForChange(job,sw,expected,target,location,guard,progress);
        return job.result;
    }

    private void loadForChange(SwitchChangeJob job,SkyTrainSwitch sw,SwitchState expected,SwitchState target,
            Location location,java.util.function.BooleanSupplier guard,Consumer<String> progress) {
        if(!job.active())return;
        if(shuttingDown || switches.get(sw.id)!=sw) {job.finish("REJECTED_SWITCH_REMOVED");return;}
        progress.accept("LOADING_CHUNKS");
        try {
            World world=location.getWorld();int cx=location.getBlockX()>>4,cz=location.getBlockZ()>>4;
            List<java.util.concurrent.CompletableFuture<Chunk>> loads=new ArrayList<>();
            for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++) loads.add(world.getChunkAtAsync(x,z,false));
            java.util.concurrent.CompletableFuture.allOf(loads.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .whenComplete((ignored,error)->{
                if(!job.active())return;
                if(error!=null) {job.finish("FAILED_CHUNK_LOAD");return;}
                if(loads.stream().anyMatch(load->load.getNow(null)==null)) {job.finish("FAILED_CHUNK_NOT_GENERATED");return;}
                try { Bukkit.getRegionScheduler().run(plugin,location,task->job.run(()->{
                    try {
                        // The bounded local inspection area must be loaded AND owned by this region.
                        for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++)
                            if(!world.isChunkLoaded(x,z) || !Bukkit.isOwnedByCurrentRegion(world,x,z)) {
                                progress.accept("WAITING_REGION");
                                Bukkit.getRegionScheduler().runDelayed(plugin,location,t->loadForChange(job,sw,expected,target,location,guard,progress),2L);
                                return;
                            }
                        progress.accept("REVALIDATING");
                        if(shuttingDown || switches.get(sw.id)!=sw || !guard.getAsBoolean()) {job.finish("REJECTED_REVALIDATION");return;}
                        if(!world.getNearbyEntities(location.clone().add(.5,0,.5),4,4,4,e->e instanceof org.bukkit.entity.Minecart).isEmpty()) {
                            job.finish("REJECTED_LOCAL_VEHICLE");return;
                        }
                        String failure=null;
                        synchronized(sw) {
                            if(!job.mayWrite()) failure="EXPIRED";
                            else if(!sw.externallyChangeable(expected) || !readable(sw.pivot) || !readable(sw.sign) || !isPhysicallyPresent(sw)
                                    || !(sw.pivot.block().getBlockData() instanceof Rail rail) || rail.getShape()!=sw.shape(expected)) {
                                failure="REJECTED_STATE_OR_LOCK";
                            } else {
                                sw.nextRedstoneVersion();sw.setCommandedState(target);
                                var mutation=sw.beginMutation(target);
                                boolean applied=false;
                                try { if(mutation!=null) applied=writePhysicalMutation(sw,mutation,null,"","PCC_CONTROL"); }
                                finally { if(mutation!=null)sw.finishMutation(mutation.version(),target,applied); }
                                if(!applied) {sw.setCommandedState(expected);failure="UNCONFIRMED";}
                            }
                        }
                        if(failure!=null) {job.finish(failure);return;}
                        job.applied();progress.accept("VERIFYING");
                        scheduleActuatorWrite(sw,sw.actuatorCommand(),0);queueSave();
                        Bukkit.getRegionScheduler().runDelayed(plugin,location,check->job.run(()->{
                            try {
                                boolean applied=!shuttingDown && switches.get(sw.id)==sw && readable(sw.pivot)
                                        && sw.physicalState()==target && sw.commandedState()==target
                                        && sw.pivot.block().getBlockData() instanceof Rail rail && rail.getShape()==sw.shape(target);
                                job.finish(applied?"COMPLETED":"UNCONFIRMED");
                            } catch(RuntimeException ex) {job.finish("UNCONFIRMED");}
                        }),4L);
                    } catch(RuntimeException ex) {job.fail("FAILED");}
                })); } catch(RuntimeException ex) {job.finish("FAILED_SCHEDULER");}
            });
        } catch(RuntimeException ex) {job.finish("FAILED_CHUNK_LOAD");}
    }

    void setCommandedState(SkyTrainSwitch railwaySwitch, SwitchState state) {
        if (railwaySwitch == null || state == null || switches.get(railwaySwitch.id) != railwaySwitch) {
            return;
        }
        railwaySwitch.nextRedstoneVersion();
        SwitchState apply = railwaySwitch.setCommandedState(state);
        scheduleActuatorWrite(railwaySwitch, railwaySwitch.actuatorCommand(), 0);
        requestPhysicalState(railwaySwitch, apply);
        queueSave();
    }

    SwitchState toggleCommandedState(SkyTrainSwitch railwaySwitch) {
        if (railwaySwitch == null || switches.get(railwaySwitch.id) != railwaySwitch) {
            return null;
        }
        SwitchState next = railwaySwitch.effectiveState() == SwitchState.DIVERGING
                ? SwitchState.STRAIGHT
                : SwitchState.DIVERGING;
        setCommandedState(railwaySwitch, next);
        return next;
    }

    boolean remove(SkyTrainSwitch railwaySwitch) {
        if (railwaySwitch == null || !switches.remove(railwaySwitch.id, railwaySwitch)) {
            return false;
        }
        signIndex.remove(railwaySwitch.sign.key(), railwaySwitch.id);
        pivotIndex.remove(railwaySwitch.pivot.key(), railwaySwitch.id);
        queueSave();
        return true;
    }

    void cleanupGhostSwitches(Consumer<Integer> completion) {
        List<SkyTrainSwitch> snapshot = List.copyOf(switches.values());
        if (snapshot.isEmpty()) {
            completion.accept(0);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(snapshot.size());
        AtomicInteger removed = new AtomicInteger();
        for (SkyTrainSwitch railwaySwitch : snapshot) {
            Location pivotLocation = railwaySwitch.pivot.location();
            if (pivotLocation == null) {
                if (remove(railwaySwitch)) {
                    removed.incrementAndGet();
                }
                finishCleanup(remaining, removed, completion);
                continue;
            }
            try {
                Bukkit.getRegionScheduler().run(plugin, pivotLocation, task -> {
                    try {
                        if (switches.get(railwaySwitch.id) == railwaySwitch
                                && !isPhysicallyPresent(railwaySwitch)
                                && remove(railwaySwitch)) {
                            removed.incrementAndGet();
                            plugin.getLogger().warning("Removed ghost SkyTrain switch " + railwaySwitch.id
                                    + "; its sign or pivot rail no longer exists.");
                        }
                    } finally {
                        finishCleanup(remaining, removed, completion);
                    }
                });
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE,
                        "Failed to schedule ghost switch check " + railwaySwitch.id, ex);
                finishCleanup(remaining, removed, completion);
            }
        }
    }

    private static void finishCleanup(AtomicInteger remaining, AtomicInteger removed,
            Consumer<Integer> completion) {
        if (remaining.decrementAndGet() == 0) {
            completion.accept(removed.get());
        }
    }

    private void scheduleActuatorRefresh(SkyTrainSwitch railwaySwitch, long delayTicks) {
        Location pivotLocation = railwaySwitch.pivot.location();
        if (pivotLocation == null || shuttingDown) {
            return;
        }
        try {
            Bukkit.getRegionScheduler().runDelayed(plugin, pivotLocation, task -> {
                if (shuttingDown || switches.get(railwaySwitch.id) != railwaySwitch) {
                    return;
                }
                if (!readable(railwaySwitch.pivot) || !readable(railwaySwitch.sign)) return;
                if (!isPhysicallyPresent(railwaySwitch)) {
                    removeGhostSwitch(railwaySwitch, "actuator refresh");
                    return;
                }
                if (refreshActuator(railwaySwitch)) {
                    queueSave();
                }
            }, Math.max(1L, delayTicks));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to schedule switch actuator refresh " + railwaySwitch.id, ex);
        }
    }

    private void scheduleRedstoneRefresh(SkyTrainSwitch railwaySwitch, long delayTicks) {
        Location pivotLocation = railwaySwitch.pivot.location();
        if (pivotLocation == null || shuttingDown) {
            return;
        }
        long version = railwaySwitch.nextRedstoneVersion();
        try {
            Bukkit.getRegionScheduler().runDelayed(plugin, pivotLocation, task -> {
                if (shuttingDown || switches.get(railwaySwitch.id) != railwaySwitch
                        || !railwaySwitch.isRedstoneVersion(version)) {
                    return;
                }
                if (!readable(railwaySwitch.pivot) || !readable(railwaySwitch.sign)
                        || !readable(railwaySwitch.support)) return;
                if (!isPhysicallyPresent(railwaySwitch)) {
                    removeGhostSwitch(railwaySwitch, "redstone refresh");
                    return;
                }
                Block sign = railwaySwitch.sign.block();
                if (sign == null) {
                    return;
                }
                Block support = railwaySwitch.support.block();
                if (support == null) {
                    return;
                }
                SwitchState commanded = readPowered(sign, support)
                        ? SwitchState.DIVERGING
                        : SwitchState.STRAIGHT;
                SwitchState apply = railwaySwitch.setCommandedState(commanded);
                requestPhysicalState(railwaySwitch, apply);
                queueSave();
            }, Math.max(1L, delayTicks));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE, "Failed to schedule switch redstone refresh " + railwaySwitch.id, ex);
        }
    }

    private void refreshActuatorsNear(Block changedBlock) {
        if (changedBlock == null) {
            return;
        }
        String worldName = changedBlock.getWorld().getName();
        boolean dirty = false;
        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            if (!railwaySwitch.sign.worldName().equals(worldName)
                    || !redstoneCanAffect(railwaySwitch, changedBlock)) {
                continue;
            }
            dirty |= refreshActuator(railwaySwitch);
        }
        if (dirty) {
            queueSave();
        }
    }

    private boolean refreshActuator(SkyTrainSwitch railwaySwitch) {
        List<SwitchBlockPosition> levers = nearbyLevers(railwaySwitch);
        SwitchBlockPosition selected = levers.size() == 1 ? levers.get(0) : null;
        SwitchBlockPosition old = railwaySwitch.actuator();
        railwaySwitch.setActuator(selected, levers.size());
        if (!samePosition(old, selected)) {
            String status = railwaySwitch.actuatorStatus();
            plugin.getLogger().info("Switch " + railwaySwitch.id + " actuator " + status
                    + (selected == null ? "" : " at " + selected.key()));
            return true;
        }
        return false;
    }

    private List<SwitchBlockPosition> nearbyLevers(SkyTrainSwitch railwaySwitch) {
        Map<String, SwitchBlockPosition> levers = new LinkedHashMap<>();
        collectLevers(levers, railwaySwitch.sign, 1);
        collectLevers(levers, railwaySwitch.support, 1);
        return List.copyOf(levers.values());
    }

    private void collectLevers(Map<String, SwitchBlockPosition> levers, SwitchBlockPosition center, int radius) {
        Block base = center.block();
        if (base == null) {
            return;
        }
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = base.getRelative(x, y, z);
                    if (block.getType() == Material.LEVER) {
                        SwitchBlockPosition position = SwitchBlockPosition.of(block);
                        levers.putIfAbsent(position.key(), position);
                    }
                }
            }
        }
    }

    private void scheduleActuatorWrite(SkyTrainSwitch railwaySwitch,
            SkyTrainSwitch.ActuatorCommand command, int attempt) {
        if (!railwaySwitch.actuatorCommandCurrent(command)) {
            return;
        }
        SwitchBlockPosition position = command.position();
        Location actuatorLocation = position.location();
        if (actuatorLocation == null || shuttingDown) {
            return;
        }
        try {
            Bukkit.getRegionScheduler().runDelayed(plugin, actuatorLocation, task -> {
                if (shuttingDown || switches.get(railwaySwitch.id) != railwaySwitch
                        || !railwaySwitch.actuatorCommandCurrent(command)) {
                    return;
                }
                LeverOutput.Result result = LeverOutput.write(position.block(), command.state() == SwitchState.DIVERGING);
                if (result == LeverOutput.Result.MISSING) {
                    railwaySwitch.setActuator(null, 0);
                    queueSave();
                } else if (result == LeverOutput.Result.UNAVAILABLE) {
                    if (attempt < 4) scheduleActuatorWrite(railwaySwitch, command, attempt + 1);
                    else plugin.getLogger().warning("Switch " + railwaySwitch.id
                            + " lever output deferred: neighbours are not loaded/owned; retry the command when loaded.");
                }
            }, attempt == 0 ? 1L : 2L);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to schedule switch actuator write " + railwaySwitch.id, ex);
        }
    }

    private void requestPhysicalState(SkyTrainSwitch railwaySwitch, SwitchState state) {
        requestPhysicalState(railwaySwitch, state, null, "", "CONTROL_OR_RESTORE");
    }

    private void requestPhysicalState(SkyTrainSwitch railwaySwitch, SwitchState state,
            UUID trainId, String trainName, String reason) {
        if (railwaySwitch == null || state == null || shuttingDown) {
            return;
        }
        SkyTrainSwitch.Mutation mutation = railwaySwitch.beginMutation(state);
        if (mutation == null) {
            return;
        }
        Location pivotLocation = railwaySwitch.pivot.location();
        if (pivotLocation == null) {
            railwaySwitch.finishMutation(mutation.version(), mutation.state(), false);
            return;
        }

        try {
            Bukkit.getRegionScheduler().run(plugin, pivotLocation, task -> {
                boolean success = false;
                try {
                    success=writePhysicalMutation(railwaySwitch,mutation,trainId,trainName,reason);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to set switch " + railwaySwitch.id + " to " + mutation.state(), ex);
                } finally {
                    railwaySwitch.finishMutation(mutation.version(), mutation.state(), success);
                }
            });
        } catch (RuntimeException ex) {
            railwaySwitch.finishMutation(mutation.version(), mutation.state(), false);
            plugin.getLogger().log(Level.WARNING, "Failed to schedule switch mutation " + railwaySwitch.id, ex);
        }
    }

    private boolean writePhysicalMutation(SkyTrainSwitch railwaySwitch,SkyTrainSwitch.Mutation mutation,
            UUID trainId,String trainName,String reason) {
        if(!railwaySwitch.mutationCurrent(mutation.version(),mutation.state()) || railwaySwitch.effectiveState()!=mutation.state()
                || !readable(railwaySwitch.pivot) || !readable(railwaySwitch.sign)) return false;
        if(!isPhysicallyPresent(railwaySwitch)) {removeGhostSwitch(railwaySwitch,"physical state mutation");return false;}
        Block pivot=railwaySwitch.pivot.block();
        if(pivot==null || pivot.getType()!=Material.RAIL || !(pivot.getBlockData() instanceof Rail rail)
                || !rail.getShapes().contains(railwaySwitch.shape(mutation.state()))) return false;
        Rail.Shape before=rail.getShape();rail.setShape(railwaySwitch.shape(mutation.state()));pivot.setBlockData(rail,false);
        if(before!=rail.getShape() && plugin.telemetrySink()!=null)
            plugin.telemetrySink().switchEvent(trainId,trainName,railwaySwitch,"SWITCH_CHANGED",reason,
                    before==railwaySwitch.straightShape?"STRAIGHT":before==railwaySwitch.divergingShape?"DIVERGING":before.name(),mutation.state().name(),"");
        return true;
    }

    private void rebuildIndexes() {
        signIndex.clear();
        pivotIndex.clear();
        for (SkyTrainSwitch railwaySwitch : switches.values()) {
            UUID signConflict = signIndex.putIfAbsent(railwaySwitch.sign.key(), railwaySwitch.id);
            UUID pivotConflict = pivotIndex.putIfAbsent(railwaySwitch.pivot.key(), railwaySwitch.id);
            if (signConflict != null || pivotConflict != null) {
                switches.remove(railwaySwitch.id, railwaySwitch);
                plugin.getLogger().warning("Removed duplicate switch definition " + railwaySwitch.id);
            }
        }
    }

    private boolean isPhysicallyPresent(SkyTrainSwitch railwaySwitch) {
        Block sign = railwaySwitch.sign.block();
        if (sign == null || !(sign.getState() instanceof Sign signState)) {
            return false;
        }
        String header = normalizedSignLine(signState.getLine(0));
        String action = normalizedSignLine(signState.getLine(1));
        if (!SignHeaders.isSkyTrain(header) || !"switch".equalsIgnoreCase(action)) {
            return false;
        }
        Block pivot = railwaySwitch.pivot.block();
        return pivot != null && pivot.getBlockData() instanceof Rail;
    }

    private static boolean readable(SwitchBlockPosition position) {
        Location location = position.location();
        return location != null && location.getWorld().isChunkLoaded(position.x() >> 4, position.z() >> 4)
                && Bukkit.isOwnedByCurrentRegion(location);
    }

    private void removeGhostSwitch(SkyTrainSwitch railwaySwitch, String reason) {
        if (remove(railwaySwitch)) {
            plugin.getLogger().warning("Removed ghost SkyTrain switch " + railwaySwitch.id
                    + " during " + reason + "; its sign or pivot rail no longer exists.");
        }
    }

    private void queueSave() {
        saveDirty.set(true);
        if (shuttingDown || !saveQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    do {
                        saveDirty.set(false);
                        save();
                    } while (saveDirty.get() && !shuttingDown);
                } finally {
                    saveQueued.set(false);
                    if (saveDirty.get() && !shuttingDown) {
                        queueSave();
                    }
                }
            });
        } catch (RuntimeException ex) {
            saveQueued.set(false);
            plugin.getLogger().log(Level.FINE, "Failed to queue switches.yml save", ex);
        }
    }

    private static SwitchLayout frontFacingLayout(Block support, BlockFace throughSign,
            SwitchGeometry geometry) {
        Block pivot = support.getRelative(BlockFace.UP, 2);
        if (pivot.getType() != Material.RAIL || !(pivot.getBlockData() instanceof Rail)) {
            throw new IllegalArgumentException("fl/fr 安装要求道岔尖轨位于控制牌背后承载方块的上方两格。");
        }
        BlockFace common = throughSign.getOppositeFace();
        return new SwitchLayout(
                pivot,
                common,
                throughSign,
                geometry.frontDivergingFace(throughSign));
    }

    private static SwitchSignMount switchSignMount(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            BlockFace front = wallSign.getFacing();
            BlockFace attachedFace = front.getOppositeFace();
            return new SwitchSignMount(signBlock.getRelative(attachedFace), attachedFace, front, false);
        }
        if (data instanceof WallHangingSign wallHangingSign) {
            BlockFace front = wallHangingSign.getFacing();
            BlockFace attachedFace = front.getOppositeFace();
            return new SwitchSignMount(signBlock.getRelative(attachedFace), attachedFace, front, false);
        }
        if (data instanceof HangingSign hangingSign) {
            // Bukkit stores the hanging sign's rotation opposite to the direction
            // the placing player looks at the sign from.
            BlockFace front = hangingSign.getRotation().getOppositeFace();
            if (!isHorizontal(front)) {
                throw new IllegalArgumentException("悬挂告示牌必须摆正朝向东、南、西或北，不能斜放。");
            }
            return new SwitchSignMount(signBlock.getRelative(BlockFace.UP), BlockFace.UP, front, true);
        }
        throw new IllegalArgumentException("道岔控制牌必须是墙上告示牌或悬挂告示牌。");
    }

    private static SwitchLayout switchLayout(SwitchSignMount signMount, SwitchGeometry geometry) {
        Block support = signMount.support();
        BlockFace front = signMount.frontFace();
        BlockFace mountFace = signMount.attachedFace();

        // A ceiling hanging sign is attached to the underside of the rail support block.
        if (signMount.hangingBelowTrack()) {
            Block pivot = support.getRelative(BlockFace.UP);
            if (pivot.getType() != Material.RAIL || !(pivot.getBlockData() instanceof Rail)) {
                throw new IllegalArgumentException("悬挂告示牌上方必须依次是承载方块和道岔尖轨。");
            }
            BlockFace common = front.getOppositeFace();
            return new SwitchLayout(pivot, common, front, geometry.divergingFace(front));
        }

        Block sidePivot = support.getRelative(BlockFace.UP);
        if (sidePivot.getType() == Material.RAIL && sidePivot.getBlockData() instanceof Rail) {
            return sideMountedLayout(sidePivot, mountFace, geometry);
        }

        Block belowPivot = support.getRelative(BlockFace.UP, 2);
        if (belowPivot.getType() == Material.RAIL && belowPivot.getBlockData() instanceof Rail) {
            BlockFace common = front.getOppositeFace();
            return new SwitchLayout(belowPivot, common, front, geometry.divergingFace(front));
        }
        throw new IllegalArgumentException("未找到尖轨：侧贴模式要求尖轨在承载方块上方一格；下装模式要求尖轨在上方两格。");
    }

    private static SwitchLayout sideMountedLayout(Block pivot, BlockFace behindSign,
            SwitchGeometry geometry) {
        BlockFace common = geometry.sideEntryFace(behindSign);
        BlockFace straight = common.getOppositeFace();
        return new SwitchLayout(pivot, common, straight, behindSign);
    }

    private static Rail.Shape shapeFor(BlockFace first, BlockFace second) {
        if ((first == BlockFace.NORTH && second == BlockFace.SOUTH)
                || (first == BlockFace.SOUTH && second == BlockFace.NORTH)) {
            return Rail.Shape.NORTH_SOUTH;
        }
        if ((first == BlockFace.EAST && second == BlockFace.WEST)
                || (first == BlockFace.WEST && second == BlockFace.EAST)) {
            return Rail.Shape.EAST_WEST;
        }
        if (contains(first, second, BlockFace.NORTH, BlockFace.EAST)) {
            return Rail.Shape.NORTH_EAST;
        }
        if (contains(first, second, BlockFace.NORTH, BlockFace.WEST)) {
            return Rail.Shape.NORTH_WEST;
        }
        if (contains(first, second, BlockFace.SOUTH, BlockFace.EAST)) {
            return Rail.Shape.SOUTH_EAST;
        }
        if (contains(first, second, BlockFace.SOUTH, BlockFace.WEST)) {
            return Rail.Shape.SOUTH_WEST;
        }
        throw new IllegalArgumentException("Unsupported switch rail faces: " + first + ", " + second);
    }

    private static boolean contains(BlockFace first, BlockFace second, BlockFace expectedA, BlockFace expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }

    private static SwitchState stateForShape(Rail.Shape shape, Rail.Shape straight, Rail.Shape diverging) {
        if (shape == straight) {
            return SwitchState.STRAIGHT;
        }
        if (shape == diverging) {
            return SwitchState.DIVERGING;
        }
        return null;
    }

    private static boolean readPowered(Block sign, Block support) {
        return sign.isBlockPowered() || sign.isBlockIndirectlyPowered() || sign.getBlockPower() > 0
                || support.isBlockPowered() || support.isBlockIndirectlyPowered() || support.getBlockPower() > 0;
    }

    private static boolean redstoneCanAffect(SkyTrainSwitch railwaySwitch, Block changed) {
        return near(changed, railwaySwitch.sign, 1)
                || near(changed, railwaySwitch.support, 1);
    }

    private static boolean near(Block block, SwitchBlockPosition position, int radius) {
        return Math.abs(block.getX() - position.x()) <= radius
                && Math.abs(block.getY() - position.y()) <= radius
                && Math.abs(block.getZ() - position.z()) <= radius;
    }

    private static boolean samePosition(SwitchBlockPosition first, SwitchBlockPosition second) {
        return first == null ? second == null : first.equals(second);
    }

    private static double horizontalDistanceSquared(Location location, SwitchBlockPosition position) {
        double dx = location.getX() - (position.x() + 0.5);
        double dz = location.getZ() - (position.z() + 0.5);
        return dx * dx + dz * dz;
    }

    private static boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private static SwitchBlockPosition offset(SwitchBlockPosition position, BlockFace face, int distance) {
        return new SwitchBlockPosition(
                position.worldName(),
                position.x() + face.getModX() * distance,
                position.y() + face.getModY() * distance,
                position.z() + face.getModZ() * distance);
    }

    private static BlockFace parseHorizontalFace(String value) {
        BlockFace face = parseBlockFace(value);
        return isHorizontal(face) ? face : null;
    }

    private static BlockFace parseBlockFace(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BlockFace.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static SwitchBlockPosition readPosition(ConfigurationSection section, String path, String world) {
        return new SwitchBlockPosition(
                world,
                section.getInt(path + ".x"),
                section.getInt(path + ".y"),
                section.getInt(path + ".z"));
    }

    private static void writePosition(YamlConfiguration config, String path, SwitchBlockPosition position) {
        config.set(path + ".x", position.x());
        config.set(path + ".y", position.y());
        config.set(path + ".z", position.z());
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
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private double approachDistance() {
        return Math.max(1.5, plugin.getConfig().getDouble("settings.switch-approach-distance", 4.0));
    }

    private double occupiedDistance() {
        return Math.max(0.5, plugin.getConfig().getDouble("settings.switch-occupied-distance", 1.35));
    }

    private double releaseDistance() {
        return Math.max(occupiedDistance() + 0.25,
                plugin.getConfig().getDouble("settings.switch-release-distance", 2.25));
    }

    record SwitchRegistration(UUID id, SwitchGeometry geometry, SwitchState state,
            SwitchBlockPosition pivot, String actuatorStatus) {
    }

    record SwitchScanResult(int registered, int existing, int invalid, int chunks) {
    }

    private record SwitchLayout(Block pivot, BlockFace commonFace, BlockFace straightFace,
            BlockFace divergingFace) {
    }

    private record SwitchSignMount(Block support, BlockFace attachedFace, BlockFace frontFace,
            boolean hangingBelowTrack) {
    }

    private record SwitchApproach(SkyTrainSwitch railwaySwitch, SwitchPort entry, double distance) {
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private enum ScanOutcome {
        NOT_SWITCH,
        REGISTERED,
        EXISTING,
        INVALID
    }
}
