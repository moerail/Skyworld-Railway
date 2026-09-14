package net.skyworld.stcs;

import java.util.List;
import java.util.Map;

interface StaIntegration extends AutoCloseable {
    boolean ownsTelemetry();
    List<Map<String, Object>> legacyPositions();
    void close();
    static StaIntegration create(StcsPlugin plugin, RailGraphManager manager) {
        var sta = plugin.getServer().getPluginManager().getPlugin("SkyworldTrainAPI");
        if (sta == null || !sta.isEnabled()) return null;
        try { return new StaTrackingProvider(plugin, manager); }
        catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("STA unavailable; legacy registry remains active: " + ex);
            return null;
        }
    }
}
