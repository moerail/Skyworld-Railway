package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import org.bukkit.plugin.ServicePriority;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.skyworld.sta.api.v5.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

/** Observational model only. M1 durable evidence is never cleared or promoted by this adapter. */
final class ShadowRuntime implements ShadowAuthorityService, SwitchControlService, AutoCloseable {
    private final StcsPlugin plugin;
    private final RailGraphManager manager;
    private final java.util.function.Supplier<Inputs> inputSource;
    interface PointControl {
        CompletionStage<String> change(Request request, java.util.function.BooleanSupplier guard, java.util.function.Consumer<String> progress);
    }
    private final PointControl pointControl;
    private final Map<UUID,Set<String>> maPoints=new HashMap<>();
    private final Map<UUID,Request> pendingPoints=new HashMap<>();
    private final Map<UUID,String> switchProgress=new HashMap<>();
    private final UUID session=UUID.randomUUID();
    private final Map<UUID,Set<String>> occupied=new LinkedHashMap<>(), reserved=new LinkedHashMap<>();
    private final Map<UUID,ShadowOccupancyStore.TrainRecord> retained=new LinkedHashMap<>();
    private final Map<UUID,Intent> intents=new LinkedHashMap<>();
    private final Map<UUID,String> reasons=new HashMap<>();
    private final Map<UUID,Boolean> reversed=new HashMap<>();
    private final Set<UUID> uncertain=new HashSet<>();
    private final Map<UUID,Set<String>> occupiedPoints=new HashMap<>();
    private final LinkedHashMap<UUID,CompletableFuture<Reply>> switchResults=new LinkedHashMap<>();
    private final Map<UUID,Request> switchRequests=new HashMap<>();
    private final Path file;
    private ScheduledTask task;
    private final MaSettings settings;
    private ShadowGraph model;
    private long sequence;
    private String persisted="";
    private long lastPersistedAt;
    private Map<UUID,Set<String>> persistedResources=Map.of();
    private Set<UUID> persistedOutside=Set.of();
    private boolean closed,failed,unbounded=true;
    private volatile Snapshot latest;
    private volatile Diagnostics diagnostics;
    private final NodePassageMonitor integrity = new NodePassageMonitor();
    private volatile String integrityFailure;
    List<NodePassageMonitor.View> integritySnapshot() { return integrity.snapshot(); }
    String integrityFailure() { return integrityFailure; }
    record Intent(UUID driver,UUID lease,UUID provider) {}
    record MaAction(UUID train, String trainName, UUID driver, String driverName,
            String action, String state, String reason) {}
    java.util.function.Consumer<MaAction> eventSink = event -> {};
    record SoundNotice(UUID driver, UUID lease, String cue) {}
    java.util.function.Consumer<SoundNotice> soundSink = notice -> {};
    private final MaSoundTracker soundTracker = new MaSoundTracker();
    private double soundJumpMeters = 20;
    private double soundLowMeters = 50;
    private long soundCooldownMillis = 1500;
    record Blocker(UUID train,String name,String issue,int resources) {}
    record Coverage(UUID train,String name,String state,int resources,List<ConsistObservation.Member> positions) {
        Coverage { positions=List.copyOf(positions); }
    }
    record Diagnostics(Snapshot snapshot,boolean sourceAvailable,List<Blocker> blockers,
            Map<UUID,UUID> drivenTrains,Map<UUID,String> trainNames,List<Coverage> coverage) {
        Diagnostics {
            blockers=List.copyOf(blockers);drivenTrains=Map.copyOf(drivenTrains);trainNames=Map.copyOf(trainNames);coverage=List.copyOf(coverage);
        }
    }
    record Inputs(RailGraph graph, Map<String,String> switchStates, boolean available, UUID driverSession, UUID rosterSession,
            Collection<DriverDeskService.Desk> desks, Collection<ConsistObservation> roster, Collection<StaMessage> reports) {}
    ShadowRuntime(StcsPlugin plugin,RailGraphManager manager) throws java.io.IOException {
        this(plugin, manager, plugin.getDataFolder().toPath().resolve("shadow-occupancy.json"),
                MaSettings.load(plugin.getConfig()),
                () -> inputs(plugin, manager), (request,guard,progress) -> {
                    try {
                        var stf=plugin.getServer().getPluginManager().getPlugin("SkyTrainFolia");
                        @SuppressWarnings("unchecked") var future=(CompletionStage<String>)stf.getClass()
                                .getMethod("changeSwitchById",String.class,String.class,String.class,String.class,int.class,int.class,int.class,
                                        java.util.function.BooleanSupplier.class,java.util.function.Consumer.class)
                                .invoke(stf,request.switchId().toString(),request.expectedState(),request.targetState(),request.position().world(),
                                        request.position().x(),request.position().y(),request.position().z(),guard,progress);
                        return future;
                    } catch(ReflectiveOperationException|RuntimeException ex) {return CompletableFuture.completedFuture("FAILED_INCOMPATIBLE_STF");}
                });
        plugin.getLogger().info("Shadow MA distances (metres): look-ahead=" + settings.lookAheadMeters()
                + ", point-lock eligibility=" + settings.lockDistanceMeters()
                + ", max-authority=" + settings.maxAuthorityDistanceMeters() + ", margin=" + settings.marginMeters()
                + "; no ATP or physical point interlocking.");
        if (plugin.getConfig().contains("ma.horizon-meters", true))
            plugin.getLogger().info("Legacy ma.horizon-meters supplies missing distance settings; explicit new keys take priority.");
        var services=plugin.getServer().getServicesManager();
        double jump = plugin.getConfig().getDouble("ma.sound.jump-threshold-meters", 20);
        soundJumpMeters = Double.isFinite(jump) && jump >= 1 ? jump : 20;
        double low = plugin.getConfig().getDouble("ma.sound.low-remaining-meters", 50);
        soundLowMeters = Double.isFinite(low) && low >= 0 ? low : 50;
        soundCooldownMillis = Math.max(0, plugin.getConfig().getLong("ma.sound.cooldown-ms", 1500));
        soundSink = notice -> {
            if (!plugin.getConfig().getBoolean("ma.sound.enabled", true)) return;
            try {
                var stf = plugin.getServer().getPluginManager().getPlugin("SkyTrainFolia");
                if (stf != null && stf.isEnabled()) stf.getClass().getMethod("playMaNotice", UUID.class, UUID.class, String.class)
                        .invoke(stf, notice.driver(), notice.lease(), notice.cue());
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* Optional audio cannot stop MA. */ }
        };
        eventSink = event -> {
            var events = services.load(RailwayEventService.class);
            if (events != null) events.publishDetailed("STCS",
                    event.action().equals("release") ? RailwayEvent.Type.MA_RELEASED : RailwayEvent.Type.MA_REQUESTED,
                    event.train(), event.trainName(), event.driver(), event.driverName(), event.reason(),
                    Map.of("state", event.state(), "simulationOnly", "true", "executable", "false"));
        };
        services.register(ShadowAuthorityService.class,this,plugin,ServicePriority.Normal);
        services.register(SwitchControlService.class,this,plugin,ServicePriority.Normal);
        task=plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,t->tick(),250,250,TimeUnit.MILLISECONDS);
    }
    ShadowRuntime(StcsPlugin plugin, RailGraphManager manager, Path file, double horizon, double margin,
            java.util.function.Supplier<Inputs> source, java.util.function.Function<Request,CompletionStage<String>> control) throws java.io.IOException {
        this(plugin, manager, file, MaSettings.legacy(horizon, margin), source, control);
    }
    ShadowRuntime(StcsPlugin plugin, RailGraphManager manager, Path file, MaSettings settings,
            java.util.function.Supplier<Inputs> source, java.util.function.Function<Request,CompletionStage<String>> control) throws java.io.IOException {
        this(plugin,manager,file,settings,source,(request,guard,progress)->control.apply(request));
    }
    ShadowRuntime(StcsPlugin plugin, RailGraphManager manager, Path file, MaSettings settings,
            java.util.function.Supplier<Inputs> source, PointControl control) throws java.io.IOException {
        this.plugin=plugin;this.manager=manager;this.file=file;this.inputSource=source;this.pointControl=control;
        this.settings=Objects.requireNonNull(settings);
        retained.putAll(ShadowOccupancyStore.load(file));
        retained.forEach((id,record)->{occupied.put(id,record.resources());uncertain.add(id);});
        latest=new Snapshot(5,true,false,session,0,System.currentTimeMillis(),0,"STARTING",List.of(),List.of());
        diagnostics=new Diagnostics(latest,false,List.of(),Map.of(),Map.of(),List.of());
    }
    private static Inputs inputs(StcsPlugin plugin,RailGraphManager manager) {
        var services=plugin.getServer().getServicesManager();
        var tracking=services.load(TrackingService.class);var roster=services.load(ConsistObservationService.class);
        var desks=services.load(DriverDeskService.class);
        boolean available=tracking!=null&&tracking.sourceAvailable()&&roster!=null&&desks!=null;
        return new Inputs(manager.occupancyGraph(),manager.shadowSwitchStates(),available,
                desks==null?null:desks.sessionId(),roster==null?null:roster.sessionId(),
                desks==null?List.of():List.copyOf(desks.driverDesks()),
                roster==null?List.of():List.copyOf(roster.consistObservations()),
                tracking==null?List.of():List.copyOf(tracking.snapshots()));
    }
    public Snapshot snapshot() { return latest; }
    Diagnostics diagnostics() { return diagnostics; }
    synchronized String command(UUID driver,String action) {
        return command(driver, action, "");
    }
    synchronized String command(UUID driver,String action,String driverName) {
        tick();
        if(closed||failed) return "UNAVAILABLE";
        var input=inputSource.get();
        if(input.driverSession()==null) return "NO_PROVIDER";
        var matches=input.desks().stream().filter(d->d.driverId().equals(driver)).toList();
        if(matches.size()!=1) return "DECLARE_DRIVE";
        var desk=matches.getFirst(); UUID train=desk.trainId();
        if(action.equals("release")) {
            soundTracker.reset(train);
            intents.remove(train);reserved.remove(train);maPoints.remove(train);reversed.remove(train);reasons.put(train,"RELEASED");
        } else if(action.equals("request")) {
            if(Set.of("ISOLATED","RECOVERING").contains(desk.atpMode())) return desk.atpMode();
            if (!intents.containsKey(train)) { reversed.remove(train); soundTracker.reset(train); }
            intents.put(train,new Intent(driver,desk.leaseId(),input.driverSession()));
            reasons.put(train,"REQUESTED");
        } else return "INVALID";
        tick();
        var authority = latest.authorities().stream().filter(a -> a.trainId().equals(train)).findFirst().orElse(null);
        if (action.equals("release")) sound(new SoundNotice(driver, desk.leaseId(), "RELEASED"));
        try {
            eventSink.accept(new MaAction(train, diagnostics.trainNames().getOrDefault(train, train.toString()),
                    driver, driverName, action, authority == null ? "INACTIVE" : authority.state(),
                    action.equals("release") ? "RELEASED" : authority == null ? "UNAVAILABLE" : authority.reason()));
        } catch (RuntimeException ex) {
            if (plugin != null) plugin.getLogger().warning("MA action event delivery failed: " + ex.getClass().getSimpleName());
        }
        return action.equals("release")?"RELEASED":"REQUESTED";
    }
    synchronized void tick() {
        if(closed||failed)return;
        long now=System.currentTimeMillis();
        try {
            var input=inputSource.get();
            // Producers can publish while the immutable inputs are being gathered.
            now=System.currentTimeMillis();
            RailGraph graph=input.graph();
            if(model==null||model.graph!=graph) {
                model=new ShadowGraph(graph);reserved.clear();maPoints.clear();intents.clear();reversed.clear();
                occupied.keySet().forEach(id->{uncertain.add(id);reasons.put(id,"GRAPH_CHANGED");});
                // A previously uncovered parked train can become covered after a graph expansion.
                // Re-map saved bodies conservatively, without loading chunks or clearing old resources.
                retained.forEach((id,record)->{
                    Set<String> cells=new HashSet<>(occupied.getOrDefault(id,Set.of()));
                    cells.addAll(savedBodyResources(record.positions()));
                    occupied.put(id,Set.copyOf(cells));
                });
            }
            boolean available=input.available();
            if (integrityFailure==null) try {
                integrity.update(model,available && Objects.equals(input.driverSession(),input.rosterSession()),
                        input.rosterSession(),input.roster(),input.switchStates(),now);
            } catch (RuntimeException ex) {
                integrityFailure=ex.getClass().getSimpleName();
                if (plugin!=null) plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Read-only integrity monitor stopped; existing occupancy/MA logic unchanged",ex);
            }
            Map<UUID,StaMessage> reports=new HashMap<>(); Map<UUID,DriverDeskService.Desk> drivers=new HashMap<>();
            if(available) {
                for(var m:input.reports()) if(m.header().kind()==StaMessage.Kind.TRACK_REPORT) reports.put(m.header().trainId(),m);
                for(var d:input.desks()) drivers.put(d.trainId(),d);
            }
            uncertain.addAll(occupied.keySet());
            unbounded=!available;
            if(available) for(var train:input.roster()) {
                if (train.session().equals(input.rosterSession()) && train.session().equals(input.driverSession())
                        && fresh(train.sampledAtMillis(), now) && train.confirmedDestruction()
                        && retained.getOrDefault(train.train(), ShadowOccupancyStore.TrainRecord.legacy(Set.of()))
                                .positions().stream().allMatch(p -> train.expectedMembers().contains(p.id()))
                        && input.roster().stream().noneMatch(other -> other.train().equals(train.train()) && !other.removed())
                        && !drivers.containsKey(train.train()) && !reports.containsKey(train.train())) {
                    clearRetained(train.train());
                    continue;
                }
                Set<String> cells=new HashSet<>();
                boolean trusted=train.session().equals(input.rosterSession()) && train.session().equals(input.driverSession())
                        && !train.removed() && fresh(train.sampledAtMillis(),now);
                var old=retained.getOrDefault(train.train(),ShadowOccupancyStore.TrainRecord.legacy(Set.of()));
                if(!trusted) {
                    occupied.putIfAbsent(train.train(),Set.of());continue;
                }
                boolean physicalComplete=!train.expectedMembers().isEmpty()
                        && train.expectedMembers().size()==train.members().size() && fresh(train.sampledAtMillis(),now);
                boolean complete=physicalComplete;
                Map<UUID,ConsistObservation.Member> byId=new HashMap<>();
                long earliest=Long.MAX_VALUE, last=0;
                for(var member:train.members()) {
                    byId.put(member.id(),member);
                    var near=model.near(member.world(),member.x(),member.y(),member.z());cells.addAll(near);
                    physicalComplete &= member.state()==ConsistObservation.State.OBSERVED && fresh(member.observedAtMillis(),now);
                    complete &= !near.isEmpty() && member.state()==ConsistObservation.State.OBSERVED && fresh(member.observedAtMillis(),now);
                    earliest=Math.min(earliest,member.observedAtMillis());last=Math.max(last,member.observedAtMillis());
                }
                complete &= last-earliest<=250;
                physicalComplete &= last-earliest<=250;
                // Conservative interpolation between adjacent observed centres; not a certified swept envelope.
                ConsistObservation.Member previous=null;
                for(UUID id:train.expectedMembers()) {
                    var member=byId.get(id);
                    if(member==null) {complete=false;physicalComplete=false;continue;}
                    if(previous!=null) {
                        double d=Math.sqrt(Math.pow(member.x()-previous.x(),2)+Math.pow(member.y()-previous.y(),2)+Math.pow(member.z()-previous.z(),2));
                        if(!member.world().equals(previous.world())||d>12) {complete=false;physicalComplete=false;}
                        else for(int i=0,n=Math.max(1,(int)Math.ceil(d*4));i<=n;i++) {
                            var near=model.near(member.world(),previous.x()+(member.x()-previous.x())*i/n,
                                    previous.y()+(member.y()-previous.y())*i/n,previous.z()+(member.z()-previous.z())*i/n);
                            if(near.isEmpty())complete=false;cells.addAll(near);
                        }
                    }
                    previous=member;
                }
                Set<String> previousResources=occupied.getOrDefault(train.train(),Set.of());
                // Only a complete fresh body outside an unchanged coverage model proves a shadow exit.
                // A missing/changed graph, missing member or an unload must not erase covered occupancy.
                boolean outside=physicalComplete && cells.isEmpty() && (previousResources.isEmpty()
                        || (old.graphRevision()==graph.revision && model.footprints.keySet().containsAll(previousResources)));
                if(complete) {
                    occupied.put(train.train(),Set.copyOf(cells));uncertain.remove(train.train());
                    Set<String> blocked=new HashSet<>();
                    for(var node:model.nodes.values()) if("switch".equals(node.type())&&node.rail()!=null) {
                        var p=node.rail(); ConsistObservation.Member a=null;
                        for(UUID memberId:train.expectedMembers()) {
                            var b=byId.get(memberId);
                            if(pointNear(p,a==null?b:a,b))blocked.add(node.id());
                            a=b;
                        }
                    }
                    occupiedPoints.put(train.train(),Set.copyOf(blocked));
                }
                else if(outside) {
                    occupied.put(train.train(),Set.of());uncertain.add(train.train());occupiedPoints.remove(train.train());
                    reserved.remove(train.train());maPoints.remove(train.train());
                } else {cells.addAll(previousResources);occupied.put(train.train(),Set.copyOf(cells));uncertain.add(train.train());}
                Map<UUID,ConsistObservation.Member> positions=new LinkedHashMap<>();
                if(!physicalComplete) old.positions().forEach(p->positions.put(p.id(),p));
                for(UUID memberId:train.expectedMembers()) {
                    var member=byId.get(memberId);
                    if(member==null)continue;
                    if(member.state()==ConsistObservation.State.OBSERVED && fresh(member.observedAtMillis(),now))
                        positions.put(member.id(),member);
                }
                boolean keepOutside=occupied.get(train.train()).isEmpty() && (outside || old.outsideConfirmed());
                retained.put(train.train(),new ShadowOccupancyStore.TrainRecord(train.name(),occupied.get(train.train()),
                        complete || outside?graph.revision:old.graphRevision(),physicalComplete?train.session():null,
                        List.copyOf(positions.values()),keepOutside));
            }
            Map<UUID,ConsistObservation> observed=new HashMap<>();
            Map<UUID,String> names=new HashMap<>();
            retained.forEach((id,record)->names.put(id,record.name()));
            for(var train:input.roster()) {observed.put(train.train(),train);names.put(train.train(),train.name());}
            List<Blocker> blockers=new ArrayList<>();
            // Empty records are outside this model's admitted occupancy, not obstacles everywhere.
            // Non-empty evidence whose resources disappeared is still unsafe to discard.
            for(var entry:occupied.entrySet()) if(!entry.getValue().isEmpty()&&!model.footprints.keySet().containsAll(entry.getValue())) {
                unbounded=true;
                blockers.add(new Blocker(entry.getKey(),names.getOrDefault(entry.getKey(),"--"),"RESOURCES_NOT_IN_GRAPH",entry.getValue().size()));
            }
            List<Coverage> coverage=new ArrayList<>();
            for(var entry:occupied.entrySet()) {
                UUID id=entry.getKey();var record=retained.getOrDefault(id,ShadowOccupancyStore.TrainRecord.legacy(entry.getValue()));
                String state=!entry.getValue().isEmpty() ? !model.footprints.keySet().containsAll(entry.getValue()) ? "GRAPH_MISMATCH"
                        : uncertain.contains(id) ? "FROZEN_IN_COVERAGE" : "LIVE_IN_COVERAGE"
                        : record.outsideConfirmed() ? "OUTSIDE_COVERAGE" : observed.containsKey(id) ? "AWAITING_COVERAGE" : "ARCHIVED_UNLOCATED";
                coverage.add(new Coverage(id,names.getOrDefault(id,"--"),state,entry.getValue().size(),record.positions()));
            }
            List<Authority> authorities=new ArrayList<>();
            Map<String,String> switchStates=new HashMap<>(input.switchStates());
            pendingPoints.keySet().forEach(id->switchStates.put(id.toString(),"pending"));
            Set<String> pendingCrossings=new HashSet<>();
            for(UUID id:pendingPoints.keySet()) {
                var node=model.nodes.get(id.toString());if(node==null||node.rail()==null)continue;
                var p=node.rail();var cells=model.cellResources.getOrDefault(ShadowGraph.cell(p.world(),p.x(),p.y(),p.z()),Set.of());
                for(var edge:model.edges.values()) if(!edge.from().equals(node.id())&&!edge.to().equals(node.id())
                        && cells.contains(model.resource.get(edge.id()))) pendingCrossings.add(model.resource.get(edge.id()));
            }
            Set<UUID> trainIds=new LinkedHashSet<>(intents.keySet());trainIds.addAll(occupied.keySet());
            for(UUID id:trainIds) {
                var m=reports.get(id);var desk=drivers.get(id);var intent=intents.get(id);
                String reason=reasons.getOrDefault(id,"IDLE");
                boolean eligible=intent!=null;
                if(intent!=null && (desk==null||!intent.lease().equals(desk.leaseId())||!intent.driver().equals(desk.driverId())
                        || !intent.provider().equals(input.driverSession()))) {
                    reason="NO_DRIVER";intents.remove(id);eligible=false;
                }
                if(desk!=null&&Set.of("ISOLATED","RECOVERING").contains(desk.atpMode())) {
                    reason=desk.atpMode();intents.remove(id);eligible=false;
                }
                boolean provenance=m!=null&&m.tracking().graphRevision()==graph.revision
                        && fresh(m.physical().state().observedAtMillis(),now)&&fresh(m.header().emittedAtMillis(),now)
                        && m.tracking().telemetrySessionId().equals(input.driverSession())
                        && Math.abs(m.physical().blocksPerMeter()-graph.blocksPerMeter)<1e-6;
                ShadowGraph.Start position=null;
                if(provenance) {
                    if(m.tracking().quality()==StaMessage.Quality.VALID) {
                        var pos=m.tracking().position();
                        position=new ShadowGraph.Start(pos.edgeId(),pos.edgeOffsetMeters());
                    } else if(m.tracking().quality()==StaMessage.Quality.UNLOCATED) {
                        var p=m.physical().state();
                        position=model.derivedStart(p.world(),p.railX(),p.railY(),p.railZ(),p.motionX(),p.motionY(),p.motionZ());
                    }
                }
                boolean located=position!=null;
                if(eligible&&located) {
                    boolean reverse=m.physical().state().reversed();
                    if(reversed.containsKey(id)&&reversed.get(id)!=reverse) {reason="DIRECTION_CHANGED";intents.remove(id);eligible=false;}
                    reversed.put(id,reverse);
                }
                if(eligible&&occupied.getOrDefault(id,Set.of()).isEmpty()) {
                    reason=retained.containsKey(id)&&retained.get(id).outsideConfirmed()?"OUTSIDE_COVERAGE":"AWAITING_COVERAGE";eligible=false;
                }
                if(eligible&&(!located||uncertain.contains(id)||unbounded)) {reason=unbounded?"FLEET_UNCERTAIN":"POSITION_UNCERTAIN";eligible=false;}
                if(eligible) {
                    Set<String> obstacles=new HashSet<>();
                    obstacles.addAll(pendingCrossings);
                    occupied.forEach((other,rs)->{if(!other.equals(id))obstacles.addAll(rs);});
                    reserved.forEach((other,rs)->{if(!other.equals(id))obstacles.addAll(rs);});
                    var result=ShadowPlanner.plan(model,position.edge(),position.offset(),m.physical().state().lengthMeters()+1/graph.blocksPerMeter,
                            settings,switchStates,obstacles);
                    reserved.put(id,result.reserved());reason=result.reason();
                    maPoints.put(id,pointsInPath(model,result.path()));
                    boolean granted = result.remaining()!=null && result.remaining()>0 && !result.path().isEmpty();
                    authorities.add(new Authority(id,desk.leaseId(),m.tracking().telemetrySessionId(),m.physical().state().sequence(),
                            granted ? "ALLOCATED_SHADOW" : "WAITING", reason,
                            granted ? result.path() : List.of(), granted ? result.edge() : null,
                            granted ? result.offset() : null, granted ? result.remaining() : null));
                } else authorities.add(new Authority(id,desk==null?null:desk.leaseId(),m==null?null:m.tracking().telemetrySessionId(),
                        m==null?0:m.physical().state().sequence(),intents.containsKey(id)?"WAITING":"INACTIVE",reason,List.of(),null,null,null));
                reasons.put(id,reason);
            }
            List<Section> sections=ShadowSections.build(model,occupied,reserved,uncertain);
            persist(false);
            latest=new Snapshot(5,true,false,session,++sequence,now,graph.revision,available?"SHADOW":"SOURCE_UNAVAILABLE",authorities,sections);
            Map<UUID,UUID> driven=new HashMap<>();drivers.values().forEach(d->driven.put(d.driverId(),d.trainId()));
            diagnostics=new Diagnostics(latest,available,blockers,driven,names,coverage);
            soundTracker.retain(intents.keySet());
            for (var authority : authorities) {
                var intent = intents.get(authority.trainId());
                if (intent == null) continue;
                String cue = soundTracker.update(authority, now, soundJumpMeters, soundCooldownMillis, soundLowMeters);
                if (!cue.isEmpty()) sound(new SoundNotice(intent.driver(), intent.lease(), cue));
            }
            if(plugin!=null)plugin.notifyPccUpdate();
        } catch(Exception|LinkageError ex) {
            failed=true;
            latest=new Snapshot(5,true,false,session,++sequence,now,model==null?0:model.graph.revision,"FAILED",List.of(),latest.sections());
            diagnostics=new Diagnostics(latest,false,diagnostics.blockers(),Map.of(),diagnostics.trainNames(),diagnostics.coverage());
            if(plugin!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Shadow MA stopped; no ATP action. Retained occupancy not cleared.",ex);
            else throw new IllegalStateException("Shadow runtime failed",ex);
        }
    }
    private static boolean fresh(long at,long now) {return at<=now&&now-at<=1500;}
    private void sound(SoundNotice notice) {
        try { soundSink.accept(notice); } catch (RuntimeException | LinkageError ignored) { /* Audio is not control. */ }
    }
    static Set<String> pointsInPath(ShadowGraph graph,List<PathPart> path) {
        Set<String> points=new HashSet<>();
        Map<String,Set<String>> pointCells=new HashMap<>();
        for(var node:graph.nodes.values()) if("switch".equals(node.type()) && node.rail()!=null) {
            var p=node.rail();pointCells.computeIfAbsent(ShadowGraph.cell(p.world(),p.x(),p.y(),p.z()),k->new HashSet<>()).add(node.id());
        }
        for(var part:path) {
            var edge=graph.edges.get(part.edgeId());if(edge==null)continue;
            if("switch".equals(graph.nodes.get(edge.to()).type()) && part.toMeters()>=edge.distanceMeters()-1e-6)
                points.add(edge.to());
            if("switch".equals(graph.nodes.get(edge.from()).type()) && part.fromMeters()<=1e-6 && part.toMeters()>0)
                points.add(edge.from());
            // Clip to the actual MA, not the entire reserved edge. Also catch geometric crossings.
            for(int i=1;i<edge.path().size();i++) {
                var a=edge.path().get(i-1);var b=edge.path().get(i);
                double from=Math.max(part.fromMeters(),a.distanceMeters()),to=Math.min(part.toMeters(),b.distanceMeters());
                if(to<from)continue;
                double span=b.distanceMeters()-a.distanceMeters();
                int steps=Math.max(1,(int)Math.ceil(ShadowGraph.distance(a,b)*(to-from)/span*2));
                for(int j=0;j<=steps;j++) {
                    double t=(from+(to-from)*j/steps-a.distanceMeters())/span;
                    String cell=ShadowGraph.cell(a.world(),(long)Math.rint(a.x()+(b.x()-a.x())*t),
                            (long)Math.rint(a.y()+(b.y()-a.y())*t),(long)Math.rint(a.z()+(b.z()-a.z())*t));
                    points.addAll(pointCells.getOrDefault(cell,Set.of()));
                }
            }
        }
        return Set.copyOf(points);
    }
    private Set<String> savedBodyResources(List<ConsistObservation.Member> positions) {
        Set<String> resources=new HashSet<>();
        for(int i=0;i<positions.size();i++) {
            var a=positions.get(i);resources.addAll(model.near(a.world(),a.x(),a.y(),a.z()));
            // Partial samples may have different timestamps/order. Nearby pairs deliberately over-cover.
            for(int j=0;j<i;j++) {
                var b=positions.get(j);
                double d=Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));
                if(!a.world().equals(b.world())||d>12)continue;
                for(int k=0,n=Math.max(1,(int)Math.ceil(d*4));k<=n;k++)
                    resources.addAll(model.near(a.world(),b.x()+(a.x()-b.x())*k/n,
                            b.y()+(a.y()-b.y())*k/n,b.z()+(a.z()-b.z())*k/n));
            }
        }
        return resources;
    }
    static boolean pointNear(RailGraph.Position point,ConsistObservation.Member a,ConsistObservation.Member b) {
        if(!point.world().equals(a.world())||!a.world().equals(b.world()))return false;
        double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),length2=dx*dx+dy*dy+dz*dz;
        double px=point.x()+.5-a.x(),py=point.y()-a.y(),pz=point.z()+.5-a.z();
        double t=length2==0?0:Math.max(0,Math.min(1,(px*dx+py*dy+pz*dz)/length2));
        return Math.pow(px-t*dx,2)+Math.pow(py-t*dy,2)+Math.pow(pz-t*dz,2)<=1.5625;
    }
    private void persist(boolean force) throws java.io.IOException {
        Map<UUID,ShadowOccupancyStore.TrainRecord> records=new LinkedHashMap<>();
        occupied.forEach((id,resources)->records.put(id,retained.getOrDefault(id,ShadowOccupancyStore.TrainRecord.legacy(resources)).withResources(resources)));
        Set<UUID> outside=new HashSet<>();
        records.forEach((id,record)->{if(record.outsideConfirmed())outside.add(id);});
        long now=System.currentTimeMillis();
        if(!force && occupied.equals(persistedResources) && outside.equals(persistedOutside) && now-lastPersistedAt<1000) return;
        String json=ShadowOccupancyStore.encode(records);
        if(json.equals(persisted))return;
        ShadowOccupancyStore.save(file,json);
        persisted=json;lastPersistedAt=now;persistedResources=Map.copyOf(occupied);persistedOutside=Set.copyOf(outside);
    }

    private void clearRetained(UUID id) {
        occupied.remove(id); retained.remove(id); reserved.remove(id); intents.remove(id);
        maPoints.remove(id); occupiedPoints.remove(id); uncertain.remove(id);
        reversed.remove(id); reasons.remove(id); soundTracker.reset(id);
    }

    synchronized String clearArchived(UUID id, String actor) {
        tick();
        var input = inputSource.get();
        if (closed || failed || !input.available() || input.rosterSession()==null
                || !input.rosterSession().equals(input.driverSession())) return "SOURCE_UNAVAILABLE";
        if (input.roster().stream().anyMatch(t -> t.train().equals(id) && !t.removed())
                || input.desks().stream().anyMatch(d -> d.trainId().equals(id))
                || input.reports().stream().anyMatch(m -> m.header().trainId().equals(id)
                        && m.header().kind()==StaMessage.Kind.TRACK_REPORT)) return "TRAIN_STILL_REPORTED";
        if (!occupied.containsKey(id)) return "NOT_FOUND";
        try {
            persist(true);
            // Backup and append-only operator audit must succeed before discarding evidence.
            var backup = file.resolveSibling(file.getFileName()+".clear-"+UUID.randomUUID()+".bak");
            Files.copy(file, backup);
            Files.writeString(file.resolveSibling("shadow-clearance-audit.log"),
                    java.time.Instant.now()+" actor="+actor+" train="+id+" backup="+backup.getFileName()+"\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            clearRetained(id);
            persist(true);
            tick();
            return "CLEARED_SHADOW_ONLY";
        } catch (java.io.IOException ex) {
            failed=true;
            if (plugin!=null) plugin.getLogger().log(java.util.logging.Level.SEVERE,"Shadow clearance failed; MA disabled",ex);
            return "FAILED_MA_DISABLED";
        }
    }
    public synchronized CompletionStage<Reply> change(Request request) {
        if(switchResults.containsKey(request.requestId())) return request.equals(switchRequests.get(request.requestId()))
                ? switchResults.get(request.requestId()) : CompletableFuture.completedFuture(new Reply(request.requestId(),"REJECTED","ID_CONFLICT"));
        CompletableFuture<Reply> answer=new CompletableFuture<>();
        if(switchResults.size()>=256) {
            UUID old=switchResults.entrySet().stream().filter(e->e.getValue().isDone()).map(Map.Entry::getKey).findFirst().orElse(null);
            if(old==null)return CompletableFuture.completedFuture(new Reply(request.requestId(),"REJECTED","BUSY"));
            switchResults.remove(old);switchRequests.remove(old);switchProgress.remove(old);
        }
        switchResults.put(request.requestId(),answer);
        switchRequests.put(request.requestId(),request);
        tick();
        String rejection=pointRejection(request);
        if(rejection!=null) {answer.complete(new Reply(request.requestId(),"REJECTED",rejection));return answer;}
        pendingPoints.put(request.switchId(),request);switchProgress.put(request.requestId(),"QUEUED");
        try {
            var future=pointControl.change(request,()->pointGuard(request),stage->progress(request,stage));
            future.whenComplete((status,error)->finishPoint(request,answer,error==null?status:"FAILED_STF_ERROR"));
        } catch(RuntimeException ex) {finishPoint(request,answer,"FAILED_INCOMPATIBLE_STF");}
        return answer;
    }
    private synchronized void progress(Request request,String stage) {
        if(request.equals(pendingPoints.get(request.switchId()))) switchProgress.put(request.requestId(),stage);
    }
    public synchronized Reply status(UUID id) {
        var result=switchResults.get(id);
        return result==null?null:result.isDone()?result.getNow(null):new Reply(id,"PENDING",switchProgress.getOrDefault(id,"QUEUED"));
    }
    private synchronized boolean pointGuard(Request request) {
        String rejection=pointRejection(request);
        if(rejection!=null) switchProgress.put(request.requestId(),rejection);
        return request.equals(pendingPoints.get(request.switchId())) && rejection==null;
    }
    private synchronized void finishPoint(Request request,CompletableFuture<Reply> result,String status) {
        pendingPoints.remove(request.switchId(),request);
        String reason=Objects.requireNonNullElse(status,"FAILED_STF_ERROR");
        if(reason.equals("REJECTED_REVALIDATION")) reason=switchProgress.getOrDefault(request.requestId(),reason);
        String category=reason.equals("COMPLETED")?"COMPLETED":Objects.requireNonNullElse(status,"").startsWith("REJECTED")?"REJECTED"
                :reason.equals("EXPIRED")?"EXPIRED":reason.equals("UNCONFIRMED")?"UNCONFIRMED":"FAILED";
        result.complete(new Reply(request.requestId(),category,reason));
    }
    private String pointRejection(Request request) {
        if(closed||failed||model==null||!diagnostics.sourceAvailable()||System.currentTimeMillis()-latest.emittedAtMillis()>1500
                ||request.graphRevision()!=model.graph.revision) return "UNAVAILABLE_OR_GRAPH_CHANGED";
        var pending=pendingPoints.get(request.switchId());
        if(pending!=null&&!pending.equals(request)) return "POINT_BUSY";
        if(pending==null&&pendingPoints.size()>=8) return "BUSY";
        var node=model.nodes.get(request.switchId().toString());
        if(node==null||!"switch".equals(node.type())||node.rail()==null) return "NOT_SWITCH";
        var p=node.rail();var position=request.position();
        if(position==null) return "POSITION_REQUIRED";
        if(!position.world().equals(p.world())||position.x()!=p.x()||position.y()!=p.y()||position.z()!=p.z()) return "POSITION_MISMATCH";
        if(maPoints.values().stream().anyMatch(points->points.contains(node.id()))) return "MA_CONFLICT";
        Set<String> local=model.near(p.world(),p.x()+.5,p.y(),p.z()+.5);
        for(var entry:occupied.entrySet()) {
            UUID id=entry.getKey();
            if(!uncertain.contains(id)) {
                if(occupiedPoints.getOrDefault(id,Set.of()).contains(node.id())) return "OCCUPIED_OR_UNCERTAIN";
                continue;
            }
            if(entry.getValue().stream().anyMatch(r->local.contains(r)||Arrays.stream(r.split("\\|")).anyMatch(end->end.startsWith(node.id()+":"))))
                return "OCCUPIED_OR_UNCERTAIN";
            var saved=retained.get(id);
            if(saved!=null) for(var a:saved.positions()) for(var b:saved.positions())
                if(a.world().equals(b.world()) && Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2)<=144
                        && pointNear(p,a,b)) return "OCCUPIED_OR_UNCERTAIN";
        }
        return null;
    }
    public synchronized void close() {
        closed=true;if(task!=null)task.cancel();
        pendingPoints.clear();
        switchResults.forEach((id,result)->result.complete(new Reply(id,"REJECTED","SERVICE_STOPPED")));
        if(!failed) try {persist(true);} catch(java.io.IOException ex) {
            if(plugin!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save final shadow occupancy",ex);
        }
        if(plugin!=null) {
            plugin.getServer().getServicesManager().unregister(ShadowAuthorityService.class,this);
            plugin.getServer().getServicesManager().unregister(SwitchControlService.class,this);
        }
    }
}
