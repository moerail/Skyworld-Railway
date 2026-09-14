package net.skyworld.stcs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TrainPositionRegistry {
    private static final int SCHEMA_VERSION = 1;

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final long staleAfterMillis;
    private final long expireAfterMillis;

    TrainPositionRegistry(long staleAfterMillis, long expireAfterMillis) {
        this.staleAfterMillis = Math.max(1_000L, staleAfterMillis);
        this.expireAfterMillis = Math.max(this.staleAfterMillis, expireAfterMillis);
    }

    boolean report(Map<?, ?> values, long currentGraphRevision, long receivedAtMillis) {
        if (values == null) {
            return false;
        }
        String trainId = text(values.get("trainId"));
        String edgeId = text(values.get("edgeId"));
        if (trainId == null || edgeId == null) {
            return false;
        }

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("schemaVersion", SCHEMA_VERSION);
        position.put("trainId", trainId);
        copyText(values, position, "name", "line", "edgeId", "edgeFromNodeId", "edgeToNodeId",
                "direction", "mode", "driver", "world");
        copyNumber(values, position, "graphRevision", "edgeOffsetMeters", "edgeLengthMeters",
                "currentMileageMeters", "speedMetersPerSecond", "speedBlocksPerTick",
                "lengthMeters", "memberCount", "x", "y", "z", "observedAtMillis");
        copyBoolean(values, position, "moving", "reversed");
        position.putIfAbsent("observedAtMillis", receivedAtMillis);
        position.put("receivedAtMillis", receivedAtMillis);
        position.put("graphCurrent", numberLong(position.get("graphRevision"), -1L) == currentGraphRevision);
        entries.put(trainId, new Entry(Map.copyOf(position), receivedAtMillis));
        return true;
    }

    boolean remove(String trainId) {
        return trainId != null && entries.remove(trainId) != null;
    }

    void clear() {
        entries.clear();
    }

    List<Map<String, Object>> positions(long currentGraphRevision, long nowMillis) {
        List<Map<String, Object>> result = new ArrayList<>();
        entries.forEach((trainId, entry) -> {
            long age = Math.max(0L, nowMillis - entry.receivedAtMillis());
            if (age >= expireAfterMillis) {
                entries.remove(trainId, entry);
                return;
            }
            Map<String, Object> position = new LinkedHashMap<>(entry.position());
            position.put("ageMillis", age);
            position.put("stale", age >= staleAfterMillis);
            position.put("graphCurrent",
                    numberLong(position.get("graphRevision"), -1L) == currentGraphRevision);
            result.add(Map.copyOf(position));
        });
        result.sort((left, right) -> textOrEmpty(left.get("name"))
                .compareToIgnoreCase(textOrEmpty(right.get("name"))));
        return List.copyOf(result);
    }

    Map<String, Object> snapshot(long currentGraphRevision, long nowMillis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("serverTimeMillis", nowMillis);
        result.put("graphRevision", currentGraphRevision);
        result.put("trains", positions(currentGraphRevision, nowMillis));
        return result;
    }

    private static void copyText(Map<?, ?> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private static void copyNumber(Map<?, ?> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                target.put(key, value);
            }
        }
    }

    private static void copyBoolean(Map<?, ?> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Boolean) {
                target.put(key, value);
            }
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String textOrEmpty(Object value) {
        String text = text(value);
        return text == null ? "" : text;
    }

    private static long numberLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private record Entry(Map<String, Object> position, long receivedAtMillis) {
    }
}
