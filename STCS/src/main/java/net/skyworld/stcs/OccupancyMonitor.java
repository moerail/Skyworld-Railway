package net.skyworld.stcs;

import java.util.Map;

interface OccupancyMonitor extends AutoCloseable {
    Map<String, Object> status();
    void close();
    static OccupancyMonitor create(StcsPlugin plugin, RailGraphManager manager) {
        try { return new OccupancyRuntime(plugin, manager); }
        catch (Exception | LinkageError ex) {
            plugin.getLogger().severe("M1 occupancy unavailable; no clearance may be inferred: " + ex);
            return new OccupancyMonitor() {
                public Map<String, Object> status() { return Map.of("state", "FAILED", "reason", ex.toString()); }
                public void close() { }
            };
        }
    }
}
