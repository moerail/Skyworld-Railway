package net.skyworld.skyatolite;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.skyworld.sta.api.v5.RailNetworkService;
import net.skyworld.sta.api.v8.*;
import net.skyworld.sta.api.v3.RailwayEvent;
import net.skyworld.sta.api.v3.RailwayEventService;
import net.skyworld.sta.api.v6.OperationalAuthorityService;
import net.skyworld.sta.api.v9.TopologyDependency;
import net.skyworld.sta.api.v10.RouteEditingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/** Preset-route call service. All mutable operations serialize on this instance; world work stays region-owned. */
public final class SkyATOLitePlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter,OperatingNetworkService {
    static final class SignData {
        String world,action,value,option,legacyOption;boolean legacyAwake;int x,y,z,rx,ry,rz;double number;
        String key(){return world+":"+x+":"+y+":"+z;}
        Topology.Pos rail(){return new Topology.Pos(world,rx+.5,ry+.0625,rz+.5);}
        String berth(){return value+"/"+option;}
    }
    static final class Waypoint {String id,visitId;boolean reverse;Double dwellSeconds;Waypoint(String id,boolean reverse){this.id=id;this.reverse=reverse;}}
    static final class Route {
        String name;double dx,dz;List<Waypoint> points=new ArrayList<>();long revision=-1;boolean published,validated;
        TopologyDependency dependency;
        Set<String> depots=new LinkedHashSet<>();long publicationVersion;
        RouteEditingService.DispatchPolicy dispatchPolicy;
        Route(String name,double dx,double dz){this.name=name;this.dx=dx;this.dz=dz;}
    }
    static final class Job {
        String faultCause="";long faultAt;
        transient ManeuverPlanner.Journey journey;transient int maneuverLeg;transient boolean maneuverReleased;transient long reverseSentAt;
        transient RouteTracking.State tracking=new RouteTracking.State();
        transient ReturnBerthGuard.State returnGuard=new ReturnBerthGuard.State();
        transient long returnNoticeAt;
        String lastReturnIssue="";
        UUID overrideAdmin;boolean overrideAuthorityReleased;
        UUID train,owner;String entryVisit;String depot,line,phase="PICKUP",reason="",berth;
        Route routeSnapshot;
        AutomaticTrainService.ParkingTarget parkingTarget;
        String alarm="";
        int index=-1,retries;long sequence,dwellUntil,nextRequest,nextRecovery,revision;
        double cruise=-1;
        TopologyDependency dependency;
        transient Topology.Plan plan;transient double baseline;transient int targetIndex;
        transient Set<String> fired=new HashSet<>();transient CompletableFuture<String> operation;
        transient boolean recoveryCompleting;
        transient int releaseRetries;
        transient AuthoritySession authoritySession=new AuthoritySession();
        transient CompletableFuture<String> authorityOperation;
        transient boolean authoritySubmitting,returnReversed,reuseRequested;
        transient long authorityRevision;
        transient List<String> authorityPath=List.of();
    }
    static final class PendingCall {UUID id;String line,station,entryVisit,reason="等待可用列车";long version,created,expires;}
    static final class State {
        int schema=2;List<PendingCall> pendingCalls=new ArrayList<>();List<OperatingNetworkService.Dispatch> dispatchHistory=new ArrayList<>();
        Map<String,SignData> signs=new LinkedHashMap<>();Map<String,Route> routes=new LinkedHashMap<>();
        Map<UUID,String> homes=new LinkedHashMap<>();Map<UUID,Job> jobs=new LinkedHashMap<>();
        Map<UUID,String> manualBerths=new HashMap<>();
        Set<UUID> manualStandby=new HashSet<>();Map<UUID,UUID> seenManualReleases=new HashMap<>();
        RouteWorkspace workspace;
        Map<String,TopologyDependency> draftDependencies=new HashMap<>();
    }
    private record Choice(String line,List<String> ids,long revision,long expires,boolean reverse,long draftRevision) {}
    private record Preview(String line,long until) {}
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private State state=new State();private Topology topology;
    private String depotLayoutKey="";private Map<String,DepotLayout.Slot> depotLayoutCache=Map.of();
    private final Map<UUID,Choice> choices=new HashMap<>();private final Map<UUID,Preview> previews=new HashMap<>();
    private final Map<String,Boolean> power=new HashMap<>();private final Map<String,Long> called=new HashMap<>();
    private final Set<String> invalidSigns=new HashSet<>();
    private final DepotHandover depotHandover=new DepotHandover();
    private final UUID operatingSession=UUID.randomUUID();
    private OperatingNetworkService.Snapshot operatingCache;
    private volatile boolean closed;
    private boolean storageFailed;private long nextDispatch;
    private final ExecutorService dispatchExecutor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SAL-dispatch-compute");t.setDaemon(true);return t;});
    private CompletableFuture<DispatchBatch> dispatchComputation;
    private Path file;
    private RouteTracking.Config trackingConfig=RouteTracking.Config.defaults();
    private long returnRecheckMillis=2000;
    public void onEnable() {
        saveDefaultConfig();file=getDataFolder().toPath().resolve("state.json");
        try {
            double confirmation=getConfig().getDouble("localisation.confirmation-ms",2000);
            if(!Double.isFinite(confirmation)||confirmation!=Math.rint(confirmation))throw new IOException("Invalid localisation.confirmation-ms");
            trackingConfig=new RouteTracking.Config(getConfig().getDouble("localisation.position-tolerance-blocks",1.5),getConfig().getDouble("localisation.endpoint-tolerance-blocks",.75),(long)confirmation);
            double returnWindow=getConfig().getDouble("depot.recheck-ms",2000);
            if(!Double.isFinite(returnWindow)||returnWindow!=Math.rint(returnWindow)||returnWindow<100||returnWindow>10000)throw new IOException("Invalid depot.recheck-ms");returnRecheckMillis=(long)returnWindow;
            double speed=getConfig().getDouble("cruise-blocks-per-tick",.4);
            if(!Double.isFinite(speed)||speed<0||speed>100)throw new IOException("Invalid cruise speed");
            for(String key:List.of("net-gap-blocks","boundary-margin-blocks","stopping-tolerance-blocks")){
                double value=getConfig().getDouble("depot."+key,key.equals("stopping-tolerance-blocks")?.5:2);
                if(!Double.isFinite(value)||value<0||key.equals("boundary-margin-blocks")&&value<1||key.equals("stopping-tolerance-blocks")&&(value<.06||value>.75))throw new IOException("Invalid depot."+key);
            }
            for(String key:List.of("batch-window-ms","request-timeout-ms","compute-budget-ms","last-vehicle-penalty-seconds")){
                double value=getConfig().getDouble("dispatch."+key,switch(key){case "batch-window-ms","compute-budget-ms"->500;case "request-timeout-ms"->120000;default->0;});
                if(!Double.isFinite(value)||value<0||key.equals("batch-window-ms")&&(value<100||value>5000)||key.equals("request-timeout-ms")&&(value<5000||value>600000)||key.equals("compute-budget-ms")&&(value<50||value>2000)||key.equals("last-vehicle-penalty-seconds")&&value>3600)throw new IOException("Invalid dispatch."+key);
            }
            if(Files.exists(file)) {state=gson.fromJson(Files.readString(file),State.class);
                if(state==null||(state.schema!=1&&state.schema!=2)||state.signs==null||state.routes==null||state.homes==null||state.jobs==null)throw new IOException("Invalid state schema");}
            for(var j:state.jobs.values()){j.phase=j.overrideAdmin==null?"RECOVERY_REQUIRED":j.overrideAuthorityReleased?"OVERRIDE_CONTROL":"OVERRIDE_STOP";j.reason="重启后等待管理员确认";j.fired=new HashSet<>();}
            initialiseWorkspace();
            migratePublicStations();
            for(var q:state.pendingCalls)dispatchResult(q,"CANCELLED",null,"",null,null,"重启取消未提交的叫车，请重新请求");state.pendingCalls.clear();
            save();
        }catch(Exception ex){storageFailed=true;closed=true;state=new State();getLogger().log(java.util.logging.Level.SEVERE,"State rejected; original retained",ex);getServer().getPluginManager().disablePlugin(this);return;}
        Objects.requireNonNull(getCommand("sal")).setExecutor(this);getCommand("sal").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getServicesManager().register(OperatingNetworkService.class,this,this,org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(RouteEditingService.class,new RouteEditingService(){
            public Catalog catalog(){return editingCatalog();}
            public CompletionStage<Reply> submit(Request r){return CompletableFuture.completedFuture(editingSubmit(r));}
        },this,org.bukkit.plugin.ServicePriority.Normal);
        getServer().getAsyncScheduler().runAtFixedRate(this,t->{try{tick();}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Automation tick failed; intents expire to brake hold",ex);}},100,100,TimeUnit.MILLISECONDS);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,t->preview(),20,20);
    }
    public synchronized void onDisable() {
        closed=true;if(dispatchComputation!=null)dispatchComputation.cancel(true);dispatchExecutor.shutdownNow();
        getServer().getServicesManager().unregister(OperatingNetworkService.class,this);
        getServer().getServicesManager().unregisterAll(this);
        var service=trains();if(service!=null)for(var j:state.jobs.values())hold(j,service,"PLUGIN_STOP");
        try{if(file!=null&&!storageFailed)save();}catch(RuntimeException ex){getLogger().severe(ex.toString());}
    }
    private AutomaticTrainService trains(){return getServer().getServicesManager().load(AutomaticTrainService.class);}
    private AutomaticAuthorityService authority(){return getServer().getServicesManager().load(AutomaticAuthorityService.class);}
    private Topology graph() {
        var service=getServer().getServicesManager().load(RailNetworkService.class);
        if(service==null)throw new IllegalArgumentException("STCS RailGraph 不可用");
        if(topology==null||topology.revision!=service.graphRevision())topology=new Topology(service.graphJson());
        return topology;
    }
    private void save() {
        if(storageFailed)throw new IllegalStateException("持久化已故障，自动运行被保持");
        try {Files.createDirectories(file.getParent());var temp=file.resolveSibling("state.json.tmp");
            Files.writeString(temp,gson.toJson(state));
            if(Files.exists(file))Files.copy(file,file.resolveSibling("state.json.bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException ex){storageFailed=true;throw new IllegalStateException("状态保存失败，停止发车",ex);}
    }
    private static boolean readable(Block block){return block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)&&Bukkit.isOwnedByCurrentRegion(block.getLocation());}
    public synchronized OperatingNetworkService.Snapshot operatingNetwork() {
        long now=System.currentTimeMillis();
        if(operatingCache!=null&&now>=operatingCache.emittedAtMillis()&&now-operatingCache.emittedAtMillis()<1000)return operatingCache;
        try {
            if(closed||storageFailed)throw new IllegalStateException("SAL unavailable");
            var g=graph();var network=getServer().getServicesManager().load(RailNetworkService.class);
            List<OperatingNetworkService.Route> routes=new ArrayList<>();
            for(var route:displayRoutes()) {
                Topology.Plan plan=null;String reason="";
                try{plan=validate(route);}catch(IllegalArgumentException ex){reason=Objects.toString(ex.getMessage(),"");}
                var check=route.dependency==null?null:network.checkDependencies(route.dependency);
                boolean current=check!=null&&check.graphRevision()==g.revision&&check.status().equals("UNCHANGED");
                String status=OperatingProjection.routeStatus(route.published,route.validated,current,plan!=null);
                List<OperatingNetworkService.Segment> segments=new ArrayList<>();
                if(plan!=null)for(var part:plan.parts()) {
                    var piece=new Topology.Plan(List.of(part),part.length(),g.scale,g.revision);
                    segments.add(new OperatingNetworkService.Segment(piece.samples(0,piece.length(),4096).stream().map(SkyATOLitePlugin::operatingPosition).toList(),false));
                }
                else segments.addAll(previewSegments(route));
                List<OperatingNetworkService.Stop> stops=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(var point:route.points)if(point.id.startsWith("station:")&&seen.add(point.id)) {
                    var sign=state.signs.get(point.id.substring(8));
                    if(sign!=null)stops.add(new OperatingNetworkService.Stop(point.id,sign.option,operatingPosition(sign.rail()),sign.number));
                }
                routes.add(new OperatingNetworkService.Route(route.name,status,reason,segments,stops));
            }
            var slots=state.signs.values().stream().filter(s->s.action.equals("depot")).map(s->
                new OperatingProjection.Slot(s.value,s.berth(),s.option,operatingPosition(s.rail()),!invalidSigns.contains(s.key()))).toList();
            Map<UUID,OperatingProjection.Task> jobs=new HashMap<>();state.jobs.forEach((id,j)->jobs.put(id,new OperatingProjection.Task(j.depot,j.line,j.phase,jobReason(j),j.berth)));
            var trains=trains();if(trains==null)throw new IllegalStateException("STF unavailable");
            var layout=depotSlots();
            var fleet=OperatingProjection.trains(state.homes,jobs,trains.trains(),slots,now,(slot,v)->{
                if(!v.bodyKnown())return null;
                if(slot.id().equals(state.manualBerths.get(v.train()))&&v.status().equals("PARKED")){var sign=state.signs.values().stream().filter(x->x.action.equals("depot")&&x.berth().equals(slot.id())).findFirst().orElse(null);if(sign!=null&&DepotLayout.covers(g,v,sign.rail()))return manualTarget(sign,v);}
                try{var space=layout.get(slot.id());var fit=space==null?null:space.fit(DepotLayout.extent(g,v).lengthBlocks(),depotTolerance());
                return fit==null?null:targetAt(slot.id(),fit.middle(),depotTolerance());}catch(IllegalArgumentException ex){return null;}});
            fleet=fleet.stream().map(t->{var job=state.jobs.get(t.id());return new OperatingNetworkService.Train(t.id(),t.name(),t.home(),t.line(),t.status(),t.phase(),t.reason(),t.berth(),t.position(),t.observedAtMillis(),t.fresh(),job==null||job.routeSnapshot==null?0:job.routeSnapshot.publicationVersion,
                job==null||job.parkingTarget==null?null:new OperatingNetworkService.Position(job.parkingTarget.world(),job.parkingTarget.x(),job.parkingTarget.y(),job.parkingTarget.z()),
                job==null||job.parkingTarget==null||layout.get(job.berth)==null||!layout.get(job.berth).valid()?null:(layout.get(job.berth).coordinate(new Topology.Pos(job.parkingTarget.world(),job.parkingTarget.x(),job.parkingTarget.y(),job.parkingTarget.z()))-layout.get(job.berth).marker())*g.scale);}).toList();
            if(network.graphRevision()!=g.revision)throw new IllegalStateException("Graph changing");
            operatingCache=new OperatingNetworkService.Snapshot(1,"AVAILABLE",operatingSession,g.revision,now,routes,OperatingProjection.depots(slots,fleet,jobs).stream().map(d->new OperatingNetworkService.Depot(d.name(),d.berths().stream().map(b->{
                var space=layout.get(b.id());var geometry=space==null?null:new OperatingNetworkService.ParkingGeometry(1,space.marker(),depotTolerance(),
                    space.valid()?(space.upper()-space.lower())*g.scale:0,space.capacity(depotTolerance()),space.access(),space.reason(),
                    space.boundary().stream().map(SkyATOLitePlugin::operatingPosition).toList());
                return new OperatingNetworkService.Berth(b.id(),b.name(),b.position(),b.valid()&&space!=null&&space.valid(),b.parkedTrains(),b.reservedBy(),geometry);
            }).toList(),d.parkedTrains(),d.runningTrains(),d.otherTrains())).toList(),fleet,dispatchView());
        }catch(RuntimeException ex){operatingCache=new OperatingNetworkService.Snapshot(1,"UNAVAILABLE",operatingSession,0,now,List.of(),List.of(),List.of());}
        return operatingCache;
    }
    private List<OperatingNetworkService.Dispatch> dispatchView(){var result=new ArrayList<OperatingNetworkService.Dispatch>();for(var q:state.pendingCalls)result.add(new OperatingNetworkService.Dispatch(q.id,q.line,q.station,"QUEUED",null,"",q.created,null,null,q.reason));result.addAll(state.dispatchHistory.reversed());return List.copyOf(result);}
    private Set<String> depotNames(){return state.signs.values().stream().filter(s->s.action.equals("depot")).map(s->s.value).collect(java.util.stream.Collectors.toCollection(TreeSet::new));}
    private RouteEditingService.Definition definition(Route r){return new RouteEditingService.Definition(r.name,r.dx,r.dz,
        r.points.stream().map(p->new RouteEditingService.Visit(p.id,p.reverse,p.dwellSeconds,p.visitId)).toList(),r.depots==null?Set.of():r.depots,r.dispatchPolicy==null?new RouteEditingService.DispatchPolicy("RESTRICTED",30,120):r.dispatchPolicy);}
    private Route compiled(RouteEditingService.Definition d){
        var r=new Route(d.name(),d.dx(),d.dz());r.depots=new LinkedHashSet<>(d.depots());r.dispatchPolicy=d.dispatchPolicy();
        for(var v:d.visits()){var p=new Waypoint(v.deviceId(),v.reverse());p.visitId=v.visitId();p.dwellSeconds=v.dwellSeconds();
            if(p.dwellSeconds==null&&p.id.startsWith("station:")){var s=state.signs.get(p.id.substring(8));if(s!=null)p.dwellSeconds=s.number;}r.points.add(p);}
        return r;
    }
    private void initialiseWorkspace(){
        for(var r:state.routes.values())for(var p:r.points)if(p.dwellSeconds==null&&p.id.startsWith("station:")){
            var sign=state.signs.get(p.id.substring(8));if(sign!=null)p.dwellSeconds=sign.number;
        }
        for(var j:state.jobs.values())if(j.routeSnapshot==null&&state.routes.containsKey(j.line))j.routeSnapshot=gson.fromJson(gson.toJson(state.routes.get(j.line)),Route.class);
        if(state.workspace==null){state.workspace=new RouteWorkspace();
            for(var r:state.routes.values()){
                var depots=new LinkedHashSet<String>();state.signs.values().stream().filter(s->s.action.equals("awake")&&s.option.equals(r.name)&&depotNames().contains(s.value)).forEach(s->depots.add(s.value));
                if(depots.isEmpty()&&depotNames().size()==1)depots.addAll(depotNames());r.depots=depots;
                state.workspace.importLegacy(definition(r),r.published);r.publicationVersion=r.published?1:0;
            }
        }
        state.workspace.restart();
        for(var j:state.jobs.values())if(j.entryVisit==null&&j.routeSnapshot!=null&&!j.routeSnapshot.points.isEmpty())j.entryVisit=definition(j.routeSnapshot).visits().getFirst().visitId();
        for(var r:state.routes.values())if(r.depots==null)r.depots=new LinkedHashSet<>();
        for(var j:state.jobs.values())if(j.routeSnapshot!=null&&j.routeSnapshot.publicationVersion==0){var line=state.workspace.line(j.line);if(line!=null&&line.publishedVersion()==1)j.routeSnapshot.publicationVersion=1;}
    }
    private void migratePublicStations(){
        if(state.schema>=2)return;int count=0;var names=new HashSet<String>();
        for(var sign:state.signs.values())if(sign.action.equals("station")){
            sign.legacyOption=sign.option;String base=sign.option.length()>40?sign.option.substring(0,40):sign.option;
            do{sign.option=base+"_S"+(++count);}while(!names.add(sign.option));
        }else if(sign.action.equals("awake"))sign.legacyAwake=true;
        state.schema=2;
    }
    private boolean returnAllowed(Job j,String group){var r=jobRoute(j);return r.dispatchPolicy==null?r.depots.contains(group):r.dispatchPolicy.supplyMode().equals("GLOBAL")||r.depots.contains(group);}
    private double dwell(Job j,int index){var wp=jobRoute(j).points.get(index);return wp.dwellSeconds==null?state.signs.get(wp.id.substring(8)).number:wp.dwellSeconds;}
    private Route jobRoute(Job j){if(j.routeSnapshot==null)throw new IllegalArgumentException("任务缺少发布版快照，请人工检查");return j.routeSnapshot;}
    private List<Route> displayRoutes(){
        var result=new ArrayList<Route>();for(var e:state.workspace.entries.entrySet()){
            var released=state.routes.get(e.getKey());
            if(e.getValue().published!=null&&released!=null&&!e.getValue().retired)result.add(released);
            else result.add(compiled(e.getValue().draft));
        }return result;
    }
    private void requireReferences(RouteEditingService.Definition d){
        if(d.dispatchPolicy().supplyMode().equals("RESTRICTED")&&d.depots().isEmpty())throw new IllegalArgumentException("DEPOT_REQUIRED");
        if(!depotNames().containsAll(d.depots()))throw new IllegalArgumentException("DEPOT_NOT_FOUND");
        for(var v:d.visits()){
            target(v.deviceId());if(v.reverse()&&!v.deviceId().startsWith("station:"))throw new IllegalArgumentException("REVERSE_REQUIRES_STATION");

        }
    }
    private synchronized RouteEditingService.Catalog editingCatalog(){
        long now=System.currentTimeMillis();try{
            if(closed||storageFailed)throw new IllegalStateException();var g=graph();var devices=new ArrayList<RouteEditingService.Device>();
            for(var n:g.nodes.values())if(n.rail()!=null)devices.add(new RouteEditingService.Device(n.id(),n.type(),n.name(),operatingPosition(new Topology.Pos(n.rail().world(),n.rail().x()+.5,n.rail().y()+.0625,n.rail().z()+.5)),"",0,true));
            for(var edge:g.edges.values())devices.add(new RouteEditingService.Device("edge:"+edge.id(),"edge",edge.from()+" → "+edge.to(),
                operatingPosition(edge.path().get(edge.path().size()/2).pos()),"",edge.distanceMeters(),true));
            for(var s:state.signs.values())devices.add(new RouteEditingService.Device(s.action.equals("station")?"station:"+s.key():"sal:"+s.key(),s.action,
                s.action.equals("depot")?s.berth():s.action.equals("station")?s.option:s.value,
                operatingPosition(s.action.equals("awake")?new Topology.Pos(s.world,s.x+.5,s.y,s.z+.5):s.rail()),s.action.equals("awake")?s.value:"",s.number,!s.legacyAwake&&!invalidSigns.contains(s.key())));
            var views=state.workspace.lines().stream().map(l->new RouteEditingService.Line(l.name(),l.revision(),l.publishedVersion(),l.draft(),l.published(),
                l.validated()&&draftCurrent(l.name()),l.retired(),l.conflict(),l.participants())).toList();
            return new RouteEditingService.Catalog(1,"AVAILABLE",operatingSession,g.revision,now,views,devices,List.copyOf(depotNames()),state.workspace.sequence);
        }catch(RuntimeException ex){return new RouteEditingService.Catalog(1,"UNAVAILABLE",operatingSession,0,now,List.of(),List.of(),List.of());}
    }
    private synchronized RouteEditingService.Reply editingSubmit(RouteEditingService.Request request){
        if(closed||storageFailed)return new RouteEditingService.Reply(request.requestId(),"REJECTED","SAL_UNAVAILABLE",null,null,0,List.of(),false);
        try{
            var g=graph();final TopologyDependency[] checked={null};boolean replay=state.workspace.receipts.containsKey(request.requestId());
            var existing=state.workspace.entries.get(request.line());
            if(!replay&&request.action().equals("PUBLISH")&&existing!=null&&!existing.conflict&&existing.validatedRevision==existing.revision&&!draftCurrent(request.line())){existing.validatedRevision=-1;state.workspace.sequence++;}
            var reply=state.workspace.apply(request,System.currentTimeMillis(),this::requireReferences,d->checked[0]=capture(validate(compiled(d))),
                ()->state.jobs.values().stream().anyMatch(j->j.line.equals(request.line())),g.revision);
            if(reply.status().equals("APPLIED")&&request.action().equals("VALIDATE")&&checked[0]!=null)state.draftDependencies.put(request.line(),checked[0]);
            if(!replay&&reply.status().equals("APPLIED")&&request.action().equals("SAVE"))state.draftDependencies.remove(request.line());
            if(!replay&&reply.status().equals("APPLIED")&&request.action().equals("PUBLISH")&&reply.line()!=null
                &&(state.routes.get(request.line())==null||state.routes.get(request.line()).publicationVersion<reply.line().publishedVersion())){
                var line=reply.line();var r=compiled(line.published());r.dependency=checked[0];r.revision=checked[0].graphRevision();r.published=true;r.validated=true;r.publicationVersion=line.publishedVersion();state.routes.put(r.name,r);
            }
            if(!replay&&reply.status().equals("APPLIED")&&request.action().equals("DELETE")&&!state.workspace.entries.containsKey(request.line())){state.routes.remove(request.line());state.draftDependencies.remove(request.line());}
            // Conflict and staged work persist as recovery records, never as the saved draft/publication.
            save();operatingCache=null;
            var e=state.workspace.entries.get(request.line());var session=e==null?null:e.sessions.get(request.actor());
            var d=session==null?(reply.line()==null?null:reply.line().draft()):session.working;
            var issues=new ArrayList<>(reply.issues());var segments=new ArrayList<OperatingNetworkService.Segment>();boolean loop=false;
            if(d!=null)try{var plan=validate(compiled(d));segments.add(new OperatingNetworkService.Segment(plan.samples(0,plan.length(),8192).stream().map(SkyATOLitePlugin::operatingPosition).toList(),false));loop=true;}
            catch(IllegalArgumentException ex){if(issues.isEmpty()&&ex instanceof RouteValidationFailure failure)issues.add(failure.issue);segments.addAll(previewSegments(compiled(d)));}
            return new RouteEditingService.Reply(reply.requestId(),reply.status(),reply.reason(),reply.session(),reply.line(),g.revision,segments,loop,state.workspace.sequence,issues);
        }catch(RuntimeException ex){return new RouteEditingService.Reply(request.requestId(),"REJECTED",Objects.toString(ex.getMessage(),"EDIT_FAILED"),null,state.workspace.line(request.line()),0,List.of(),false);}
    }
    private boolean draftCurrent(String name){
        var dependency=state.draftDependencies.get(name);var service=getServer().getServicesManager().load(RailNetworkService.class);
        if(dependency==null||service==null)return false;var check=service.checkDependencies(dependency);
        return check!=null&&check.status().equals("UNCHANGED");
    }
    private String gameActor(CommandSender sender){return "GAME:"+(sender instanceof Player p?p.getUniqueId():"CONSOLE");}
    private RouteEditingService.Reply gameRequest(CommandSender sender,String action,String line,UUID token,RouteEditingService.Definition d,String decision){
        var current=state.workspace.line(line);var reply=editingSubmit(new RouteEditingService.Request(UUID.randomUUID(),action,line,gameActor(sender),"GAME",token,current==null?0:current.revision(),d,decision));
        if(!reply.status().equals("APPLIED"))throw new IllegalArgumentException(reply.reason());return reply;
    }
    private RouteEditingService.Reply gameBegin(CommandSender sender,String line){return gameRequest(sender,"BEGIN",line,null,null,null);}
    private void gameSave(CommandSender sender,String line,RouteEditingService.Definition d){
        var begin=gameBegin(sender,line);gameRequest(sender,"UPDATE",line,begin.session(),d,null);gameRequest(sender,"SAVE",line,begin.session(),null,null);
    }
    private static OperatingNetworkService.Position operatingPosition(Topology.Pos p){return new OperatingNetworkService.Position(p.world(),p.x(),p.y(),p.z());}
    private Topology.Pos rail(Block sign) {
        Block support=sign.getBlockData() instanceof WallSign wall?sign.getRelative(wall.getFacing().getOppositeFace()):sign;
        return graph().railAbove(sign.getWorld().getName(),sign.getX(),sign.getY(),sign.getZ(),support.getX(),support.getZ());
    }
    private static String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public synchronized void sign(SignChangeEvent event) {
        String id=key(event.getBlock());boolean known=state.signs.containsKey(id);
        if(!LiteSign.header(event.getLine(0))&&!known)return;
        if(!event.getPlayer().hasPermission("skyatolite.admin")){event.setCancelled(true);SalCommandUi.send(event.getPlayer(),"§c需要 SkyATO-Lite 管理权限。");return;}
        try {
            if(!LiteSign.header(event.getLine(0))){removeSign(id,event.getPlayer());return;}
            var spec=LiteSign.parse(event.getLine(1),event.getLine(2),event.getLine(3));
            if(known&&busySign(id))throw new IllegalArgumentException("请先结束引用此牌子的任务");
            Block b=event.getBlock();var d=new SignData();d.world=b.getWorld().getName();d.x=b.getX();d.y=b.getY();d.z=b.getZ();
            d.action=spec.action();d.value=spec.value();d.option=spec.option();d.number=spec.number();
            if(!d.action.equals("awake")){var r=rail(b);d.rx=(int)r.x();d.ry=(int)r.y();d.rz=(int)r.z();}
            if(d.action.equals("depot")&&state.signs.values().stream().anyMatch(s->!s.key().equals(id)&&s.action.equals("depot")&&s.berth().equals(d.berth())))throw new IllegalArgumentException("停车位名称已存在");
            if(d.action.equals("station")&&state.signs.values().stream().anyMatch(x->!x.key().equals(id)&&x.action.equals("station")&&x.option.equals(d.option)))throw new IllegalArgumentException("停车点名称已存在");
            guardSignEdit(event.getPlayer(),id,d);
            var old=state.signs.put(id,d);power.remove(id);
            invalidSigns.remove(id);
            if(d.action.equals("awake")) {
                boolean on=b.isBlockPowered()||b.isBlockIndirectlyPowered();
                if(b.getBlockData() instanceof WallSign wall){var support=b.getRelative(wall.getFacing().getOppositeFace());if(readable(support))on|=support.isBlockPowered()||support.isBlockIndirectlyPowered();}
                power.put(id,on);
            }

            save();SalCommandUi.send(event.getPlayer(),"已登记："+d.action+" "+d.value+" "+d.option);
        }catch(RuntimeException ex){event.setCancelled(true);SalCommandUi.send(event.getPlayer(),"§c"+ex.getMessage());}
    }
    private boolean busySign(String id){return state.jobs.values().stream().anyMatch(j->jobRoute(j).points.stream().anyMatch(p->p.id.equals("station:"+id)))
        ||state.jobs.values().stream().anyMatch(j->{var s=state.signs.get(id);return s!=null&&s.action.equals("depot")&&(s.value.equals(j.depot)||s.berth().equals(j.berth));});}
    private void guardSignEdit(CommandSender sender,String id,SignData replacement){
        var old=state.signs.get(id);var affected=new LinkedHashSet<String>();
        for(var s:Arrays.asList(old,replacement))if(s!=null){
            if(s.action.equals("station"))for(var entry:state.workspace.entries.entrySet())if(java.util.stream.Stream.of(entry.getValue().draft,entry.getValue().published).filter(Objects::nonNull).anyMatch(def->def.visits().stream().anyMatch(v->v.deviceId().equals("station:"+id))))affected.add(entry.getKey());
            if(s.action.equals("depot"))for(var e:state.workspace.entries.entrySet())if(e.getValue().draft.depots().contains(s.value)||e.getValue().published!=null&&e.getValue().published.depots().contains(s.value))affected.add(e.getKey());
            if(Set.of("message","property","v_target").contains(s.action))for(var e:state.workspace.entries.entrySet())try{if(Double.isFinite(validate(compiled(e.getValue().draft)).distanceAt(s.rail())))affected.add(e.getKey());}catch(IllegalArgumentException ignored){}
        }
        // Detect conflicts for every affected line before touching the sign or any draft.
        for(var name:affected)gameBegin(sender,name);
        if(old!=null&&old.action.equals("depot")&&(replacement==null||!replacement.action.equals("depot")||!replacement.value.equals(old.value))
            &&state.signs.values().stream().filter(s->s.action.equals("depot")&&s.value.equals(old.value)).count()<=1&&!affected.isEmpty())
            throw new IllegalArgumentException("车辆段仍被线路绑定，不能删除最后一个停车位");
    }
    private void changeStationDraft(CommandSender sender,String line,String id,boolean add){
        var e=state.workspace.entries.get(line);if(e==null)return;var d=e.draft;var points=new ArrayList<>(d.visits());
        if(add){if(points.stream().noneMatch(v->v.deviceId().equals("station:"+id)))points.add(new RouteEditingService.Visit("station:"+id,false,null));}
        else points.removeIf(v->v.deviceId().equals("station:"+id));
        gameSave(sender,line,new RouteEditingService.Definition(line,d.dx(),d.dz(),points,d.depots(),d.dispatchPolicy()));
    }
    private void removeSign(String id,CommandSender sender){
        if(busySign(id))throw new IllegalArgumentException("此设备被运行任务引用，先结束任务");
        guardSignEdit(sender,id,null);var previous=state.signs.remove(id);

        for(var r:state.routes.values())if(r.points.stream().anyMatch(p->p.id.equals("station:"+id)))r.published=false;
        save();operatingCache=null;
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public synchronized void broken(BlockBreakEvent event) {
        String id=key(event.getBlock());if(!state.signs.containsKey(id))return;
        if(!event.getPlayer().hasPermission("skyatolite.admin")){event.setCancelled(true);return;}
        try{removeSign(id,event.getPlayer());}catch(RuntimeException ex){event.setCancelled(true);SalCommandUi.send(event.getPlayer(),"§c"+ex.getMessage());}
    }
    @EventHandler
    public synchronized void redstone(BlockRedstoneEvent event) {
        for(var d:state.signs.values())if(d.action.equals("awake")&&d.world.equals(event.getBlock().getWorld().getName())
                &&Math.abs(d.x-event.getBlock().getX())<=2&&Math.abs(d.y-event.getBlock().getY())<=2&&Math.abs(d.z-event.getBlock().getZ())<=2) {
            var world=event.getBlock().getWorld();var id=d.key();
            getServer().getRegionScheduler().run(this,world,d.x>>4,d.z>>4,t->awakePower(id));
        }
    }
    private synchronized void awakePower(String id) {
        var d=state.signs.get(id);if(d==null||!d.action.equals("awake"))return;var w=Bukkit.getWorld(d.world);if(w==null||!w.isChunkLoaded(d.x>>4,d.z>>4))return;
        var b=w.getBlockAt(d.x,d.y,d.z);if(!readable(b)||!(b.getState() instanceof Sign sign))return;if(d.legacyAwake){feedback(b,"旧awake请重写为：线路名／停车点名");return;}
        if(!LiteSign.header(sign.getLine(0))||!sign.getLine(1).trim().equalsIgnoreCase("awake")||!sign.getLine(2).trim().equals(d.value)||!sign.getLine(3).trim().equals(d.option))return;
        boolean on=b.isBlockPowered()||b.isBlockIndirectlyPowered();
        if(b.getBlockData() instanceof WallSign wall){var support=b.getRelative(wall.getFacing().getOppositeFace());if(readable(support))on|=support.isBlockPowered()||support.isBlockIndirectlyPowered();}
        Boolean previous=power.put(id,on);if(previous==null||!on||Boolean.TRUE.equals(previous))return;
        // The event is a real observed edge; no startup polling dispatches trains.
        try{call(d.value,d.option);feedback(b,"已叫车："+d.option);}catch(RuntimeException ex){feedback(b,ex.getMessage());}
    }
    private void feedback(Block b,String text) {
        for(var p:Bukkit.getOnlinePlayers())p.getScheduler().run(this,t->{if(p.getWorld().equals(b.getWorld())&&p.getLocation().distanceSquared(b.getLocation())<256)SalCommandUi.send(p,text);},null);
    }
    private UUID selected(Player p,AutomaticTrainService service) {
        if(!(p.getVehicle() instanceof Minecart cart))throw new IllegalArgumentException("请先坐进列车");
        var id=service.trainForMember(cart.getUniqueId());if(id==null)throw new IllegalArgumentException("这辆矿车不属于 STF 编组");return id;
    }
    private void depotCommand(Player player,String[] args) {
        var service=Objects.requireNonNull(trains(),"STF 自动控制接口不可用");
        UUID id=selected(player,service);var v=view(service,id);
        if(args[0].equalsIgnoreCase("release")) {
            if(args.length!=1)throw new IllegalArgumentException("用法：/sal release（坐在回库待命列车中）");
            if(state.jobs.containsKey(id)||v==null||!fresh(v)||!v.status().equals("PARKED"))
                throw new IllegalArgumentException("仅能释放已回库停稳的待命列车；运行任务请先结束");
            boolean previous=state.manualStandby.contains(id);state.manualStandby.add(id);save();
            String result=service.releaseStandby(AtoProtocol.Packet.DEPOT_MANUAL_RELEASE.header(),id,player.getUniqueId());
            if(!result.equals("RELEASING")){if(!previous)state.manualStandby.remove(id);save();throw new IllegalArgumentException(result);}
            depotHandover.forget(id);SalCommandUi.send(player,"已提交待命释放。列车将保持制动并切为手动，随后可 /st drive。");return;
        }
        if(args.length>2)throw new IllegalArgumentException("/sal handover [六位确认码]");
        var driver=service.manualDrivers().stream().filter(d->d.train().equals(id)&&d.driver().equals(player.getUniqueId())).findFirst()
            .orElseThrow(()->new IllegalArgumentException("你没有本车当前驾驶权，请先 /st drive"));
        var berth=handoverBerth(v);
        var target=manualTarget(berth,v);
        if(state.jobs.containsKey(id))throw new IllegalArgumentException("本车仍有SAL任务，请先结束或override移管");
        String blocked=DepotHandover.reason(v,target,System.currentTimeMillis());if(!blocked.isEmpty())throw new IllegalArgumentException(blocked);
        if(args.length==1){var requested=depotHandover.request(driver,v,target,System.currentTimeMillis());requested.marker=markerTarget(berth);offerMessage(requested);return;}
        var offer=depotHandover.confirm(id,player.getUniqueId(),driver.lease(),args[1],System.currentTimeMillis());
        if(!offer.target.contains(v)||!markerTarget(berth).equals(offer.marker)){depotHandover.forget(id);throw new IllegalArgumentException("停车位置或库位已经变化，请重新申请确认码");}
        player.sendMessage(SalCommandUi.component("已确认移交至 "+berth.berth()+"。请在30秒内输入 /st release 确认移管，或 ")
            .append(Component.text("[确认释放驾驶权]",net.kyori.adventure.text.format.NamedTextColor.BLUE).clickEvent(ClickEvent.runCommand("/st release"))));
    }
    public synchronized boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length>0&&Set.of("release","handover").contains(args[0].toLowerCase(Locale.ROOT))) {
            if(!(sender instanceof Player p)||!sender.hasPermission("skyatolite.driver")||!sender.hasPermission("skytrain.use")){SalCommandUi.send(sender,"§c需要玩家及 skyatolite.driver、skytrain.use 权限。");return true;}
            try {depotCommand(p,args);}catch(RuntimeException ex){SalCommandUi.send(sender,"§c"+ex.getMessage());}
            return true;
        }
        if(args.length==0||args[0].equalsIgnoreCase("help")) {
            if(!sender.hasPermission("skyatolite.admin")&&!sender.hasPermission("skyatolite.driver")) { SalCommandUi.send(sender,"§c需要 skyatolite.driver 权限。"); return true; }
            for(var line:SalCommandUi.help(sender.hasPermission("skyatolite.admin"))) SalCommandUi.send(sender,line);
            return true;
        }
        if(!sender.hasPermission("skyatolite.admin")){SalCommandUi.send(sender,"§c需要 SkyATO-Lite 管理权限。");return true;}
        try {

            if(args[0].equalsIgnoreCase("status")){for(var q:state.pendingCalls)SalCommandUi.send(sender,q.id+" "+q.line+" @ "+q.station+" QUEUED "+q.reason);for(var j:state.jobs.values())SalCommandUi.send(sender,j.train+" "+j.line+" "+j.phase+" "+jobReason(j));if(state.jobs.isEmpty()&&state.pendingCalls.isEmpty())SalCommandUi.send(sender,"没有运行或待派任务");if(!state.dispatchHistory.isEmpty())SalCommandUi.send(sender,"§7最近派车历史（以下不是活动任务）：");for(var d:state.dispatchHistory.reversed().stream().limit(5).toList())SalCommandUi.send(sender,d.line()+" @ "+d.station()+" "+d.status()+" "+d.reason());return true;}
            if(args[0].equalsIgnoreCase("awake")&&args.length==3){call(args[1],args[2]);SalCommandUi.send(sender,"已进入派车队列；/sal status 查看结果");return true;}
            if(args[0].equalsIgnoreCase("station")&&args.length==2&&args[1].equalsIgnoreCase("list")){for(var x:state.signs.values())if(x.action.equals("station"))SalCommandUi.send(sender,x.option+" station:"+x.key());return true;}
            if(args[0].equalsIgnoreCase("route")){routeCommand(sender,args);return true;}
            var service=Objects.requireNonNull(trains(),"STF 自动控制接口不可用");
            if(args[0].equalsIgnoreCase("override")){
                if(args.length!=2)throw new IllegalArgumentException("/sal override <列车名或UUID>");
                var matches=service.trains().stream().filter(v->v.train().toString().equalsIgnoreCase(args[1])||v.name().equalsIgnoreCase(args[1])).toList();
                if(matches.size()!=1)throw new IllegalArgumentException("列车不存在或名称不唯一，请使用UUID");var v=matches.getFirst();var j=state.jobs.get(v.train());
                var admin=sender instanceof Player p?p.getUniqueId():UUID.nameUUIDFromBytes(("SAL-ADMIN:"+sender.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(j==null&&!v.status().equals("PARKED"))throw new IllegalArgumentException("该车没有SAL任务或自动待命状态");
                state.manualStandby.add(v.train());depotHandover.forget(v.train());
                if(j!=null){j.overrideAdmin=admin;j.phase="OVERRIDE_STOP";j.operation=null;j.authorityOperation=null;j.nextRequest=0;j.reason="管理员override：保持制动后移交手动";hold(j,service,j.reason);save();}
                else {save();String result=service.handoffToManual(AtoProtocol.Packet.RELEASE.header(),v.train(),null,admin);if(!Set.of("RELEASING","ALREADY_MANUAL").contains(result))throw new IllegalArgumentException("override等待："+result);}
                SalCommandUi.send(sender,"已提交override；/sal status查看释放进度。确认完成后 /st drive，车身占用和故障保持保留。");return true;
            }
            if(args[0].equalsIgnoreCase("train")&&args.length>=2&&args[1].equalsIgnoreCase("home")&&sender instanceof Player p) {
                UUID id=selected(p,service);if(args.length==2){SalCommandUi.send(sender,"车辆段："+state.homes.get(id));return true;}
                if(state.jobs.containsKey(id))throw new IllegalArgumentException("先结束当前任务");
                if(state.signs.values().stream().noneMatch(s->s.action.equals("depot")&&s.value.equals(args[2])))throw new IllegalArgumentException("车辆段不存在");
                state.homes.put(id,args[2]);save();SalCommandUi.send(sender,"归属已绑定："+args[2]+"；回库时自动分配停车位");return true;
            }
            if(args.length==2&&Set.of("stop","resume","finish").contains(args[0].toLowerCase(Locale.ROOT))) {
                var j=state.jobs.get(UUID.fromString(args[1]));if(j==null)throw new IllegalArgumentException("任务不存在");
                if(args[0].equalsIgnoreCase("stop")){j.phase="PAUSED";j.reason="管理员停止";hold(j,service,j.reason);save();}
                else if(args[0].equalsIgnoreCase("resume")) {
                    var view=view(service,j.train);if(view==null||view.speedBlocksPerTick()>.001)throw new IllegalArgumentException("列车未停稳或不可见");
                    if(view.owner()!=null&&!view.owner().equals(j.owner))throw new IllegalArgumentException("控制权冲突");
                    String result=service.claim(AtoProtocol.Packet.CLAIM.header(),j.train,j.owner);if(!result.equals("CLAIMED"))throw new IllegalArgumentException(result);
                    var r=jobRoute(j);r.dependency=capture(validate(r));r.revision=graph().revision;j.dependency=r.dependency;j.revision=r.revision;j.phase="RETURN";j.plan=null;j.berth=null;j.reason="管理员恢复后先回库";j.retries=0;j.alarm="";save();
                } else {
                    var view=view(service,j.train);if(view==null||view.speedBlocksPerTick()>.001||view.integrityHeld())throw new IllegalArgumentException("需完整定位并停稳");
                    hold(j,service,"FINISH");j.phase="FINISHING";j.releaseRetries=0;j.operation=authority().release(j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");save();
                }
                SalCommandUi.send(sender,"已处理");return true;
            }
            throw new IllegalArgumentException("用法：/sal help");
        }catch(Exception ex){SalCommandUi.send(sender,"§c"+Objects.toString(ex.getMessage(),ex.getClass().getSimpleName()));}
        return true;
    }
    private Route route(String name){var r=state.routes.get(name);if(r==null)throw new IllegalArgumentException("运营线路不存在："+name);return r;}
    private void routeCommand(CommandSender sender,String[] a) {
        if(a.length<3)throw new IllegalArgumentException("/sal route <操作> <线路>");
        String action=a[1].toLowerCase(Locale.ROOT),name=a[2];
        if(action.equals("choose")){
            if(!(sender instanceof Player p))throw new IllegalArgumentException("玩家操作");
            var c=choices.remove(p.getUniqueId());int index=Integer.parseInt(a[2])-1;
            if(c==null||c.expires<System.currentTimeMillis()||c.revision!=graph().revision||state.workspace.line(c.line)==null||state.workspace.line(c.line).revision()!=c.draftRevision||index<0||index>=c.ids.size())throw new IllegalArgumentException("选择已过期");
            var begin=gameBegin(sender,c.line);var d=begin.line().draft();var visits=new ArrayList<>(d.visits());visits.add(new RouteEditingService.Visit(c.ids.get(index),c.reverse,null,UUID.randomUUID().toString()));
            gameSave(sender,c.line,new RouteEditingService.Definition(d.name(),d.dx(),d.dz(),visits,d.depots(),d.dispatchPolicy()));return;
        }
        if(action.equals("create")){
            double dx=1,dz=0;if(sender instanceof Player p){var dir=p.getLocation().getDirection();dx=dir.getX();dz=dir.getZ();}
            var depots=a.length>3?new LinkedHashSet<>(Arrays.asList(a[3].split(","))):new LinkedHashSet<String>();

            gameRequest(sender,"CREATE",name,null,new RouteEditingService.Definition(name,dx,dz,List.of(),depots,new RouteEditingService.DispatchPolicy("GLOBAL",30,120)),null);
            SalCommandUi.send(sender,"已创建草稿并绑定车辆段；发布前需验证。完成编辑后 /sal route close "+name);return;
        }
        if(action.equals("resolve")){
            if(a.length<4)throw new IllegalArgumentException("/sal route resolve <线路> DISCARD|<参与者actor>");
            gameRequest(sender,"RESOLVE",name,null,null,a[3]);SalCommandUi.send(sender,"冲突已人工解消；未自动保存或发布");return;
        }
        var line=state.workspace.line(name);if(line==null)throw new IllegalArgumentException("线路不存在");
        if(action.equals("list")){
            SalCommandUi.send(sender,"草稿 v"+line.revision()+" / 发布 v"+line.publishedVersion()+(line.conflict()?" [编辑冻结]":""));
            for(var participant:line.participants())SalCommandUi.send(sender,participant.source()+" "+participant.actor());
            for(int i=0;i<line.draft().visits().size();i++)SalCommandUi.send(sender,(i+1)+". "+line.draft().visits().get(i));return;
        }
        if(action.equals("preview")){
            if(!(sender instanceof Player p))throw new IllegalArgumentException("玩家操作");
            if(a.length>3&&a[3].equalsIgnoreCase("off"))previews.remove(p.getUniqueId());else previews.put(p.getUniqueId(),new Preview(name,System.currentTimeMillis()+120000));return;
        }
        if(action.equals("close")){var e=state.workspace.entries.get(name);var own=e.sessions.get(gameActor(sender));if(own==null)throw new IllegalArgumentException("本侧没有编辑会话");gameRequest(sender,"CANCEL",name,own.token,null,null);return;}
        var begin=gameBegin(sender,name);var d=begin.line().draft();var visits=new ArrayList<>(d.visits());double dx=d.dx(),dz=d.dz();var depots=d.depots();var policy=d.dispatchPolicy();
        switch(action){
            case "direction" -> {if(a.length<4)throw new IllegalArgumentException("需要 north/south/east/west");switch(a[3]){case "north"->{dx=0;dz=-1;}case "south"->{dx=0;dz=1;}case "east"->{dx=1;dz=0;}case "west"->{dx=-1;dz=0;}default->throw new IllegalArgumentException("方向无效");}}
            case "depot" -> {if(a.length<4)throw new IllegalArgumentException("车辆段列表或 none");depots=a[3].equalsIgnoreCase("none")?Set.of():new LinkedHashSet<>(Arrays.asList(a[3].split(",")));}
            case "supply" -> {if(a.length<4)throw new IllegalArgumentException("global/restricted [偏好秒数] [接客上限秒数]");policy=new RouteEditingService.DispatchPolicy(a[3].toUpperCase(Locale.ROOT),a.length>4?Double.parseDouble(a[4]):policy.preferenceSeconds(),a.length>5?Double.parseDouble(a[5]):policy.maxPickupSeconds());}
            case "remove" -> {if(a.length<4)throw new IllegalArgumentException("需要序号");int index=Integer.parseInt(a[3])-1;if(index<0||index>=visits.size())throw new IllegalArgumentException("序号无效");visits.remove(index);}
            case "bind" -> {
                if(!(sender instanceof Player p))throw new IllegalArgumentException("站在设备旁操作");
                boolean reverse=a.length>3&&a[a.length-1].equalsIgnoreCase("reverse");
                if(a.length>3&&!a[3].equalsIgnoreCase("reverse")){target(a[3]);visits.add(new RouteEditingService.Visit(a[3],reverse,null,UUID.randomUUID().toString()));}
                else {
                    var loc=p.getLocation();var pos=new Topology.Pos(loc.getWorld().getName(),loc.getX(),loc.getY(),loc.getZ());var ids=new ArrayList<String>();
                    for(var n:graph().nodes.values())if(n.rail()!=null&&n.rail().squared(pos)<=64)ids.add(n.id());
                    for(var sign:state.signs.values())if(sign.action.equals("station")&&sign.rail().squared(pos)<=64)ids.add("station:"+sign.key());
                    if(ids.isEmpty())throw new IllegalArgumentException("8格内无本线路设备");
                    if(ids.size()>1){choices.put(p.getUniqueId(),new Choice(name,List.copyOf(ids),graph().revision,System.currentTimeMillis()+60000,reverse,line.revision()));for(int i=0;i<ids.size();i++)SalCommandUi.send(sender,(i+1)+". "+ids.get(i));SalCommandUi.send(sender,"/sal route choose <编号>（60秒内）");return;}
                    visits.add(new RouteEditingService.Visit(ids.getFirst(),reverse,null,UUID.randomUUID().toString()));
                }
            }
            case "validate","publish" -> {gameRequest(sender,"VALIDATE",name,begin.session(),null,null);if(action.equals("publish")){gameRequest(sender,"PUBLISH",name,begin.session(),null,null);gameRequest(sender,"CANCEL",name,begin.session(),null,null);}SalCommandUi.send(sender,action.equals("publish")?"已发布新版本，既有任务保持原版本":"草稿已验证");return;}
            case "close" -> {gameRequest(sender,"CANCEL",name,begin.session(),null,null);return;}
            case "delete","retire" -> {gameRequest(sender,action.equals("delete")?"DELETE":"RETIRE",name,begin.session(),null,null);return;}
            default -> throw new IllegalArgumentException("未知线路操作");
        }
        gameSave(sender,name,new RouteEditingService.Definition(name,dx,dz,visits,depots,policy));SalCommandUi.send(sender,"草稿已保存；已发布版及既有任务未改变");
    }
    private Topology.Pos targetPosition(Topology.Target t){
        if(t.position()!=null)return t.position();
        if(t.edge()!=null){var e=graph().edges.get(t.edge());return e.path().get(e.path().size()/2).pos();}
        var p=graph().nodes.get(t.node()).rail();return new Topology.Pos(p.world(),p.x()+.5,p.y()+.0625,p.z()+.5);
    }
    private Topology.Target target(String id) {
        if(id.startsWith("edge:")){if(!graph().edges.containsKey(id.substring(5)))throw new IllegalArgumentException("轨道图边已改变，请重选");return Topology.Target.edge(id.substring(5));}
        if(id.startsWith("station:")){var s=state.signs.get(id.substring(8));if(s==null||!s.action.equals("station")||invalidSigns.contains(s.key()))throw new IllegalArgumentException("station 已移除或改变");return Topology.Target.pos(s.rail());}
        if(!graph().nodes.containsKey(id))throw new IllegalArgumentException("设备已移除："+id);return Topology.Target.node(id);
    }
    private List<OperatingNetworkService.Segment> previewSegments(Route r){
        var segments=new ArrayList<OperatingNetworkService.Segment>();Topology.Location cursor=null;Topology.Pos previous=null;boolean known=true;var g=graph();
        for(int i=0;i<r.points.size();i++)try{
            var destination=target(r.points.get(i).id);var position=targetPosition(destination);
            if(i==0){try{cursor=g.locate(position,r.dx,0,r.dz);}catch(IllegalArgumentException ex){known=false;}previous=position;continue;}
            if(known)try{
                var before=r.points.get(i-1);if(before.reverse){var tangent=Topology.tangent(cursor);cursor=g.locate(target(before.id).position(),-tangent[0],-tangent[1],-tangent[2]);}
                var plan=g.plan(cursor,List.of(destination));segments.add(new OperatingNetworkService.Segment(plan.samples(0,plan.length(),8192).stream().map(SkyATOLitePlugin::operatingPosition).toList(),false));cursor=plan.end();previous=position;continue;
            }catch(IllegalArgumentException ex){known=false;}
            if(previous!=null&&previous.world().equals(position.world()))segments.add(new OperatingNetworkService.Segment(List.of(operatingPosition(previous),operatingPosition(position)),true));previous=position;
        }catch(IllegalArgumentException ex){known=false;previous=null;}
        return segments;
    }
    private String visitLabel(String id,Topology g){
        if(id.startsWith("station:")){var s=state.signs.get(id.substring(8));return s==null?id:s.option;}
        if(id.startsWith("edge:")){var e=g.edges.get(id.substring(5));return e==null?id:visitLabel(e.from(),g)+" → "+visitLabel(e.to(),g);}
        var n=g.nodes.get(id);return n==null?id:n.name();
    }
    private Topology.Plan validate(Route r) {
        if(r.points.size()<2||!r.points.getFirst().id.startsWith("station:")||!r.points.getLast().id.startsWith("station:"))throw new IllegalArgumentException("首尾必须是 station，至少两个路径点；同一站可在末尾再次绑定");
        var g=graph();var firstId=r.points.getFirst().id;
        var start=RouteValidationFailure.at(1,1,firstId,firstId,visitLabel(firstId,g),visitLabel(firstId,g),()->g.locate(target(firstId).position(),r.dx,0,r.dz));

        List<Topology.Part> parts=new ArrayList<>();var cursor=start;
        for(int i=1;i<=r.points.size();i++) {
            var before=r.points.get(i-1);final int from=i,to=i%r.points.size()+1;var next=r.points.get(to-1);final var departure=cursor;
            cursor=RouteValidationFailure.at(from,to,before.id,next.id,visitLabel(before.id,g),visitLabel(next.id,g),()->{
                if(!before.reverse)return departure;if(!before.id.startsWith("station:"))throw new IllegalArgumentException("只能在 station 折返");var p=departure.edge().path();var a=p.getFirst();var b=p.getLast();return g.locate(target(before.id).position(),a.x()-b.x(),a.y()-b.y(),a.z()-b.z());});
            if(i==r.points.size()&&r.points.getLast().id.equals(r.points.getFirst().id))break;
            final var origin=cursor;var plan=RouteValidationFailure.at(from,to,before.id,next.id,visitLabel(before.id,g),visitLabel(next.id,g),()->g.plan(origin,List.of(target(next.id))));parts.addAll(plan.parts());cursor=plan.end();
        }
        if(parts.isEmpty()||parts.stream().mapToDouble(Topology.Part::length).sum()<1)throw new IllegalArgumentException("不能用零长度路径作为闭环");
        var e=cursor.edge();var p=e.path();double dx=p.getLast().x()-p.getFirst().x(),dz=p.getLast().z()-p.getFirst().z();
        var startEdge=start.edge();var sp=startEdge.path();double sx=sp.getLast().x()-sp.getFirst().x(),sz=sp.getLast().z()-sp.getFirst().z();
        if(dx*sx+dz*sz<0){int from=r.points.size(),to=1;if(r.points.getLast().id.equals(firstId)){from--;to=r.points.size();}var a=r.points.get(from-1).id;var b=r.points.get(to-1).id;throw new RouteValidationFailure(from,to,a,b,visitLabel(a,g),visitLabel(b,g),new IllegalArgumentException("回到首站时方向不符，需显式折返"));}
        return new Topology.Plan(parts,parts.stream().mapToDouble(Topology.Part::length).sum(),g.scale,g.revision);
    }
    private TopologyDependency capture(Topology.Plan plan) {
        var network=getServer().getServicesManager().load(RailNetworkService.class);
        var result=network==null?null:network.captureDependencies(plan.revision(),plan.parts().stream().map(p->p.edge().id()).distinct().toList());
        if(result==null)throw new IllegalArgumentException("STCS 轨道依赖不可用或正在更新，请重试");return result;
    }
    private static TopologyDependency combine(TopologyDependency a,TopologyDependency b) {
        var edges=new TreeMap<String,String>();if(a!=null)edges.putAll(a.edges());edges.putAll(b.edges());
        return new TopologyDependency(9,b.graphRevision(),edges);
    }
    private void ensurePublished(Route r) {
        if(!r.published)throw new IllegalArgumentException("线路未发布，请先验证发布");
        var g=graph();if(r.revision==g.revision&&r.dependency!=null)return;
        var network=getServer().getServicesManager().load(RailNetworkService.class);
        var check=network==null||r.dependency==null?null:network.checkDependencies(r.dependency);
        if(check!=null&&(check.graphRevision()!=g.revision||check.status().equals("UNAVAILABLE")))
            throw new IllegalArgumentException("轨道图正在更新，请重试");
        if(check==null||!check.status().equals("UNCHANGED")) {
            try{r.dependency=capture(validate(r));}
            catch(IllegalArgumentException ex){r.published=false;save();throw ex;}
        }
        r.revision=g.revision;save();
    }
    private SignData station(String ref){
        String name=ref.split("@",2)[0];return state.signs.values().stream().filter(x->x.action.equals("station")&&x.option.equals(name)&&!invalidSigns.contains(x.key())).findFirst().orElseThrow(()->new IllegalArgumentException("停车点不存在或不可用："+name));
    }
    private int entryIndex(Route route,SignData station,String ref){
        String requested=ref.contains("@")?ref.substring(ref.indexOf('@')+1):null;return JourneyEntry.select(definition(route),"station:"+station.key(),requested);
    }
    private synchronized void call(String line,String stationRef) {
        if(closed||storageFailed)throw new IllegalArgumentException("自动运行不可用");
        var publication=state.workspace.line(line);if(publication==null||publication.retired()||publication.published()==null)throw new IllegalArgumentException("线路未发布或已停用");
        var r=route(line);var stop=station(stationRef);int index=entryIndex(r,stop,stationRef);String visit=definition(r).visits().get(index).visitId();
        if(state.pendingCalls.stream().anyMatch(q->q.line.equals(line)&&q.version==r.publicationVersion&&q.entryVisit.equals(visit)))return;
        long now=System.currentTimeMillis();String key=line+"/"+visit;if(now-called.getOrDefault(key,0L)<2000)throw new IllegalArgumentException("叫车过于频繁");
        if(state.pendingCalls.size()>=64)throw new IllegalArgumentException("派车队列已满");called.put(key,now);
        var q=new PendingCall();q.id=UUID.randomUUID();q.line=line;q.station=stationRef;q.entryVisit=visit;q.version=r.publicationVersion;q.created=now;
        q.expires=now+Math.max(5000,Math.min(600000,getConfig().getLong("dispatch.request-timeout-ms",120000)));state.pendingCalls.add(q);save();operatingCache=null;
    }
    /** Rotate a published circuit while keeping its immutable version and the selected arrival heading. */
    private Route entryRoute(Route published,PendingCall q){
        var stop=station(q.station);int selected=entryIndex(published,stop,q.station);var source=definition(published);
        if(!source.visits().get(selected).visitId().equals(q.entryVisit))throw new IllegalArgumentException("始发访问已变化");
        var rotated=JourneyEntry.rotate(graph(),source,selected,this::target);var r=compiled(rotated);r.publicationVersion=published.publicationVersion;r.published=true;r.validated=true;r.revision=published.revision;r.dependency=published.dependency;
        validate(r);return r;
    }
    private void dispatchResult(PendingCall q,String status,UUID train,String depot,Double eta,Double cost,String reason){
        state.dispatchHistory.add(new OperatingNetworkService.Dispatch(q.id,q.line,q.station,status,train,depot,q.created,eta,cost,reason));
        while(state.dispatchHistory.size()>64)state.dispatchHistory.removeFirst();
    }
    private record DispatchOption(AutomaticTrainService.View view,ManeuverPlanner.Journey journey,Route route,DispatchOptimizer.Candidate score,String home) {}
    private record DispatchInput(PendingCall request,Route route) {}
    private record DispatchFleet(AutomaticTrainService.View view,String home) {}
    private record DispatchBatch(long revision,long sampledAt,List<DispatchOptimizer.Candidate> selected,Map<String,DispatchOption> options,boolean budgetLimited,Map<UUID,String> reasons) {}
    private void dispatchPending(AutomaticTrainService service,AutomaticAuthorityService auth,Topology g,long now){
        if(dispatchComputation!=null){if(!dispatchComputation.isDone())return;try{commitDispatch(dispatchComputation.join(),service,g,now);}catch(CompletionException ex){getLogger().warning("派车计算失败；未授予任何运行许可："+ex.getCause());}finally{dispatchComputation=null;}return;}
        if(state.pendingCalls.isEmpty())return;if(now<state.pendingCalls.stream().mapToLong(q->q.created).min().orElse(now)+Math.max(100,getConfig().getLong("dispatch.batch-window-ms",500)))return;var observations=List.copyOf(service.trains());var inputs=new ArrayList<DispatchInput>();boolean changed=false;
        var excluded=new ArrayList<String>();var eligible=new ArrayList<DispatchFleet>();
        for(var v:observations.stream().sorted(java.util.Comparator.comparing(v->v.train().toString())).toList()){
            var reason=DispatchDiagnostics.exclusion(v,state.homes.containsKey(v.train()),state.manualStandby.contains(v.train()),state.jobs.containsKey(v.train()),now);
            if(!reason.isEmpty())DispatchDiagnostics.add(excluded,v.name()+"："+reason);else if(eligible.size()<256)eligible.add(new DispatchFleet(v,state.homes.get(v.train())));else DispatchDiagnostics.add(excluded,v.name()+"：候选数量超过256");
        }
        var fleet=List.copyOf(eligible);
        for(var q:List.copyOf(state.pendingCalls))try{
            if(now>=q.expires)throw new IllegalArgumentException("等待派车超时："+q.reason);
            var publication=state.workspace.line(q.line);if(publication==null||publication.retired()||publication.publishedVersion()!=q.version)throw new IllegalArgumentException("线路发布版已变化或停用，请重新叫车");
            var base=route(q.line);ensurePublished(base);var r=entryRoute(base,q);
            var reusable=state.jobs.values().stream().filter(j->j.line.equals(q.line)&&jobRoute(j).publicationVersion==q.version&&Objects.equals(j.entryVisit,q.entryVisit)&&canReuse(j,view(service,j.train),jobRoute(j))).findFirst().orElse(null);
            if(reusable!=null){reusable.reuseRequested=true;hold(reusable,service,"始发站复用，取消回库");if(!Set.of("RETURN_REVERSING","REUSE_REVERSING").contains(reusable.phase)){reusable.phase="REUSE_RELEASE";reusable.plan=null;resetAuthority(reusable);}dispatchResult(q,"REUSED",reusable.train,reusable.depot,0.0,0.0,"复用始发站列车并刷新站停");state.pendingCalls.remove(q);changed=true;continue;}
            var incoming=state.jobs.values().stream().filter(j->j.line.equals(q.line)&&jobRoute(j).publicationVersion==q.version&&Objects.equals(j.entryVisit,q.entryVisit)&&Set.of("PICKUP","PICKUP_RELEASE","PICKUP_REVERSING","DWELL_PICKUP","REUSE_RELEASE","REUSE_REVERSING").contains(j.phase)).findFirst().orElse(null);
            if(incoming!=null){dispatchResult(q,"MERGED",incoming.train,incoming.depot,null,null,"已有车辆接客，合并重复请求");state.pendingCalls.remove(q);changed=true;continue;}
            var copy=gson.fromJson(gson.toJson(q),PendingCall.class);copy.reason=String.join("；",excluded);inputs.add(new DispatchInput(copy,r));
        }catch(IllegalArgumentException ex){dispatchResult(q,"REJECTED",null,"",null,null,ex.getMessage());state.pendingCalls.remove(q);changed=true;}
        if(changed){save();operatingCache=null;}
        if(inputs.isEmpty())return;
        double gap=depotGap(),cruise=Math.max(.05,getConfig().getDouble("cruise-blocks-per-tick",.4)),scarcity=Math.max(0,getConfig().getDouble("dispatch.last-vehicle-penalty-seconds",0));
        long budget=Math.max(50,Math.min(2000,getConfig().getLong("dispatch.compute-budget-ms",500)));
        var targets=new HashMap<String,Topology.Target>();for(var input:inputs)targets.put(input.request().id.toString(),target(input.route().points.getFirst().id));
        dispatchComputation=CompletableFuture.supplyAsync(()->computeDispatch(g,List.copyOf(inputs),fleet,observations,Map.copyOf(targets),now,gap,cruise,scarcity,budget),dispatchExecutor);
    }
    private static DispatchBatch computeDispatch(Topology g,List<DispatchInput> inputs,List<DispatchFleet> fleet,List<AutomaticTrainService.View> observations,Map<String,Topology.Target> targets,long now,double gap,double cruise,double scarcityPenalty,long budget){
        long deadline=System.nanoTime()+budget*1_000_000;boolean limited=false;var requests=new ArrayList<DispatchOptimizer.Demand>();var options=new LinkedHashMap<String,DispatchOption>();var notes=new LinkedHashMap<UUID,List<String>>();
        outer: for(var input:inputs){var q=input.request();var r=input.route();requests.add(new DispatchOptimizer.Demand(q.id,q.created));var diagnostics=new ArrayList<String>();notes.put(q.id,diagnostics);if(q.reason!=null&&!q.reason.isBlank())diagnostics.add(q.reason);
            for(var vehicle:fleet){
                if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline){limited=true;break outer;}
                var v=vehicle.view();String home=vehicle.home();var policy=r.dispatchPolicy;if(policy.supplyMode().equals("RESTRICTED")&&!r.depots.contains(home)){DispatchDiagnostics.add(diagnostics,v.name()+"：停车组 "+home+" 不在允许组 "+r.depots);continue;}
                var free=DepotLayout.available(g,observations,v.train(),gap/2,now);
                if(!free.reason().isEmpty()){DispatchDiagnostics.add(diagnostics,v.name()+"："+free.reason());continue;}
                try{
                    var journey=ManeuverPlanner.pickup(g,v,targets.get(q.id.toString()),free,cruise,deadline);
                    double eta=journey.seconds(g.scale,cruise)+2;var tangent=Topology.tangent(journey.end());if(tangent[0]*r.dx+tangent[2]*r.dz<0)eta+=8;
                    if(eta>policy.maxPickupSeconds()){DispatchDiagnostics.add(diagnostics,v.name()+" 正向/反向/途中折返：预计接客 "+Math.round(eta)+"s 超过上限 "+policy.maxPickupSeconds()+"s，路径 "+Math.round(journey.length()*g.scale)+"格");continue;}
                    long stock=fleet.stream().filter(t->t.home().equals(home)).count();double scarcity=stock<=1?scarcityPenalty:0;
                    var candidate=new DispatchOptimizer.Candidate(q.id,v.train(),eta,r.depots.isEmpty()||r.depots.contains(home),policy.preferenceSeconds(),scarcity);
                    options.put(q.id+"/"+v.train(),new DispatchOption(v,journey,r,candidate,home));
                    DispatchDiagnostics.add(diagnostics,v.name()+"：可达，"+journey.reversals()+"次换向，预计接客 "+Math.round(eta)+"s");
                }catch(IllegalArgumentException ex){DispatchDiagnostics.add(diagnostics,v.name()+"："+ex.getMessage());}
            }
        }
        var reasons=new HashMap<UUID,String>();for(var e:notes.entrySet())reasons.put(e.getKey(),DispatchDiagnostics.summary(e.getValue(),limited));
        return new DispatchBatch(g.revision,now,DispatchOptimizer.match(requests,options.values().stream().map(DispatchOption::score).toList(),now),Map.copyOf(options),limited,Map.copyOf(reasons));
    }
    private static boolean sameDispatchPosition(AutomaticTrainService.View current,AutomaticTrainService.View original){
        if(!current.members().equals(original.members())||current.body().size()!=original.body().size()||!current.bodyKnown()||current.speedBlocksPerTick()>.001||current.integrityHeld())return false;
        for(int i=0;i<current.body().size();i++)if(DepotLayout.pos(current.body().get(i)).squared(DepotLayout.pos(original.body().get(i)))>.0625)return false;return current.dx()*original.dx()+current.dy()*original.dy()+current.dz()*original.dz()>0;
    }
    private void commitDispatch(DispatchBatch batch,AutomaticTrainService service,Topology g,long now){
        if(closed||storageFailed)return;if(batch.revision()!=g.revision||now-batch.sampledAt()>5000){for(var q:state.pendingCalls)q.reason="计算结果未提交：图版本变化或快照超过5秒，等待重算";operatingCache=null;return;}boolean changed=false;
        for(var q:state.pendingCalls)q.reason=batch.reasons().getOrDefault(q.id,batch.budgetLimited()?"计算预算已到，此请求等待下一批":"等待下一批计算");
        for(var chosen:batch.selected()){
            var q=state.pendingCalls.stream().filter(x->x.id.equals(chosen.request())).findFirst().orElse(null);if(q==null||now>=q.expires)continue;var option=batch.options().get(q.id+"/"+chosen.train());
            var publication=state.workspace.line(q.line);if(publication==null||publication.retired()||publication.publishedVersion()!=q.version||!route(q.line).published)continue;
            try{station(q.station);}catch(IllegalArgumentException ex){continue;}
            var current=view(service,chosen.train());if(current==null||!fresh(current)||current.owner()!=null||!current.status().equals("PARKED")||state.jobs.containsKey(current.train())||graph().revision!=g.revision||!sameDispatchPosition(current,option.view())||state.manualStandby.contains(current.train())||!Objects.equals(state.homes.get(current.train()),option.home()))continue;
            var j=new Job();j.train=current.train();j.owner=UUID.randomUUID();j.routeSnapshot=option.route();j.entryVisit=q.entryVisit;j.line=q.line;j.depot=state.homes.get(j.train);j.revision=g.revision;
            try{j.dependency=journeyDependency(option.route().dependency,option.journey());}catch(IllegalArgumentException ex){q.reason=ex.getMessage();continue;}
            state.jobs.put(j.train,j);save();String result=service.claim(AtoProtocol.Packet.CLAIM.header(),j.train,j.owner);
            if(!result.equals("CLAIMED")){state.jobs.remove(j.train);q.reason=result;changed=true;continue;}
            state.manualBerths.remove(j.train);startJourney(j,option.journey(),"PICKUP",current);j.targetIndex=0;j.reason="派车成本 "+String.format(java.util.Locale.ROOT,"%.1f",chosen.cost())+"秒；预计接客 "+String.format(java.util.Locale.ROOT,"%.1f",chosen.pickupSeconds())+"秒";
            dispatchResult(q,"ASSIGNED",j.train,j.depot,chosen.pickupSeconds(),chosen.cost(),chosen.preferred()?"采用偏好／共享车辆":"偏好代价超过容忍范围或批量匹配更优，使用共享车辆");state.pendingCalls.remove(q);changed=true;
        }
        if(changed){save();operatingCache=null;}
    }
    private TopologyDependency journeyDependency(TopologyDependency base,ManeuverPlanner.Journey journey){
        for(var leg:journey.legs())base=combine(base,capture(leg.plan()));return base;
    }
    private void startJourney(Job j,ManeuverPlanner.Journey journey,String phase,AutomaticTrainService.View v){
        j.journey=journey;j.maneuverLeg=0;j.operation=null;j.maneuverReleased=false;
        if(journey.legs().getFirst().reverseBefore()){j.plan=null;j.phase=phase+"_RELEASE";}
        else {j.phase=phase;installManeuverLeg(j,v);}
    }
    private void installManeuverLeg(Job j,AutomaticTrainService.View v){
        if(j.reuseRequested){j.journey=null;j.plan=null;j.phase="REUSE_RELEASE";resetAuthority(j);return;}
        var leg=j.journey.legs().get(j.maneuverLeg);var first=leg.plan().parts().getFirst();
        if(j.phase.endsWith("REVERSING")){
            var at=locate(v);
            if(!at.edge().id().equals(first.edge().id())||Math.abs(at.offset()-first.from())*graph().scale>1.5)
                throw new IllegalArgumentException("车头未匹配下一段起点");
        }
        j.plan=leg.plan();j.baseline=v.travelledBlocks();j.phase=j.phase.startsWith("RETURN")?"RETURN":"PICKUP";
        j.tracking=new RouteTracking.State();j.fired.clear();j.maneuverReleased=false;resetAuthority(j);
    }
    private boolean reverseReady(Job j,AutomaticTrainService.View v){
        try{
            var g=graph();var free=DepotLayout.available(g,trains().trains(),j.train,depotGap()/2,System.currentTimeMillis());
            var next=ManeuverPlanner.reverseAt(g,locate(v),ManeuverPlanner.span(g,v),free);
            var expected=j.journey.legs().get(j.maneuverLeg).plan().parts().getFirst();
            var rear=v.reference(2);
            return next!=null&&rear!=null&&next.edge().id().equals(expected.edge().id())&&Math.abs(next.offset()-expected.from())*g.scale<=.75
                &&Topology.project(next.edge(),DepotLayout.pos(rear),0,0,0).error()<=.75;
        }catch(IllegalArgumentException ex){j.reason="折返车身/净空复核："+ex.getMessage();return false;}
    }
    private Topology.Location departureStart(AutomaticTrainService.View v,boolean backwards){
        if(!backwards)return locate(v);var rear=v.reference(2);if(rear==null)throw new IllegalArgumentException("列车尾部定位未知");
        return graph().locate(DepotLayout.pos(rear),-v.dx(),-v.dy(),-v.dz());
    }
    private Topology.Location locate(AutomaticTrainService.View v){
        return locateFix(v).location();
    }
    private TrainLocalisation.Fix locateFix(AutomaticTrainService.View v){
        var services=getServer().getServicesManager();var source=services.load(net.skyworld.sta.api.v5.TelemetryService.class);
        var telemetry=source==null?null:source.snapshots().stream().filter(m->m.header().trainId().equals(v.train())&&m.header().kind()==net.skyworld.sta.api.v5.StaMessage.Kind.TELEMETRY_REPORT&&m.header().sessionId().equals(source.sessionId())).findFirst().orElse(null);
        return TrainLocalisation.resolve(graph(),services.load(RailNetworkService.class),v,telemetry,System.currentTimeMillis(),trackingConfig.positionBlocks());
    }
    private static boolean fresh(AutomaticTrainService.View v){long age=System.currentTimeMillis()-v.observedAt();return age>=0&&age<=1000;}
    private static AutomaticTrainService.View view(AutomaticTrainService s,UUID id){return s.trains().stream().filter(v->v.train().equals(id)).findFirst().orElse(null);}
    private void hold(Job j,AutomaticTrainService service,String phase){service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,System.currentTimeMillis()+1000,0,0,true,j.line,"",phase));}
    private static void resetAuthority(Job j) {
        if(j.authoritySession==null)j.authoritySession=new AuthoritySession();j.authoritySession.reset();j.authorityOperation=null;j.nextRequest=0;
    }
    private static void maintainAuthority(Job j,AutomaticAuthorityService auth,Topology g,long now,List<String> desired) {
        if(j.authoritySession==null)j.authoritySession=new AuthoritySession();
        if(j.authorityOperation!=null) {
            if(!j.authorityOperation.isDone())return;
            String status=j.authorityOperation.getNow("FAILED");j.authorityOperation=null;
            if(j.authoritySubmitting&&Set.of("REQUESTED_ACTIVE","REQUESTED","ROUTE_ACTIVE").contains(status))j.authoritySession.accepted(j.authorityRevision,j.authorityPath);
            else if(!j.authoritySubmitting&&!status.equals("RENEWED"))j.authoritySession.reset();
            if(!Set.of("RENEWED","REQUESTED_ACTIVE","REQUESTED","ROUTE_ACTIVE").contains(status))j.reason=status;
        }
        if(now<j.nextRequest)return;
        var path=desired==null?j.authoritySession.path():List.copyOf(desired);
        if(path.isEmpty()||desired==null&&!j.authoritySession.active())return;
        boolean submit=j.authoritySession.needsSubmit(g.revision,path);
        if(submit&&desired==null)return;
        j.nextRequest=now+500;j.authoritySubmitting=submit;j.authorityRevision=g.revision;j.authorityPath=path;
        j.authorityOperation=(submit?auth.request(AtoProtocol.Packet.AUTHORITY_REQUEST.header(),j.train,j.owner,j.owner,g.revision,path,++j.sequence)
            :auth.renew(AtoProtocol.Packet.ROUTE_HEARTBEAT.header(),j.train,j.owner,j.owner,g.revision,++j.sequence))
            .toCompletableFuture().exceptionally(ex->"FAILED");
    }
    private boolean canReuse(Job j,AutomaticTrainService.View v,Route r) {
        return PlatformReusePolicy.eligible(v,j.owner,target(r.points.getFirst().id).position(),j.phase,j.index,r.points.size()-1,
            r.points.getLast().id.equals(r.points.getFirst().id),System.currentTimeMillis());
    }
    private boolean platformHeading(AutomaticTrainService.View v,Route r) {
        var g=graph();var start=g.locate(target(r.points.getFirst().id).position(),r.dx,0,r.dz);
        var tangent=Topology.tangent(start);return tangent[0]*v.dx()+tangent[1]*v.dy()+tangent[2]*v.dz()>0;
    }
    private void reuseReady(Job j,long now) {
        j.journey=null;
        j.index=0;j.targetIndex=0;j.phase="DWELL_PICKUP";j.reuseRequested=false;j.returnReversed=false;
        j.plan=null;j.berth=null;j.parkingTarget=null;j.operation=null;resetAuthority(j);
        var station=state.signs.get(jobRoute(j).points.getFirst().id.substring(8));
        j.dwellUntil=now+Math.round(dwell(j,0)*1000);j.reason="首站接客，站停重新计时";save();
    }
    private synchronized void tick() {
        if(closed||storageFailed)return;var service=trains();var auth=authority();if(service==null||auth==null)return;long now=System.currentTimeMillis();
        for(var j:List.copyOf(state.jobs.values()))if(j.overrideAdmin!=null){var phase=j.phase;boolean released=j.overrideAuthorityReleased;
            if(ManualOverrideFlow.step(j,view(service,j.train),service,auth,now,()->overrideChannelRelease(j.train))){state.jobs.remove(j.train);state.manualStandby.add(j.train);save();operatingCache=null;getLogger().info("override完成："+j.train+"，管理员="+j.overrideAdmin+"；可 /st drive，车身占用保留。");var admin=Bukkit.getPlayer(j.overrideAdmin);if(admin!=null)admin.getScheduler().run(this,t->SalCommandUi.send(admin,"override完成："+j.train+"，可 /st drive。TIMS/ATP保持仍保留。"),null);}
            else if(!phase.equals(j.phase)||released!=j.overrideAuthorityReleased)save();
        }
        var g=graph();
        // Heartbeat all established routes before dispatch and geometry work for
        // any one train. Waiting for traffic never creates a different route.
        for(var j:List.copyOf(state.jobs.values()))if(j.overrideAdmin==null
                &&Set.of("PICKUP","RUNNING","RETURN","DWELL","DWELL_PICKUP").contains(j.phase))
            maintainAuthority(j,auth,g,now,null);
        forgetConfirmedDestructions(now);
        depotHandovers(service,now);
        captureIdleBerths(service);
        if(now>=nextDispatch){nextDispatch=now+Math.max(100,getConfig().getLong("dispatch.batch-window-ms",500));dispatchPending(service,auth,g,now);}
        for(var r:state.routes.values())if(r.published&&(r.revision!=g.revision||r.dependency==null)) {
            try{ensurePublished(r);}catch(IllegalArgumentException ignored){ /* keep invalid routes unpublished */ }
        }
        for(var j:List.copyOf(state.jobs.values())) {
            if(j.overrideAdmin!=null)continue;
            var v=view(service,j.train);
            if(v==null||!fresh(v)||!j.owner.equals(v.owner())){j.reason="车辆观测/控制权不可用";continue;}
            if(j.revision!=g.revision) {
                var network=getServer().getServicesManager().load(RailNetworkService.class);
                var check=network==null||j.dependency==null?null:network.checkDependencies(j.dependency);
                if(check!=null&&check.graphRevision()==g.revision&&check.status().equals("UNCHANGED")) {
                    j.revision=g.revision;
                } else {j.phase="RECOVERY_REQUIRED";j.reason="任务所依赖轨道已变化或尚未完成验证";}
            }
            if(Set.of("PAUSED","RECOVERY_REQUIRED","FAULT").contains(j.phase)){hold(j,service,j.reason);continue;}
            if(ManeuverControl.step(j,v,service,auth,now,vehicle->reverseReady(j,vehicle),()->installManeuverLeg(j,v),()->{
                hold(j,service,"分段折返");if(!j.maneuverReleased&&j.operation==null&&j.phase.endsWith("RELEASE"))maintainAuthority(j,auth,g,now,null);
            }))continue;
            if(j.phase.equals("PICKUP_RELEASE")) {
                hold(j,service,"出库换向：释放旧许可");
                if(j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
                if(j.operation==null||!j.operation.isDone())continue;
                if(!"RELEASED".equals(j.operation.getNow("FAILED"))){releaseRetry(j,j.operation.getNow("FAILED"),now);continue;}
                j.operation=null;
                if(service.reverse(AtoProtocol.Packet.REVERSE.header(),j.train,j.owner).equals("QUEUED"))j.phase="PICKUP_REVERSING";
                continue;
            }
            if(j.phase.equals("PICKUP_REVERSING")) {
                hold(j,service,"出库换向");
                if(v.status().equals("REVERSED")) {
                    try{j.plan=g.shortest(locate(v),target(jobRoute(j).points.getFirst().id),DepotLayout.available(g,service.trains(),j.train,depotGap()/2,now));j.dependency=combine(jobRoute(j).dependency,capture(j.plan));
                        j.baseline=v.travelledBlocks();j.targetIndex=0;j.phase="PICKUP";resetAuthority(j);}
                    catch(IllegalArgumentException ex){j.phase="FAULT";j.reason=ex.getMessage();save();}
                }
                continue;
            }
            if(j.phase.equals("REUSE_RELEASE")) {
                hold(j,service,"首站复用：释放回库许可");
                if(j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
                if(j.operation==null||!j.operation.isDone())continue;
                if(!"RELEASED".equals(j.operation.getNow("FAILED"))){releaseRetry(j,j.operation.getNow("FAILED"),now);continue;}
                j.operation=null;
                if(platformHeading(v,jobRoute(j)))reuseReady(j,now);
                else if(service.reverse(AtoProtocol.Packet.REVERSE.header(),j.train,j.owner).equals("QUEUED"))j.phase="REUSE_REVERSING";
                continue;
            }
            if(j.phase.equals("REUSE_REVERSING")) {
                hold(j,service,"首站复用：恢复发车方向");if(v.status().equals("REVERSED"))reuseReady(j,now);continue;
            }
            if(j.phase.equals("RETURN_RELEASE")) {
                hold(j,service,"回库换向：释放旧许可");
                if(j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
                if(j.operation==null||!j.operation.isDone())continue;
                if(!"RELEASED".equals(j.operation.getNow("FAILED"))){releaseRetry(j,j.operation.getNow("FAILED"),now);continue;}
                j.operation=null;
                if(service.reverse(AtoProtocol.Packet.REVERSE.header(),j.train,j.owner).equals("QUEUED")){j.phase="RETURN_REVERSING";j.returnReversed=true;}
                continue;
            }
            if(j.phase.equals("RETURN_REVERSING")) {
                hold(j,service,"回库换向");
                if(v.status().equals("REVERSED")) {
                    j.plan=null;resetAuthority(j);
                    if(j.reuseRequested){j.phase="REUSE_RELEASE";j.operation=null;}
                    else {j.phase="RETURN";returnPlan(j,v);}
                }
                continue;
            }
            if(v.status().equals("RECOVERED_WAIT_RELEASE")) {
                hold(j,service,"恢复：释放旧许可");
                if(j.operation==null){j.recoveryCompleting=true;j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");}
                if(j.operation.isDone()) {
                    if("RELEASED".equals(j.operation.getNow("FAILED")))service.recover(AtoProtocol.Packet.RECOVER.header(),j.train,j.owner);
                    else {j.phase="FAULT";j.reason="TIMS 恢复后旧许可释放失败";save();}
                }
                continue;
            }
            if(j.recoveryCompleting){j.recoveryCompleting=false;j.operation=null;}
            if(v.integrityHeld()) {
                hold(j,service,v.integrityReason());j.reason="TIMS："+v.integrityReason();
                if(now>=j.nextRecovery&&j.retries<3&&v.speedBlocksPerTick()<=.001){service.recover(AtoProtocol.Packet.RECOVER.header(),j.train,j.owner);j.retries++;j.nextRecovery=now+5000;}
                if(j.retries>=3&&now>=j.nextRecovery){j.phase="FAULT";j.reason="TIMS 自动恢复未成功，请检查";save();}continue;
            }
            if(j.phase.startsWith("RECOVERY_")){recovery(j,v,service,auth,now);continue;}
            if(j.phase.equals("FINISHING")) {
                hold(j,service,"结束任务");
                if(j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
                if(j.operation!=null&&j.operation.isDone()) {
                    if("RELEASED".equals(j.operation.getNow("FAILED"))){
                        String released=service.release(AtoProtocol.Packet.RELEASE.header(),j.train,j.owner);
                        if(released.equals("RELEASING")){if(j.berth!=null){state.homes.put(j.train,j.berth.substring(0,j.berth.indexOf('/')));state.manualBerths.remove(j.train);}state.jobs.remove(j.train);save();getLogger().info("SAL任务结束：train="+v.name()+" ("+j.train+"), berth="+j.berth+", lastReturnIssue="+Objects.toString(j.lastReturnIssue,""));}
                        else {j.phase="FAULT";j.reason=released;save();}
                    }
                    else {
                        String result=j.operation.getNow("FAILED");
                        if(releaseTransient(result)&&j.releaseRetries++<10){j.operation=null;j.nextRequest=now+500;j.reason="等待释放确认："+result;}
                        else {j.phase="FAULT";j.reason="许可释放失败："+result;save();}
                    }
                }continue;
            }
            if(j.phase.startsWith("DWELL")) {
                maintainAuthority(j,auth,g,now,null);
                hold(j,service,j.phase);if(now<j.dwellUntil)continue;
                var r=jobRoute(j);var wp=r.points.get(j.index);
                if(wp.reverse) {
                    if(j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
                    if(j.operation==null)continue;
                    if(!j.operation.isDone())continue;
                    if(!"RELEASED".equals(j.operation.getNow("FAILED"))){releaseRetry(j,j.operation.getNow("FAILED"),now);continue;}
                    if(!service.reverse(AtoProtocol.Packet.REVERSE.header(),j.train,j.owner).equals("QUEUED")){j.phase="FAULT";j.reason="折返条件不满足";save();continue;}
                    j.phase="REVERSING";j.operation=null;j.plan=null;continue;
                }
                nextLeg(j,v);continue;
            }
            if(j.phase.equals("REVERSING")){hold(j,service,"折返");if(v.status().equals("REVERSED"))nextLeg(j,v);continue;}
            if(j.plan==null){if(j.phase.equals("RETURN"))returnPlan(j,v);else nextLeg(j,v);if(j.plan==null){hold(j,service,j.reason);if(j.phase.equals("RETURN"))maintainAuthority(j,auth,g,now,null);continue;}}
            if(j.phase.equals("RETURN")) {
                var sign=state.signs.values().stream().filter(x->x.action.equals("depot")&&x.berth().equals(j.berth)).findFirst().orElse(null);
                var check=sign==null?new ReturnBerthGuard.Check(ReturnBerthGuard.Kind.TARGET_MISSING,"库位牌已不存在："+j.berth):parkingTargetCheck(j,v);
                if(j.returnGuard==null)j.returnGuard=new ReturnBerthGuard.State();
                var action=j.returnGuard.step(check,v.speedBlocksPerTick()<=.001,now,returnRecheckMillis,()->hold(j,service,check.reason()),()->maintainAuthority(j,auth,g,now,null));
                if(action!=ReturnBerthGuard.Action.PROCEED){
                    j.reason=check.reason()+"；已制动复核";j.lastReturnIssue=check.reason();
                    if(now>=j.returnNoticeAt){getLogger().warning("SAL回库复核：train="+v.name()+", berth="+j.berth+", graph="+g.revision+", kind="+check.kind()+", detail="+check.reason());j.returnNoticeAt=now+5000;}
                    if(action==ReturnBerthGuard.Action.REPLAN){j.plan=null;j.parkingTarget=null;j.reason+="；停稳后重新选择库位";save();}
                    else if(action==ReturnBerthGuard.Action.FAULT){j.phase="FAULT";j.reason+="；车身路径持续未知，保持停车，请检查或override";save();}
                    continue;
                }
                // The accepted RailGraph path and berth remain stable while
                // another train uses the throat. STCS limits/extends MA against
                // live occupancy; it is not a reason to discard this plan.
            }
            double expected=Math.max(0,(v.travelledBlocks()-j.baseline)/g.scale);
            if(j.tracking==null)j.tracking=new RouteTracking.State();boolean wasWaiting=j.tracking.pending();RouteTracking.Decision tracked;
            try{tracked=j.tracking.located(j.plan,locateFix(v),expected,trackingConfig,now);}catch(IllegalArgumentException ex){tracked=j.tracking.unavailable(j.plan,ex.getMessage(),trackingConfig,now);}
            if(tracked.verdict()==RouteTracking.Verdict.HOLD){hold(j,service,"定位短暂不确定，停车复核");j.reason="定位复核（保持制动）："+tracked.reason();maintainAuthority(j,auth,g,now,null);continue;}
            if(tracked.verdict()==RouteTracking.Verdict.RECOVER){deviation(j,v,service,tracked.reason());continue;}
            if(wasWaiting)j.reason="定位复核已恢复；仍须满足MA与ATP条件";
            double travelled=tracked.progress();
            double remaining=Math.max(0,j.plan.length()-travelled);
            service.corridor(AtoProtocol.Packet.CHUNK_CORRIDOR.header(),j.train,j.owner,chunks(j.plan,travelled));
            var target=j.phase.equals("RETURN")?j.berth:"第 "+jobRoute(j).points.subList(0,j.targetIndex+1).stream().filter(p->p.id.startsWith("station:")).count()+" 站";
            service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,now+1000,remaining*g.scale,
                    j.cruise>=0?j.cruise:getConfig().getDouble("cruise-blocks-per-tick",.4),true,j.line,target,j.phase));
            triggerMessages(j,service,travelled);
            maintainAuthority(j,auth,g,now,g.authorityEdges(j.plan,0,Math.max(16,v.lengthBlocks()+4)));
            if(remaining<=.06&&v.speedBlocksPerTick()<=.001) {
                hold(j,service,"到达");
                if(j.journey!=null&&j.maneuverLeg+1<j.journey.legs().size()){
                    j.maneuverLeg++;j.plan=null;j.operation=null;j.maneuverReleased=false;
                    j.phase=j.phase+"_RELEASE";j.reason="到达折返点，等待释放旧MA并换向";save();continue;
                }
                j.journey=null;j.plan=null;j.fired.clear();j.releaseRetries=0;
                if(j.phase.equals("RETURN")){if(j.parkingTarget==null||!j.parkingTarget.contains(v)){j.reason="中心停车位置未确认";continue;}j.phase="FINISHING";j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");}
                else {j.index=j.targetIndex;var s=state.signs.get(jobRoute(j).points.get(j.index).id.substring(8));j.phase=j.index==0&&!platformHeading(v,jobRoute(j))?"REUSE_RELEASE":j.index==0?"DWELL_PICKUP":"DWELL";j.dwellUntil=now+Math.round(dwell(j,j.index)*1000);j.reason="停站";}
                save();
            }
        }
    }
    private boolean overrideChannelRelease(UUID train){
        var service=getServer().getServicesManager().load(OperationalAuthorityService.class);if(service==null)return false;
        service.revokeForChannelChange(train);return ManualOverrideFlow.inactive(service.operationalSnapshot(),train,System.currentTimeMillis());
    }
    static String jobReason(Job j){return j.reason+(j.journey==null?"":"；机动段 "+(j.maneuverLeg+1)+"/"+j.journey.legs().size()+"，计划换向 "+j.journey.reversals()+"次")
        +(j.faultCause==null||j.faultCause.isBlank()||j.faultCause.equals(j.reason)?"":"；原始故障["+j.faultAt+"]："+j.faultCause);}
    private void deviation(Job j,AutomaticTrainService.View v,AutomaticTrainService service,String reason) {
        j.journey=null;
        var failedPhase=j.phase;var edges=j.plan==null?"":j.plan.edges(0).toString();j.faultCause=reason.length()>2048?reason.substring(0,2048):reason;j.faultAt=System.currentTimeMillis();
        hold(j,service,reason);j.reason=reason;j.phase="RECOVERY_PREPARE";j.operation=null;j.plan=null;j.berth=null;
        if(!"ROUTE_DEVIATION".equals(j.alarm)) {
            j.alarm="ROUTE_DEVIATION";var events=getServer().getServicesManager().load(RailwayEventService.class);
            if(events!=null)events.publishDetailed("SkyATO-Lite",RailwayEvent.Type.MA_UNAVAILABLE,j.train,v.name(),j.owner,"SkyATO-Lite",
                    "ROUTE_DEVIATION",Map.of("line",j.line,"recovery","PCC_SR","detail",reason.length()>256?reason.substring(0,256):reason,"phase",failedPhase,"graphRevision",Long.toString(j.revision),"position",v.world()+" "+v.x()+","+v.y()+","+v.z(),"planEdges",edges.length()>256?edges.substring(0,256):edges));
            getLogger().warning("SAL route recovery: train="+v.name()+" ("+j.train+"), phase="+failedPhase+", graph="+j.revision+", cause="+reason);
        }
        save();
    }
    private static boolean releaseTransient(String status) {
        return Set.of("STOP_FIRST","TIMS_HOLD","NO_REPORT","DRIVER_RELEASE_PENDING","STALE_REQUEST").contains(status);
    }
    private void releaseRetry(Job j,String status,long now) {
        if(releaseTransient(status)&&j.releaseRetries++<10){j.operation=null;j.nextRequest=now+500;j.reason="等待释放确认："+status;}
        else {j.phase="FAULT";j.reason="许可释放失败："+status;save();}
    }
    private void recovery(Job j,AutomaticTrainService.View v,AutomaticTrainService service,AutomaticAuthorityService auth,long now) {
        var g=graph();
        try{locate(v);}catch(IllegalArgumentException ex){hold(j,service,"恢复等待可信定位");j.reason=ex.getMessage();return;}
        if(j.phase.equals("RECOVERY_PREPARE")) {
            hold(j,service,"偏离线路：停车后准备 SR");
            if(v.speedBlocksPerTick()<=.001&&service.prepareSr(AtoProtocol.Packet.PREPARE_SR.header(),j.train,j.owner).equals("QUEUED")){j.phase="RECOVERY_RELEASE";j.operation=null;j.releaseRetries=0;save();}
            return;
        }
        if(j.phase.equals("RECOVERY_RELEASE")) {
            hold(j,service,"恢复：释放旧 MA");
            if(v.status().equals("SR_WAIT_RELEASE")&&j.operation==null&&now>=j.nextRequest)j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
            if(j.operation!=null&&j.operation.isDone()) {
                if("RELEASED".equals(j.operation.getNow("FAILED"))){service.recover(AtoProtocol.Packet.RECOVER.header(),j.train,j.owner);j.phase="RECOVERY_READY";j.operation=null;}
                else releaseRetry(j,j.operation.getNow("FAILED"),now);
            }
            return;
        }
        if(j.phase.equals("RECOVERY_READY")) {
            hold(j,service,"等待 SR 请求");if(v.status().equals("SR_WAIT_RELEASE")||now<j.nextRequest)return;
            j.operation=auth.requestSr(AtoProtocol.Packet.SR_REQUEST.header(),j.train,j.owner,j.owner,g.revision,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");
            j.phase="RECOVERY_SR";j.reason="等待 PCC 管理员逐段批准 SR";save();return;
        }
        if(j.operation!=null&&j.operation.isDone()) {
            String result=j.operation.getNow("FAILED");j.operation=null;
            if(!Set.of("SR_PENDING","ALREADY_APPROVED").contains(result)) {
                hold(j,service,"SR 请求未确认："+result);
                if(releaseTransient(result)&&j.releaseRetries++<10){j.phase="RECOVERY_READY";j.nextRequest=now+500;}
                else {j.phase="FAULT";j.reason="SR 请求失败："+result;save();}
                return;
            }
        }
        var source=getServer().getServicesManager().load(OperationalAuthorityService.class);var snapshot=source==null?null:source.operationalSnapshot();
        var grant=snapshot==null||!snapshot.status().equals("AVAILABLE")||snapshot.graphRevision()!=g.revision||now<snapshot.emittedAtMillis()||now-snapshot.emittedAtMillis()>1500
                ?null:snapshot.grants().stream().filter(a->a.trainId().equals(j.train)&&a.driverLeaseId().equals(j.owner)&&a.executable()&&a.mode().equals("SR")).findFirst().orElse(null);
        if(grant==null){hold(j,service,"等待 PCC 批准 SR");return;}
        if(!v.mode().equals("SR")){hold(j,service,"接收已批准 SR");service.acceptRecoverySr(AtoProtocol.Packet.ACCEPT_RECOVERY_SR.header(),j.train,j.owner,grant.id());return;}
        if(j.berth!=null&&v.speedBlocksPerTick()<=.001&&state.signs.values().stream().anyMatch(s->s.action.equals("depot")&&s.berth().equals(j.berth)
                &&j.parkingTarget!=null&&j.parkingTarget.contains(v))) {
            j.phase="FINISHING";j.releaseRetries=0;j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");save();return;
        }
        List<Topology.Part> parts=new ArrayList<>();for(var segment:grant.path()) {
            var edge=g.edges.get(segment.edgeId());if(edge==null){hold(j,service,"SR 图数据缺失");return;}
            parts.add(new Topology.Part(edge,segment.fromMeters(),segment.toMeters()));
        }
        var granted=new Topology.Plan(parts,parts.stream().mapToDouble(Topology.Part::length).sum(),g.scale,g.revision);
        service.corridor(AtoProtocol.Packet.CHUNK_CORRIDOR.header(),j.train,j.owner,chunks(granted,0));
        Topology.Plan parking=null;String berth=null;var slots=depotSlots();
        var free=DepotLayout.available(g,service.trains(),j.train,depotGap()/2,now);
        for(var s:state.signs.values())if(s.action.equals("depot")&&returnAllowed(j,s.value)&&!invalidSigns.contains(s.key())) {
            if(state.jobs.values().stream().anyMatch(other->other!=j&&s.berth().equals(other.berth)))continue;
            try {
                var candidate=depotCandidate(v,locate(v),slots.get(s.berth()),free);if(candidate==null)continue;
                boolean inside=candidate.plan().parts().stream().allMatch(p->grant.path().stream().anyMatch(a->a.edgeId().equals(p.edge().id())&&p.from()>=a.fromMeters()-.75&&p.to()<=a.toMeters()+1e-6));
                if(inside&&candidate.plan().length()+1.5<grant.remainingMeters()&&(parking==null||candidate.plan().length()<parking.length())){parking=candidate.plan();berth=s.berth();j.parkingTarget=candidate.target();}
            }catch(IllegalArgumentException ignored){}
        }
        if(berth!=null&&!berth.equals(j.berth)){j.berth=berth;save();}
        double remaining=parking==null?Math.max(0,grant.remainingMeters()-1.5):parking.length();
        service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,now+1000,remaining*g.scale,.1,true,j.line,parking==null?"SR 分段边界":berth,"SR_RECOVERY"));
        if(v.speedBlocksPerTick()<=.001) {
            if(parking!=null&&parking.length()<=.12&&j.parkingTarget!=null&&j.parkingTarget.contains(v)){j.phase="FINISHING";j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");save();}
            else if(parking==null&&grant.remainingMeters()<=2){j.phase="RECOVERY_PREPARE";j.operation=null;save();}
        }
    }
    private void nextLeg(Job j,AutomaticTrainService.View v) {
        j.journey=null;
        var r=jobRoute(j);j.plan=null;j.operation=null;
        if(j.index>=r.points.size()-1){j.phase="RETURN";returnPlan(j,v);return;}
        List<Topology.Target> targets=new ArrayList<>();int next=j.index+1;
        for(;next<r.points.size();next++){targets.add(target(r.points.get(next).id));if(r.points.get(next).id.startsWith("station:"))break;}
        if(next>=r.points.size()){j.phase="FAULT";j.reason="末尾缺少 station";save();return;}
        try{j.plan=graph().plan(locate(v),targets);j.dependency=combine(r.dependency,capture(j.plan));j.baseline=v.travelledBlocks();j.targetIndex=next;j.phase="RUNNING";j.fired.clear();}
        catch(IllegalArgumentException ex){j.phase="FAULT";j.reason=ex.getMessage();save();}
    }
    private double depotGap(){return Math.max(0,getConfig().getDouble("depot.net-gap-blocks",2));}
    private double depotTolerance(){return Math.max(.06,Math.min(.75,getConfig().getDouble("depot.stopping-tolerance-blocks",.5)));}
    private Map<String,DepotLayout.Slot> depotSlots(){
        var g=graph();double margin=Math.max(1,getConfig().getDouble("depot.boundary-margin-blocks",2));
        var marks=state.signs.values().stream().filter(s->s.action.equals("depot")&&!invalidSigns.contains(s.key())).map(s->new DepotLayout.Mark(s.berth(),s.rail())).toList();
        String key=g.revision+":"+depotGap()+":"+margin+":"+marks;
        if(!key.equals(depotLayoutKey)){depotLayoutCache=new DepotLayout(g,depotGap(),margin).build(marks);depotLayoutKey=key;}
        return depotLayoutCache;
    }
    private static AutomaticTrainService.ParkingTarget targetAt(String berth,Topology.Pos pos,double tolerance){
        return new AutomaticTrainService.ParkingTarget(pos.world(),pos.x(),pos.y(),pos.z(),berth,AtoProtocol.Packet.DEPOT_CAPTURE.header(),1,tolerance);
    }
    private AutomaticTrainService.ParkingTarget parkingTarget(SignData berth,AutomaticTrainService.View v){
        if(v==null||!v.bodyKnown())return null;try {var slot=depotSlots().get(berth.berth());var fit=slot==null?null:slot.fit(DepotLayout.extent(graph(),v).lengthBlocks(),depotTolerance());
        return fit==null?null:targetAt(berth.berth(),fit.middle(),depotTolerance());}catch(IllegalArgumentException ex){return null;}
    }
    private ReturnBerthGuard.Check parkingTargetCheck(Job j,AutomaticTrainService.View v){return ReturnBerthGuard.check(depotSlots().get(j.berth),j.parkingTarget,depotTolerance(),()->DepotLayout.extent(graph(),v).lengthBlocks());}
    private boolean parkingTargetValid(Job j,AutomaticTrainService.View v){return parkingTargetCheck(j,v).valid();}
    private record DepotCandidate(Topology.Plan plan,AutomaticTrainService.ParkingTarget target,DepotLayout.Slot slot,ManeuverPlanner.Journey journey) {
        DepotCandidate(Topology.Plan plan,AutomaticTrainService.ParkingTarget target,DepotLayout.Slot slot){this(plan,target,slot,ManeuverPlanner.Journey.direct(plan));}
    }
    private DepotCandidate depotCandidate(AutomaticTrainService.View v,Topology.Location start,DepotLayout.Slot slot,
            java.util.function.Predicate<Topology.Part> available) {
        var arrival=DepotLayout.approach(graph(),v,start,slot,depotTolerance(),available);
        return arrival==null?null:new DepotCandidate(arrival.plan(),targetAt(slot.id(),arrival.fit().middle(),depotTolerance()),slot);
    }
    private DepotCandidate returnCandidate(AutomaticTrainService.View v,Topology.Location start,DepotLayout.Slot slot,
            java.util.function.Predicate<Topology.Part> routeAvailable,java.util.function.Predicate<Topology.Part> berthAvailable,long deadline) {
        var arrival=ManeuverPlanner.depot(graph(),v,start,slot,depotTolerance(),routeAvailable,berthAvailable,Math.max(.05,getConfig().getDouble("cruise-blocks-per-tick",.4)),deadline);
        return arrival==null?null:new DepotCandidate(arrival.journey().first(),targetAt(slot.id(),arrival.fit().middle(),depotTolerance()),slot,arrival.journey());
    }
    private double returnCost(Job j,DepotCandidate candidate,String group){var r=jobRoute(j);double penalty=r.dispatchPolicy==null?30:r.dispatchPolicy.preferenceSeconds();return candidate.journey().seconds(graph().scale,Math.max(.05,getConfig().getDouble("cruise-blocks-per-tick",.4)))+(r.depots.isEmpty()||r.depots.contains(group)?0:penalty);}
    private void returnPlan(Job j,AutomaticTrainService.View v) {
        DepotCandidate best=null;String searchIssue="";var g=graph();var service=trains();var slots=depotSlots();long deadline=System.nanoTime()+200_000_000L;
        var fleet=List.copyOf(service.trains());long now=System.currentTimeMillis();
        var available=DepotLayout.available(g,fleet,j.train,depotGap()/2,now);
        // Other SAL jobs are temporary traffic. Retain parked/manual obstacles
        // when choosing an entrance, and still check the complete destination
        // envelope against every train before reserving a berth.
        var access=DepotLayout.available(g,fleet.stream().filter(t->{var other=state.jobs.get(t.train());return other==null
                ||!Set.of("PICKUP","RUNNING","RETURN","DWELL","DWELL_PICKUP","PICKUP_RELEASE","PICKUP_REVERSING",
                        "RETURN_RELEASE","RETURN_REVERSING","REVERSING","REUSE_RELEASE","REUSE_REVERSING").contains(other.phase);}).toList(),j.train,depotGap()/2,now);
        for(var s:state.signs.values())if(s.action.equals("depot")&&s.value.equals(j.depot)&&!invalidSigns.contains(s.key())) {
            if(state.jobs.values().stream().anyMatch(other->other!=j&&s.berth().equals(other.berth)))continue;
            if(System.nanoTime()>=deadline){searchIssue="本轮回库寻路预算已到，等待下一轮";break;}
            {
                try {
                    var candidate=returnCandidate(v,locate(v),slots.get(s.berth()),access,available,deadline);if(candidate==null)continue;
                    boolean sameRoad=best!=null&&candidate.slot().spans().stream().map(x->x.edge().id()).collect(java.util.stream.Collectors.toSet()).equals(best.slot().spans().stream().map(x->x.edge().id()).collect(java.util.stream.Collectors.toSet()));
                    boolean depthPriority=sameRoad&&candidate.slot().access().equals("SINGLE_ENDED")&&Math.abs(candidate.slot().depth()-best.slot().depth())>.01;
                    if(best==null||depthPriority&&candidate.slot().depth()>best.slot().depth()||!depthPriority&&returnCost(j,candidate,s.value)<returnCost(j,best,best.target().berth().split("/")[0]))best=candidate;
                }catch(IllegalArgumentException ex){searchIssue=Objects.toString(ex.getMessage(),"回库几何未确认");}
            }
        }
        if(best==null){j.reason="等待可达、未受阻且整列可容纳的中心停车位"+(searchIssue.isBlank()?"":"；"+searchIssue);return;}
        j.berth=best.slot().id();j.parkingTarget=best.target();
        j.dependency=journeyDependency(jobRoute(j).dependency,best.journey());
        startJourney(j,best.journey(),"RETURN",v);j.reason="按图回库，"+best.journey().reversals()+"次换向";save();
    }
    private static AutomaticTrainService.ParkingTarget markerTarget(SignData berth){return berth==null?null:targetAt(berth.berth(),berth.rail(),.75);}
    private static AutomaticTrainService.ParkingTarget manualTarget(SignData berth,AutomaticTrainService.View v){
        var middle=v==null?null:v.reference(1);return berth==null||middle==null?null:targetAt(berth.berth(),DepotLayout.pos(middle),.75);
    }
    private SignData handoverBerth(AutomaticTrainService.View v) {
        if(v==null||!fresh(v))return null;var middle=v.reference(1);if(middle==null)return null;
        return state.signs.values().stream().filter(s->s.action.equals("depot")&&!invalidSigns.contains(s.key())&&DepotLayout.covers(graph(),v,s.rail()))
            .min(java.util.Comparator.comparingDouble(s->s.rail().squared(DepotLayout.pos(middle)))).orElse(null);
    }
    private void driverMessage(UUID driver,Component text) {
        var player=Bukkit.getPlayer(driver);if(player!=null)player.getScheduler().run(this,t->{if(player.isOnline()&&player.hasPermission("skyatolite.driver"))player.sendMessage(text);},null);
    }
    private void offerMessage(DepotHandover.Offer offer) {
        var player=Bukkit.getPlayer(offer.driver.driver());if(player==null)return;
        String token=offer.token;
        player.getScheduler().run(this,t->{synchronized(this){
            var service=trains();if(closed||service==null||!depotHandover.deliverable(offer,token,System.currentTimeMillis())||!player.isOnline()
                ||!player.hasPermission("skyatolite.driver")||!player.hasPermission("skytrain.use")
                ||!service.manualDrivers().contains(offer.driver)
                ||!DepotHandover.ready(view(service,offer.driver.train()),offer.target,System.currentTimeMillis()))return;
            player.sendMessage(SalCommandUi.component("可移交至 "+offer.target.berth()+"；六位确认码："+token+"。输入 /sal handover "+token+" 或点击确认。 ")
                .append(Component.text("[确认SAL纳管]",net.kyori.adventure.text.format.NamedTextColor.BLUE).clickEvent(ClickEvent.runCommand("/sal handover "+token))));
            depotHandover.delivered(offer,token);
        }},null);
    }
    private void depotHandovers(AutomaticTrainService service,long now) {
        boolean changed=false;
        // Receipts are published under STF's driver lock before the no-driver snapshot.
        for(var receipt:service.manualReleases())if(!receipt.receipt().equals(state.seenManualReleases.get(receipt.train()))) {
            state.seenManualReleases.put(receipt.train(),receipt.receipt());state.manualStandby.add(receipt.train());changed=true;
            var offer=depotHandover.release(receipt,now);if(offer==null)continue;
            var v=view(service,receipt.train());var berth=handoverBerth(v);
            if(v==null||!fresh(v)||v.owner()!=null||!v.status().equals("IDLE")||v.speedBlocksPerTick()>.001
                ||v.integrityHeld()||berth==null||!offer.target.contains(v)||!markerTarget(berth).equals(offer.marker)||state.jobs.containsKey(v.train())) {
                driverMessage(receipt.driver(),SalCommandUi.component("移管未完成：列车位置、停稳状态或控制权已变化，仍保留手动状态。"));continue;
            }
            String result=service.acceptDepotHandover(AtoProtocol.Packet.DEPOT_HANDOVER_COMMIT.header(),v.train(),UUID.nameUUIDFromBytes(("SAL-PARK:"+v.train()+":"+berth.value).getBytes(java.nio.charset.StandardCharsets.UTF_8)),offer.target,receipt);
            if(result.equals("PARKED")) {
                state.homes.put(v.train(),berth.value);state.manualBerths.put(v.train(),berth.berth());state.manualStandby.remove(v.train());
                driverMessage(receipt.driver(),SalCommandUi.component("移管完成："+v.name()+" 已在 "+berth.berth()+" 转为SAL自动待命。"));
            } else driverMessage(receipt.driver(),SalCommandUi.component("移管未完成："+result+"；仍保留手动状态。"));
        }
        Set<UUID> eligible=new HashSet<>();
        for(var driver:service.manualDrivers()) {
            if(state.jobs.containsKey(driver.train()))continue;
            changed|=state.manualStandby.add(driver.train());
            var v=view(service,driver.train());var berth=handoverBerth(v);if(berth==null)continue;
            var target=manualTarget(berth,v);if(!DepotHandover.ready(v,target,now))continue;
            eligible.add(driver.train());var offer=depotHandover.observe(driver,v,target,now);
            if(offer!=null){offer.marker=markerTarget(berth);offerMessage(offer);}
        }
        depotHandover.retain(eligible);if(changed)save();
    }
    private void captureIdleBerths(AutomaticTrainService service) {
        for(var v:service.trains())if(!state.manualStandby.contains(v.train())&&v.owner()==null&&v.status().equals("IDLE")&&fresh(v)&&!v.integrityHeld()&&!state.jobs.containsKey(v.train())) {
            var home=state.homes.get(v.train());if(home==null)continue;
            for(var s:state.signs.values())if(s.action.equals("depot")&&s.value.equals(home)&&!invalidSigns.contains(s.key())) {
                var target=s.berth().equals(state.manualBerths.get(v.train()))&&DepotLayout.covers(graph(),v,s.rail())?manualTarget(s,v):parkingTarget(s,v);
                if(target!=null&&target.contains(v)) {
                    service.park(v.train(),UUID.nameUUIDFromBytes(("SAL-PARK:"+v.train()+":"+home).getBytes(java.nio.charset.StandardCharsets.UTF_8)),target);break;
                }
            }
        }
    }
    private void forgetConfirmedDestructions(long now) {
        var source=getServer().getServicesManager().load(net.skyworld.sta.api.v3.ConsistObservationService.class);
        if(source==null||source.consistProtocolVersion()!=3)return;
        var roster=List.copyOf(source.consistObservations());boolean changed=false;
        for(var receipt:roster)if(receipt.session().equals(source.sessionId())&&receipt.confirmedDestruction()
            &&now>=receipt.sampledAtMillis()&&now-receipt.sampledAtMillis()<=1500
            &&roster.stream().noneMatch(other->other.train().equals(receipt.train())&&!other.removed())) {
            changed|=state.jobs.remove(receipt.train())!=null;
            changed|=state.homes.remove(receipt.train())!=null;changed|=state.manualBerths.remove(receipt.train())!=null;
            changed|=state.manualStandby.remove(receipt.train());
            changed|=state.seenManualReleases.remove(receipt.train())!=null;
            depotHandover.forget(receipt.train());
        }
        if(changed)save();
    }
    private List<AutomaticTrainService.Chunk> chunks(Topology.Plan plan,double travelled) {
        var chunks=new LinkedHashSet<AutomaticTrainService.Chunk>();
        for(var p:plan.samples(travelled,Math.min(256,plan.length()-travelled),256))chunks.add(new AutomaticTrainService.Chunk(p.world(),((int)Math.floor(p.x()))>>4,((int)Math.floor(p.z()))>>4));
        return chunks.stream().limit(128).toList();
    }
    private void triggerMessages(Job j,AutomaticTrainService service,double travelled) {
        var ordered=state.signs.values().stream().filter(s->!invalidSigns.contains(s.key())&&Set.of("message","v_target","property").contains(s.action))
                .sorted(java.util.Comparator.comparingDouble(s->j.plan.distanceAt(s.rail()))).toList();
        for(var s:ordered) {
            double distance=j.plan.distanceAt(s.rail());
            if(Double.isFinite(distance)&&distance<=travelled+.05&&j.fired.add(s.key())) {
                if(s.action.equals("message"))service.message(AtoProtocol.Packet.PASSENGER_MESSAGE.header(),j.train,j.owner,s.value,s.number);
                // Cruise overrides are applied by the task's next intent, never by changing protection.
                else {j.cruise=s.number;j.reason="V_target="+s.number;}
            }
        }
    }
    private synchronized void preview() {
        if(closed)return;long now=System.currentTimeMillis();
        for(var s:List.copyOf(state.signs.values())) {
            var w=Bukkit.getWorld(s.world);if(w==null||!w.isChunkLoaded(s.x>>4,s.z>>4))continue;
            getServer().getRegionScheduler().run(this,w,s.x>>4,s.z>>4,t->verifySign(s.key()));
        }
        previews.entrySet().removeIf(e->e.getValue().until<now);
        for(var entry:previews.entrySet()) {
            var player=Bukkit.getPlayer(entry.getKey());var workspaceEntry=state.workspace.entries.get(entry.getValue().line);var r=workspaceEntry==null?null:compiled(workspaceEntry.draft);if(player==null||r==null)continue;
            boolean valid=false;List<Topology.Pos> points=new ArrayList<>();
            try{var plan=validate(r);points=plan.samples(0,plan.length(),512);valid=true;}catch(RuntimeException ex){
                try {
                    var first=target(r.points.getFirst().id);var g=graph();
                    if(first.position()!=null) {
                        var cursor=g.locate(first.position(),r.dx,0,r.dz);
                        for(int i=1;i<r.points.size()&&points.size()<512;i++) {
                            var previous=r.points.get(i-1);
                            if(previous.reverse) {var e=cursor.edge();var a=e.path().getFirst();var b=e.path().getLast();cursor=g.locate(target(previous.id).position(),a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
                            var part=g.plan(cursor,List.of(target(r.points.get(i).id)));
                            points.addAll(part.samples(0,part.length(),512-points.size()));cursor=part.end();
                        }
                    }
                }catch(RuntimeException ignored){}
                try {for(var wp:r.points){var t=target(wp.id);points.add(targetPosition(t));}}catch(RuntimeException ignored){}
            }
            var copy=List.copyOf(points);var dust=new Particle.DustOptions(valid?Color.LIME:Color.RED,1);
            player.getScheduler().run(this,t->{for(var p:copy)if(player.getWorld().getName().equals(p.world())){var l=new Location(player.getWorld(),p.x(),p.y()+1,p.z());if(player.getLocation().distanceSquared(l)<96*96)player.spawnParticle(Particle.DUST,l,1,0,0,0,0,dust);}},null);
        }
    }
    private synchronized void verifySign(String id) {
        if(closed)return;var s=state.signs.get(id);if(s==null)return;var w=Bukkit.getWorld(s.world);if(w==null)return;
        var b=w.getBlockAt(s.x,s.y,s.z);if(!readable(b))return;
        boolean valid=false;
        if(b.getState() instanceof Sign sign&&LiteSign.header(sign.getLine(0)))try {
            var actual=LiteSign.parse(sign.getLine(1),sign.getLine(2),sign.getLine(3));
            valid=actual.action().equals(s.action)&&actual.value().equals(s.value)&&(actual.option().equals(s.option)||s.action.equals("station")&&actual.option().equals(s.legacyOption));
        }catch(RuntimeException ignored){}
        if(valid) {
            invalidSigns.remove(id);
            if(s.action.equals("awake")&&!power.containsKey(id)) {
                boolean on=b.isBlockPowered()||b.isBlockIndirectlyPowered();
                if(b.getBlockData() instanceof WallSign wall){var support=b.getRelative(wall.getFacing().getOppositeFace());if(readable(support))on|=support.isBlockPowered()||support.isBlockIndirectlyPowered();}
                power.put(id,on);
            }
        } else if(invalidSigns.add(id)) {
            for(var r:state.routes.values())if(r.points.stream().anyMatch(p->p.id.equals("station:"+id)))r.published=false;
            for(var j:state.jobs.values())if(jobRoute(j).points.stream().anyMatch(p->p.id.equals("station:"+id))
                    ||s.action.equals("depot")&&s.berth().equals(j.berth)) {j.phase="FAULT";j.reason="引用的牌子被移除或改写";}
            save();
        }
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(args.length==1&&!sender.hasPermission("skyatolite.admin")&&sender.hasPermission("skyatolite.driver"))return List.of("release","handover");
        if(!sender.hasPermission("skyatolite.admin"))return List.of();
        if(args.length==1)return List.of("help","route","station","train","awake","status","stop","resume","finish","override","release","handover");
        if(args.length==2&&args[0].equalsIgnoreCase("override")){var service=trains();return service==null?List.of():service.trains().stream().map(AutomaticTrainService.View::name).toList();}
        if(args.length==2&&args[0].equalsIgnoreCase("route"))return List.of("create","bind","choose","direction","depot","supply","list","remove","validate","publish","preview","close","resolve","retire","delete");
        synchronized(this){if(args.length==3&&args[0].equalsIgnoreCase("route"))return List.copyOf(state.workspace.entries.keySet());}
        return List.of();
    }
}
