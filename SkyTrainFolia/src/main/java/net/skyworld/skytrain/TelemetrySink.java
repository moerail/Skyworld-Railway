package net.skyworld.skytrain;

import java.util.UUID;

/** Optional STA implementation is isolated so STF can still run without the API jar. */
interface TelemetrySink {
    default ShadowCurve.Input shadowCurveInput(UUID train, UUID lease, long now, boolean reversed, String reverser) {
        return ShadowCurve.Input.unavailable("UNAVAILABLE");
    }
    default CabAuthorityView cabAuthority(UUID train, UUID lease, long now) { return CabAuthorityView.unavailable(); }
    default java.util.function.Consumer<UUID> beginRemoval(Train train) { return id -> {}; }
    default void protectionEvent(Train train, UUID actor, String actorName, String previous, String next) { }
    default void switchEvent(UUID trainId, String trainName, SkyTrainSwitch railwaySwitch,
            String type, String reason, String previous, String next, String entry) { }
    default void driverEvent(Train train, java.util.UUID driver, String driverName, String type, String reason) { }
    default void observe(Train train, String driver, long now, long interval) {}
    default void remove(UUID id) {}
    default void close() {}
    default StcsTrackSnapshot query(TrainTrackPosition position) { return null; }
    default StationForecast stationAhead(TrainTrackPosition position,double maxBlocks) { return null; }
    static TelemetrySink create(SkyTrainPlugin plugin) {
        var sta = plugin.getServer().getPluginManager().getPlugin("SkyworldTrainAPI");
        try { return create(sta, () -> new StaTelemetryPublisher(plugin)); }
        catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("STA unavailable; legacy telemetry remains active: " + ex);
            return null;
        }
    }
    static TelemetrySink create(org.bukkit.plugin.Plugin sta, java.util.function.Supplier<TelemetrySink> factory) {
        return sta == null || !sta.isEnabled() ? null : factory.get();
    }
}
