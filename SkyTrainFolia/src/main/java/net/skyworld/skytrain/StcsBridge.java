package net.skyworld.skytrain;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

final class StcsBridge {
    private final SkyTrainPlugin plugin;
    private volatile Plugin provider;
    private volatile Method queryMethod;
    private volatile Method reportMethod;
    private volatile Method removeMethod;
    private volatile boolean failureLogged;
    private final ConcurrentMap<UUID, ReportState> reports = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, QueryState> queries = new ConcurrentHashMap<>();

    StcsBridge(SkyTrainPlugin plugin) {
        this.plugin = plugin;
    }

    StcsTrackSnapshot query(Train train) {
        if (train == null) {
            return StcsTrackSnapshot.unavailable();
        }
        TrainRailPath path = train.trackPath();
        TrainTrackPosition position = path == null ? null : path.activeLeaderTrackPosition(train.reversed);
        if (position != null && plugin.telemetrySink() != null) {
            StcsTrackSnapshot typed = plugin.telemetrySink().query(position);
            if (typed != null) return typed;
        }
        if (position == null || !resolveProvider()) {
            return StcsTrackSnapshot.unavailable();
        }
        try {
            Object value = queryMethod.invoke(provider,
                    position.worldName, position.railX, position.railY, position.railZ,
                    position.motionX, position.motionY, position.motionZ);
            if (!(value instanceof Map<?, ?> result) || !Boolean.TRUE.equals(result.get("available"))) {
                return StcsTrackSnapshot.unavailable();
            }
            failureLogged = false;
            return new StcsTrackSnapshot(
                    string(result.get("edgeId")),
                    string(result.get("edgeFromNodeId")),
                    string(result.get("edgeToNodeId")),
                    number(result.get("edgeOffsetMeters")),
                    number(result.get("edgeLengthMeters")),
                    numberLong(result.get("graphRevision")),
                    string(result.get("line")),
                    number(result.get("currentMileageMeters")),
                    string(result.get("nextMarkerType")),
                    string(result.get("nextMarkerName")),
                    number(result.get("nextMarkerMileageMeters")),
                    number(result.get("distanceMeters")),
                    string(result.get("nextSwitchName")),
                    number(result.get("nextSwitchMileageMeters")),
                    string(result.get("nextSwitchPosition")),
                    number(result.get("distanceToSwitchMeters")));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
            if (!failureLogged) {
                failureLogged = true;
                plugin.getLogger().log(Level.WARNING, "STCS navigation query failed; using SkyTrain fallback data.", ex);
            }
            return StcsTrackSnapshot.unavailable();
        }
    }

    void observe(Train train, String driverName, double movedDistance,
            long nowMillis, long publishIntervalMillis, long reconcileIntervalMillis) {
        if (train != null && plugin.telemetrySink() != null) {
            plugin.telemetrySink().observe(train, driverName, nowMillis, publishIntervalMillis);
            return;
        }
        if (train == null || !resolveProvider() || reportMethod == null) {
            return;
        }
        double speedBlocksPerTick = Math.max(train.currentSpeed(), train.maxMemberSpeed());
        boolean moving = speedBlocksPerTick > 0.001;
        QueryState previousQuery = queries.get(train.id());
        double travelled = previousQuery == null ? 0.0
                : previousQuery.distanceSinceQuery() + Math.max(0.0, movedDistance);
        double previousRemaining = previousQuery == null ? 0.0
                : Math.max(0.0, previousQuery.snapshot().edgeLengthMeters()
                        - previousQuery.snapshot().edgeOffsetMeters());
        long reconcileInterval = Math.max(1_000L, reconcileIntervalMillis);
        boolean queryRequired = previousQuery == null
                || nowMillis - previousQuery.queriedAtMillis() >= reconcileInterval
                || train.reversed != previousQuery.reversed()
                || moving != previousQuery.moving()
                || travelled + 0.25 >= previousRemaining;

        StcsTrackSnapshot snapshot;
        if (queryRequired) {
            snapshot = query(train);
            if (!snapshot.edgeAvailable()) {
                return;
            }
            travelled = 0.0;
            queries.put(train.id(), new QueryState(snapshot, train.reversed, moving, nowMillis, 0.0));
        } else {
            snapshot = previousQuery.snapshot().withEstimatedOffset(travelled);
            queries.put(train.id(), new QueryState(previousQuery.snapshot(), train.reversed,
                    moving, previousQuery.queriedAtMillis(), travelled));
        }

        ReportState previous = reports.get(train.id());
        boolean changed = previous == null
                || !snapshot.edgeId().equals(previous.edgeId())
                || snapshot.graphRevision() != previous.graphRevision()
                || train.reversed != previous.reversed()
                || moving != previous.moving()
                || !train.properties().conductionMode.storageName().equals(previous.mode())
                || !same(driverName, previous.driver());
        boolean publishDue = previous == null
                || nowMillis - previous.reportedAtMillis() >= Math.max(50L, publishIntervalMillis);
        if (!queryRequired && !changed && !publishDue) {
            return;
        }

        TrainRailPath path = train.trackPath();
        TrainTrackPosition position = path == null ? null : path.activeLeaderTrackPosition(train.reversed);
        if (position == null) {
            return;
        }
        double blocksPerMeter = Math.max(0.01,
                plugin.getConfig().getDouble("infrastructure.blocks-per-meter", 1.0));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("trainId", train.id().toString());
        report.put("name", train.name());
        report.put("graphRevision", snapshot.graphRevision());
        report.put("edgeId", snapshot.edgeId());
        put(report, "edgeFromNodeId", snapshot.edgeFromNodeId());
        put(report, "edgeToNodeId", snapshot.edgeToNodeId());
        put(report, "edgeOffsetMeters", snapshot.edgeOffsetMeters());
        put(report, "edgeLengthMeters", snapshot.edgeLengthMeters());
        put(report, "line", snapshot.line());
        put(report, "currentMileageMeters", snapshot.currentMileageMeters());
        report.put("speedBlocksPerTick", speedBlocksPerTick);
        report.put("speedMetersPerSecond", speedBlocksPerTick * 20.0 / blocksPerMeter);
        report.put("lengthMeters", train.spacing * Math.max(0, train.memberCount() - 1) / blocksPerMeter);
        report.put("memberCount", train.memberCount());
        report.put("direction", train.reversed ? "reverse" : "forward");
        report.put("reversed", train.reversed);
        report.put("moving", moving);
        report.put("mode", train.properties().conductionMode.automatic() ? "automatic" : "manual");
        put(report, "driver", driverName);
        report.put("world", position.worldName);
        report.put("x", position.x);
        report.put("y", position.y);
        report.put("z", position.z);
        report.put("observedAtMillis", nowMillis);
        try {
            reportMethod.invoke(provider, report);
            reports.put(train.id(), new ReportState(snapshot.edgeId(), snapshot.graphRevision(),
                    train.reversed, moving, train.properties().conductionMode.storageName(),
                    driverName, nowMillis));
            failureLogged = false;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
            logFailure("STCS train position report failed.", ex);
        }
    }

    void remove(UUID trainId) {
        if (trainId == null) return;
        if (plugin.telemetrySink() != null) {
            plugin.telemetrySink().remove(trainId);
            reports.remove(trainId); queries.remove(trainId);
            return;
        }
        reports.remove(trainId);
        queries.remove(trainId);
        if (trainId == null || !resolveProvider() || removeMethod == null) {
            return;
        }
        try {
            removeMethod.invoke(provider, trainId.toString());
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
            logFailure("STCS train position removal failed.", ex);
        }
    }

    void clear() {
        reports.clear();
        queries.clear();
    }

    private boolean resolveProvider() {
        Plugin current = provider;
        if (current != null && current.isEnabled() && queryMethod != null) {
            return true;
        }
        Plugin candidate = Bukkit.getPluginManager().getPlugin("STCS");
        if (candidate == null || !candidate.isEnabled()) {
            provider = null;
            queryMethod = null;
            reportMethod = null;
            removeMethod = null;
            return false;
        }
        try {
            Method method = candidate.getClass().getMethod("queryTrack",
                    String.class, int.class, int.class, int.class,
                    double.class, double.class, double.class);
            provider = candidate;
            queryMethod = method;
            try {
                reportMethod = candidate.getClass().getMethod("reportTrainPosition", Map.class);
                removeMethod = candidate.getClass().getMethod("removeTrainPosition", String.class);
            } catch (NoSuchMethodException ignored) {
                reportMethod = null;
                removeMethod = null;
            }
            return true;
        } catch (NoSuchMethodException ex) {
            if (!failureLogged) {
                failureLogged = true;
                plugin.getLogger().warning("STCS is installed but does not expose a compatible queryTrack API.");
            }
            return false;
        }
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double number(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : null;
    }

    private static long numberLong(Object value) {
        return value instanceof Number number ? number.longValue() : -1L;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void logFailure(String message, Exception ex) {
        if (!failureLogged) {
            failureLogged = true;
            plugin.getLogger().log(Level.WARNING, message, ex);
        }
    }

    private record ReportState(String edgeId, long graphRevision, boolean reversed,
            boolean moving, String mode, String driver, long reportedAtMillis) {
    }

    private record QueryState(StcsTrackSnapshot snapshot, boolean reversed, boolean moving,
            long queriedAtMillis, double distanceSinceQuery) {
    }
}

record StcsTrackSnapshot(String edgeId, String edgeFromNodeId, String edgeToNodeId,
        Double edgeOffsetMeters, Double edgeLengthMeters, long graphRevision,
        String line, Double currentMileageMeters, String nextType,
        String nextName, Double nextMileageMeters, Double distanceMeters,
        String nextSwitchName, Double nextSwitchMileageMeters,
        String nextSwitchPosition, Double nextSwitchDistanceMeters) {
    static StcsTrackSnapshot unavailable() {
        return new StcsTrackSnapshot(null, null, null, null, null, -1L,
                null, null, null, null, null, null,
                null, null, null, null);
    }

    boolean available() {
        return nextName != null && distanceMeters != null;
    }

    boolean nextSwitchAvailable() {
        return nextSwitchName != null && nextSwitchDistanceMeters != null;
    }

    boolean edgeAvailable() {
        return edgeId != null && edgeOffsetMeters != null && edgeLengthMeters != null
                && graphRevision >= 0L;
    }

    StcsTrackSnapshot withEstimatedOffset(double travelledMeters) {
        double offset = Math.min(edgeLengthMeters, Math.max(0.0, edgeOffsetMeters + travelledMeters));
        return new StcsTrackSnapshot(edgeId, edgeFromNodeId, edgeToNodeId,
                offset, edgeLengthMeters, graphRevision, line, currentMileageMeters,
                nextType, nextName, nextMileageMeters, distanceMeters,
                nextSwitchName, nextSwitchMileageMeters, nextSwitchPosition, nextSwitchDistanceMeters);
    }
}
