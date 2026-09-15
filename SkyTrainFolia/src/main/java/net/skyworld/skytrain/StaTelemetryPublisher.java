package net.skyworld.skytrain;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.ServicePriority;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v2.*;
import net.skyworld.sta.api.v3.*;
import java.util.function.Consumer;

final class StaTelemetryPublisher implements TelemetrySink, TelemetryService, ConsistObservationService, net.skyworld.sta.api.v4.DriverDeskService {
    private final SkyTrainPlugin plugin;
    private final UUID session = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final LatestMessageStore store;
    private final ConcurrentMap<UUID, TrainTelemetrySnapshot> previous = new ConcurrentHashMap<>();
    private final double scale;
    private final ScheduledTask task;
    private volatile boolean closed;
    private volatile boolean warned;
    private volatile List<ConsistObservation> consists = List.of();
    private long nextConsistSample;
    private final ConcurrentMap<UUID, ConsistObservation> destructions = new ConcurrentHashMap<>();
    private final java.nio.file.Path destructionFile;
    private String savedDestructions = "";
    StaTelemetryPublisher(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        destructionFile = plugin.getDataFolder().toPath().resolve("confirmed-destructions.json");
        try {
            if (java.nio.file.Files.exists(destructionFile)) {
                var saved = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(destructionFile), ConsistObservation[].class);
                for (var receipt : saved) {
                    if (!receipt.confirmedDestruction()) throw new IllegalArgumentException("Incomplete destruction receipt");
                    destructions.put(receipt.train(), receipt);
                }
            }
        } catch (java.io.IOException | RuntimeException ex) {
            throw new IllegalStateException("Invalid destruction receipts; original file retained", ex);
        }
        scale = plugin.getConfig().getDouble("infrastructure.blocks-per-meter", 1.0);
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid distance scale");
        store = new LatestMessageStore(session, 16384, ex -> plugin.getLogger().warning("STA consumer failed: " + ex));
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, t -> {
            store.dispatch(); store.pruneRemovals(System.currentTimeMillis() - 120000);
            try { sampleConsists(); }
            catch (RuntimeException ex) { warn("STA consist sampling failed; previous observations retained: " + ex); }
        }, 50, 50, TimeUnit.MILLISECONDS);
        plugin.getServer().getServicesManager().register(TelemetryService.class, this, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(ConsistObservationService.class, this, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(net.skyworld.sta.api.v4.DriverDeskService.class, this, plugin, ServicePriority.Normal);
        plugin.getLogger().info("STA v2 telemetry provider active; legacy position reporting disabled.");
    }
    public UUID sessionId() { return session; }
    public Collection<net.skyworld.sta.api.v4.DriverDeskService.Desk> driverDesks() { return plugin.driverDesks(); }
    public void driverEvent(Train train, UUID driver, String driverName, String type, String reason) {
        try {
            var service = plugin.getServer().getServicesManager().load(RailwayEventService.class);
            if (service != null) service.publish("STF", RailwayEvent.Type.valueOf(type), train.id(), train.name(),
                    driver, driverName, reason);
            else warn("STA event service unavailable; driver safety actions still applied");
        } catch (RuntimeException | LinkageError ex) { warn("Driver event delivery failed: " + ex); }
    }
    public Collection<ConsistObservation> consistObservations() { return consists; }
    public Consumer<UUID> beginRemoval(Train train) {
        var expected = List.copyOf(train.members());
        var evidence = train.memberEvidence();
        List<ConsistObservation.Member> members = new ArrayList<>();
        for (UUID id : expected) {
            var e = evidence.get(id);
            if (e == null) return ignored -> {};
            var p = e.position();
            members.add(new ConsistObservation.Member(id, p.worldName, p.x, p.y, p.z,
                    p.timeMillis, System.currentTimeMillis(), ConsistObservation.State.REMOVED));
        }
        return new RemovalConfirmation(expected, () -> destructions.put(train.id(),
                new ConsistObservation(session, sequence.incrementAndGet(), train.id(), train.name(),
                        System.currentTimeMillis(), expected, members, true)));
    }
    public void protectionEvent(Train train, UUID actor, String actorName, String previous, String next) {
        try {
            var service = plugin.getServer().getServicesManager().load(RailwayEventService.class);
            if (service != null) service.publishDetailed("STF", RailwayEvent.Type.ATP_MODE_CHANGED,
                    train.id(), train.name(), null, "", "MODE_COMMAND",
                    Map.of("actorId", actor.toString(), "actorName", actorName, "previous", previous, "next", next));
        } catch (RuntimeException | LinkageError ex) { warn("Protection event delivery failed: " + ex); }
    }
    public void switchEvent(UUID trainId, String trainName, SkyTrainSwitch railwaySwitch,
            String type, String reason, String previous, String next, String entry) {
        try {
            var service = plugin.getServer().getServicesManager().load(RailwayEventService.class);
            if (service != null) service.publishDetailed("STF", RailwayEvent.Type.valueOf(type), trainId, trainName,
                    null, "", reason, Map.of("switchId", railwaySwitch.id.toString(),
                    "switchName", railwaySwitch.localName, "world", railwaySwitch.pivot.worldName(),
                    "previous", previous, "next", next, "entry", entry));
        } catch (RuntimeException | LinkageError ex) { warn("Switch event delivery failed: " + ex); }
    }
    private void sampleConsists() {
        long now = System.currentTimeMillis();
        if (closed || now < nextConsistSample || !plugin.isRailInfrastructureReady()) return;
        nextConsistSample = now + 250;
        List<ConsistObservation> result = new ArrayList<>();
        for (Train train : plugin.observedTrains()) {
            List<ConsistObservation.Member> members = new ArrayList<>();
            train.memberEvidence().forEach((id, e) -> {
                var p = e.position();
                members.add(new ConsistObservation.Member(id, p.worldName, p.x, p.y, p.z,
                        p.timeMillis, e.stateAtMillis(), ConsistObservation.State.valueOf(e.state())));
            });
            result.add(new ConsistObservation(session, sequence.incrementAndGet(), train.id(), train.name(),
                    now, train.members(), members, false));
        }
        // Persist before publication; receipts survive missed sampling and server restarts.
        var receipts = destructions.values().stream().sorted(Comparator.comparing(r -> r.train().toString())).toList();
        String json = new com.google.gson.Gson().toJson(receipts);
        if (!json.equals(savedDestructions)) {
            try {
                var tmp = destructionFile.resolveSibling(destructionFile.getFileName() + ".tmp");
                java.nio.file.Files.writeString(tmp, json);
                try { java.nio.file.Files.move(tmp, destructionFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    java.nio.file.Files.move(tmp, destructionFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                savedDestructions = json;
            } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot persist destruction receipts", ex); }
        }
        var live = result.stream().map(ConsistObservation::train).collect(java.util.stream.Collectors.toSet());
        for (var receipt : receipts) if (!live.contains(receipt.train())) result.add(new ConsistObservation(
                session, sequence.incrementAndGet(), receipt.train(), receipt.name(), now,
                receipt.expectedMembers(), receipt.members(), true));
        // Absence alone never means track clear.
        consists = List.copyOf(result);
    }
    public Collection<StaMessage> snapshots() { return store.snapshots(); }
    public Subscription subscribe(Consumer<StaMessage> listener) { return store.subscribe(listener); }
    public void observe(Train train, String driver, long now, long interval) {
        if (closed) return;
        try {
            TrainRailPath path = train.trackPath();
            TrainTrackPosition p = path == null ? null : path.activeLeaderTrackPosition(train.reversed);
            if (p == null || train.memberCount() < 1) return;
            double speed = Math.max(train.currentSpeed(), train.maxMemberSpeed());
            TrainMode mode = train.properties().conductionMode.automatic() ? TrainMode.AUTOMATIC : TrainMode.MANUAL;
            var old = previous.get(train.id());
            var cab = new TrainTelemetrySnapshot.CabState(train.protectionMode.name(), train.reverser.name(),
                    train.powerNotch, train.brakeNotch, train.emergencyBrake,
                    train.protectionMode == ProtectionMode.RECOVERING || train.driverEmergencyHold);
            boolean moving = speed > 0.001;
            if (old != null && now - old.observedAtMillis() < Math.max(50, interval)
                    && old.reversed() == train.reversed && old.moving() == moving && old.mode() == mode
                    && Objects.equals(old.cab(), cab)
                    && Objects.equals(old.trainNumber(), train.properties().trainNumber)
                    && Objects.equals(old.driverName(), Objects.requireNonNullElse(driver, ""))
                    && old.world().equals(p.worldName) && old.memberCount() == train.memberCount()) return;
            long seq = sequence.incrementAndGet();
            var state = new TrainTelemetrySnapshot(train.id(), train.name(), seq, now, p.worldName,
                    p.railX, p.railY, p.railZ, p.x, p.y, p.z, p.motionX, p.motionY, p.motionZ,
                    Math.max(0, speed) * 20.0 / scale, train.spacing * Math.max(0, train.memberCount() - 1) / scale,
                    train.memberCount(), moving, train.reversed, mode, driver, cab, train.properties().trainNumber);
            var m = new StaMessage(new StaMessage.Header(2, StaMessage.Kind.TELEMETRY_REPORT,
                    StaMessage.Source.STF, session, seq, now, train.id()), new StaMessage.Physical(state, scale), null);
            if (store.offer(m)) {
                previous.put(train.id(), state);
                if (old != null && old.cab() != null && !old.cab().emergencyBrake() && cab.emergencyBrake()) {
                    driverEvent(train, null, driver, "EMERGENCY_BRAKE_APPLIED",
                            train.driverEmergencyHold ? "DRIVER_HOLD"
                            : train.protectionMode == ProtectionMode.RECOVERING ? "RECOVERING" : "EB_INPUT");
                }
            }
            else warn("STA capacity/order rejected telemetry");
        } catch (RuntimeException ex) { warn("STA observation failed: " + ex); }
    }
    private void warn(String message) { if (!warned) { warned = true; plugin.getLogger().warning(message); } }
    public void remove(UUID id) {
        if (id == null || closed) return;
        previous.remove(id);
        store.offer(new StaMessage(new StaMessage.Header(2, StaMessage.Kind.TRAIN_REMOVED,
                StaMessage.Source.STF, session, sequence.incrementAndGet(), System.currentTimeMillis(), id), null, null));
    }
    public StcsTrackSnapshot query(TrainTrackPosition p) {
        var service = plugin.getServer().getServicesManager().load(RailNetworkService.class);
        if (service == null || service.protocolVersion() != 2) return null;
        try {
            var n = service.query(new RailNetworkService.RailQuery(p.worldName, p.railX, p.railY, p.railZ,
                    p.motionX, p.motionY, p.motionZ));
            if (n == null || n.position() == null) return StcsTrackSnapshot.unavailable();
            var t = n.position();
            return new StcsTrackSnapshot(t.edgeId(), t.edgeFromNodeId(), t.edgeToNodeId(), t.edgeOffsetMeters(),
                    t.edgeLengthMeters(), t.graphRevision(), t.line(), t.mileageMeters(), n.nextType(), n.nextName(),
                    n.nextMileageMeters(), n.distanceMeters(), n.nextSwitchName(), n.nextSwitchMileageMeters(),
                    n.nextSwitchPosition(), n.nextSwitchDistanceMeters());
        } catch (RuntimeException ex) { warn("STA navigation unavailable: " + ex); return StcsTrackSnapshot.unavailable(); }
    }
    public void close() {
        closed = true; task.cancel();
        plugin.getServer().getServicesManager().unregister(TelemetryService.class, this);
        plugin.getServer().getServicesManager().unregister(ConsistObservationService.class, this);
        plugin.getServer().getServicesManager().unregister(net.skyworld.sta.api.v4.DriverDeskService.class, this);
        consists = List.of();
        store.close(); previous.clear();
    }
    public StationForecast stationAhead(TrainTrackPosition p,double maxBlocks) {
        try {
            var service=plugin.getServer().getServicesManager().load(RailNetworkService.class);
            if(service==null || service.protocolVersion()!=2 || Math.abs(service.blocksPerMeter()-scale)>0.000001) return null;
            var target=service.stationAhead(new RailNetworkService.RailQuery(p.worldName,p.railX,p.railY,p.railZ,
                    p.motionX,p.motionY,p.motionZ),maxBlocks/scale);
            if(target==null || target.graphRevision()!=service.graphRevision()
                    || System.currentTimeMillis()-target.observedAtMillis()>1000) return null;
            double fraction=(p.x-(p.railX+0.5))*p.motionX+(p.z-(p.railZ+0.5))*p.motionZ;
            return new StationForecast(target.graphRevision(),target.observedAtMillis(),
                    target.world()+":"+target.signX()+":"+target.signY()+":"+target.signZ(),
                    Math.max(0,target.distanceMeters()*target.blocksPerMeter()-fraction));
        } catch(RuntimeException|LinkageError ex) { warn("STA station forecast unavailable: "+ex); return null; }
    }
}
