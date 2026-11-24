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
        String world,action,value,option;int x,y,z,rx,ry,rz;double number;
        String key(){return world+":"+x+":"+y+":"+z;}
        Topology.Pos rail(){return new Topology.Pos(world,rx+.5,ry+.0625,rz+.5);}
        String berth(){return value+"/"+option;}
    }
    static final class Waypoint {String id;boolean reverse;Double dwellSeconds;Waypoint(String id,boolean reverse){this.id=id;this.reverse=reverse;}}
    static final class Route {
        String name;double dx,dz;List<Waypoint> points=new ArrayList<>();long revision=-1;boolean published,validated;
        TopologyDependency dependency;
        Set<String> depots=new LinkedHashSet<>();long publicationVersion;
        Route(String name,double dx,double dz){this.name=name;this.dx=dx;this.dz=dz;}
    }
    static final class Job {
        UUID train,owner;String depot,line,phase="PICKUP",reason="",berth;
        Route routeSnapshot;
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
    static final class State {
        int schema=1;Map<String,SignData> signs=new LinkedHashMap<>();Map<String,Route> routes=new LinkedHashMap<>();
        Map<UUID,String> homes=new LinkedHashMap<>();Map<UUID,Job> jobs=new LinkedHashMap<>();
        Set<UUID> manualStandby=new HashSet<>();Map<UUID,UUID> seenManualReleases=new HashMap<>();
        RouteWorkspace workspace;
        Map<String,TopologyDependency> draftDependencies=new HashMap<>();
    }
    private record Choice(String line,List<String> ids,long revision,long expires,boolean reverse,long draftRevision) {}
    private record Preview(String line,long until) {}
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private State state=new State();private Topology topology;
    private final Map<UUID,Choice> choices=new HashMap<>();private final Map<UUID,Preview> previews=new HashMap<>();
    private final Map<String,Boolean> power=new HashMap<>();private final Map<String,Long> called=new HashMap<>();
    private final Set<String> invalidSigns=new HashSet<>();
    private final DepotHandover depotHandover=new DepotHandover();
    private final UUID operatingSession=UUID.randomUUID();
    private OperatingNetworkService.Snapshot operatingCache;
    private volatile boolean closed;
    private boolean storageFailed;
    private Path file;
    public void onEnable() {
        saveDefaultConfig();file=getDataFolder().toPath().resolve("state.json");
        try {
            double speed=getConfig().getDouble("cruise-blocks-per-tick",.4);
            if(!Double.isFinite(speed)||speed<0||speed>100)throw new IOException("Invalid cruise speed");
            if(Files.exists(file)) {state=gson.fromJson(Files.readString(file),State.class);
                if(state==null||state.schema!=1||state.signs==null||state.routes==null||state.homes==null||state.jobs==null)throw new IOException("Invalid state schema");}
            for(var j:state.jobs.values()){j.phase="RECOVERY_REQUIRED";j.reason="重启后等待管理员确认";j.fired=new HashSet<>();}
            initialiseWorkspace();
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
        closed=true;
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
                else {
                    Topology.Pos previous=null;
                    for(var point:route.points)try {
                        var target=target(point.id);var position=targetPosition(target);
                        if(position!=null&&previous!=null&&previous.world().equals(position.world()))
                            segments.add(new OperatingNetworkService.Segment(List.of(operatingPosition(previous),operatingPosition(position)),true));
                        previous=position;
                    }catch(IllegalArgumentException ex){previous=null;}
                }
                List<OperatingNetworkService.Stop> stops=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(var point:route.points)if(point.id.startsWith("station:")&&seen.add(point.id)) {
                    var sign=state.signs.get(point.id.substring(8));
                    if(sign!=null)stops.add(new OperatingNetworkService.Stop(point.id,route.name+" · "+(stops.size()+1),operatingPosition(sign.rail()),sign.number));
                }
                routes.add(new OperatingNetworkService.Route(route.name,status,reason,segments,stops));
            }
            var slots=state.signs.values().stream().filter(s->s.action.equals("depot")).map(s->
                new OperatingProjection.Slot(s.value,s.berth(),s.option,operatingPosition(s.rail()),!invalidSigns.contains(s.key()))).toList();
            Map<UUID,OperatingProjection.Task> jobs=new HashMap<>();state.jobs.forEach((id,j)->jobs.put(id,new OperatingProjection.Task(j.depot,j.line,j.phase,j.reason,j.berth)));
            var trains=trains();if(trains==null)throw new IllegalStateException("STF unavailable");
            var fleet=OperatingProjection.trains(state.homes,jobs,trains.trains(),slots,now);
            fleet=fleet.stream().map(t->{var job=state.jobs.get(t.id());return new OperatingNetworkService.Train(t.id(),t.name(),t.home(),t.line(),t.status(),t.phase(),t.reason(),t.berth(),t.position(),t.observedAtMillis(),t.fresh(),job==null||job.routeSnapshot==null?0:job.routeSnapshot.publicationVersion);}).toList();
            if(network.graphRevision()!=g.revision)throw new IllegalStateException("Graph changing");
            operatingCache=new OperatingNetworkService.Snapshot(1,"AVAILABLE",operatingSession,g.revision,now,routes,OperatingProjection.depots(slots,fleet,jobs),fleet);
        }catch(RuntimeException ex){operatingCache=new OperatingNetworkService.Snapshot(1,"UNAVAILABLE",operatingSession,0,now,List.of(),List.of(),List.of());}
        return operatingCache;
    }
    private Set<String> depotNames(){return state.signs.values().stream().filter(s->s.action.equals("depot")).map(s->s.value).collect(java.util.stream.Collectors.toCollection(TreeSet::new));}
    private RouteEditingService.Definition definition(Route r){return new RouteEditingService.Definition(r.name,r.dx,r.dz,
        r.points.stream().map(p->new RouteEditingService.Visit(p.id,p.reverse,p.dwellSeconds)).toList(),r.depots==null?Set.of():r.depots);}
    private Route compiled(RouteEditingService.Definition d){
        var r=new Route(d.name(),d.dx(),d.dz());r.depots=new LinkedHashSet<>(d.depots());
        for(var v:d.visits()){var p=new Waypoint(v.deviceId(),v.reverse());p.dwellSeconds=v.dwellSeconds();
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
        for(var r:state.routes.values())if(r.depots==null)r.depots=new LinkedHashSet<>();
        for(var j:state.jobs.values())if(j.routeSnapshot!=null&&j.routeSnapshot.publicationVersion==0){var line=state.workspace.line(j.line);if(line!=null&&line.publishedVersion()==1)j.routeSnapshot.publicationVersion=1;}
    }
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
        if(d.depots().isEmpty())throw new IllegalArgumentException("DEPOT_REQUIRED");
        if(!depotNames().containsAll(d.depots()))throw new IllegalArgumentException("DEPOT_NOT_FOUND");
        for(var v:d.visits()){
            target(v.deviceId());if(v.reverse()&&!v.deviceId().startsWith("station:"))throw new IllegalArgumentException("REVERSE_REQUIRES_STATION");
            if(v.deviceId().startsWith("station:")&&!state.signs.get(v.deviceId().substring(8)).option.equals(d.name()))throw new IllegalArgumentException("STATION_ROUTE_MISMATCH");
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
                operatingPosition(s.action.equals("awake")?new Topology.Pos(s.world,s.x+.5,s.y,s.z+.5):s.rail()),s.action.equals("station")||s.action.equals("awake")?s.option:"",s.number,!invalidSigns.contains(s.key())));
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
            var segments=new ArrayList<OperatingNetworkService.Segment>();boolean loop=false;
            if(d!=null)try{var plan=validate(compiled(d));segments.add(new OperatingNetworkService.Segment(plan.samples(0,plan.length(),8192).stream().map(SkyATOLitePlugin::operatingPosition).toList(),false));loop=true;}
            catch(IllegalArgumentException ex){Topology.Pos previous=null;for(var visit:d.visits())try{
                var t=target(visit.deviceId());var p=targetPosition(t);
                if(previous!=null&&previous.world().equals(p.world()))segments.add(new OperatingNetworkService.Segment(List.of(operatingPosition(previous),operatingPosition(p)),true));previous=p;
            }catch(RuntimeException ignored){previous=null;}}
            return new RouteEditingService.Reply(reply.requestId(),reply.status(),reply.reason(),reply.session(),reply.line(),g.revision,segments,loop,state.workspace.sequence);
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
            if(d.action.equals("station")&&!state.workspace.entries.containsKey(d.option))throw new IllegalArgumentException("先 /sal route create "+d.option);
            guardSignEdit(event.getPlayer(),id,d);
            var old=state.signs.put(id,d);power.remove(id);
            invalidSigns.remove(id);
            if(d.action.equals("awake")) {
                boolean on=b.isBlockPowered()||b.isBlockIndirectlyPowered();
                if(b.getBlockData() instanceof WallSign wall){var support=b.getRelative(wall.getFacing().getOppositeFace());if(readable(support))on|=support.isBlockPowered()||support.isBlockIndirectlyPowered();}
                power.put(id,on);
            }
            if(old!=null&&old.action.equals("station")&&(!d.action.equals("station")||!old.option.equals(d.option))) changeStationDraft(event.getPlayer(),old.option,id,false);
            if(d.action.equals("station"))changeStationDraft(event.getPlayer(),d.option,id,true);
            save();SalCommandUi.send(event.getPlayer(),"已登记："+d.action+" "+d.value+" "+d.option);
        }catch(RuntimeException ex){event.setCancelled(true);SalCommandUi.send(event.getPlayer(),"§c"+ex.getMessage());}
    }
    private boolean busySign(String id){return state.jobs.values().stream().anyMatch(j->jobRoute(j).points.stream().anyMatch(p->p.id.equals("station:"+id)))
        ||state.jobs.values().stream().anyMatch(j->{var s=state.signs.get(id);return s!=null&&s.action.equals("depot")&&s.value.equals(j.depot);});}
    private void guardSignEdit(CommandSender sender,String id,SignData replacement){
        var old=state.signs.get(id);var affected=new LinkedHashSet<String>();
        for(var s:Arrays.asList(old,replacement))if(s!=null){
            if(s.action.equals("station"))affected.add(s.option);
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
        gameSave(sender,line,new RouteEditingService.Definition(line,d.dx(),d.dz(),points,d.depots()));
    }
    private void removeSign(String id,CommandSender sender){
        if(busySign(id))throw new IllegalArgumentException("此设备被运行任务引用，先结束任务");
        guardSignEdit(sender,id,null);var previous=state.signs.remove(id);
        if(previous!=null&&previous.action.equals("station"))changeStationDraft(sender,previous.option,id,false);
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
        var d=state.signs.get(id);if(d==null||!d.action.equals("awake"))return;var w=Bukkit.getWorld(d.world);if(w==null)return;
        var b=w.getBlockAt(d.x,d.y,d.z);if(!readable(b)||!(b.getState() instanceof Sign sign))return;
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
        if(args.length!=2)throw new IllegalArgumentException("请点击库位停稳后收到的 SAL 纳管确认按钮");
        var driver=service.manualDrivers().stream().filter(d->d.train().equals(id)&&d.driver().equals(player.getUniqueId())).findFirst()
            .orElseThrow(()->new IllegalArgumentException("你没有本车当前驾驶权，请先 /st drive"));
        var berth=handoverBerth(v);
        if(berth==null||!DepotHandover.ready(v,parkingTarget(berth),System.currentTimeMillis()))throw new IllegalArgumentException("列车须完整停稳在库位上");
        var offer=depotHandover.confirm(id,player.getUniqueId(),driver.lease(),UUID.fromString(args[1]),System.currentTimeMillis());
        if(!offer.target.equals(parkingTarget(berth))){depotHandover.forget(id);throw new IllegalArgumentException("库位已经变化，请重新停稳");}
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

            if(args[0].equalsIgnoreCase("status")){for(var j:state.jobs.values())SalCommandUi.send(sender,j.train+" "+j.line+" "+j.phase+" "+j.reason);if(state.jobs.isEmpty())SalCommandUi.send(sender,"没有运行任务");return true;}
            if(args[0].equalsIgnoreCase("awake")&&args.length==3){call(args[1],args[2]);SalCommandUi.send(sender,"已提交叫车");return true;}
            if(args[0].equalsIgnoreCase("route")){routeCommand(sender,args);return true;}
            var service=Objects.requireNonNull(trains(),"STF 自动控制接口不可用");
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
            var begin=gameBegin(sender,c.line);var d=begin.line().draft();var visits=new ArrayList<>(d.visits());visits.add(new RouteEditingService.Visit(c.ids.get(index),c.reverse,null));
            gameSave(sender,c.line,new RouteEditingService.Definition(d.name(),d.dx(),d.dz(),visits,d.depots()));return;
        }
        if(action.equals("create")){
            double dx=1,dz=0;if(sender instanceof Player p){var dir=p.getLocation().getDirection();dx=dir.getX();dz=dir.getZ();}
            var depots=a.length>3?new LinkedHashSet<>(Arrays.asList(a[3].split(","))):depotNames();
            if(a.length<=3&&depots.size()!=1)throw new IllegalArgumentException("请指定至少一个已有车辆段：/sal route create <线路> <车辆段[,车辆段]>");
            gameRequest(sender,"CREATE",name,null,new RouteEditingService.Definition(name,dx,dz,List.of(),depots),null);
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
        var begin=gameBegin(sender,name);var d=begin.line().draft();var visits=new ArrayList<>(d.visits());double dx=d.dx(),dz=d.dz();var depots=d.depots();
        switch(action){
            case "direction" -> {if(a.length<4)throw new IllegalArgumentException("需要 north/south/east/west");switch(a[3]){case "north"->{dx=0;dz=-1;}case "south"->{dx=0;dz=1;}case "east"->{dx=1;dz=0;}case "west"->{dx=-1;dz=0;}default->throw new IllegalArgumentException("方向无效");}}
            case "depot" -> {if(a.length<4)throw new IllegalArgumentException("至少一个车辆段");depots=new LinkedHashSet<>(Arrays.asList(a[3].split(",")));}
            case "remove" -> {if(a.length<4)throw new IllegalArgumentException("需要序号");int index=Integer.parseInt(a[3])-1;if(index<0||index>=visits.size())throw new IllegalArgumentException("序号无效");visits.remove(index);}
            case "bind" -> {
                if(!(sender instanceof Player p))throw new IllegalArgumentException("站在设备旁操作");
                boolean reverse=a.length>3&&a[a.length-1].equalsIgnoreCase("reverse");
                if(a.length>3&&!a[3].equalsIgnoreCase("reverse")){target(a[3]);visits.add(new RouteEditingService.Visit(a[3],reverse,null));}
                else {
                    var loc=p.getLocation();var pos=new Topology.Pos(loc.getWorld().getName(),loc.getX(),loc.getY(),loc.getZ());var ids=new ArrayList<String>();
                    for(var n:graph().nodes.values())if(n.rail()!=null&&n.rail().squared(pos)<=64)ids.add(n.id());
                    for(var sign:state.signs.values())if(sign.action.equals("station")&&sign.option.equals(name)&&sign.rail().squared(pos)<=64)ids.add("station:"+sign.key());
                    if(ids.isEmpty())throw new IllegalArgumentException("8格内无本线路设备");
                    if(ids.size()>1){choices.put(p.getUniqueId(),new Choice(name,List.copyOf(ids),graph().revision,System.currentTimeMillis()+60000,reverse,line.revision()));for(int i=0;i<ids.size();i++)SalCommandUi.send(sender,(i+1)+". "+ids.get(i));SalCommandUi.send(sender,"/sal route choose <编号>（60秒内）");return;}
                    visits.add(new RouteEditingService.Visit(ids.getFirst(),reverse,null));
                }
            }
            case "validate","publish" -> {gameRequest(sender,"VALIDATE",name,begin.session(),null,null);if(action.equals("publish")){gameRequest(sender,"PUBLISH",name,begin.session(),null,null);gameRequest(sender,"CANCEL",name,begin.session(),null,null);}SalCommandUi.send(sender,action.equals("publish")?"已发布新版本，既有任务保持原版本":"草稿已验证");return;}
            case "close" -> {gameRequest(sender,"CANCEL",name,begin.session(),null,null);return;}
            case "delete","retire" -> {gameRequest(sender,action.equals("delete")?"DELETE":"RETIRE",name,begin.session(),null,null);return;}
            default -> throw new IllegalArgumentException("未知线路操作");
        }
        gameSave(sender,name,new RouteEditingService.Definition(name,dx,dz,visits,depots));SalCommandUi.send(sender,"草稿已保存；已发布版及既有任务未改变");
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
    private Topology.Plan validate(Route r) {
        if(r.points.size()<2||!r.points.getFirst().id.startsWith("station:")||!r.points.getLast().id.startsWith("station:"))throw new IllegalArgumentException("首尾必须是 station，至少两个路径点；同一站可在末尾再次绑定");
        var first=target(r.points.getFirst().id).position();var g=graph();var start=g.locate(first,r.dx,0,r.dz);
        for(var wp:r.points)if(wp.id.startsWith("station:")&&!state.signs.get(wp.id.substring(8)).option.equals(r.name))throw new IllegalArgumentException("station 绑定的是另一条运营线路");
        List<Topology.Part> parts=new ArrayList<>();var cursor=start;
        for(int i=1;i<=r.points.size();i++) {
            var before=r.points.get(i-1);
            if(before.reverse){if(!before.id.startsWith("station:"))throw new IllegalArgumentException("只能在 station 折返");var p=cursor.edge().path();var a=p.getFirst();var b=p.getLast();cursor=g.locate(target(before.id).position(),a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
            if(i==r.points.size()&&r.points.getLast().id.equals(r.points.getFirst().id))break;
            var plan=g.plan(cursor,List.of(target(r.points.get(i%r.points.size()).id)));parts.addAll(plan.parts());cursor=plan.end();
        }
        if(parts.isEmpty()||parts.stream().mapToDouble(Topology.Part::length).sum()<1)throw new IllegalArgumentException("不能用零长度路径作为闭环");
        var e=cursor.edge();var p=e.path();double dx=p.getLast().x()-p.getFirst().x(),dz=p.getLast().z()-p.getFirst().z();
        var startEdge=start.edge();var sp=startEdge.path();double sx=sp.getLast().x()-sp.getFirst().x(),sz=sp.getLast().z()-sp.getFirst().z();
        if(dx*sx+dz*sz<0)throw new IllegalArgumentException("回到首站时方向不符，需显式折返");
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
    private synchronized void call(String depot,String line) {
        if(closed||storageFailed)throw new IllegalArgumentException("自动运行不可用");
        var publication=state.workspace.line(line);if(publication==null||publication.retired()||publication.published()==null)throw new IllegalArgumentException("线路未发布或已停用");
        if(!publication.published().depots().contains(depot))throw new IllegalArgumentException("车辆段未绑定此运营线路");
        var r=route(line);var g=graph();
        ensurePublished(r);
        String entry=depot+"/"+line;long now=System.currentTimeMillis();if(now-called.getOrDefault(entry,0L)<2000)throw new IllegalArgumentException("叫车过于频繁");called.put(entry,now);
        var service=Objects.requireNonNull(trains(),"STF 不可用");if(authority()==null)throw new IllegalArgumentException("MA 服务不可用");
        for(var j:state.jobs.values())if(j.line.equals(line)&&j.depot.equals(depot)) {
            var v=view(service,j.train);
            if(jobRoute(j).publicationVersion==r.publicationVersion&&canReuse(j,v,jobRoute(j))) {
                j.reuseRequested=true;hold(j,service,"首站复用，取消回库");
                if(!Set.of("RETURN_REVERSING","REUSE_REVERSING").contains(j.phase)){j.phase="REUSE_RELEASE";j.plan=null;resetAuthority(j);}
                j.reason="复用首站列车，重新计站停时间";save();return;
            }
        }
        if(state.jobs.values().stream().anyMatch(j->j.line.equals(line)&&j.depot.equals(depot)&&Set.of("PICKUP","PICKUP_RELEASE","PICKUP_REVERSING","DWELL_PICKUP","REUSE_RELEASE","REUSE_REVERSING").contains(j.phase)))
            throw new IllegalArgumentException("已有车辆正在接客");
        AutomaticTrainService.View chosen=null;Topology.Plan best=null;boolean reverse=false;
        for(var v:service.trains())if(depot.equals(state.homes.get(v.train()))&&!state.manualStandby.contains(v.train())&&!state.jobs.containsKey(v.train())&&v.owner()==null&&Set.of("IDLE","PARKED").contains(v.status())&&fresh(v)&&v.speedBlocksPerTick()<=.001&&!v.integrityHeld()) {
            for(boolean backwards:new boolean[]{false,true})try {
                var start=backwards?g.locate(new Topology.Pos(v.world(),v.x(),v.y(),v.z()),-v.dx(),-v.dy(),-v.dz()):locate(v);
                var plan=g.shortest(start,target(r.points.getFirst().id));
                if(best==null||plan.length()<best.length()){chosen=v;best=plan;reverse=backwards;}
            }catch(IllegalArgumentException ignored){}
        }
        if(chosen==null)throw new IllegalArgumentException("没有完整、空闲且能够到达首站的归属列车");
        var j=new Job();j.routeSnapshot=gson.fromJson(gson.toJson(r),Route.class);j.train=chosen.train();j.owner=UUID.randomUUID();j.depot=depot;j.line=line;j.revision=g.revision;
        j.dependency=combine(r.dependency,capture(best));
        state.jobs.put(j.train,j);save();String result=service.claim(AtoProtocol.Packet.CLAIM.header(),j.train,j.owner);
        if(!result.equals("CLAIMED")){state.jobs.remove(j.train);save();throw new IllegalArgumentException(result);}
        j.plan=reverse?null:best;j.baseline=chosen.travelledBlocks();j.targetIndex=0;j.phase=reverse?"PICKUP_RELEASE":"PICKUP";j.reason=reverse?"换向后前往首站":"前往首站";save();
    }
    private Topology.Location locate(AutomaticTrainService.View v){
        var service=getServer().getServicesManager().load(RailNetworkService.class);
        var nav=service.query(new RailNetworkService.RailQuery(v.world(),(int)Math.floor(v.x()),(int)Math.floor(v.y()),(int)Math.floor(v.z()),v.dx(),v.dy(),v.dz()));
        var p=nav==null?null:nav.position();var g=graph();
        if(p==null||!p.graphCurrent()||p.stale()||p.graphRevision()!=g.revision||!g.edges.containsKey(p.edgeId()))throw new IllegalArgumentException("STA 定位不确定");
        var projected=Topology.project(g.edges.get(p.edgeId()),new Topology.Pos(v.world(),v.x(),v.y(),v.z()),v.dx(),v.dy(),v.dz());
        var physical=g.locate(new Topology.Pos(v.world(),v.x(),v.y(),v.z()),v.dx(),v.dy(),v.dz());
        if(physical.error()+1e-4<projected.error()&&(physical.edge().to().equals(projected.edge().from())
                ||projected.edge().to().equals(physical.edge().from())))projected=physical;
        if(projected.error()>1.5)throw new IllegalArgumentException("实体与图位置不符");return projected;
    }
    private static boolean fresh(AutomaticTrainService.View v){long age=System.currentTimeMillis()-v.observedAt();return age>=0&&age<=1000;}
    private static AutomaticTrainService.View view(AutomaticTrainService s,UUID id){return s.trains().stream().filter(v->v.train().equals(id)).findFirst().orElse(null);}
    private void hold(Job j,AutomaticTrainService service,String phase){service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,System.currentTimeMillis()+1000,0,0,true,j.line,"",phase));}
    private static void resetAuthority(Job j) {
        if(j.authoritySession==null)j.authoritySession=new AuthoritySession();j.authoritySession.reset();j.authorityOperation=null;j.nextRequest=0;
    }
    private void maintainAuthority(Job j,AutomaticAuthorityService auth,Topology g,long now,List<String> desired) {
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
        var points=start.edge().path();var a=points.getFirst();var b=points.getLast();
        return (b.x()-a.x())*v.dx()+(b.y()-a.y())*v.dy()+(b.z()-a.z())*v.dz()>0;
    }
    private void reuseReady(Job j,long now) {
        j.index=0;j.targetIndex=0;j.phase="DWELL_PICKUP";j.reuseRequested=false;j.returnReversed=false;
        j.plan=null;j.berth=null;j.operation=null;resetAuthority(j);
        var station=state.signs.get(jobRoute(j).points.getFirst().id.substring(8));
        j.dwellUntil=now+Math.round(dwell(j,0)*1000);j.reason="首站接客，站停重新计时";save();
    }
    private synchronized void tick() {
        if(closed||storageFailed)return;var service=trains();var auth=authority();if(service==null||auth==null)return;var g=graph();long now=System.currentTimeMillis();
        forgetConfirmedDestructions(now);
        depotHandovers(service,now);
        captureIdleBerths(service);
        for(var r:state.routes.values())if(r.published&&(r.revision!=g.revision||r.dependency==null)) {
            try{ensurePublished(r);}catch(IllegalArgumentException ignored){ /* keep invalid routes unpublished */ }
        }
        for(var j:List.copyOf(state.jobs.values())) {
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
                    try{j.plan=g.shortest(locate(v),target(jobRoute(j).points.getFirst().id));j.dependency=combine(jobRoute(j).dependency,capture(j.plan));
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
                        if(released.equals("RELEASING")){state.jobs.remove(j.train);save();}
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
            if(j.plan==null){if(j.phase.equals("RETURN"))returnPlan(j,v);else nextLeg(j,v);if(j.plan==null){hold(j,service,j.reason);continue;}}
            double expected=Math.max(0,(v.travelledBlocks()-j.baseline)/g.scale);
            double travelled;
            try{travelled=j.plan.progress(locate(v),expected);}catch(IllegalArgumentException ex){deviation(j,v,service,ex.getMessage());continue;}
            if(!Double.isFinite(travelled)){deviation(j,v,service,"列车偏离任务路径");continue;}
            double remaining=Math.max(0,j.plan.length()-travelled);
            service.corridor(AtoProtocol.Packet.CHUNK_CORRIDOR.header(),j.train,j.owner,chunks(j.plan,travelled));
            var target=j.phase.equals("RETURN")?j.berth:"第 "+jobRoute(j).points.subList(0,j.targetIndex+1).stream().filter(p->p.id.startsWith("station:")).count()+" 站";
            service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,now+1000,remaining*g.scale,
                    j.cruise>=0?j.cruise:getConfig().getDouble("cruise-blocks-per-tick",.4),true,j.line,target,j.phase));
            triggerMessages(j,service,travelled);
            maintainAuthority(j,auth,g,now,g.authorityEdges(j.plan,0,Math.max(16,v.lengthBlocks()+4)));
            if(remaining<=.06&&v.speedBlocksPerTick()<=.001) {
                hold(j,service,"到达");j.plan=null;j.fired.clear();j.releaseRetries=0;
                if(j.phase.equals("RETURN")){j.phase="FINISHING";j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");}
                else {j.index=j.targetIndex;var s=state.signs.get(jobRoute(j).points.get(j.index).id.substring(8));j.phase=j.index==0?"DWELL_PICKUP":"DWELL";j.dwellUntil=now+Math.round(dwell(j,j.index)*1000);j.reason="停站";}
                save();
            }
        }
    }
    private void deviation(Job j,AutomaticTrainService.View v,AutomaticTrainService service,String reason) {
        hold(j,service,reason);j.reason=reason;j.phase="RECOVERY_PREPARE";j.operation=null;j.plan=null;j.berth=null;
        if(!"ROUTE_DEVIATION".equals(j.alarm)) {
            j.alarm="ROUTE_DEVIATION";var events=getServer().getServicesManager().load(RailwayEventService.class);
            if(events!=null)events.publishDetailed("SkyATO-Lite",RailwayEvent.Type.MA_UNAVAILABLE,j.train,v.name(),j.owner,"SkyATO-Lite",
                    "ROUTE_DEVIATION",Map.of("line",j.line,"recovery","PCC_SR","detail",reason.length()>256?reason.substring(0,256):reason));
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
                &&s.rail().squared(new Topology.Pos(v.world(),v.x(),v.y(),v.z()))<=.0144)) {
            j.phase="FINISHING";j.releaseRetries=0;j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");save();return;
        }
        List<Topology.Part> parts=new ArrayList<>();for(var segment:grant.path()) {
            var edge=g.edges.get(segment.edgeId());if(edge==null){hold(j,service,"SR 图数据缺失");return;}
            parts.add(new Topology.Part(edge,segment.fromMeters(),segment.toMeters()));
        }
        var granted=new Topology.Plan(parts,parts.stream().mapToDouble(Topology.Part::length).sum(),g.scale,g.revision);
        service.corridor(AtoProtocol.Packet.CHUNK_CORRIDOR.header(),j.train,j.owner,chunks(granted,0));
        Topology.Plan parking=null;String berth=null;
        for(var s:state.signs.values())if(s.action.equals("depot")&&s.value.equals(j.depot)&&!invalidSigns.contains(s.key())) {
            if(state.jobs.values().stream().anyMatch(other->other!=j&&s.berth().equals(other.berth)))continue;
            if(service.trains().stream().anyMatch(other->!other.train().equals(j.train)&&new Topology.Pos(other.world(),other.x(),other.y(),other.z()).squared(s.rail())<Math.pow(other.lengthBlocks()+2,2)))continue;
            try {
                var candidate=g.plan(locate(v),List.of(Topology.Target.pos(s.rail())));
                boolean inside=candidate.parts().stream().allMatch(p->grant.path().stream().anyMatch(a->a.edgeId().equals(p.edge().id())&&p.from()>=a.fromMeters()-.75&&p.to()<=a.toMeters()+1e-6));
                if(inside&&candidate.length()+1.5<grant.remainingMeters()&&stopFits(candidate,v.lengthBlocks())&&(parking==null||candidate.length()<parking.length())){parking=candidate;berth=s.berth();}
            }catch(IllegalArgumentException ignored){}
        }
        if(berth!=null&&!berth.equals(j.berth)){j.berth=berth;save();}
        double remaining=parking==null?Math.max(0,grant.remainingMeters()-1.5):parking.length();
        service.intent(j.train,new AutomaticTrainService.Intent(j.owner,++j.sequence,now+1000,remaining*g.scale,.1,true,j.line,parking==null?"SR 分段边界":berth,"SR_RECOVERY"));
        if(v.speedBlocksPerTick()<=.001) {
            if(parking!=null&&parking.length()<=.12){j.phase="FINISHING";j.operation=auth.release(AtoProtocol.Packet.AUTHORITY_RELEASE.header(),j.train,j.owner,j.owner,++j.sequence).toCompletableFuture().exceptionally(ex->"FAILED");save();}
            else if(parking==null&&grant.remainingMeters()<=2){j.phase="RECOVERY_PREPARE";j.operation=null;save();}
        }
    }
    private void nextLeg(Job j,AutomaticTrainService.View v) {
        var r=jobRoute(j);j.plan=null;j.operation=null;
        if(j.index>=r.points.size()-1){j.phase="RETURN";returnPlan(j,v);return;}
        List<Topology.Target> targets=new ArrayList<>();int next=j.index+1;
        for(;next<r.points.size();next++){targets.add(target(r.points.get(next).id));if(r.points.get(next).id.startsWith("station:"))break;}
        if(next>=r.points.size()){j.phase="FAULT";j.reason="末尾缺少 station";save();return;}
        try{j.plan=graph().plan(locate(v),targets);j.dependency=combine(r.dependency,capture(j.plan));j.baseline=v.travelledBlocks();j.targetIndex=next;j.phase="RUNNING";j.fired.clear();}
        catch(IllegalArgumentException ex){j.phase="FAULT";j.reason=ex.getMessage();save();}
    }
    private void returnPlan(Job j,AutomaticTrainService.View v) {
        Topology.Plan best=null;String berth=null;boolean reverse=false;
        var service=trains();
        for(var s:state.signs.values())if(s.action.equals("depot")&&s.value.equals(j.depot)&&!invalidSigns.contains(s.key())) {
            if(state.jobs.values().stream().anyMatch(other->other!=j&&s.berth().equals(other.berth)))continue;
            if(service.trains().stream().anyMatch(other->!other.train().equals(j.train)&&new Topology.Pos(other.world(),other.x(),other.y(),other.z()).squared(s.rail())<Math.pow(other.lengthBlocks()+2,2)))continue;
            for(boolean backwards:new boolean[]{false,true}) {
                if(backwards&&(j.returnReversed||v.speedBlocksPerTick()>.001))continue;
                try {
                    var g=graph();var start=backwards?g.locate(new Topology.Pos(v.world(),v.x(),v.y(),v.z()),-v.dx(),-v.dy(),-v.dz()):locate(v);
                    var plan=g.shortest(start,Topology.Target.pos(s.rail()));
                    if(stopFits(plan,v.lengthBlocks())&&(best==null||plan.length()<best.length())){best=plan;berth=s.berth();reverse=backwards;}
                }catch(IllegalArgumentException ignored){}
            }
        }
        if(best==null){j.reason="等待可达的空闲停车位";return;}
        if(reverse) {j.berth=berth;j.plan=null;j.operation=null;resetAuthority(j);j.phase="RETURN_RELEASE";j.reason="停稳换向后按图回库";save();return;}
        j.dependency=combine(jobRoute(j).dependency,capture(best));
        j.berth=berth;j.plan=best;j.baseline=v.travelledBlocks();j.phase="RETURN";j.fired.clear();save();
    }
    private static AutomaticTrainService.ParkingTarget parkingTarget(SignData berth) {
        return new AutomaticTrainService.ParkingTarget(berth.world,berth.rx+.5,berth.ry+.0625,berth.rz+.5,berth.berth());
    }
    private SignData handoverBerth(AutomaticTrainService.View v) {
        if(v==null)return null;
        return state.signs.values().stream().filter(s->s.action.equals("depot")&&!invalidSigns.contains(s.key())
            &&parkingTarget(s).contains(v.world(),v.x(),v.y(),v.z())).findFirst().orElse(null);
    }
    private void driverMessage(UUID driver,Component text) {
        var player=Bukkit.getPlayer(driver);if(player!=null)player.getScheduler().run(this,t->{if(player.isOnline()&&player.hasPermission("skyatolite.driver"))player.sendMessage(text);},null);
    }
    private void offerMessage(DepotHandover.Offer offer) {
        var player=Bukkit.getPlayer(offer.driver.driver());if(player==null)return;
        player.getScheduler().run(this,t->{synchronized(this){
            var service=trains();if(closed||service==null||!depotHandover.current(offer)||!player.isOnline()
                ||!player.hasPermission("skyatolite.driver")||!player.hasPermission("skytrain.use")
                ||!service.manualDrivers().contains(offer.driver)
                ||!DepotHandover.ready(view(service,offer.driver.train()),offer.target,System.currentTimeMillis()))return;
            player.sendMessage(SalCommandUi.component("已在 "+offer.target.berth()+" 停稳3秒，可移交为SAL自动待命列车。 ")
                .append(Component.text("[确认SAL纳管]",net.kyori.adventure.text.format.NamedTextColor.BLUE).clickEvent(ClickEvent.runCommand("/sal handover "+offer.token))));
        }},null);
    }
    private void depotHandovers(AutomaticTrainService service,long now) {
        boolean changed=false;
        // Receipts are published under STF's driver lock before the no-driver snapshot.
        for(var receipt:service.manualReleases())if(!receipt.receipt().equals(state.seenManualReleases.get(receipt.train()))) {
            state.seenManualReleases.put(receipt.train(),receipt.receipt());state.manualStandby.add(receipt.train());changed=true;
            var offer=depotHandover.release(receipt,now);if(offer==null)continue;
            var v=view(service,receipt.train());var berth=handoverBerth(v);
            if(v==null||!fresh(v)||v.owner()!=null||!v.status().equals("IDLE")||v.speedBlocksPerTick()>.000001
                ||v.integrityHeld()||berth==null||!parkingTarget(berth).equals(offer.target)||state.jobs.containsKey(v.train())) {
                driverMessage(receipt.driver(),SalCommandUi.component("移管未完成：列车位置、停稳状态或控制权已变化，仍保留手动状态。"));continue;
            }
            String result=service.acceptDepotHandover(AtoProtocol.Packet.DEPOT_HANDOVER_COMMIT.header(),v.train(),UUID.nameUUIDFromBytes(("SAL-PARK:"+v.train()+":"+berth.value).getBytes(java.nio.charset.StandardCharsets.UTF_8)),offer.target,receipt);
            if(result.equals("PARKED")) {
                state.homes.put(v.train(),berth.value);state.manualStandby.remove(v.train());
                driverMessage(receipt.driver(),SalCommandUi.component("移管完成："+v.name()+" 已在 "+berth.berth()+" 转为SAL自动待命。"));
            } else driverMessage(receipt.driver(),SalCommandUi.component("移管未完成："+result+"；仍保留手动状态。"));
        }
        Set<UUID> eligible=new HashSet<>();
        for(var driver:service.manualDrivers()) {
            if(state.jobs.containsKey(driver.train()))continue;
            changed|=state.manualStandby.add(driver.train());
            var v=view(service,driver.train());var berth=handoverBerth(v);if(berth==null)continue;
            var target=parkingTarget(berth);if(!DepotHandover.ready(v,target,now))continue;
            eligible.add(driver.train());var offer=depotHandover.observe(driver,v,target,now);
            if(offer!=null)offerMessage(offer);
        }
        depotHandover.retain(eligible);if(changed)save();
    }
    private void captureIdleBerths(AutomaticTrainService service) {
        for(var v:service.trains())if(!state.manualStandby.contains(v.train())&&v.owner()==null&&v.status().equals("IDLE")&&fresh(v)&&!v.integrityHeld()&&!state.jobs.containsKey(v.train())) {
            var home=state.homes.get(v.train());if(home==null)continue;
            for(var s:state.signs.values())if(s.action.equals("depot")&&s.value.equals(home)&&!invalidSigns.contains(s.key())) {
                var target=new AutomaticTrainService.ParkingTarget(s.world,s.rx+.5,s.ry+.0625,s.rz+.5,s.berth());
                if(target.contains(v.world(),v.x(),v.y(),v.z())) {
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
            changed|=state.homes.remove(receipt.train())!=null;
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
    private boolean stopFits(Topology.Plan plan,double lengthBlocks) {
        double cursor=-plan.parts().getFirst().from();
        for(var part:plan.parts()) {
            var node=graph().nodes.get(part.edge().from());
            if(node!=null&&node.type().equals("switch")&&(plan.length()-cursor)*plan.scale()<lengthBlocks+2)return false;
            cursor+=part.edge().distanceMeters();
        }
        var last=plan.parts().getLast();var node=graph().nodes.get(last.edge().to());
        return node==null||!node.type().equals("switch")||last.to()<last.edge().distanceMeters()-.5/plan.scale();
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
            valid=actual.action().equals(s.action)&&actual.value().equals(s.value)&&actual.option().equals(s.option);
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
        if(args.length==1&&!sender.hasPermission("skyatolite.admin")&&sender.hasPermission("skyatolite.driver"))return List.of("release");
        if(!sender.hasPermission("skyatolite.admin"))return List.of();
        if(args.length==1)return List.of("help","route","train","awake","status","stop","resume","finish","release");
        if(args.length==2&&args[0].equalsIgnoreCase("route"))return List.of("create","bind","choose","direction","depot","list","remove","validate","publish","preview","close","resolve","retire","delete");
        synchronized(this){if(args.length==3&&args[0].equalsIgnoreCase("route"))return List.copyOf(state.workspace.entries.keySet());}
        return List.of();
    }
}
