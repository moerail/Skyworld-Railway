package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.plugin.ServicePriority;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v2.*;

final class StaTrackingProvider implements StaIntegration, TrackingService, RailNetworkService {
    private final StcsPlugin plugin;
    private final RailGraphManager manager;
    private final UUID session = UUID.randomUUID();
    private final LatestMessageStore store;
    private final TrackingResolver resolver;
    private final ScheduledTask task;
    private volatile boolean available, owns, closed;
    private boolean warned;
    StaTrackingProvider(StcsPlugin plugin, RailGraphManager manager) {
        this.plugin = plugin; this.manager = manager;
        long stale = Math.max(1000, plugin.getConfig().getLong("telemetry.stale-after-seconds", 15) * 1000);
        long expire = Math.max(stale, plugin.getConfig().getLong("telemetry.expire-after-seconds", 60) * 1000);
        resolver = new TrackingResolver(session, stale, expire);
        store = new LatestMessageStore(session, 16384, ex -> plugin.getLogger().warning("STA consumer failed: " + ex));
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, t -> update(), 100, 100, TimeUnit.MILLISECONDS);
        var services = plugin.getServer().getServicesManager();
        services.register(TrackingService.class, this, plugin, ServicePriority.Normal);
        services.register(RailNetworkService.class, this, plugin, ServicePriority.Normal);
    }
    private synchronized void update() {
        if (closed) return;
        long now = System.currentTimeMillis();
        try {
            var provider = plugin.getServer().getServicesManager().load(TelemetryService.class);
            available = provider != null && provider.protocolVersion() == 2;
            if (available) {
                owns = true;
                for (var m : resolver.accept(provider.sessionId(), provider.snapshots(), now)) store.offer(m);
            }
            for (var m : resolver.resolve(this, available, now)) {
                if (!store.offer(m) && !warned) { warned = true; plugin.getLogger().warning("STA tracking store at capacity"); }
            }
            store.dispatch(); store.pruneRemovals(now - 120000); plugin.notifyPccUpdate();
        } catch (RuntimeException ex) {
            available = false;
            // Explicitly invalidate last data even when a provider/query throws.
            for (var m : resolver.resolve(this, false, now)) store.offer(m);
            store.dispatch();
            if (!warned) { warned = true; plugin.getLogger().warning("STA tracking unavailable: " + ex); }
        }
    }
    public boolean ownsTelemetry() {
        // Check live registration too: reject legacy writes from the moment STF registers.
        return owns || plugin.getServer().getServicesManager().load(TelemetryService.class) != null;
    }
    public boolean sourceAvailable() { return available; }
    public int protocolVersion() { return StaMessage.VERSION; }
    public UUID sessionId() { return session; }
    public Collection<StaMessage> snapshots() { return store.snapshots(); }
    public Subscription subscribe(Consumer<StaMessage> listener) { return store.subscribe(listener); }
    public long graphRevision() { return manager.revision(); }
    public double blocksPerMeter() { return manager.graphBlocksPerMeter(); }
    public String graphJson() { return manager.graphJson(); }
    public TrackPositionSnapshot edgePosition(long revision, String edgeId, double offsetMeters) {
        return closed ? null : manager.edgePosition(revision, edgeId, offsetMeters);
    }
    public StationApproach stationAhead(RailQuery query,double maximumDistanceMeters) {
        if(query==null) return null;
        var advice=manager.stationAhead(query.world(),query.railX(),query.railY(),query.railZ(),
                query.directionX(),query.directionY(),query.directionZ(),maximumDistanceMeters);
        if(advice==null) return null;
        var target=advice.target(); var p=target.node().sign();
        return new StationApproach(advice.revision(),advice.observedAt(),target.node().id(),p.world(),p.x(),p.y(),p.z(),
                target.distanceMeters(),advice.scale());
    }
    public Navigation query(RailQuery q) {
        var m = manager.query(q.world(), q.railX(), q.railY(), q.railZ(), q.directionX(), q.directionY(), q.directionZ());
        if (!Boolean.TRUE.equals(m.get("available"))) return null;
        long revision = ((Number)m.get("graphRevision")).longValue();
        var p = new TrackPositionSnapshot(revision, text(m, "edgeId"), text(m, "edgeFromNodeId"), text(m, "edgeToNodeId"),
                number(m, "edgeOffsetMeters"), number(m, "edgeLengthMeters"), text(m, "line"), number(m, "currentMileageMeters"),
                System.currentTimeMillis(), revision == manager.revision(), false);
        return new Navigation(p, text(m,"nextMarkerType"), text(m,"nextMarkerName"), number(m,"nextMarkerMileageMeters"),
                number(m,"distanceMeters"), text(m,"nextSwitchName"), number(m,"nextSwitchMileageMeters"),
                text(m,"nextSwitchPosition"), number(m,"distanceToSwitchMeters"));
    }
    private static String text(Map<String,Object> m, String k) { Object v = m.get(k); return v == null ? null : v.toString(); }
    private static Double number(Map<String,Object> m, String k) { Object v=m.get(k); return v instanceof Number n ? n.doubleValue() : null; }
    public List<Map<String,Object>> legacyPositions() {
        long now = System.currentTimeMillis();
        return snapshots().stream().filter(m -> m.header().kind() == StaMessage.Kind.TRACK_REPORT)
                .map(m -> PccProjection.train(m, now)).toList();
    }
    public synchronized void close() {
        closed = true; available = false; task.cancel();
        var services = plugin.getServer().getServicesManager();
        services.unregister(TrackingService.class, this); services.unregister(RailNetworkService.class, this); store.close();
    }
}
