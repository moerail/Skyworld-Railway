package net.skyworld.stcs;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class RailGraphManager {
    private static final double EPSILON = 0.0001;
    private static final Set<String> INCOMPLETE_SCAN_REASONS = Set.of(
            "chunk_not_loaded", "chunk_load_failed", "world_not_loaded",
            "source_schedule_failed", "scan_schedule_failed");

    private final StcsPlugin plugin;
    private final File markerFile;
    private final File graphFile;
    private final ConcurrentMap<UUID, StcsMarker> markers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> signIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<UUID>> railIndex = new ConcurrentHashMap<>();
    private final AtomicLong buildSequence = new AtomicLong();
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private final Object ioLock = new Object();
    private volatile RailGraph graph;
    private volatile boolean legacyGraphLoaded;
    private volatile long legacyRevision = -1L;
    private final SkyTrainSwitchBridge switchBridge;
    private final SkyTrainStationBridge stationBridge;
    private volatile boolean shuttingDown;
    private GraphBuild activeBuild;
    private boolean pendingFull, drainScheduled;
    private RailGraph.Position rebuildCentre;
    private final Map<RailGraph.Position,Boolean> pendingChanges = new LinkedHashMap<>();
    private final Set<RailGraph.Position> brokenRails = ConcurrentHashMap.newKeySet();
    private volatile List<RailGraph.Issue> scanDiagnostics = List.of();
    private volatile long lastCheckedMillis;
    private long nextReview;
    private final AtomicBoolean reviewing = new AtomicBoolean();

    RailGraphManager(StcsPlugin plugin) {
        this.plugin = plugin;
        this.switchBridge = new SkyTrainSwitchBridge(plugin);
        this.stationBridge = new SkyTrainStationBridge(plugin);
        this.markerFile = new File(plugin.getDataFolder(), "markers.yml");
        this.graphFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("graph.file", "railgraph.json"));
        this.graph = RailGraph.empty(maxDistanceMeters(), blocksPerMeter());
    }

    int markerCount() {
        return markers.size();
    }

    int edgeCount() {
        return graph.edges.size();
    }

    int nodeCount() {
        return graph.nodes.size();
    }

    long switchNodeCount() {
        return graph.nodes.stream().filter(node -> "switch".equals(node.type())).count();
    }

    long revision() {
        return graph.revision;
    }

    File graphFile() {
        return graphFile;
    }

    void load() {
        if (!markerFile.isFile()) {
            loadLastGraph();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(markerFile);
        ConfigurationSection root = yaml.getConfigurationSection("markers");
        if (root == null) {
            loadLastGraph();
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                MarkerType type = MarkerType.parse(section.getString("type"));
                BlockPosition sign = readPosition(section.getConfigurationSection("sign"));
                BlockPosition rail = readPosition(section.getConfigurationSection("rail"));
                String line = section.getString("line", "").trim();
                String name = section.getString("name", "").trim();
                if (type == null || sign == null || rail == null || name.isBlank()
                        || (line.isBlank() && type != MarkerType.BALISE && type != MarkerType.STATION)) {
                    continue;
                }
                markers.put(id, new StcsMarker(id, type, sign, rail, line, name,
                        section.getDouble("direction-x"), section.getDouble("direction-z")));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped invalid STCS marker " + key + ": " + ex.getMessage());
            }
        }
        rebuildIndexes();
        loadLastGraph();
    }

    void start() {
        reconcileStartup(0);
        nextReview=System.nanoTime()+reviewIntervalSeconds()*1_000_000_000L;
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin,t->{
            if(shuttingDown||!infrastructureReady())return;
            synchronized(this) {
                var positions=infrastructureChanges();
                for(var p:positions)pendingChanges.put(p,true);
                if(pendingFull||!pendingChanges.isEmpty())scheduleDrain();
            }
            if(System.nanoTime()>=nextReview) {
                nextReview=System.nanoTime()+reviewIntervalSeconds()*1_000_000_000L;
                reviewLoadedRails();
            }
        },1,1,java.util.concurrent.TimeUnit.SECONDS);
    }

    private long reviewIntervalSeconds() { return Math.max(1,Math.min(86400,plugin.getConfig().getLong("graph.review-interval-seconds",60))); }

    private void reviewLoadedRails() {
        synchronized(this){if(activeBuild!=null)return;}
        if(!reviewing.compareAndSet(false,true))return;
        var snapshot=graph;
        try {LoadedRailReview.start(plugin,snapshot,observations->{
            try {synchronized(this) {
                if(shuttingDown||graph!=snapshot)return;
                var changed=new ArrayList<RailGraph.Position>();
                observations.forEach((p,valid)->{
                    boolean different=valid?brokenRails.remove(p):brokenRails.add(p);
                    if(different)changed.add(p);
                });
                // Retry genuinely unresolved, currently loaded sources, including repaired disconnected tracks.
                var ids=GraphIncremental.blocking(snapshot.unresolved,snapshot.edges).stream().map(RailGraph.Issue::source).collect(java.util.stream.Collectors.toSet());
                snapshot.nodes.stream().filter(n->ids.contains(n.id())&&loaded(n.rail())).map(RailGraph.Node::rail).forEach(changed::add);
                for(var p:changed)pendingChanges.put(p,true);
                if(!changed.isEmpty())scheduleDrain();
                lastCheckedMillis=System.currentTimeMillis();
            }}finally{reviewing.set(false);}
        });}catch(RuntimeException ex){reviewing.set(false);plugin.getLogger().log(Level.WARNING,"Rail review failed",ex);}
    }

    net.skyworld.sta.api.v9.TopologyDependency captureDependencies(long revision,List<String> ids) {
        var snapshot=graph;if(snapshot.revision!=revision)return null;
        var result=GraphIncremental.capture(snapshot,List.copyOf(ids));return graph==snapshot?result:null;
    }
    net.skyworld.sta.api.v9.TopologyDependency.Check checkDependencies(net.skyworld.sta.api.v9.TopologyDependency dependency) {
        var snapshot=graph;var result=GraphIncremental.check(snapshot,dependency);
        return graph==snapshot?result:new net.skyworld.sta.api.v9.TopologyDependency.Check(graph.revision,"UNAVAILABLE",List.of());
    }

    private List<RailGraph.Position> infrastructureChanges() {
        var current=currentNodes();var old=new java.util.HashMap<String,RailGraph.Node>();graph.nodes.forEach(n->old.put(n.id(),n));
        var changes=new ArrayList<RailGraph.Position>();
        for(var n:current) {var before=old.remove(n.id());if(!GraphIncremental.nodeStamp(n).equals(GraphIncremental.nodeStamp(before))) {
            if(n.rail()!=null)changes.add(n.rail());if(before!=null&&before.rail()!=null)changes.add(before.rail());
        }}
        old.values().forEach(n->{if(n.rail()!=null)changes.add(n.rail());});return changes;
    }

    private List<StcsMarker> currentMarkers() {
        var result=new ArrayList<>(markers.values());var known=result.stream().map(StcsMarker::id).collect(java.util.stream.Collectors.toSet());
        for(var s:stationBridge.snapshots())if(known.add(s.id()))result.add(s);return List.copyOf(result);
    }
    private List<RailGraph.Node> currentNodes() {
        var nodes=new ArrayList<>(currentMarkers().stream().map(this::node).toList());
        switchBridge.snapshots().stream().map(this::switchNode).forEach(nodes::add);
        return importsAvailable()?nodes:graph.nodes;
    }
    private boolean importsAvailable() {
        return Bukkit.getPluginManager().getPlugin("SkyTrainFolia")==null||switchBridge.available()&&stationBridge.available();
    }

    synchronized void updateAround(BlockPosition p) {
        if(p!=null){pendingChanges.put(position(p),true);scheduleDrain();}
    }

    private void scheduleDrain() {
        if(shuttingDown||drainScheduled||activeBuild!=null)return;
        drainScheduled=true;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->{synchronized(this){drainScheduled=false;beginPending();}},2);
    }

    private boolean infrastructureReady() {
        var stf = Bukkit.getPluginManager().getPlugin("SkyTrainFolia");
        if (stf == null) return graph.nodes.stream().noneMatch(n -> n.type().equals("switch") || n.type().equals("station"));
        if (!stf.isEnabled()) return false;
        try {
            return Boolean.TRUE.equals(stf.getClass().getMethod("isRailInfrastructureReady").invoke(stf));
        } catch (NoSuchMethodException ex) { return true; }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }

    private void reconcileStartup(int attempt) {
        if (shuttingDown) return;
        if (!infrastructureReady()) {
            if (attempt < 30) Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> reconcileStartup(attempt + 1), 20L);
            else plugin.getLogger().warning("STF infrastructure unavailable; retaining restored RailGraph. Rebuild after STF recovers.");
            return;
        }
        Set<String> known = graph.nodes.stream().map(RailGraph.Node::id).collect(java.util.stream.Collectors.toSet());
        boolean missing = switchBridge.snapshots().stream().anyMatch(s -> !known.contains(s.id().toString()))
                || stationBridge.snapshots().stream().anyMatch(s -> !known.contains(s.id().toString()));
        if (missing) {
            plugin.getLogger().info("Restored RailGraph lacks registered STF infrastructure; rebuilding with previous incomplete edges retained.");
            rebuildGraph();
            return;
        }
        if (legacyGraphLoaded && graph.revision != 0L && markers.isEmpty()
                && switchBridge.snapshots().isEmpty() && stationBridge.snapshots().isEmpty()
                && !graph.nodes.isEmpty()) {
            plugin.getLogger().warning("Legacy RailGraph retained: no infrastructure is available for a safe rescan.");
            return;
        }
        if (legacyGraphLoaded || (graph.revision == 0L && !markers.isEmpty())) {
            if (legacyGraphLoaded) plugin.getLogger().info("Legacy RailGraph detected; rebuilding from loaded world rails.");
            rebuildGraph();
        }
    }

    void shutdown() {
        shuttingDown = true;
        buildSequence.incrementAndGet();
        saveNow();
        exportGraphNow(graph);
    }

    Registration register(Block signBlock, MarkerType type, String lineText, String detailText) {
        if (signBlock == null || type == null) {
            throw new IllegalArgumentException("无法识别 STCS 控制牌。");
        }
        String cleanedLine = clean(lineText, 64);
        String line = cleanedLine == null ? "" : cleanedLine;
        if (line.isBlank() && type != MarkerType.BALISE && type != MarkerType.STATION) {
            throw new IllegalArgumentException("第三行必须填写线路名；只有侧线 balise 可以留空。");
        }
        Block railBlock = nearestRail(signBlock.getLocation().add(0.5, 0.5, 0.5), railSearchRadius());
        if (railBlock == null) {
            throw new IllegalArgumentException("牌子附近 3 格内找不到轨道。");
        }

        BlockPosition sign = BlockPosition.of(signBlock);
        BlockPosition rail = BlockPosition.of(railBlock);
        UUID existingId = signIndex.get(sign.key());
        StcsMarker previous = existingId == null ? null : markers.get(existingId);
        UUID id = previous == null ? UUID.randomUUID() : previous.id();

        Vector direction = new Vector();
        String name;
        if (!type.isLineBoundary()) {
            name = clean(detailText, 64);
            if (name == null) {
                throw new IllegalArgumentException(type.storageName() + " 第四行必须填写编号或名称。");
            }
        } else {
            direction = markerDirection(signBlock, railBlock, detailText, type);
            name = type.storageName().toUpperCase(Locale.ROOT) + '@' + shortId(id);
        }

        if (type == MarkerType.ORIGIN) {
            StcsMarker duplicate = markers.values().stream()
                    .filter(marker -> marker.type() == MarkerType.ORIGIN)
                    .filter(marker -> marker.lineKey().equals(line.toLowerCase(Locale.ROOT)))
                    .filter(marker -> !marker.id().equals(id))
                    .findFirst().orElse(null);
            if (duplicate != null) {
                throw new IllegalArgumentException("线路 " + line + " 已存在 Origin。");
            }
        }

        StcsMarker marker = new StcsMarker(id, type, sign, rail, line, name,
                direction.getX(), direction.getZ());
        if (previous != null) {
            removeIndexes(previous);
        }
        markers.put(id, marker);
        addIndexes(marker);
        queueSave();
        if(previous!=null)updateAround(previous.rail());
        updateAround(marker.rail());
        return new Registration(marker, previous == null);
    }

    boolean removeAt(Block block) {
        if (block == null) {
            return false;
        }
        UUID id = signIndex.get(BlockPosition.of(block).key());
        StcsMarker marker = id == null ? null : markers.remove(id);
        if (marker == null) {
            return false;
        }
        removeIndexes(marker);
        queueSave();
        updateAround(marker.rail());
        return true;
    }

    StcsMarker nearestMarker(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double limit = radius * radius;
        return markers.values().stream()
                .filter(marker -> marker.sign().world().equals(location.getWorld().getName()))
                .filter(marker -> distanceSquared(location, marker.sign()) <= limit)
                .min(Comparator.comparingDouble(marker -> distanceSquared(location, marker.sign())))
                .orElse(null);
    }

    SkyTrainSwitchBridge.SwitchSnapshot nearestSwitch(Location location, double radius) {
        return switchBridge.nearest(location, radius);
    }

    SkyTrainSwitchBridge.SwitchSnapshot changeNearestSwitch(Location location, double radius) {
        return switchBridge.changeNearest(location, radius);
    }

    List<String> describe(StcsMarker marker) {
        if (marker == null) {
            return List.of();
        }
        RailGraph.Node node = graph.nodes.stream()
                .filter(candidate -> candidate.id().equals(marker.id().toString()))
                .findFirst().orElse(null);
        List<String> result = new ArrayList<>();
        String effectiveLine = node == null ? marker.line() : node.line();
        result.add("&e" + marker.type().storageName().toUpperCase(Locale.ROOT)
                + " &f" + marker.name() + " &7| 线路: &b"
                + (effectiveLine == null || effectiveLine.isBlank() ? "侧线/未指定" : effectiveLine));
        if (marker.line().isBlank() && node != null && node.lineReferenceId() != null) {
            result.add("&7里程原点参考节点: &f" + node.lineReferenceId());
        }
        result.add("&7UUID: &f" + marker.id());
        result.add("&7绑定轨道: &f" + formatPosition(marker.rail()));
        result.add("&7图里程: " + (node == null || node.mileageMeters() == null
                ? "&c未能从 Origin 到达" : "&a" + formatMileage(node.mileageMeters())));
        List<RailGraph.Edge> outgoing = graph.outgoing(marker.id());
        if (outgoing.isEmpty()) {
            result.add("&7下游连接: &c无");
        } else {
            for (RailGraph.Edge edge : outgoing) {
                RailGraph.Node target = graph.nodes.stream()
                        .filter(candidate -> candidate.id().equals(edge.to())).findFirst().orElse(null);
                result.add("&7" + edge.sourcePort() + " -> &a"
                        + (target == null ? edge.to() : target.name()) + " &f"
                        + String.format(Locale.ROOT, "%.2f m", edge.distanceMeters()));
                var assignment = graph.lineAssignments.get(edge.id());
                if (assignment != null) result.add("&7Line: &f" + assignment.status()
                        + (assignment.line().isBlank() ? "" : " / " + assignment.line()));
            }
        }
        return List.copyOf(result);
    }

    Map<String, Object> query(String world, int railX, int railY, int railZ,
            double directionX, double directionY, double directionZ) {
        return graph.query(world, railX, railY, railZ, directionX, directionY, directionZ,
                this::liveSwitchState, this::navigationSwitchState);
    }

    private String navigationSwitchState(RailGraph.Node node) {
        var p = node == null ? null : node.rail();
        var live = p == null ? null : switchBridge.snapshotAt(p.world(), p.x(), p.y(), p.z());
        if (live == null) return "UNKNOWN";
        String physical = live.physicalState();
        if (!("straight".equalsIgnoreCase(physical) || "diverging".equalsIgnoreCase(physical))) return "UNKNOWN";
        return physical.equalsIgnoreCase(live.commandedState()) ? physical : "PENDING";
    }

    String graphJson() {
        RailGraph snapshot=graph;
        Gson gson = new Gson();
        JsonObject root = gson.toJsonTree(snapshot).getAsJsonObject();
        root.addProperty("topologySemantics",1);
        root.add("routingIssues",gson.toJsonTree(GraphIncremental.blocking(snapshot.unresolved,snapshot.edges)));
        root.add("scanDiagnostics",gson.toJsonTree(scanDiagnostics));
        root.addProperty("lastCheckedMillis",lastCheckedMillis);
        JsonArray nodes = root.getAsJsonArray("nodes");
        if (nodes != null) {
            for (JsonElement element : nodes) {
                JsonObject nodeJson = element.getAsJsonObject();
                if (!"switch".equals(nodeJson.get("type").getAsString())) {
                    continue;
                }
                String id = nodeJson.get("id").getAsString();
                RailGraph.Node railwaySwitch = snapshot.nodes.stream()
                        .filter(node -> node.id().equals(id)).findFirst().orElse(null);
                String liveState = liveSwitchState(railwaySwitch);
                if (liveState != null && !liveState.isBlank()) {
                    nodeJson.addProperty("state", liveState);
                }
            }
        }
        return gson.toJson(root);
    }

    net.skyworld.sta.api.v1.TrackPositionSnapshot edgePosition(long revision, String edgeId, double offset) {
        RailGraph snapshot = graph;
        var position = snapshot.edgePosition(revision, edgeId, offset);
        return graph == snapshot ? position : null;
    }

    RailGraph occupancyGraph() { return graph; }

    Map<String, String> shadowSwitchStates() {
        Map<String, String> states = new java.util.HashMap<>();
        for (var sw : switchBridge.snapshots()) {
            String state = sw.physicalState().toLowerCase(java.util.Locale.ROOT);
            states.put(sw.id().toString(), Set.of("straight", "diverging").contains(state) ? state : "unknown");
        }
        return Map.copyOf(states);
    }

    record StationAdvice(long revision, long observedAt, RailGraph.StationTarget target, double scale) {}

    StationAdvice stationAhead(String world,int x,int y,int z,double dx,double dy,double dz,double limit) {
        RailGraph snapshot=graph;
        if(!Double.isFinite(limit)||limit<=0) return null;
        var target=snapshot.stationAhead(world,x,y,z,dx,dy,dz,Math.min(limit,10000),n->{
                    var p=n.rail();
                    var live=p==null?null:switchBridge.snapshotAt(p.world(),p.x(),p.y(),p.z());
                    return live==null?null:live.effectiveState();
                });
        if(target==null || graph!=snapshot) return null;
        return new StationAdvice(snapshot.revision,System.currentTimeMillis(),target,snapshot.blocksPerMeter);
    }

    private String liveSwitchState(RailGraph.Node railwaySwitch) {
        RailGraph.Position position = railwaySwitch == null ? null : railwaySwitch.rail();
        if (position == null) {
            return railwaySwitch == null ? null : railwaySwitch.state();
        }
        SkyTrainSwitchBridge.SwitchSnapshot live = switchBridge.snapshotAt(
                position.world(), position.x(), position.y(), position.z());
        return live == null ? railwaySwitch.state() : live.effectiveState();
    }

    synchronized void rebuildGraph() { rebuildGraph(null); }

    synchronized void rebuildGraph(Location centre) {
        pendingFull=true;
        rebuildCentre=centre==null?null:new RailGraph.Position(centre.getWorld().getName(),centre.getBlockX(),centre.getBlockY(),centre.getBlockZ());
        scheduleDrain();
    }

    private void beginPending() {
        if (!infrastructureReady()) {
            plugin.getLogger().warning("RailGraph rebuild deferred: STF infrastructure not ready; existing graph retained.");
            return;
        }
        if (shuttingDown) {
            return;
        }
        if(activeBuild!=null||!pendingFull&&pendingChanges.isEmpty())return;
        long buildId = nextBuildId(buildSequence, graph.revision);
        List<StcsMarker> snapshot = currentMarkers();
        List<SkyTrainSwitchBridge.SwitchSnapshot> switches = switchBridge.snapshots();
        if(!importsAvailable())return; // An unavailable producer is not an empty/deleted infrastructure catalogue.
        var nodes=new ArrayList<>(snapshot.stream().map(this::node).toList());switches.stream().map(this::switchNode).forEach(nodes::add);
        var affected=pendingFull?nodes.stream().map(RailGraph.Node::id).collect(java.util.stream.Collectors.toSet())
            :GraphIncremental.affected(graph,nodes,List.copyOf(pendingChanges.keySet()));
        var centre=rebuildCentre;pendingFull=false;rebuildCentre=null;pendingChanges.clear();
        var loaded=nodes.stream().filter(n->affected.contains(n.id())&&loaded(n.rail()))
            .sorted(Comparator.comparingDouble(n->priority(n.rail(),centre))).map(RailGraph.Node::id).toList();
        GraphBuild build = new GraphBuild(buildId, loaded.size(), snapshot, switches);
        build.scanned.addAll(loaded);activeBuild=build;
        if(loaded.isEmpty()){publish(build);return;}
        var actions=new ArrayList<Runnable>();
        for(var id:loaded) {
            var marker=snapshot.stream().filter(m->m.id().toString().equals(id)).findFirst().orElse(null);
            if(marker!=null)actions.add(()->scanMarker(build,marker));
            else {var sw=switches.stream().filter(s->s.id().toString().equals(id)).findFirst().orElseThrow();actions.add(()->scanSwitch(build,sw));}
        }
        scanBatch(build,actions,0);
    }

    private static boolean loaded(RailGraph.Position p) {
        if(p==null)return false;var w=Bukkit.getWorld(p.world());return w!=null&&w.isChunkLoaded(p.x()>>4,p.z()>>4);
    }
    private static double priority(RailGraph.Position p,RailGraph.Position centre) {
        if(centre==null)return 0;
        return !centre.world().equals(p.world())?Double.MAX_VALUE:square(p.x()-centre.x())+square(p.y()-centre.y())+square(p.z()-centre.z());
    }
    private void scanBatch(GraphBuild build,List<Runnable> actions,int offset) {
        if(!build.active())return;
        int end=Math.min(actions.size(),offset+Math.max(1,Math.min(64,plugin.getConfig().getInt("graph.sources-per-tick",4))));
        for(int i=offset;i<end;i++)actions.get(i).run();
        if(end<actions.size())Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->scanBatch(build,actions,end),1);
    }

    void exportGraph() {
        exportGraphAsync(graph);
    }

    static long nextBuildId(AtomicLong sequence, long restoredRevision) {
        sequence.accumulateAndGet(restoredRevision, Math::max);
        return sequence.incrementAndGet();
    }

    static boolean currentExport(long snapshotRevision, long currentRevision) {
        return snapshotRevision >= currentRevision;
    }

    private void scanMarker(GraphBuild build, StcsMarker marker) {
        Location location = marker.rail().location();
        if (location == null) {
            build.issue(marker, "none", "world_not_loaded", 0.0);
            build.taskDone();
            return;
        }
        schedule(location, () -> {
            if (!build.active()) {
                return;
            }
            Rail.Shape shape = RailGeometry.shape(location.getBlock());
            if (shape == null) {
                build.issue(marker, "none", "source_rail_missing", 0.0);
                build.taskDone();
                return;
            }
            List<BlockFace> ports;
            if (marker.type().scansBothDirections()) {
                ports = RailGeometry.endpoints(shape);
            } else {
                Vector wanted = marker.type() == MarkerType.END
                        ? marker.direction().multiply(-1.0) : marker.direction();
                BlockFace port = RailGeometry.chooseEndpoint(shape, wanted);
                ports = port == null ? List.of() : List.of(port);
            }
            if (ports.isEmpty()) {
                build.issue(marker, "none", "unsupported_source_geometry", 0.0);
                build.taskDone();
                return;
            }
            AtomicInteger remaining = new AtomicInteger(ports.size());
            for (BlockFace port : ports) {
                List<RailGraph.Point> path = new ArrayList<>();
                path.add(point(marker.rail(), 0.0));
                Set<String> visited = new HashSet<>();
                visited.add(marker.rail().key());
                scanNext(build, marker.id().toString(), port.name().toLowerCase(Locale.ROOT),
                        marker.id(), marker.rail(), port, 0.0, path, visited,
                        () -> {
                            if (remaining.decrementAndGet() == 0) {
                                build.taskDone();
                            }
                        });
            }
        }, reason -> {
            build.issue(marker, "none", reason == null ? "source_schedule_failed" : reason, 0.0);
            build.taskDone();
        });
    }

    private void scanSwitch(GraphBuild build, SkyTrainSwitchBridge.SwitchSnapshot railwaySwitch) {
        Location location = railwaySwitch.pivot().location();
        if (location == null) {
            build.issue(railwaySwitch.id().toString(), "none", "world_not_loaded", 0.0);
            build.taskDone();
            return;
        }
        schedule(location, () -> {
            if (!build.active()) {
                return;
            }
            List<Map.Entry<String, BlockFace>> ports = List.of(
                    Map.entry("common", railwaySwitch.common()),
                    Map.entry("straight", railwaySwitch.straight()),
                    Map.entry("diverging", railwaySwitch.diverging()));
            AtomicInteger remaining = new AtomicInteger(ports.size());
            for (Map.Entry<String, BlockFace> port : ports) {
                List<RailGraph.Point> path = new ArrayList<>();
                path.add(point(railwaySwitch.pivot(), 0.0));
                Set<String> visited = new HashSet<>();
                visited.add(railwaySwitch.pivot().key());
                scanNext(build, railwaySwitch.id().toString(), port.getKey(), null,
                        railwaySwitch.pivot(), port.getValue(), 0.0, path, visited, () -> {
                            if (remaining.decrementAndGet() == 0) {
                                build.taskDone();
                            }
                        });
            }
        }, reason -> {
            build.issue(railwaySwitch.id().toString(), "none", reason == null ? "switch_schedule_failed" : reason, 0.0);
            build.taskDone();
        });
    }

    private void scanNext(GraphBuild build, String sourceId, String sourcePort, UUID excludedMarker,
            BlockPosition current,
            BlockFace exitFace, double distanceMeters, List<RailGraph.Point> path,
            Set<String> visited, Runnable completion) {
        if (!build.active()) {
            completion.run();
            return;
        }
        int nextX = current.x() + exitFace.getModX();
        int nextZ = current.z() + exitFace.getModZ();
        World world = Bukkit.getWorld(current.world());
        if (world == null) {
            build.issue(sourceId, sourcePort, "world_not_loaded", distanceMeters);
            completion.run();
            return;
        }
        int chunkX = nextX >> 4;
        int chunkZ = nextZ >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            build.issue(sourceId, sourcePort, "chunk_not_loaded", distanceMeters);
            completion.run();
            return;
        }

        Location nextLocation = new Location(world, nextX, current.y(), nextZ);
        scheduleLoadedStep(build, sourceId, sourcePort, excludedMarker, current,
                exitFace, distanceMeters, path, visited, completion,
                world, nextX, nextZ, chunkX, chunkZ, nextLocation);
    }

    private void scheduleLoadedStep(GraphBuild build, String sourceId, String sourcePort,
            UUID excludedMarker, BlockPosition current, BlockFace exitFace,
            double distanceMeters, List<RailGraph.Point> path, Set<String> visited,
            Runnable completion, World world, int nextX, int nextZ,
            int chunkX, int chunkZ, Location nextLocation) {
        schedule(nextLocation, () -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                build.issue(sourceId, sourcePort, "chunk_not_loaded", distanceMeters);
                completion.run();
                return;
            }
            Block nextBlock = connectedRail(world, nextX, current.y(), nextZ, exitFace.getOppositeFace());
            if (nextBlock == null) {
                build.issue(sourceId, sourcePort, "rail_ended", distanceMeters);
                completion.run();
                return;
            }
            BlockPosition next = BlockPosition.of(nextBlock);
            double stepBlocks = Math.sqrt(square(next.x() - current.x())
                    + square(next.y() - current.y()) + square(next.z() - current.z()));
            double nextDistance = distanceMeters + stepBlocks / blocksPerMeter();
            if (nextDistance > maxDistanceMeters() + EPSILON) {
                build.issue(sourceId, sourcePort, "distance_limit", nextDistance);
                completion.run();
                return;
            }
            if (!visited.add(next.key())) {
                build.issue(sourceId, sourcePort, "loop", nextDistance);
                completion.run();
                return;
            }
            List<RailGraph.Point> nextPath = new ArrayList<>(path);
            nextPath.add(point(next, nextDistance));

            BlockFace entryFace = exitFace.getOppositeFace();
            SkyTrainSwitchBridge.SwitchSnapshot targetSwitch = build.switchAt(next);
            if (targetSwitch == null) {
                targetSwitch = switchBridge.snapshotAt(next.world(), next.x(), next.y(), next.z());
                if (targetSwitch != null && build.addDiscoveredSwitch(targetSwitch)) {
                    scanSwitch(build, targetSwitch);
                }
            }
            if (targetSwitch != null && !targetSwitch.id().toString().equals(sourceId)) {
                String targetPort = targetSwitch.portName(entryFace);
                addEdge(build, sourceId, sourcePort, targetSwitch.id().toString(), targetPort,
                        nextDistance, nextPath);
                completion.run();
                return;
            }

            StcsMarker target = build.markerAt(next, excludedMarker);
            if (target != null) {
                addEdge(build, sourceId, sourcePort, target.id().toString(),
                        entryFace.name().toLowerCase(Locale.ROOT), nextDistance, nextPath);
                completion.run();
                return;
            }

            Rail.Shape shape = RailGeometry.shape(nextBlock);
            BlockFace nextExit = RailGeometry.exitFromEntry(shape, entryFace,
                    RailGeometry.direction(exitFace));
            if (nextExit == null) {
                build.issue(sourceId, sourcePort, "incompatible_rail_geometry", nextDistance);
                completion.run();
                return;
            }
            scanNext(build, sourceId, sourcePort, excludedMarker, next, nextExit,
                    nextDistance, nextPath, visited, completion);
        }, reason -> {
            build.issue(sourceId, sourcePort, reason == null ? "scan_schedule_failed" : reason, distanceMeters);
            completion.run();
        });
    }

    private void addEdge(GraphBuild build, String sourceId, String sourcePort,
            String targetId, String targetPort, double distance, List<RailGraph.Point> path) {
        String edgeId = sourceId + ':' + sourcePort + ':' + targetId + ':' + targetPort;
        var edge=new RailGraph.Edge(edgeId, sourceId, targetId, sourcePort,
                targetPort == null ? "unknown" : targetPort, distance, List.copyOf(path));
        build.edges.add(edge);
        // Extend a local edit only as far as changed boundary connections, not through the whole graph.
        if(build.base.edges.stream().noneMatch(edge::equals)&&!build.scanned.contains(targetId)) {
            var marker=build.markers.stream().filter(m->m.id().toString().equals(targetId)).findFirst().orElse(null);
            var sw=build.switches.values().stream().filter(s->s.id().toString().equals(targetId)).findFirst().orElse(null);
            var pos=marker!=null?position(marker.rail()):sw==null?null:position(sw.pivot());
            if(loaded(pos)&&build.scanned.add(targetId)) {
                build.remainingTasks.incrementAndGet();
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->{if(marker!=null)scanMarker(build,marker);else scanSwitch(build,sw);},1);
            }
        }
    }

    private Block connectedRail(World world, int x, int y, int z, BlockFace entryFace) {
        for (int candidateY : new int[] { y, y + 1, y - 1 }) {
            Block block = world.getBlockAt(x, candidateY, z);
            Rail.Shape shape = RailGeometry.shape(block);
            if (shape != null && (RailGeometry.endpoints(shape).contains(entryFace)
                    || switchBridge.acceptsEntry(world.getName(), x, candidateY, z, entryFace))) {
                return block;
            }
        }
        return null;
    }

    private synchronized void publish(GraphBuild build) {
        if (!build.active()) {
            return;
        }
        List<RailGraph.Node> nodes = new ArrayList<>(build.markers.stream()
                .sorted(Comparator.comparing((StcsMarker marker) -> marker.lineKey())
                        .thenComparing(marker -> marker.type().ordinal())
                        .thenComparing(StcsMarker::name))
                .map(this::node)
                .toList());
        build.switches.values().stream()
                .sorted(Comparator.comparing(railwaySwitch -> railwaySwitch.id().toString()))
                .map(this::switchNode)
                .forEach(nodes::add);
        List<RailGraph.Edge> freshEdges = build.edges.stream()
                .sorted(Comparator.comparing(RailGraph.Edge::from).thenComparing(RailGraph.Edge::sourcePort))
                .toList();
        List<RailGraph.Edge> edges = GraphIncremental.merge(build.base,nodes,build.scanned,freshEdges,build.issues);
        var issues=new ArrayList<RailGraph.Issue>();
        var nodeIds=nodes.stream().map(RailGraph.Node::id).collect(java.util.stream.Collectors.toSet());
        build.base.unresolved.stream().filter(i->nodeIds.contains(i.source())&&!build.scanned.contains(i.source())&&!i.reason().equals("rail_broken")).forEach(issues::add);
        issues.addAll(build.issues);
        // A completed physical walk is newer repair evidence than an earlier periodic break observation.
        for(var edge:freshEdges)for(var p:edge.path())brokenRails.remove(new RailGraph.Position(p.world(),p.x(),p.y(),p.z()));
        // A confirmed break remains a fault even when its source node is currently unloaded.
        for(var e:edges)for(var p:e.path())if(brokenRails.contains(new RailGraph.Position(p.world(),p.x(),p.y(),p.z()))) {
            issues.add(new RailGraph.Issue(e.from(),e.sourcePort(),"rail_broken",p.distanceMeters()));break;
        }
        scanDiagnostics=List.copyOf(issues);lastCheckedMillis=System.currentTimeMillis();
        RailGraph completed = new RailGraph(build.id, maxDistanceMeters(), blocksPerMeter(),
                nodes, edges, List.copyOf(issues), lineDefinitions(build.markers));
        boolean changed=!GraphIncremental.same(graph,completed);
        if(changed){graph=completed;exportGraphAsync(completed);}
        activeBuild=null;
        plugin.notifyPccUpdate();
        // Retried unresolved endpoints are not new faults on every review.
        var previousProblems=new HashSet<>(GraphIncremental.blocking(build.base.unresolved,build.base.edges));
        var problems=GraphIncremental.blocking(issues,edges).stream().filter(i->!previousProblems.contains(i)).toList();
        String summary="RailGraph "+(changed?"revision "+completed.revision+" updated":"unchanged at revision "+graph.revision)
            +"; scanned "+build.scanned.size()+" local loaded source(s), "+edges.size()+" edge(s). Unloaded paths retained.";
        if(!problems.isEmpty()) {
            plugin.getLogger().warning(summary+" Routing problem(s): "+problems.size()+"; "
                +problems.stream().limit(8).map(i->i.source()+"/"+i.sourcePort()+": "+i.reason()).collect(java.util.stream.Collectors.joining("; ")));
        } else if(plugin.getConfig().getBoolean("graph.log-successful-reviews",false)) {
            plugin.getLogger().info(summary);
        }
        if(pendingFull||!pendingChanges.isEmpty())scheduleDrain();
    }

    static List<RailGraph.Edge> preserveIncompleteEdges(List<RailGraph.Edge> previous,
            List<RailGraph.Edge> fresh, List<RailGraph.Issue> issues, Set<String> currentNodeIds) {
        Set<String> incompletePorts = new HashSet<>();
        Set<String> incompleteSources = new HashSet<>();
        for (RailGraph.Issue issue : issues) {
            if (!INCOMPLETE_SCAN_REASONS.contains(issue.reason())) {
                continue;
            }
            if (issue.sourcePort() == null || issue.sourcePort().equalsIgnoreCase("none")) {
                incompleteSources.add(issue.source());
            } else {
                incompletePorts.add(portKey(issue.source(), issue.sourcePort()));
            }
        }
        if (incompletePorts.isEmpty() && incompleteSources.isEmpty()) {
            return fresh;
        }

        Map<String, RailGraph.Edge> merged = new LinkedHashMap<>();
        for (RailGraph.Edge edge : fresh) {
            merged.put(portKey(edge.from(), edge.sourcePort()), edge);
        }
        for (RailGraph.Edge edge : previous) {
            String key = portKey(edge.from(), edge.sourcePort());
            boolean incomplete = incompleteSources.contains(edge.from()) || incompletePorts.contains(key);
            if (incomplete && currentNodeIds.contains(edge.from()) && currentNodeIds.contains(edge.to())) {
                merged.putIfAbsent(key, edge);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(RailGraph.Edge::from).thenComparing(RailGraph.Edge::sourcePort))
                .toList();
    }

    private static String portKey(String source, String port) {
        return source + '\u0000' + (port == null ? "" : port.toLowerCase(Locale.ROOT));
    }

    private RailGraph.Node node(StcsMarker marker) {
        return new RailGraph.Node(marker.id().toString(), marker.type().storageName(), marker.name(), marker.line(),
                position(marker.sign()), position(marker.rail()), Map.of(), List.of(), null, null, null);
    }

    private static Map<String, RailLineIndex.Definition> lineDefinitions(java.util.Collection<StcsMarker> markers) {
        Map<String, RailLineIndex.Definition> definitions = new LinkedHashMap<>();
        for (StcsMarker marker : markers) definitions.put(marker.id().toString(),
                new RailLineIndex.Definition(marker.line(),marker.directionX(),marker.directionZ()));
        return Map.copyOf(definitions);
    }

    private RailGraph.Node switchNode(SkyTrainSwitchBridge.SwitchSnapshot railwaySwitch) {
        return new RailGraph.Node(railwaySwitch.id().toString(), "switch",
                railwaySwitch.localName().isBlank() ? "SW-" + shortId(railwaySwitch.id())
                        : railwaySwitch.localName(),
                "", null, position(railwaySwitch.pivot()),
                railwaySwitch.ports(), List.of(
                        "common>straight", "common>diverging",
                        "straight>common", "diverging>common"),
                railwaySwitch.effectiveState(), null, null);
    }

    private static RailGraph.Position position(BlockPosition position) {
        return new RailGraph.Position(position.world(), position.x(), position.y(), position.z());
    }

    private static RailGraph.Point point(BlockPosition position, double distanceMeters) {
        return new RailGraph.Point(position.world(), position.x(), position.y(), position.z(), distanceMeters);
    }

    private StcsMarker markerAt(BlockPosition position, UUID excluding) {
        Set<UUID> ids = railIndex.get(position.key());
        if (ids == null) {
            return null;
        }
        return ids.stream().filter(id -> !id.equals(excluding)).sorted()
                .map(markers::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private void schedule(Location location, Runnable action, java.util.function.Consumer<String> rejected) {
        if (!location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            rejected.accept("chunk_not_loaded");
            return;
        }
        try {
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                try {
                    // Scheduling does not guarantee that the chunk is still loaded at execution.
                    RailGeometry.requireAvailable(location);
                    action.run();
                } catch (RailGeometry.ChunkUnavailableException ex) {
                    rejected.accept("chunk_not_loaded");
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "STCS rail scan step failed at " + location, ex);
                    rejected.accept(null);
                }
            });
        } catch (RuntimeException ex) {
            rejected.accept(null);
        }
    }

    private void queueSave() {
        if (shuttingDown || !saveQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                saveNow();
            } finally {
                saveQueued.set(false);
            }
        });
    }

    private void saveNow() {
        synchronized (ioLock) {
            plugin.getDataFolder().mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            for (StcsMarker marker : markers.values()) {
                String path = "markers." + marker.id();
                yaml.set(path + ".type", marker.type().storageName());
                writePosition(yaml, path + ".sign", marker.sign());
                writePosition(yaml, path + ".rail", marker.rail());
                yaml.set(path + ".line", marker.line());
                yaml.set(path + ".name", marker.name());
                yaml.set(path + ".direction-x", marker.directionX());
                yaml.set(path + ".direction-z", marker.directionZ());
            }
            try {
                yaml.save(markerFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save STCS markers", ex);
            }
        }
    }

    private void exportGraphAsync(RailGraph snapshot) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> exportGraphNow(snapshot));
    }

    private void exportGraphNow(RailGraph snapshot) {
        synchronized (ioLock) {
            if (!currentExport(snapshot.revision, graph.revision)) return;
            // Keep the old file until a world scan has actually produced a new revision.
            if (legacyGraphLoaded && snapshot.revision == legacyRevision) return;
            try {
                plugin.getDataFolder().mkdirs();
                File temporary = new File(graphFile.getParentFile(), graphFile.getName() + ".tmp");
                Gson gson = plugin.getConfig().getBoolean("graph.pretty-print", true)
                        ? new GsonBuilder().setPrettyPrinting().serializeNulls().create()
                        : new GsonBuilder().serializeNulls().create();
                try (Writer writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
                    gson.toJson(RailGraphStorage.encode(snapshot), writer);
                }
                if (legacyGraphLoaded && graphFile.isFile()) {
                    File backup = new File(graphFile.getParentFile(), graphFile.getName() + ".legacy.bak");
                    if (!backup.exists()) Files.copy(graphFile.toPath(), backup.toPath());
                }
                try {
                    Files.move(temporary.toPath(), graphFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary.toPath(), graphFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                legacyGraphLoaded = false;
                legacyRevision = -1L;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to export STCS RailGraph", ex);
            }
        }
    }

    private void loadLastGraph() {
        if (!graphFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(graphFile.toPath(), StandardCharsets.UTF_8)) {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[8192];
            for (int n; (n = reader.read(buffer)) != -1;) json.append(buffer, 0, n);
            RailGraphStorage.Decoded decoded = RailGraphStorage.decode(json.toString());
            RailGraph stored = decoded.graph();
            if (stored != null && stored.nodes != null && stored.edges != null && stored.unresolved != null) {
                graph = new RailGraph(stored.revision, stored.maxScanDistanceMeters,
                        stored.blocksPerMeter, stored.nodes, stored.edges, stored.unresolved,
                        stored.lineDefinitions == null ? lineDefinitions(markers.values()) : stored.lineDefinitions);
                buildSequence.accumulateAndGet(stored.revision, Math::max);
                legacyGraphLoaded = decoded.legacy();
                legacyRevision = decoded.legacy() ? stored.revision : -1L;
                scanDiagnostics=graph.unresolved;
                for(var issue:graph.unresolved)if(issue.reason().equals("rail_broken"))
                    for(var edge:graph.edges)if(GraphIncremental.matches(issue,edge))
                        for(var p:edge.path())if(Math.abs(p.distanceMeters()-issue.scannedMeters())<.001)
                            brokenRails.add(new RailGraph.Position(p.world(),p.x(),p.y(),p.z()));
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore the previous STCS RailGraph; it will be rebuilt.", ex);
        }
    }

    private void rebuildIndexes() {
        signIndex.clear();
        railIndex.clear();
        for (StcsMarker marker : markers.values()) {
            addIndexes(marker);
        }
    }

    private void addIndexes(StcsMarker marker) {
        signIndex.put(marker.sign().key(), marker.id());
        railIndex.computeIfAbsent(marker.rail().key(), ignored -> ConcurrentHashMap.newKeySet()).add(marker.id());
    }

    private void removeIndexes(StcsMarker marker) {
        signIndex.remove(marker.sign().key(), marker.id());
        Set<UUID> atRail = railIndex.get(marker.rail().key());
        if (atRail != null) {
            atRail.remove(marker.id());
            if (atRail.isEmpty()) {
                railIndex.remove(marker.rail().key(), atRail);
            }
        }
    }

    private Block nearestRail(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int scan = (int) Math.ceil(radius);
        Block nearest = null;
        double best = radius * radius;
        for (int x = center.getBlockX() - scan; x <= center.getBlockX() + scan; x++) {
            for (int y = center.getBlockY() - scan; y <= center.getBlockY() + scan; y++) {
                for (int z = center.getBlockZ() - scan; z <= center.getBlockZ() + scan; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!(block.getBlockData() instanceof Rail)) {
                        continue;
                    }
                    double distance = square(x + 0.5 - center.getX())
                            + square(y + 0.1 - center.getY()) + square(z + 0.5 - center.getZ());
                    if (distance <= best) {
                        best = distance;
                        nearest = block;
                    }
                }
            }
        }
        return nearest;
    }

    private Vector markerDirection(Block signBlock, Block railBlock, String sideText, MarkerType type) {
        String side = sideText == null ? "" : sideText.trim().toLowerCase(Locale.ROOT);
        if (!side.equals("left") && !side.equals("right")) {
            throw new IllegalArgumentException(type.storageName() + " 第四行必须写 left 或 right。");
        }
        Vector facing = signFacing(signBlock.getBlockData());
        if (facing.lengthSquared() < EPSILON) {
            throw new IllegalArgumentException("无法从牌子朝向确定方向。");
        }
        Vector screenDirection = side.equals("left")
                ? new Vector(-facing.getZ(), 0.0, facing.getX())
                : new Vector(facing.getZ(), 0.0, -facing.getX());
        BlockFace endpoint = RailGeometry.chooseEndpoint(RailGeometry.shape(railBlock), screenDirection);
        if (endpoint == null) {
            throw new IllegalArgumentException("控制牌附近的轨道几何无法确定左右方向。");
        }
        return RailGeometry.direction(endpoint);
    }

    private static Vector signFacing(BlockData data) {
        BlockFace face = data instanceof WallSign wallSign ? wallSign.getFacing()
                : data instanceof Rotatable rotatable ? rotatable.getRotation() : null;
        return face == null ? new Vector() : new Vector(face.getModX(), 0.0, face.getModZ());
    }

    private double maxDistanceMeters() {
        return clamp(plugin.getConfig().getDouble("scan.max-distance-meters", 256.0), 1.0, 100000.0);
    }

    private double blocksPerMeter() {
        return clamp(plugin.getConfig().getDouble("scan.blocks-per-meter", 1.0), 0.01, 100.0);
    }

    double graphBlocksPerMeter() { return graph.blocksPerMeter; }

    private double railSearchRadius() {
        return clamp(plugin.getConfig().getDouble("scan.marker-rail-search-radius", 3.0), 1.0, 6.0);
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() || result.length() > maxLength ? null : result;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static double distanceSquared(Location location, BlockPosition position) {
        return square(location.getX() - (position.x() + 0.5))
                + square(location.getY() - (position.y() + 0.5))
                + square(location.getZ() - (position.z() + 0.5));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String formatPosition(BlockPosition position) {
        return position.world() + ' ' + position.x() + ' ' + position.y() + ' ' + position.z();
    }

    private static String formatMileage(double meters) {
        long kilometres = (long) Math.floor(Math.abs(meters) / 1000.0);
        double remainder = Math.abs(meters) - kilometres * 1000.0;
        return (meters < -0.0005 ? "-" : "") + "K" + kilometres + "+"
                + String.format(Locale.ROOT, "%06.2f", remainder);
    }

    private static BlockPosition readPosition(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world", "").trim();
        return world.isEmpty() ? null : new BlockPosition(world,
                section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    private static void writePosition(YamlConfiguration yaml, String path, BlockPosition position) {
        yaml.set(path + ".world", position.world());
        yaml.set(path + ".x", position.x());
        yaml.set(path + ".y", position.y());
        yaml.set(path + ".z", position.z());
    }

    record Registration(StcsMarker marker, boolean created) {
    }

    private final class GraphBuild {
        private final long id;
        private final AtomicInteger remainingTasks;
        private final List<StcsMarker> markers;
        private final ConcurrentMap<String, List<StcsMarker>> markersByRail = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, SkyTrainSwitchBridge.SwitchSnapshot> switches =
                new ConcurrentHashMap<>();
        private final List<RailGraph.Edge> edges = new CopyOnWriteArrayList<>();
        private final List<RailGraph.Issue> issues = new CopyOnWriteArrayList<>();
        private final Set<String> scanned = ConcurrentHashMap.newKeySet();
        private final RailGraph base;

        private GraphBuild(long id, int taskCount, List<StcsMarker> markers,
                List<SkyTrainSwitchBridge.SwitchSnapshot> switchSnapshots) {
            this.id = id;
            this.base=graph;
            this.remainingTasks = new AtomicInteger(taskCount);
            this.markers = markers;
            for (StcsMarker marker : markers) {
                markersByRail.computeIfAbsent(marker.rail().key(), ignored -> new CopyOnWriteArrayList<>())
                        .add(marker);
            }
            for (SkyTrainSwitchBridge.SwitchSnapshot railwaySwitch : switchSnapshots) {
                switches.put(railwaySwitch.pivot().key(), railwaySwitch);
            }
        }

        private boolean active() {
            return !shuttingDown && buildSequence.get() == id;
        }

        private void taskDone() {
            if (remainingTasks.decrementAndGet() == 0) {
                publish(this);
            }
        }

        private SkyTrainSwitchBridge.SwitchSnapshot switchAt(BlockPosition position) {
            return switches.get(position.key());
        }

        private StcsMarker markerAt(BlockPosition position, UUID excluding) {
            return markersByRail.getOrDefault(position.key(), List.of()).stream()
                    .filter(marker -> excluding == null || !marker.id().equals(excluding))
                    .sorted(Comparator.comparing(StcsMarker::id)).findFirst().orElse(null);
        }

        private boolean addDiscoveredSwitch(SkyTrainSwitchBridge.SwitchSnapshot railwaySwitch) {
            if (switches.putIfAbsent(railwaySwitch.pivot().key(), railwaySwitch) != null) {
                return false;
            }
            remainingTasks.incrementAndGet();
            scanned.add(railwaySwitch.id().toString());
            return true;
        }

        private void issue(StcsMarker source, String port, String reason, double distance) {
            issue(source.id().toString(), port, reason, distance);
        }

        private void issue(String sourceId, String port, String reason, double distance) {
            issues.add(new RailGraph.Issue(sourceId, port.toLowerCase(Locale.ROOT), reason, distance));
        }
    }
}
