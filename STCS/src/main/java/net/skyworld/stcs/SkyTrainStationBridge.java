package net.skyworld.stcs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

final class SkyTrainStationBridge {
    private final StcsPlugin plugin;
    private Plugin skyTrain;
    private Method listMethod;

    SkyTrainStationBridge(StcsPlugin plugin) {
        this.plugin = plugin;
    }

    List<StcsMarker> snapshots() {
        try {
            Plugin current = resolvePlugin();
            if (current == null) {
                return List.of();
            }
            Object result = listMethod.invoke(current);
            if (!(result instanceof List<?> list)) {
                return List.of();
            }
            List<StcsMarker> stations = new ArrayList<>();
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) {
                    StcsMarker station = station(map);
                    if (station != null) {
                        stations.add(station);
                    }
                }
            }
            return List.copyOf(stations);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("SkyTrain station query unavailable: " + ex.getMessage());
            skyTrain = null;
            listMethod = null;
            return List.of();
        }
    }

    private Plugin resolvePlugin() throws NoSuchMethodException {
        Plugin current = Bukkit.getPluginManager().getPlugin("SkyTrainFolia");
        if (current == null || !current.isEnabled()) {
            return null;
        }
        if (current != skyTrain || listMethod == null) {
            skyTrain = current;
            listMethod = current.getClass().getMethod("listStationMarkers");
        }
        return current;
    }

    private static StcsMarker station(Map<?, ?> values) {
        try {
            UUID id = UUID.fromString(text(values.get("id")));
            String world = text(values.get("world"));
            String signWorld = text(values.get("signWorld"));
            BlockPosition rail = new BlockPosition(world,
                    integer(values.get("x")), integer(values.get("y")), integer(values.get("z")));
            BlockPosition sign = new BlockPosition(signWorld,
                    integer(values.get("signX")), integer(values.get("signY")), integer(values.get("signZ")));
            if (world.isBlank() || signWorld.isBlank()) {
                return null;
            }
            return new StcsMarker(id, MarkerType.STATION, sign, rail, "",
                    "STATION@" + id.toString().substring(0, 8), 0.0, 0.0);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int integer(Object value) {
        return Integer.parseInt(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
