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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

final class LineInfrastructureManager {
    private static final double EPSILON = 0.0001;

    private final SkyTrainPlugin plugin;
    private final File dataFile;
    private final ConcurrentMap<UUID, InfrastructureMarker> markers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> signIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<UUID>> railIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> originByLine = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private final AtomicBoolean saveDirty = new AtomicBoolean();
    private final Object dataIoLock = new Object();
    private volatile boolean shuttingDown;

    LineInfrastructureManager(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "infrastructure.yml");
    }

    int markerCount() {
        return markers.size();
    }

    long count(InfrastructureMarkerType type) {
        return markers.values().stream().filter(marker -> marker.type == type).count();
    }

    List<String> lineNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (InfrastructureMarker marker : markers.values()) {
            names.putIfAbsent(marker.lineKey, marker.lineName);
        }
        return names.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    LineMileageClearResult clearLineMileage(String lineName) {
        String cleaned = cleanLine(lineName);
        if (cleaned == null) {
            throw new IllegalArgumentException("线路名不能为空且不能超过 64 个字符。");
        }
        String lineKey = InfrastructureMarker.normalizeLine(cleaned);
        List<InfrastructureMarker> sameLine = markers.values().stream()
                .filter(marker -> marker.lineKey.equals(lineKey))
                .toList();
        if (sameLine.isEmpty()) {
            return new LineMileageClearResult(false, cleaned, 0);
        }

        int cleared = 0;
        for (InfrastructureMarker marker : sameLine) {
            if (marker.clearCalibration()) {
                cleared++;
            }
        }
        if (cleared > 0) {
            queueSave();
        }
        return new LineMileageClearResult(true, sameLine.get(0).lineName, cleared);
    }

    void load() {
        synchronized (dataIoLock) {
            Map<UUID, InfrastructureMarker> loaded = new LinkedHashMap<>();
            if (dataFile.exists() && dataFile.length() > 0L) {
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(dataFile);
                } catch (IOException | InvalidConfigurationException ex) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to read infrastructure.yml; existing runtime data was kept.", ex);
                    return;
                }

                ConfigurationSection root = config.getConfigurationSection("markers");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            ConfigurationSection section = root.getConfigurationSection(key);
                            if (section == null) {
                                continue;
                            }
                            InfrastructureMarkerType type = InfrastructureMarkerType.parse(section.getString("type"));
                            String world = section.getString("world");
                            String line = cleanLine(section.getString("line"));
                            if (type == null || world == null || line == null) {
                                throw new IllegalArgumentException("Missing type, world, or line");
                            }
                            SwitchBlockPosition sign = readPosition(section, "sign", world);
                            SwitchBlockPosition rail = readPosition(section, "rail", world);
                            Vector positiveDirection = new Vector(
                                    section.getDouble("positive-direction.x", 0.0),
                                    section.getDouble("positive-direction.y", 0.0),
                                    section.getDouble("positive-direction.z", 0.0));
                            Vector boundaryDirection = new Vector(
                                    section.getDouble("boundary-direction.x", 0.0),
                                    section.getDouble("boundary-direction.y", 0.0),
                                    section.getDouble("boundary-direction.z", 0.0));
                            InfrastructureMarker marker = new InfrastructureMarker(
                                    id, type, sign, rail, line,
                                    section.getString("display-name", ""),
                                    section.getBoolean("mileage-known", type == InfrastructureMarkerType.ORIGIN),
                                    section.getDouble("mileage-meters", 0.0),
                                    positiveDirection, boundaryDirection);
                            loaded.put(id, marker);
                        } catch (RuntimeException ex) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Skipping invalid infrastructure marker " + key, ex);
                        }
                    }
                }
            }

            markers.clear();
            markers.putAll(loaded);
            rebuildIndexes();
        }
    }

    void start() {
        shuttingDown = false;
    }

    void shutdown() {
        shuttingDown = true;
    }

    void reload() {
        save();
        load();
        start();
    }

    void save() {
        synchronized (dataIoLock) {
            YamlConfiguration config = new YamlConfiguration();
            for (InfrastructureMarker marker : markers.values()) {
                String path = "markers." + marker.id;
                config.set(path + ".type", marker.type.storageName());
                config.set(path + ".world", marker.sign.worldName());
                writePosition(config, path + ".sign", marker.sign);
                writePosition(config, path + ".rail", marker.rail);
                config.set(path + ".line", marker.lineName);
                config.set(path + ".display-name", marker.displayName);
                config.set(path + ".mileage-known", marker.mileageKnown());
                config.set(path + ".mileage-meters", marker.mileageMeters());
                Vector direction = marker.positiveDirection();
                config.set(path + ".positive-direction.x", direction.getX());
                config.set(path + ".positive-direction.y", direction.getY());
                config.set(path + ".positive-direction.z", direction.getZ());
                Vector boundaryDirection = marker.boundaryDirection();
                config.set(path + ".boundary-direction.x", boundaryDirection.getX());
                config.set(path + ".boundary-direction.y", boundaryDirection.getY());
                config.set(path + ".boundary-direction.z", boundaryDirection.getZ());
            }
            try {
                saveYamlAtomically(config, dataFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save infrastructure.yml", ex);
            }
        }
    }

    MarkerRegistration registerSign(Block signBlock, InfrastructureMarkerType type,
            String lineText, String detailText) {
        if (signBlock == null || type == null) {
            throw new IllegalArgumentException("无法识别基础设施控制牌。");
        }
        String lineName = cleanLine(lineText);
        if (lineName == null) {
            throw new IllegalArgumentException("第三行必须填写线路名，例如 line_a。");
        }

        RailInfo rail = nearestRail(signBlock.getLocation().add(0.5, 0.5, 0.5), markerRailSearchRadius());
        if (rail == null) {
            throw new IllegalArgumentException("控制牌附近找不到铁轨，请把牌子放在轨道侧面 1~2 格内。");
        }

        SwitchBlockPosition signPosition = SwitchBlockPosition.of(signBlock);
        SwitchBlockPosition railPosition = SwitchBlockPosition.of(rail.block);
        UUID existingId = signIndex.get(signPosition.key());
        InfrastructureMarker old = existingId == null ? null : markers.get(existingId);
        String lineKey = InfrastructureMarker.normalizeLine(lineName);
        if (type == InfrastructureMarkerType.ORIGIN) {
            UUID existingOrigin = originByLine.get(lineKey);
            if (existingOrigin != null && !existingOrigin.equals(existingId)) {
                InfrastructureMarker duplicate = markers.get(existingOrigin);
                String where = duplicate == null ? existingOrigin.toString()
                        : duplicate.sign.worldName() + " " + duplicate.sign.x() + " "
                                + duplicate.sign.y() + " " + duplicate.sign.z();
                throw new IllegalArgumentException("线路 " + lineName + " 已存在 Origin: " + where);
            }
        }

        UUID id = existingId == null ? UUID.randomUUID() : existingId;
        String displayName = switch (type) {
            case ORIGIN -> "Origin";
            case END -> "End";
            case BALISE -> cleanDisplayName(detailText, id);
        };
        Vector positiveDirection = new Vector();
        Vector boundaryDirection = new Vector();
        if (type == InfrastructureMarkerType.ORIGIN) {
            positiveDirection = markerDirection(signBlock, rail, detailText, type);
        } else if (type == InfrastructureMarkerType.END) {
            boundaryDirection = markerDirection(signBlock, rail, detailText, type);
            if (old != null && old.type == type && old.lineKey.equals(lineKey)) {
                positiveDirection = old.positiveDirection();
            }
        } else if (old != null && old.type == type && old.lineKey.equals(lineKey)) {
            positiveDirection = old.positiveDirection();
        }

        boolean keepCalibration = old != null && old.type == type && old.lineKey.equals(lineKey)
                && type != InfrastructureMarkerType.ORIGIN && old.mileageKnown();
        InfrastructureMarker marker = new InfrastructureMarker(
                id, type, signPosition, railPosition, lineName, displayName,
                keepCalibration, keepCalibration ? old.mileageMeters() : 0.0,
                positiveDirection, boundaryDirection);

        if (old != null) {
            removeFromIndexes(old);
        }
        markers.put(id, marker);
        addToIndexes(marker);
        queueSave();
        return new MarkerRegistration(id, type, lineName, displayName, railPosition);
    }

    boolean removeSign(Block signBlock) {
        if (signBlock == null) {
            return false;
        }
        UUID id = signIndex.get(SwitchBlockPosition.of(signBlock).key());
        return id != null && remove(markers.get(id));
    }

    boolean remove(InfrastructureMarker marker) {
        if (marker == null || !markers.remove(marker.id, marker)) {
            return false;
        }
        removeFromIndexes(marker);
        queueSave();
        return true;
    }

    InfrastructureMarker nearest(Location location, InfrastructureMarkerType type, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double radiusSquared = Math.max(0.25, radius * radius);
        return markers.values().stream()
                .filter(marker -> marker.type == type)
                .filter(marker -> marker.sign.worldName().equals(location.getWorld().getName()))
                .filter(marker -> distanceSquared(location, marker.sign) <= radiusSquared)
                .min(Comparator.comparingDouble(marker -> distanceSquared(location, marker.sign)))
                .orElse(null);
    }

    InfrastructureMarker nearestAny(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double radiusSquared = Math.max(0.25, radius * radius);
        return markers.values().stream()
                .filter(marker -> marker.sign.worldName().equals(location.getWorld().getName()))
                .filter(marker -> distanceSquared(location, marker.sign) <= radiusSquared)
                .min(Comparator.comparingDouble(marker -> distanceSquared(location, marker.sign)))
                .orElse(null);
    }

    NextBaliseSnapshot nextBalise(Train train) {
        TrainMileageSnapshot mileage = train == null ? TrainMileageSnapshot.unknown() : train.mileageSnapshot();
        if (!mileage.known() || mileage.lineName() == null || mileage.travelSign() == 0) {
            return NextBaliseSnapshot.unavailable();
        }

        String lineKey = InfrastructureMarker.normalizeLine(mileage.lineName());
        InfrastructureMarker next = markers.values().stream()
                .filter(marker -> marker.lineKey.equals(lineKey))
                .filter(InfrastructureMarker::mileageKnown)
                .filter(marker -> mileage.travelSign() > 0
                        ? marker.mileageMeters() > mileage.meters() + 0.01
                        : marker.mileageMeters() < mileage.meters() - 0.01)
                .min(Comparator.comparingDouble(marker -> Math.abs(marker.mileageMeters() - mileage.meters())))
                .orElse(null);
        if (next == null) {
            return NextBaliseSnapshot.unavailable();
        }

        double distance = Math.abs(next.mileageMeters() - mileage.meters());
        if (distance > signalRangeMeters()) {
            return NextBaliseSnapshot.unavailable();
        }
        return new NextBaliseSnapshot(
                next.displayName,
                formatMileageCompact(next.mileageMeters()),
                distance);
    }

    Collection<InfrastructureMarker> markers(InfrastructureMarkerType type) {
        return markers.values().stream()
                .filter(marker -> marker.type == type)
                .sorted(Comparator.comparing((InfrastructureMarker marker) -> marker.lineKey)
                        .thenComparing(marker -> marker.displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(marker -> marker.id.toString()))
                .toList();
    }

    InfrastructureMarker origin(String lineKey) {
        UUID id = originByLine.get(InfrastructureMarker.normalizeLine(lineKey));
        return id == null ? null : markers.get(id);
    }

    void observeTrainMovement(Train train, List<TrainRailPath.RailProbe> probes,
            double movedBlocks) {
        if (train == null || probes == null || probes.isEmpty()) {
            return;
        }

        double blocksPerMeter = blocksPerMeter();
        double cursor = 0.0;
        boolean markerSeen = false;
        for (TrainRailPath.RailProbe probe : probes) {
            double probeDistance = Math.max(cursor, Math.min(Math.max(0.0, movedBlocks), probe.distance()));
            train.advanceMileage(probeDistance - cursor, blocksPerMeter, probe.trackPosition().motion(), signalRangeMeters());
            cursor = probeDistance;

            List<InfrastructureMarker> atRail = markersAt(probe.trackPosition());
            if (atRail.isEmpty()) {
                continue;
            }
            markerSeen = true;
            for (InfrastructureMarker marker : atRail) {
                if (!train.markInfrastructureMarker(marker.id)) {
                    continue;
                }
                applyMarker(train, marker, probe.trackPosition().motion());
            }
        }
        if (cursor < movedBlocks) {
            TrainTrackPosition last = probes.get(probes.size() - 1).trackPosition();
            train.advanceMileage(movedBlocks - cursor, blocksPerMeter, last.motion(), signalRangeMeters());
        }
        if (!markerSeen) {
            train.clearInfrastructureMarkerLatch();
        }
    }

    List<String> describe(InfrastructureMarker marker) {
        if (marker == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("&e" + marker.type.storageName().toUpperCase(Locale.ROOT)
                + " &f" + marker.displayName + " &7| 线路: &b" + marker.lineName);
        lines.add("&7UUID: &f" + marker.id);
        lines.add("&7牌子: &f" + formatPosition(marker.sign)
                + " &7| 绑定轨道: &f" + formatPosition(marker.rail));
        lines.add("&7里程: " + (marker.mileageKnown()
                ? "&a" + formatMileage(marker.mileageMeters())
                : "&c未标定（让列车从该线路 Origin 驶过此处）"));

        InfrastructureMarker origin = origin(marker.lineKey);
        if (marker.type != InfrastructureMarkerType.ORIGIN) {
            lines.add("&7线路 Origin: " + (origin == null
                    ? "&c未配置"
                    : "&a" + formatPosition(origin.sign)));
        }
        if (marker.type == InfrastructureMarkerType.BALISE && marker.mileageKnown()) {
            lines.add("&8以下为同线路的里程前后关系，不代表道岔或支线拓扑连接。");
            appendAdjacentBalises(lines, marker);
        }
        return List.copyOf(lines);
    }

    String describeTrainMileage(Train train) {
        TrainMileageSnapshot snapshot = train == null ? TrainMileageSnapshot.unknown() : train.mileageSnapshot();
        if (snapshot.lineName() == null || snapshot.lineName().isBlank()) {
            return "&7列车尚未经过已注册的 Origin 或 Balise，当前里程未知。";
        }
        String mileage = snapshot.known() ? formatMileage(snapshot.meters()) : "未标定";
        String balise = snapshot.lastBaliseName() == null ? "--" : snapshot.lastBaliseName();
        String range = snapshot.inSignalRange() ? "&a信号范围内" : "&c超出信号区间";
        return "&7线路: &b" + snapshot.lineName()
                + " &7| 里程: &f" + mileage
                + " &7| 上一 Balise: &f" + balise
                + " &7| 状态: " + range;
    }

    double inspectionRadius() {
        return RailMath.clamp(plugin.getConfig().getDouble("infrastructure.inspection-radius", 2.0), 1.0, 8.0);
    }

    static String formatMileage(double meters) {
        return formatMileageCompact(meters) + " ("
                + String.format(Locale.ROOT, "%.2f m", meters) + ")";
    }

    static String formatMileageCompact(double meters) {
        double absolute = Math.abs(meters);
        long kilometres = (long) Math.floor(absolute / 1000.0);
        double remainder = absolute - kilometres * 1000.0;
        return (meters < -0.0005 ? "-" : "") + "K" + kilometres + "+"
                + String.format(Locale.ROOT, "%06.2f", remainder);
    }

    private void applyMarker(Train train, InfrastructureMarker marker, Vector travelDirection) {
        switch (marker.type) {
            case ORIGIN -> train.anchorMileage(marker.lineName, 0.0,
                    marker.positiveDirection(), travelDirection,
                    marker.id, marker.displayName, true);
            case BALISE -> {
                if (marker.mileageKnown()) {
                    train.anchorMileage(marker.lineName, marker.mileageMeters(),
                            marker.positiveDirection(), travelDirection, marker.id, marker.displayName, true);
                    return;
                }

                TrainMileageSnapshot current = train.mileageSnapshot();
                if (current.known()
                        && InfrastructureMarker.normalizeLine(current.lineName()).equals(marker.lineKey)) {
                    Vector positive = train.positiveMileageDirection(travelDirection);
                    if (marker.calibrate(current.meters(), positive)) {
                        queueSave();
                    }
                    train.anchorMileage(marker.lineName, marker.mileageMeters(),
                            marker.positiveDirection(), travelDirection, marker.id, marker.displayName, true);
                } else {
                    train.setUnknownMileageLine(marker.lineName, marker.id, marker.displayName);
                }
            }
            case END -> {
                TrainMileageSnapshot current = train.mileageSnapshot();
                boolean arrivedFromSameLine = current.lineName() != null
                        && InfrastructureMarker.normalizeLine(current.lineName()).equals(marker.lineKey);
                if (current.known() && arrivedFromSameLine
                        && marker.calibrate(current.meters(), train.positiveMileageDirection(travelDirection))) {
                    queueSave();
                }
                if (marker.mileageKnown()) {
                    train.anchorMileage(marker.lineName, marker.mileageMeters(),
                            marker.positiveDirection(), travelDirection,
                            marker.id, marker.displayName, true);
                } else {
                    train.setUnknownMileageLine(marker.lineName, marker.id, marker.displayName);
                }

                if (travelsOutward(marker, travelDirection, arrivedFromSameLine)) {
                    train.markLineEnd(marker.lineKey, travelDirection);
                }
            }
        }
    }

    private List<InfrastructureMarker> markersAt(TrainTrackPosition position) {
        String key = position.worldName.toLowerCase(Locale.ROOT) + ':'
                + position.railX + ':' + position.railY + ':' + position.railZ;
        Set<UUID> ids = railIndex.get(key);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(markers::get).filter(java.util.Objects::nonNull).toList();
    }

    private void appendAdjacentBalises(List<String> lines, InfrastructureMarker marker) {
        List<InfrastructureMarker> sameLine = markers.values().stream()
                .filter(candidate -> candidate.type == InfrastructureMarkerType.BALISE)
                .filter(candidate -> candidate.lineKey.equals(marker.lineKey))
                .filter(InfrastructureMarker::mileageKnown)
                .sorted(Comparator.comparingDouble(InfrastructureMarker::mileageMeters))
                .toList();
        int index = sameLine.indexOf(marker);
        InfrastructureMarker previous = index > 0 ? sameLine.get(index - 1) : null;
        InfrastructureMarker next = index >= 0 && index + 1 < sameLine.size() ? sameLine.get(index + 1) : null;
        lines.add(adjacentText("负里程方向", marker, previous));
        lines.add(adjacentText("正里程方向", marker, next));
    }

    private String adjacentText(String label, InfrastructureMarker from, InfrastructureMarker target) {
        if (target == null) {
            return "&7" + label + ": &c限定距离内无已标定 Balise";
        }
        double distance = Math.abs(target.mileageMeters() - from.mileageMeters());
        if (distance > signalRangeMeters()) {
            return "&7" + label + ": &c" + target.displayName + " 距离 "
                    + String.format(Locale.ROOT, "%.2f m（超出 %.0f m 信号范围）", distance, signalRangeMeters());
        }
        return "&7" + label + ": &a" + target.displayName + " &7| 距离: &f"
                + String.format(Locale.ROOT, "%.2f m", distance);
    }

    private void rebuildIndexes() {
        signIndex.clear();
        railIndex.clear();
        originByLine.clear();
        for (InfrastructureMarker marker : List.copyOf(markers.values())) {
            UUID signConflict = signIndex.putIfAbsent(marker.sign.key(), marker.id);
            UUID originConflict = marker.type == InfrastructureMarkerType.ORIGIN
                    ? originByLine.putIfAbsent(marker.lineKey, marker.id)
                    : null;
            if (signConflict != null || originConflict != null) {
                markers.remove(marker.id, marker);
                plugin.getLogger().warning("Removed duplicate infrastructure marker " + marker.id);
                continue;
            }
            railIndex.computeIfAbsent(marker.rail.key(), ignored -> ConcurrentHashMap.newKeySet()).add(marker.id);
        }
    }

    private void addToIndexes(InfrastructureMarker marker) {
        signIndex.put(marker.sign.key(), marker.id);
        railIndex.computeIfAbsent(marker.rail.key(), ignored -> ConcurrentHashMap.newKeySet()).add(marker.id);
        if (marker.type == InfrastructureMarkerType.ORIGIN) {
            originByLine.put(marker.lineKey, marker.id);
        }
    }

    private void removeFromIndexes(InfrastructureMarker marker) {
        signIndex.remove(marker.sign.key(), marker.id);
        Set<UUID> atRail = railIndex.get(marker.rail.key());
        if (atRail != null) {
            atRail.remove(marker.id);
            if (atRail.isEmpty()) {
                railIndex.remove(marker.rail.key(), atRail);
            }
        }
        if (marker.type == InfrastructureMarkerType.ORIGIN) {
            originByLine.remove(marker.lineKey, marker.id);
        }
    }

    private Vector markerDirection(Block signBlock, RailInfo rail, String sideText,
            InfrastructureMarkerType type) {
        String side = sideText == null ? "" : sideText.trim().toLowerCase(Locale.ROOT);
        if (!"left".equals(side) && !"right".equals(side)) {
            throw new IllegalArgumentException(type.storageName()
                    + " 第四行必须写 left 或 right。Origin 表示正里程方向，End 表示线路结束方向。");
        }
        Vector facing = signFacing(signBlock.getBlockData());
        if (facing.lengthSquared() < EPSILON) {
            throw new IllegalArgumentException("无法从牌子朝向确定 " + type.storageName() + " 的方向。");
        }
        Vector screenDirection = "left".equals(side)
                ? new Vector(-facing.getZ(), 0.0, facing.getX())
                : new Vector(facing.getZ(), 0.0, -facing.getX());
        Vector positive = RailMath.direction(rail.rail.getShape(), screenDirection);
        if (positive.lengthSquared() < EPSILON) {
            throw new IllegalArgumentException(type.storageName() + " 必须放在能确定左右方向的轨道旁。");
        }
        return positive.normalize();
    }

    private static Vector normalized(Vector direction) {
        if (direction == null || direction.lengthSquared() < EPSILON) {
            return new Vector();
        }
        return direction.clone().normalize();
    }

    static boolean travelsOutward(InfrastructureMarker marker, Vector travelDirection,
            boolean arrivedFromSameLine) {
        Vector boundaryDirection = marker == null ? new Vector() : marker.boundaryDirection();
        return boundaryDirection.lengthSquared() < EPSILON
                ? arrivedFromSameLine
                : normalized(travelDirection).dot(boundaryDirection) > 0.5;
    }

    private static Vector signFacing(BlockData data) {
        BlockFace face = null;
        if (data instanceof WallSign wallSign) {
            face = wallSign.getFacing();
        } else if (data instanceof Rotatable rotatable) {
            face = rotatable.getRotation();
        }
        if (face == null) {
            return new Vector();
        }
        Vector vector = new Vector(face.getModX(), 0.0, face.getModZ());
        return vector.lengthSquared() < EPSILON ? vector : vector.normalize();
    }

    private RailInfo nearestRail(Location center, double radius) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        int scan = (int) Math.ceil(radius);
        RailInfo nearest = null;
        double nearestDistance = radius * radius;
        for (int x = center.getBlockX() - scan; x <= center.getBlockX() + scan; x++) {
            for (int y = center.getBlockY() - scan; y <= center.getBlockY() + scan; y++) {
                for (int z = center.getBlockZ() - scan; z <= center.getBlockZ() + scan; z++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    BlockData data = block.getBlockData();
                    if (!(data instanceof Rail rail)) {
                        continue;
                    }
                    double dx = x + 0.5 - center.getX();
                    double dy = y + 0.1 - center.getY();
                    double dz = z + 0.5 - center.getZ();
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= nearestDistance) {
                        nearestDistance = distance;
                        nearest = new RailInfo(block, rail, false, false);
                    }
                }
            }
        }
        return nearest;
    }

    private double markerRailSearchRadius() {
        return RailMath.clamp(plugin.getConfig().getDouble("infrastructure.marker-rail-search-radius", 2.5), 1.0, 4.0);
    }

    private double blocksPerMeter() {
        return RailMath.clamp(plugin.getConfig().getDouble("infrastructure.blocks-per-meter", 1.0), 0.01, 100.0);
    }

    private double signalRangeMeters() {
        return RailMath.clamp(plugin.getConfig().getDouble("infrastructure.balise-search-distance", 500.0), 1.0, 100000.0);
    }

    private static String cleanLine(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() || cleaned.length() > 64 ? null : cleaned;
    }

    private static String cleanDisplayName(String value, UUID id) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return "Balise-" + id.toString().substring(0, 8);
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
            plugin.getLogger().log(Level.FINE, "Failed to queue infrastructure.yml save", ex);
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

    private static String formatPosition(SwitchBlockPosition position) {
        return position.worldName() + " " + position.x() + " " + position.y() + " " + position.z();
    }

    private static double distanceSquared(Location location, SwitchBlockPosition position) {
        double dx = location.getX() - (position.x() + 0.5);
        double dy = location.getY() - (position.y() + 0.5);
        double dz = location.getZ() - (position.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static void saveYamlAtomically(YamlConfiguration config, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        config.save(temporary);
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record MarkerRegistration(UUID id, InfrastructureMarkerType type, String lineName,
            String displayName, SwitchBlockPosition rail) {
    }

    record LineMileageClearResult(boolean found, String lineName, int clearedMarkers) {
    }
}
