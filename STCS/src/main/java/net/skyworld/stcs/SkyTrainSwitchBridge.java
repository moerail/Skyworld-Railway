package net.skyworld.stcs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

final class SkyTrainSwitchBridge {
    private final StcsPlugin plugin;
    private Plugin skyTrain;
    private Method queryMethod;
    private Method listMethod;
    private Method changeNearestMethod;

    SkyTrainSwitchBridge(StcsPlugin plugin) {
        this.plugin = plugin;
    }

    List<SwitchSnapshot> snapshots() {
        try {
            Plugin current = resolvePlugin();
            if (current == null) {
                return List.of();
            }
            Object result = listMethod.invoke(current);
            if (!(result instanceof List<?> list)) {
                return List.of();
            }
            List<SwitchSnapshot> snapshots = new ArrayList<>();
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) {
                    SwitchSnapshot snapshot = snapshot(map);
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                }
            }
            return List.copyOf(snapshots);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            unavailable(ex);
            return List.of();
        }
    }

    SwitchSnapshot snapshotAt(String worldName, int x, int y, int z) {
        Map<?, ?> geometry = geometry(worldName, x, y, z);
        return geometry.isEmpty() ? null : snapshot(geometry);
    }

    SwitchSnapshot nearest(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double limit = radius * radius;
        return snapshots().stream()
                .filter(snapshot -> snapshot.pivot().world().equals(location.getWorld().getName()))
                .filter(snapshot -> distanceSquared(location, snapshot.pivot()) <= limit)
                .min((a, b) -> Double.compare(distanceSquared(location, a.pivot()),
                        distanceSquared(location, b.pivot())))
                .orElse(null);
    }

    SwitchSnapshot changeNearest(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        try {
            Plugin current = resolvePlugin();
            if (current == null) {
                return null;
            }
            Object result = changeNearestMethod.invoke(current, location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(), radius);
            return result instanceof Map<?, ?> map ? snapshot(map) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            unavailable(ex);
            return null;
        }
    }

    boolean acceptsEntry(String worldName, int x, int y, int z, BlockFace entry) {
        SwitchSnapshot snapshot = snapshotAt(worldName, x, y, z);
        return snapshot != null && snapshot.portName(entry) != null;
    }

    private Map<?, ?> geometry(String worldName, int x, int y, int z) {
        try {
            Plugin current = resolvePlugin();
            if (current == null) {
                return Map.of();
            }
            Object result = queryMethod.invoke(current, worldName, x, y, z);
            return result instanceof Map<?, ?> map ? map : Map.of();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            unavailable(ex);
            return Map.of();
        }
    }

    private Plugin resolvePlugin() throws NoSuchMethodException {
        Plugin current = Bukkit.getPluginManager().getPlugin("SkyTrainFolia");
        if (current == null || !current.isEnabled()) {
            return null;
        }
        if (current != skyTrain || queryMethod == null || listMethod == null) {
            skyTrain = current;
            queryMethod = current.getClass().getMethod(
                    "querySwitchGeometry", String.class, int.class, int.class, int.class);
            listMethod = current.getClass().getMethod("listSwitchGeometries");
            changeNearestMethod = current.getClass().getMethod("changeNearestSwitch",
                    String.class, double.class, double.class, double.class, double.class);
        }
        return current;
    }

    private void unavailable(Exception ex) {
        plugin.getLogger().fine("SkyTrain switch geometry query unavailable: " + ex.getMessage());
        skyTrain = null;
        queryMethod = null;
        listMethod = null;
        changeNearestMethod = null;
    }

    private static SwitchSnapshot snapshot(Map<?, ?> values) {
        try {
            UUID id = UUID.fromString(text(values.get("id")));
            String world = text(values.get("world"));
            int x = Integer.parseInt(text(values.get("x")));
            int y = Integer.parseInt(text(values.get("y")));
            int z = Integer.parseInt(text(values.get("z")));
            BlockFace common = face(values.get("common"));
            BlockFace straight = face(values.get("straight"));
            BlockFace diverging = face(values.get("diverging"));
            if (world.isBlank() || common == null || straight == null || diverging == null) {
                return null;
            }
            return new SwitchSnapshot(id, new BlockPosition(world, x, y, z), common, straight, diverging,
                    text(values.get("commandedState")), text(values.get("physicalState")),
                    text(values.get("localName")), text(values.get("actuatorStatus")));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double distanceSquared(Location location, BlockPosition position) {
        double dx = location.getX() - (position.x() + 0.5);
        double dy = location.getY() - (position.y() + 0.5);
        double dz = location.getZ() - (position.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static BlockFace face(Object value) {
        try {
            return BlockFace.valueOf(text(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    record SwitchSnapshot(UUID id, BlockPosition pivot, BlockFace common,
            BlockFace straight, BlockFace diverging, String commandedState, String physicalState,
            String localName, String actuatorStatus) {

        List<BlockFace> exits(BlockFace entry) {
            if (entry == common) {
                return straight == diverging ? List.of(straight) : List.of(straight, diverging);
            }
            if (entry == straight || entry == diverging) {
                return List.of(common);
            }
            return List.of();
        }

        String portName(BlockFace face) {
            if (face == common) {
                return "common";
            }
            if (face == straight) {
                return "straight";
            }
            if (face == diverging) {
                return "diverging";
            }
            return null;
        }

        Map<String, String> ports() {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("common", common.name().toLowerCase(Locale.ROOT));
            result.put("straight", straight.name().toLowerCase(Locale.ROOT));
            result.put("diverging", diverging.name().toLowerCase(Locale.ROOT));
            return Map.copyOf(result);
        }

        String effectiveState() {
            return physicalState == null || physicalState.isBlank() || physicalState.equalsIgnoreCase("unknown")
                    ? commandedState.toLowerCase(Locale.ROOT) : physicalState.toLowerCase(Locale.ROOT);
        }
    }
}
