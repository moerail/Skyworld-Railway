package net.skyworld.skytrain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.skyworld.sta.api.v8.AutomaticTrainService;

/** Task leases are independent of player leases. Intents are consumed on the leader entity tick. */
final class TrainAutomation implements AutomaticTrainService, net.skyworld.sta.api.v8.SwitchActuatorService, AutomationController {
    static final class Session {
        final UUID owner;
        volatile Intent intent;
        volatile double remaining;
        volatile double travelled;
        volatile long applied = -1;
        volatile boolean reverse, recover, release;
        volatile boolean parking;
        volatile boolean manualRelease;
        volatile UUID recoveryFence;
        volatile boolean prepareSr,srRecovery;
        volatile UUID approvedSr;
        volatile String status = "WAIT_INTENT";
        volatile String text;
        volatile double seconds;
        volatile long messageRevision;
        volatile long messageUntil;
        final Map<UUID,Long> delivered = new ConcurrentHashMap<>();
        volatile List<Chunk> corridor = List.of();
        Session(UUID owner) { this.owner=owner; }
    }
    private final SkyTrainPlugin plugin;
    private final Map<UUID,Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID,ManualRelease> manualReleases = new ConcurrentHashMap<>();
    TrainAutomation(SkyTrainPlugin plugin) {
        this.plugin=plugin;
        plugin.getServer().getServicesManager().register(AutomaticTrainService.class,this,plugin,ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(net.skyworld.sta.api.v8.SwitchActuatorService.class,this,plugin,ServicePriority.Normal);
    }
    public java.util.concurrent.CompletionStage<String> change(net.skyworld.sta.api.v5.SwitchControlService.Request request,
            java.util.function.BooleanSupplier guard,java.util.function.Consumer<String> progress) {
        Objects.requireNonNull(guard);Objects.requireNonNull(progress);
        var network=plugin.getServer().getServicesManager().load(net.skyworld.sta.api.v5.RailNetworkService.class);
        if(request.position()==null||network==null||request.graphRevision()!=network.graphRevision())
            return java.util.concurrent.CompletableFuture.completedFuture("REJECTED_GRAPH_CHANGED");
        var p=request.position();
        return plugin.changeSwitchById(request.switchId().toString(),request.expectedState(),request.targetState(),p.world(),p.x(),p.y(),p.z(),guard,progress);
    }
    private Train train(UUID id) { return plugin.observedTrains().stream().filter(t->t.id().equals(id)).findFirst().orElse(null); }
    public boolean owns(Train t) { return sessions.containsKey(t.id()); }
    Session session(Train t) { return sessions.get(t.id()); }
    public List<DriverDeskSnapshot> desks() {
        var live=plugin.observedTrains().stream().map(Train::id).collect(java.util.stream.Collectors.toSet());
        return sessions.entrySet().stream().filter(e->live.contains(e.getKey())&&!e.getValue().release&&!e.getValue().parking).map(e->
                new DriverDeskSnapshot(e.getKey(),e.getValue().owner,e.getValue().owner,"ACTIVE")).toList();
    }
    static List<Position> body(Train t,long now) {
        var ids=new ArrayList<>(t.members());if(t.reversed)Collections.reverse(ids);var result=new ArrayList<Position>();
        for(var id:ids){var m=t.snapshot(id);if(m==null||now<m.timeMillis||now-m.timeMillis>1000)return List.of();result.add(new Position(m.worldName,m.x,m.y,m.z));}
        if(result.stream().anyMatch(p->!p.world().equals(result.getFirst().world())))return List.of();return List.copyOf(result);
    }
    static boolean atTarget(Train t,ParkingTarget target,long now){var p=AutomaticTrainService.reference(body(t,now),target.Q_TP_Alignment());return p!=null&&target.contains(p.world(),p.x(),p.y(),p.z());}
    public Collection<View> trains() {
        List<View> result=new ArrayList<>(); long now=System.currentTimeMillis();
        for(Train t:plugin.observedTrains()) {
            var ids=t.members(); if(ids.isEmpty()) continue;
            var m=t.snapshot(ids.get(t.reversed?ids.size()-1:0)); if(m==null)continue;
            var integrity=t.tims.view(now); var s=sessions.get(t.id());
            var direction=t.rememberedDirection();
            result.add(new View(t.id(),t.name(),ids,m.worldName,m.x,m.y,m.z,direction.getX(),direction.getY(),direction.getZ(),
                    Math.max(t.currentSpeed(),t.maxMemberSpeed()),Math.max(1,t.spacing*(ids.size()-1)+1),m.timeMillis,
                    integrity.state().name(),integrity.reason(),integrity.brakeHeld(),t.operatingMode.name(),s==null||s.parking?null:s.owner,
                    s==null?(plugin.manualDesks().stream().anyMatch(d->d.trainId().equals(t.id()))?"MANUAL_DRIVER":"IDLE"):s.status,s==null?0:s.travelled,net.skyworld.sta.api.v8.AtoProtocol.Packet.VEHICLE_FEEDBACK.header(),body(t,now)));
        }
        return List.copyOf(result);
    }
    public UUID trainForMember(UUID member) {
        return plugin.observedTrains().stream().filter(t->t.members().contains(member)).map(Train::id).findFirst().orElse(null);
    }
    public Collection<ManualDriver> manualDrivers() {
        return plugin.manualDesks().stream().map(d->new ManualDriver(d.trainId(),d.driverId(),d.leaseId())).toList();
    }
    public Collection<ManualRelease> manualReleases() {return List.copyOf(manualReleases.values());}
    public void manualReleased(Train t,UUID driver,UUID lease,long at) {
        manualReleases.put(t.id(),new ManualRelease(t.id(),driver,lease,UUID.randomUUID(),at));
    }
    public synchronized String releaseStandby(UUID id,UUID requester) {
        Objects.requireNonNull(requester);var t=train(id);var s=sessions.get(id);
        if(t==null)return "UNKNOWN_TRAIN";
        if(s==null||!s.parking)return "NOT_PARKED";
        long now=System.currentTimeMillis();
        if(!stopped(t,now))return "STOP_FIRST";
        if(t.tims.view(now).brakeHeld()||t.operatingMode==OperatingMode.TR||t.operatingMode==OperatingMode.PT)return "PROTECTION_HOLD";
        s.manualRelease=true;s.release=true;return "RELEASING";
    }
    private static boolean stopped(Train t,long now) {
        return t.currentSpeed()<=.001 && t.maxMemberSpeed()<=.001 && !t.members().isEmpty()
                && t.members().stream().allMatch(id->{var m=t.snapshot(id);return m!=null && now>=m.timeMillis
                    && now-m.timeMillis<=1000 && m.velocity.lengthSquared()<=.000001;});
    }
    public synchronized String claim(UUID id,UUID owner) {
        Objects.requireNonNull(owner); var t=train(id); if(t==null)return "UNKNOWN_TRAIN";
        var old=sessions.get(id); if(old!=null&&old.release)return "BUSY";
        if(old!=null&&!old.parking)return old.owner.equals(owner)?"CLAIMED":"BUSY";
        if(plugin.manualDesks().stream().anyMatch(d->d.trainId().equals(id)))return "MANUAL_DRIVER";
        if(!stopped(t,System.currentTimeMillis()))return "STOP_FIRST";
        if(t.tims.view(System.currentTimeMillis()).brakeHeld())return "TIMS_HOLD";
        if(t.protectionMode==ProtectionMode.ISOLATED || t.protectionMode==ProtectionMode.RECOVERING
                ||t.protectionMode==ProtectionMode.BYPASS||t.operatingMode==OperatingMode.TR||t.operatingMode==OperatingMode.PT)return "PROTECTION_HOLD";
        if(!plugin.registerAutomation(t,()->sessions.put(id,new Session(owner))))return "MANUAL_DRIVER";
        return "CLAIMED";
    }
    public synchronized String park(UUID id,UUID owner,ParkingTarget target) {
        return capture(id,owner,target,null);
    }
    public synchronized String acceptDepotHandover(UUID id,UUID owner,ParkingTarget target,ManualRelease receipt) {
        Objects.requireNonNull(receipt);
        if(!receipt.train().equals(id)||!receipt.equals(manualReleases.get(id)))return "STALE_RELEASE";
        long now=System.currentTimeMillis();
        if(now<receipt.releasedAt()||now-receipt.releasedAt()>1500)return "STALE_RELEASE";
        var t=train(id);if(t==null)return "UNKNOWN_TRAIN";
        if(!stopped(t,now))return "STOP_FIRST";
        return capture(id,owner,target,receipt);
    }
    private String capture(UUID id,UUID owner,ParkingTarget target,ManualRelease receipt) {
        Objects.requireNonNull(owner);Objects.requireNonNull(target);target.header().require(net.skyworld.sta.api.v8.AtoProtocol.Packet.DEPOT_CAPTURE);
        var t=train(id);if(t==null)return "UNKNOWN_TRAIN";
        var old=sessions.get(id);if(old!=null)return receipt==null&&old.parking?"PARKED":"BUSY";
        if(plugin.manualDesks().stream().anyMatch(d->d.trainId().equals(id)))return "MANUAL_DRIVER";
        long now=System.currentTimeMillis();var members=t.members();if(members.isEmpty())return "NO_MEMBERS";
        var front=t.snapshot(members.get(t.reversed?members.size()-1:0));
        if(front==null||now<front.timeMillis||now-front.timeMillis>1000||!atTarget(t,target,now))return "NOT_AT_BERTH";
        if(t.maxMemberSpeed()>.4||t.tims.view(now).brakeHeld())return "PROTECTION_HOLD";
        if(t.protectionMode!=ProtectionMode.ACTIVE||t.operatingMode==OperatingMode.TR||t.operatingMode==OperatingMode.PT)return "PROTECTION_HOLD";
        var s=new Session(owner);s.parking=true;s.status="PARKED";
        s.intent=new Intent(owner,0,now+1000,0,0,true,"",target.berth(),"PARKED");
        boolean[] installed={false};
        if(!plugin.registerAutomation(t,()->{
            if(receipt!=null) {
                long current=System.currentTimeMillis();var latest=t.snapshot(members.get(t.reversed?members.size()-1:0));
                if(!receipt.equals(manualReleases.get(id))||!stopped(t,current)||latest==null
                    ||!atTarget(t,target,current))return;
            }
            sessions.put(id,s);installed[0]=true;
            if(receipt!=null)manualReleases.remove(id,receipt);
        }))return "MANUAL_DRIVER";
        return installed[0]?"PARKED":"STALE_RELEASE";
    }
    public synchronized boolean intent(UUID id,Intent intent) {
        var s=sessions.get(id); long now=System.currentTimeMillis();
        if(s==null || !s.owner.equals(intent.owner()) || s.release || intent.expiresAt()<=now || intent.expiresAt()>now+5000
                || (s.intent!=null && intent.sequence()<=s.intent.sequence()))return false;
        s.intent=intent; return true;
    }
    public synchronized String release(UUID id,UUID owner) {
        var s=sessions.get(id);var t=train(id);
        if(s==null||!s.owner.equals(owner)||t==null)return "NOT_OWNER";
        if(!stopped(t,System.currentTimeMillis())||t.tims.view(System.currentTimeMillis()).brakeHeld())return "STOP_FIRST";
        s.release=true; return "RELEASING";
    }
    public synchronized String handoffToManual(UUID id,UUID owner,UUID administrator){
        Objects.requireNonNull(administrator);var t=train(id);if(t==null)return "UNKNOWN_TRAIN";var s=sessions.get(id);
        String result=manualHandoffStatus(s,owner,stopped(t,System.currentTimeMillis()));if(!result.equals("RELEASING"))return result;
        s.manualRelease=true;s.release=true;return "RELEASING";
    }
    static String manualHandoffStatus(Session s,UUID owner,boolean stopped){
        if(s==null)return stopped?"ALREADY_MANUAL":"STOP_FIRST";
        if(owner==null?!s.parking:!s.owner.equals(owner))return "NOT_OWNER";
        return stopped?"RELEASING":"STOP_FIRST";
    }
    public String reverse(UUID id,UUID owner) {
        var s=sessions.get(id);var t=train(id);
        if(s==null||!s.owner.equals(owner)||t==null)return "NOT_OWNER";
        if(!stopped(t,System.currentTimeMillis())||t.tims.view(System.currentTimeMillis()).brakeHeld())return "STOP_FIRST";
        s.reverse=true;return "QUEUED";
    }
    public String recover(UUID id,UUID owner) {
        var s=sessions.get(id);if(s==null||!s.owner.equals(owner))return "NOT_OWNER";
        s.recover=true;return "QUEUED";
    }
    public String prepareSr(UUID id,UUID owner) {
        var s=sessions.get(id);var t=train(id);
        if(s==null||!s.owner.equals(owner)||t==null)return "NOT_OWNER";
        if(!stopped(t,System.currentTimeMillis())||t.tims.view(System.currentTimeMillis()).brakeHeld())return "STOP_FIRST";
        s.prepareSr=true;return "QUEUED";
    }
    public String acceptRecoverySr(UUID id,UUID owner,UUID grant) {
        var s=sessions.get(id);if(s==null||!s.owner.equals(owner)||!s.srRecovery)return "NOT_OWNER";
        s.approvedSr=Objects.requireNonNull(grant);return "QUEUED";
    }
    public void corridor(UUID id,UUID owner,List<Chunk> chunks) {
        var s=sessions.get(id);if(s!=null&&s.owner.equals(owner)&&chunks.size()<=128)s.corridor=List.copyOf(chunks);
    }
    public void message(UUID id,UUID owner,String text,double seconds) {
        if(text==null || !Double.isFinite(seconds)||seconds<=0||seconds>3600)return;
        var s=sessions.get(id);if(s==null||!s.owner.equals(owner))return;
        synchronized(s) {s.text=text;s.seconds=seconds;s.messageUntil=System.currentTimeMillis()+Math.round(seconds*1000);s.messageRevision++;}
    }
    public void passenger(Train t, org.bukkit.entity.Minecart cart) {
        var s=sessions.get(t.id());if(s==null||s.text==null||s.messageUntil<=System.currentTimeMillis())return;
        String text; double seconds;long revision;
        synchronized(s) {text=s.text;seconds=Math.max(.001,(s.messageUntil-System.currentTimeMillis())/1000.0);revision=s.messageRevision;}
        for(var e:cart.getPassengers())if(e instanceof Player p && s.delivered.getOrDefault(p.getUniqueId(),0L)<revision) {
            s.delivered.put(p.getUniqueId(),revision);
            // One atomic title packet sequence; no delayed clear from an older message can erase a newer one.
            var title=Title.title(Component.text(text),Component.empty(),Title.Times.times(Duration.ZERO,
                    Duration.ofMillis(Math.round(seconds*1000)),Duration.ZERO));
            UUID cartId=cart.getUniqueId();
            p.getScheduler().run(plugin,task->{if(sessions.get(t.id())==s && s.messageRevision==revision && s.messageUntil>System.currentTimeMillis()
                    && p.getVehicle()!=null && p.getVehicle().getUniqueId().equals(cartId))p.showTitle(title);},null);
        }
    }
    public void tick(Train t,long now) {
        var s=sessions.get(t.id());if(s==null)return;
        if(plugin.manualDesks().stream().anyMatch(d->d.trainId().equals(t.id()))) {
            hold(t,s,"MANUAL_CONFLICT");return;
        }
        if(s.release) {
            hold(t,s,"RELEASING");
            if(stopped(t,now)) {t.activeAtp.clear();if(t.operatingMode!=OperatingMode.TR&&t.operatingMode!=OperatingMode.PT)t.operatingMode=OperatingMode.SB;t.driverEmergencyHold=false;
                if(s.manualRelease){t.properties().conductionMode=ConductionMode.MANUAL;t.manualTakeover=true;t.manualReleaseConfirmed=false;}
                sessions.remove(t.id(),s);}
            return;
        }
        t.properties().conductionMode=ConductionMode.AUTOMATIC;t.protectionMode=ProtectionMode.ACTIVE;
        t.manualTakeover=false;t.driverEmergencyHold=false;
        t.driveControlEnabled=true;
        if(s.parking) {
            t.playerPushActive=false;
            if(t.operatingMode!=OperatingMode.TR&&t.operatingMode!=OperatingMode.PT)t.operatingMode=OperatingMode.SB;
            hold(t,s,"PARKED");return;
        }
        if(s.prepareSr && s.recoveryFence==null) {
            s.prepareSr=false;
            if(stopped(t,now)&&!t.tims.view(now).brakeHeld()) {
                s.srRecovery=true;s.recoveryFence=t.activeAtp.beginRelease();
                if(t.operatingMode!=OperatingMode.TR&&t.operatingMode!=OperatingMode.PT)t.operatingMode=OperatingMode.SB;
            }
        }
        if(s.recover) {
            s.recover=false;
            String reason=t.tims.latchedReason();
            boolean transientReason=AutomaticRecoveryPolicy.mayAcknowledgeTims(reason,t.operatingMode,t.integrityTrip,t.activeAtp.remainingMeters());
            if(s.recoveryFence!=null) {
                var authority=plugin.getServer().getServicesManager().load(net.skyworld.sta.api.v6.OperationalAuthorityService.class);
                var snapshot=authority==null?null:authority.operationalSnapshot();
                if(stopped(t,now)&&snapshot!=null&&snapshot.status().equals("AVAILABLE")&&now>=snapshot.emittedAtMillis()
                        &&now-snapshot.emittedAtMillis()<=1500&&snapshot.grants().stream().noneMatch(g->g.trainId().equals(t.id()))
                        && t.activeAtp.completeRelease(s.recoveryFence,true)) {
                    s.recoveryFence=null;
                    if(t.operatingMode!=OperatingMode.TR&&t.operatingMode!=OperatingMode.PT)t.operatingMode=OperatingMode.SB;
                    t.emergencyBrake=false;s.status="RECOVERED";
                }
            } else if(transientReason && stopped(t,now) && t.tims.acknowledge(now,true)) {
                s.srRecovery=false;
                t.integrityTrip=false;
                s.recoveryFence=t.activeAtp.beginRelease();t.operatingMode=OperatingMode.SB;s.status="RECOVERED_WAIT_RELEASE";
            } else s.status="RECOVERY_NOT_CONFIRMED";
        }
        if(t.tims.view(now).brakeHeld()) {hold(t,s,"TIMS_"+t.tims.view(now).reason());return;}
        if(s.recoveryFence!=null){hold(t,s,s.srRecovery?"SR_WAIT_RELEASE":"RECOVERED_WAIT_RELEASE");return;}
        if(s.approvedSr!=null&&s.srRecovery&&stopped(t,now)) {
            var source=plugin.getServer().getServicesManager().load(net.skyworld.sta.api.v6.OperationalAuthorityService.class);
            var snapshot=source==null?null:source.operationalSnapshot();
            if(snapshot!=null&&snapshot.status().equals("AVAILABLE")&&now>=snapshot.emittedAtMillis()&&now-snapshot.emittedAtMillis()<=1500
                    &&snapshot.grants().stream().anyMatch(g->g.id().equals(s.approvedSr)&&g.trainId().equals(t.id())
                        &&g.driverLeaseId().equals(s.owner)&&g.executable()&&g.mode().equals("SR"))) {
                if(t.operatingMode==OperatingMode.TR)t.operatingMode=t.operatingMode.acknowledge(true);
                t.operatingMode=t.operatingMode.release(true);t.integrityTrip=false;t.activeAtp.clear();t.emergencyBrake=false;s.approvedSr=null;
            }
        }
        if(s.reverse) {
            if(!stopped(t,now)) {hold(t,s,"STOP_BEFORE_REVERSE");return;}
            t.reversed=!t.reversed;CabOrientation.alignAutomatic(t);
            t.reverseRememberedDirection();t.reverseMileageDirection();t.clearMemberTargets();t.trackPath(null);
            t.reverseSettleUntilMillis=Math.max(t.reverseSettleUntilMillis,now+Math.max(1,plugin.getConfig().getLong("settings.reverse-settle-ticks",12))*50);
            t.activeAtp.clear();t.operatingMode=OperatingMode.SB;
            s.reverse=false;s.intent=null;s.status="REVERSED";return;
        }
        var i=s.intent;
        if(i==null||i.expiresAt()<now) {hold(t,s,s.status.equals("REVERSED")?"REVERSED":"INTENT_EXPIRED");return;}
        if(s.status.equals("REVERSED")&&i.remainingBlocks()==0&&i.cruiseBlocksPerTick()==0){hold(t,s,"REVERSED");return;}
        if(s.applied!=i.sequence()) {s.remaining=i.remainingBlocks();s.applied=i.sequence();}
        if(t.operatingMode==OperatingMode.TR || t.operatingMode==OperatingMode.PT) {hold(t,s,"PROTECTION_"+t.operatingMode);return;}
        CabOrientation.alignAutomatic(t);
        // Front/rear cab plus FORWARD always agrees with the selected train direction.
        t.reverser=Reverser.FORWARD;t.emergencyBrake=false;t.moving=true;
        Double atpLimit=t.activeAtpLimitMps==null?null:t.activeAtpLimitMps*plugin.getConfig().getDouble("infrastructure.blocks-per-meter",1)/20;
        var handles=AutomaticHandlePolicy.select(plugin.vehicleProfile(),AutomaticHandlePolicy.Settings.read(plugin.getConfig()),
                t.currentSpeed(),i.cruiseBlocksPerTick(),atpLimit,s.remaining,i.stopAtTarget());
        t.powerNotch=handles.power();t.brakeNotch=handles.brake();
        s.status=i.phase();
    }
    private static void hold(Train t,Session s,String reason) {t.powerNotch=0;t.brakeNotch=7;t.moving=true;t.driveControlEnabled=true;s.status=reason;}
    public void moved(Train t,double distance) {var s=sessions.get(t.id());if(s!=null){s.remaining=Math.max(0,s.remaining-distance);s.travelled+=distance;}}
    public double movementLimit(Train t,double planned) {var s=sessions.get(t.id());return s!=null&&s.intent!=null&&s.intent.stopAtTarget()?Math.min(planned,s.remaining):planned;}
    public String sidebar(Train t) {var s=sessions.get(t.id());return s==null?null:(s.intent==null?s.status:s.intent.line()+" → "+s.intent.next()+" | "+s.status);}
    public List<AutomationController.ChunkPosition> corridor(Train t) {
        var s=sessions.get(t.id());return s==null?List.of():s.corridor.stream().map(c->new AutomationController.ChunkPosition(c.world(),c.x(),c.z())).toList();
    }
    public boolean requestFs(Train t,UUID driver,UUID lease) {
        if(!DriverSafety.defaultFsReady(t,System.currentTimeMillis()))return false;
        var service=plugin.getServer().getServicesManager().load(net.skyworld.sta.api.v8.AutomaticAuthorityService.class);
        if(service!=null)service.requestFs(net.skyworld.sta.api.v8.AtoProtocol.Packet.FS_REQUEST.header(),t.id(),driver,lease);
        return service!=null;
    }
    public void close() {for(var t:plugin.observedTrains())if(owns(t))DriverSafety.revokeAuthority(t);sessions.clear();manualReleases.clear();}
    public void retired(UUID train) {sessions.remove(train);manualReleases.remove(train);}
}
