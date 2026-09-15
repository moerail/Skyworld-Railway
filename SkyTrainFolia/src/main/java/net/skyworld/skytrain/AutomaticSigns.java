package net.skyworld.skytrain;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Region-owned sign observations; train actions execute only on the train's owner tick. */
final class AutomaticSigns {
    record Observation(String key, SwitchBlockPosition sign, Side side, SwitchBlockPosition rail,
            TrainSignHeader header, AutomaticSignSpec spec, double faceX, double faceZ,
            boolean powered, boolean previousPower, long edge, long observedAt, Set<BlockFace> poweredSides, boolean explicitSpeed) {
        Vector facing() { return new Vector(faceX,0,faceZ); }
        boolean active(boolean redstone) { return header.activate(powered,previousPower,redstone); }
    }
    private final SkyTrainPlugin plugin;
    private final TrainManager manager;
    private final StationManager stations;
    private final Map<String,SwitchBlockPosition> positions = new ConcurrentHashMap<>();
    private final Map<String,Observation> observations = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Map<String,Long> nextSpawn = new ConcurrentHashMap<>();
    private final Map<String,Boolean> outputs = new ConcurrentHashMap<>();
    private final Map<UUID,Set<String>> occupied = new ConcurrentHashMap<>();
    private final Map<UUID,Map<String,Long>> seenEdges = new ConcurrentHashMap<>();
    private final Map<UUID,Long> queryAt = new ConcurrentHashMap<>();
    private final AtomicLong edges = new AtomicLong();
    private final AtomicLong saves = new AtomicLong();
    private final Object fileLock = new Object();
    private final Object spawnLock = new Object();
    private ScheduledTask task;
    private volatile boolean closed = true;

    AutomaticSigns(SkyTrainPlugin plugin, TrainManager manager, StationManager stations) {
        this.plugin=plugin; this.manager=manager; this.stations=stations;
    }
    static boolean action(String line) {
        String value=plain(line).toLowerCase(Locale.ROOT).split("\\s+",2)[0];
        return value.equals("station") || value.equals("spawn") || value.equals("destroy") || value.equals("property");
    }
    void validate(Block sign, String headerText, String second, String third, String fourth) {
        var header=TrainSignHeader.parse(headerText);
        if(header==null) throw new IllegalArgumentException("Invalid train sign header");
        if(!header.remote().isEmpty()) throw new IllegalArgumentException("Remote train-name headers are not supported in this preview");
        var spec=AutomaticSignSpec.parse(plain(second),plain(third),plain(fourth),defaultSpeed());
        if(spec.route()) throw new IllegalArgumentException("station route requires destination routing; not available in this MVP");
        if(spec.action().equals("spawn")) manager.validateSpawnPattern(spec.pattern());
        if(RailSignAccess.railFor(sign)==null) throw new IllegalArgumentException("No associated rail in the sign column");
    }
    void register(Block sign) {
        if(closed) return;
        var pos=SwitchBlockPosition.of(sign);
        positions.put(pos.key(),pos);
        refresh(sign,false);
        persist();
    }
    void remove(Block sign) {
        String key=SwitchBlockPosition.of(sign).key();
        boolean existed=positions.remove(key)!=null;
        for(Side side:Side.values()) observations.remove(key+":"+side);
        nextSpawn.keySet().removeIf(k->k.startsWith(key+":"));
        if(Boolean.TRUE.equals(outputs.remove(key)) && RailSignAccess.readable(sign)
                && sign.getState() instanceof Sign) StationOutput.apply(sign,false,manager::switchLever);
        if(existed) persist();
    }
    void start() {
        stop(); closed=false;
        outputs.clear();
        var yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"automatic-signs.yml"));
        for(Map<?,?> item:yaml.getMapList("signs")) try {
            var p=new SwitchBlockPosition(item.get("world").toString(),((Number)item.get("x")).intValue(),
                    ((Number)item.get("y")).intValue(),((Number)item.get("z")).intValue());
            positions.put(p.key(),p);
            if(Boolean.TRUE.equals(item.get("output-on"))) outputs.put(p.key(),true);
        } catch(RuntimeException ex) { plugin.getLogger().warning("Invalid automatic sign registry entry: "+item); }
        for(var item:stations.snapshots()) {
            var p=new SwitchBlockPosition(item.get("signWorld"),Integer.parseInt(item.get("signX")),
                    Integer.parseInt(item.get("signY")),Integer.parseInt(item.get("signZ")));
            positions.put(p.key(),p);
        }
        task=Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,t->{
            for(var p:positions.values()) schedule(p);
        },1L,5L);
    }
    void stop() {
        closed=true;
        if(task!=null) task.cancel();
        task=null; observations.clear(); pending.clear(); nextSpawn.clear();
        occupied.clear(); seenEdges.clear(); queryAt.clear();
    }
    void forget(UUID train) { occupied.remove(train); seenEdges.remove(train); queryAt.remove(train); }
    private void persist() {
        long revision=saves.incrementAndGet();
        List<Map<String,Object>> rows=positions.values().stream().map(p->Map.<String,Object>of(
                "world",p.worldName(),"x",p.x(),"y",p.y(),"z",p.z(),"output-on",outputs.getOrDefault(p.key(),false))).toList();
        Bukkit.getAsyncScheduler().runNow(plugin,t->{
            synchronized(fileLock) {
                if(revision!=saves.get()) return;
                var yaml=new YamlConfiguration(); yaml.set("signs",rows);
                try { yaml.save(new File(plugin.getDataFolder(),"automatic-signs.yml")); }
                catch(java.io.IOException ex) { plugin.getLogger().warning("Could not save automatic signs: "+ex); }
            }
        });
    }
    private void schedule(SwitchBlockPosition p) {
        Location location=p.location();
        if(closed || location==null || !location.getWorld().isChunkLoaded(p.x()>>4,p.z()>>4)
                || !pending.add(p.key())) return;
        try { Bukkit.getRegionScheduler().run(plugin,location,t->{
            try { if(!closed) refresh(location.getBlock(),true); }
            finally { pending.remove(p.key()); }
        }); } catch(RuntimeException ex) { pending.remove(p.key()); }
    }
    void redstone(Block changed) {
        for(var p:positions.values()) if(p.worldName().equals(changed.getWorld().getName())
                && Math.abs(p.x()-changed.getX())<=2 && Math.abs(p.y()-changed.getY())<=2
                && Math.abs(p.z()-changed.getZ())<=2) schedule(p);
    }
    private void refresh(Block block, boolean spawn) {
        if(!RailSignAccess.readable(block)) return;
        if(!(block.getState() instanceof Sign sign)) { remove(block); stations.removeSign(block); return; }
        if(!RailSignAccess.powerReadable(block)) return;
        var pos=SwitchBlockPosition.of(block);
        Block rail=RailSignAccess.railFor(block);
        // Unknown ownership / unloading is never proof that the definition was removed.
        if(rail==null) return;
        boolean hasStation=false;
        for(Side side:Side.values()) {
            String key=pos.key()+":"+side;
            var text=sign.getSide(side);
            var header=TrainSignHeader.parse(text.getLine(0));
            if(header==null || !header.remote().isEmpty() || !action(text.getLine(1))) {
                observations.remove(key); continue;
            }
            try {
                var spec=AutomaticSignSpec.parse(plain(text.getLine(1)),plain(text.getLine(2)),plain(text.getLine(3)),defaultSpeed());
                hasStation|=spec.action().equals("station");
                var powerState=SignPower.read(block);
                if(powerState==null) return;
                boolean power=powerState.powered();
                var old=observations.get(key);
                boolean changed=old!=null && old.powered()!=power;
                Vector facing=RailSignAccess.facing(block,side);
                Set<BlockFace> sides=powerState.sides();
                boolean revised=old!=null && (changed || !old.poweredSides().equals(sides)
                        || !old.spec().equals(spec) || !old.header().equals(header));
                var observation=new Observation(key,pos,side,SwitchBlockPosition.of(rail),header,spec,
                        facing.getX(),facing.getZ(),power,revised?old.powered():old==null?power:old.previousPower(),
                        revised?edges.incrementAndGet():old==null?0:old.edge(),System.currentTimeMillis(),Set.copyOf(sides),
                        AutomaticSignSpec.explicitStationSpeed(text.getLine(3)));
                observations.put(key,observation);
                if(spawn && spec.action().equals("spawn")) {
                    boolean pulse=changed && header.activate(power,old.powered(),true);
                    boolean rising=changed && !header.pulse() && !header.always() && observation.active(false);
                    long due=nextSpawn.computeIfAbsent(key,k->System.currentTimeMillis()+Math.max(50,spec.intervalMillis()));
                    boolean periodic=spec.intervalMillis()>0 && !header.pulse() && observation.active(false)
                            && System.currentTimeMillis()>=due;
                    if(pulse || rising || periodic) {
                        nextSpawn.put(key,System.currentTimeMillis()+Math.max(1000,spec.intervalMillis()));
                        spawn(observation,rail);
                    }
                }
            } catch(IllegalArgumentException ex) { observations.remove(key); }
        }
        if(!hasStation) stations.removeSign(block);
        else manager.stationOutput(pos.key(),value -> {
            boolean previous=outputs.getOrDefault(pos.key(),false);
            if(previous!=value && StationOutput.apply(block,value,manager::switchLever)) {
                outputs.put(pos.key(),value);
                persist();
            }
        });
    }
    private void spawn(Observation sign,Block rail) {
        synchronized(spawnLock) {
            try {
                Vector right=RailSignAccess.direction("right",sign.facing(),sign.facing());
                Vector preferred=RailMath.direction(((org.bukkit.block.data.Rail)rail.getBlockData()).getShape(),right);
                boolean forward=RailSignAccess.entered(sign.header(),sign.facing(),preferred);
                boolean backward=RailSignAccess.entered(sign.header(),sign.facing(),preferred.clone().multiply(-1));
                boolean centered=forward==backward;
                if(!forward && backward) preferred.multiply(-1);
                if(centered) {
                    BlockFace along=Math.abs(preferred.getX())>Math.abs(preferred.getZ())?BlockFace.EAST:BlockFace.SOUTH;
                    boolean a=sign.header().poweredSide(sign.poweredSides().contains(along));
                    boolean b=sign.header().poweredSide(sign.poweredSides().contains(along.getOppositeFace()));
                    if(a!=b) {
                        Vector power=new Vector(along.getModX(),0,along.getModZ()).multiply(a?1:-1);
                        if(preferred.dot(power)<0) preferred.multiply(-1);
                        centered=false;
                    }
                }
                manager.spawnAutomatic(sign.spec(),rail,preferred,centered);
            } catch(IllegalArgumentException ex) {
                plugin.getLogger().fine("Spawn postponed at "+sign.key()+": "+ex.getMessage());
            }
        }
    }
    boolean tick(Train train,Block rail,Location location,Vector direction,long now) {
        if(!manager.automaticEligible(train)) return false;
        if(train.automaticRun==null && train.moving) {
            var cruiseSpec=AutomaticSignSpec.parse("station","0","continue "+train.targetSpeed,defaultSpeed());
            var cruise=new AutomaticRun("",cruiseSpec,0,false,train.reversed);
            cruise.phase=AutomaticRun.Phase.CRUISE;
            cruise.inheritTargetSpeed=train.properties().automaticTargetSpeed!=null;
            cruise.reason="Automatic cruise";
            train.automaticRun=cruise;
        }
        Set<String> present=new HashSet<>();
        // Sign discovery follows the rail column. It never searches nearby parallel tracks.
        for(Block sign:RailSignAccess.signsFor(rail)) {
            var pos=SwitchBlockPosition.of(sign);
            refresh(sign,true);
            boolean recognized=false;
            for(Side side:Side.values()) {
                var value=observations.get(pos.key()+":"+side);
                if(value!=null) { present.add(value.key()); recognized=true; }
            }
            if(recognized && positions.putIfAbsent(pos.key(),pos)==null) persist();
        }
        Set<String> occupiedRails=new HashSet<>();
        for (MemberSnapshot member:train.snapshots()) {
            if(now-member.timeMillis>1000) continue;
            occupiedRails.add(new SwitchBlockPosition(member.worldName,(int)Math.floor(member.x),
                    (int)Math.floor(member.y),(int)Math.floor(member.z)).key());
        }
        // Include rails crossed between ticks, so high-speed trains do not skip destroy signs.
        var currentPath=train.trackPath();
        if(currentPath!=null) for(var probe:currentPath.lastMoveProbes()) {
            var p=probe.trackPosition();
            occupiedRails.add(new SwitchBlockPosition(p.worldName,p.railX,p.railY,p.railZ).key());
        }
        for(Observation observation:observations.values()) if(occupiedRails.contains(observation.rail().key())) present.add(observation.key());
        Set<String> previous=occupied.put(train.id(),Set.copyOf(present));
        if(previous==null) previous=Set.of();
        Map<String,Long> consumed=seenEdges.computeIfAbsent(train.id(),k->new ConcurrentHashMap<>());
        for(String key:present) {
            var sign=observations.get(key);
            if(sign==null || now-sign.observedAt()>1000 || !RailSignAccess.entered(sign.header(),sign.facing(),direction)) continue;
            long old=consumed.getOrDefault(key,sign.edge());
            boolean edge=sign.edge()!=old;
            consumed.put(key,sign.edge());
            if(edge && sign.header().pulse() && sign.header().inverted()
                    && sign.powered()!=sign.previousPower()
                    && (sign.powered()?sign.header().rising():sign.header().falling())
                    && train.automaticRun!=null && train.automaticRun.signKey.equals(key)) {
                train.automaticRun.phase=AutomaticRun.Phase.CRUISE;
                train.automaticRun.coasting=true;
                train.automaticRun.reason="Station released by inverted pulse";
            }
            if(!sign.active(edge)) continue;
            if(sign.spec().action().equals("property") && (!previous.contains(key) || edge)) {
                train.properties().setAutomaticTargetSpeed(Double.toString(sign.spec().speed()));
                if (train.automaticRun!=null && train.automaticRun.phase==AutomaticRun.Phase.CRUISE)
                    train.automaticRun.inheritTargetSpeed=true;
                manager.save();
            }
            if(sign.spec().action().equals("destroy") && (!previous.contains(key) || edge)) {
                return manager.destroyAutomatic(train);
            }
            if(sign.spec().action().equals("station")
                    && (train.automaticRun==null || train.automaticRun.phase==AutomaticRun.Phase.CRUISE)
                    && (!previous.contains(key) || edge)) {
                Location target=sign.rail().location();
                double offset=target==null?0:target.add(.5,.0625,.5).toVector().subtract(location.toVector()).dot(direction);
                begin(train,sign,offset,direction,now);
            }
        }
        AutomaticRun run=train.automaticRun;
        if(run!=null && run.graphApproach && run.phase==AutomaticRun.Phase.APPROACH
                && run.remaining>2
                && now-queryAt.getOrDefault(train.id(),0L)>=200) {
            queryAt.put(train.id(),now);
            var forecast=stationAhead(train,now);
            if(forecast==null || !run.signKey.startsWith(forecast.signKey().toLowerCase(Locale.ROOT)+":")) {
                run.hold("Station route changed or unavailable");
            } else {
                run.remaining=stoppingDistance(forecast.distanceBlocks(),run.spec.offset());
            }
        }
        if((run==null || run.phase==AutomaticRun.Phase.CRUISE || run.phase==AutomaticRun.Phase.DEPART)
                && now-queryAt.getOrDefault(train.id(),0L)>=200) {
            queryAt.put(train.id(),now);
            var forecast=stationAhead(train,now);
            if(forecast!=null) for(Side side:Side.values()) {
                var sign=observations.get(forecast.signKey().toLowerCase(Locale.ROOT)+":"+side);
                if(sign!=null && (run==null || !run.signKey.equals(sign.key))
                        && now-sign.observedAt()<1000 && sign.active(false)
                        && RailSignAccess.entered(sign.header(),sign.facing(),forecast.approachDirection())) {
                    begin(train,sign,forecast.distanceBlocks(),forecast.approachDirection(),now);
                    if(train.automaticRun!=null) train.automaticRun.graphApproach=true;
                    break;
                }
            }
        }
        drive(train,direction,now);
        return false;
    }
    private void begin(Train train,Observation sign,double railDistance,Vector direction,long now) {
        var spec=sign.spec();
        if(spec.route()) return;
        String departure=departure(sign,direction);
        if(!departure.equals(spec.direction())) spec=new AutomaticSignSpec(spec.action(),spec.launch(),spec.offset(),
                spec.waitMillis(),departure,spec.speed(),spec.route(),spec.intervalMillis(),spec.pattern());
        boolean reverse=RailSignAccess.direction(departure,sign.facing(),direction).dot(direction)<-.1;
        double distance=stoppingDistance(railDistance,spec.offset());
        AutomaticRun next=new AutomaticRun(sign.key(),spec,distance,reverse,train.reversed);
        next.inheritTargetSpeed=!sign.explicitSpeed();
        next.initialSpeed=train.currentSpeed();
        if(spec.waitMillis()==0 && !departure.isEmpty() && !reverse) {
            // TC's zero-delay through station launches without first docking.
            next.phase=AutomaticRun.Phase.DEPART;
        }
        train.automaticRun=next;
        train.clearPlayerPush(); train.moving=true; train.pauseUntilMillis=0;
        if(distance<=.02 && next.phase==AutomaticRun.Phase.APPROACH && train.currentSpeed()<.01) next.arrived(now);
    }
    private static String departure(Observation sign,Vector direction) {
        BlockFace along=Math.abs(direction.getX())>Math.abs(direction.getZ())?BlockFace.EAST:BlockFace.SOUTH;
        boolean a=sign.header().poweredSide(sign.poweredSides().contains(along));
        boolean b=sign.header().poweredSide(sign.poweredSides().contains(along.getOppositeFace()));
        if(!sign.header().always() && a!=b) return (a?along:along.getOppositeFace()).name().toLowerCase(Locale.ROOT);
        return sign.spec().direction();
    }
    private void drive(Train train,Vector direction,long now) {
        AutomaticRun run=train.automaticRun;
        if(run==null) return;
        if(run.phase==AutomaticRun.Phase.APPROACH && run.remaining<=.02 && train.currentSpeed()<.01) run.arrived(now);
        var observation=observations.get(run.signKey);
        if(run.phase==AutomaticRun.Phase.APPROACH || run.phase==AutomaticRun.Phase.WAIT) {
            if(observation==null || now-observation.observedAt()>1500) run.hold("Station observation unavailable");
            else if(!observation.header().pulse() && !observation.active(false)) {
                run.phase=AutomaticRun.Phase.CRUISE; run.reason="Station released by redstone";
                run.coasting=true;
            } else if(run.phase==AutomaticRun.Phase.WAIT
                    && !departure(observation,direction).equals(run.spec.direction())) {
                begin(train,observation,0,direction,now);
                run=train.automaticRun; run.arrived(now);
            }
        }
        VehicleProfile v=plugin.vehicleProfile();
        if(run.coasting) { train.powerNotch=0; train.brakeNotch=0; return; }
        double speed=train.currentSpeed();
        double cap=Math.min(train.maxSpeed,v.maxSpeed());
        double cruise=Math.min(cap,run.inheritTargetSpeed
                ? train.properties().automaticTargetSpeed==null?defaultSpeed():train.properties().automaticTargetSpeed
                : run.spec.speed()==null?cap:Math.abs(run.spec.speed()));
        if(run.phase==AutomaticRun.Phase.WAIT && now>=run.until) {
            if(run.reverse && train.reversed==run.initialReversed) {
                train.reversePending=true;
                train.reverseBrakeDeadlineMillis=Math.max(train.reverseBrakeDeadlineMillis,now+5000);
            } else if(!train.reversePending && train.reverseSettleUntilMillis<=now) {
                run.phase=AutomaticRun.Phase.DEPART; run.reason="Station departure";
            }
        }
        double[] powers=new double[5], brakes=new double[8];
        double ratio=v.forceMode()?Math.max(v.minimumRatio(),Math.min(1,v.baseSpeed()/Math.max(v.baseSpeed(),speed))
                *Math.min(1,v.weakeningSpeed()/Math.max(v.weakeningSpeed(),speed))):1;
        for(int i=1;i<=4;i++) powers[i]=v.powerAcceleration(i)*ratio;
        for(int i=1;i<=7;i++) brakes[i]=v.brakeAcceleration(i);
        double resistance=v.rolling()+v.air()*speed*speed+(v.forceMode()?v.grade()*direction.getY():0);
        double target=switch(run.phase) {
            case APPROACH -> cap;
            case WAIT,HOLD -> 0;
            case DEPART,CRUISE -> cruise;
        };
        if(run.phase==AutomaticRun.Phase.DEPART) {
            if(speed >= cruise - .003) { run.phase=AutomaticRun.Phase.CRUISE; run.reason="Automatic cruise"; }
        }
        run.desiredSpeed=target;
        int notch=run.notch(speed,target,powers,brakes,resistance,now,train.brakeNotch>0?-train.brakeNotch:train.powerNotch);
        train.powerNotch=Math.max(0,notch); train.brakeNotch=Math.max(0,-notch);
        if (run.beginBrakingNotice(speed, notch)) plugin.announceStationBraking(train, run);
        train.reverser=train.reversed?Reverser.BACKWARD:Reverser.FORWARD;
    }
    private double defaultSpeed() { return plugin.getConfig().getDouble("settings.station-launch-speed",.4); }
    static double stoppingDistance(double railDistance,double offset) { return Math.max(0,railDistance+offset); }
    private record StationAhead(String signKey,double distanceBlocks,Vector approachDirection) { }
    private StationAhead stationAhead(Train train,long now) {
        var path=train.trackPath();
        if(path==null) return null;
        double range=localLookAhead(plugin.getConfig().getDouble("settings.station-local-look-ahead-blocks",256));
        if(range>0) for(var probe:path.probeAhead(range,train.reversed)) {
            var p=probe.trackPosition();
            World world=Bukkit.getWorld(p.worldName);
            if(world==null) break;
            Block rail=world.getBlockAt(p.railX,p.railY,p.railZ);
            if(!RailSignAccess.readable(rail)) break;
            for(Block sign:RailSignAccess.signsFor(rail)) {
                refresh(sign,false);
                var pos=SwitchBlockPosition.of(sign);
                for(Side side:Side.values()) {
                    var o=observations.get(pos.key()+":"+side);
                    if(o==null || !o.spec().action().equals("station") || now-o.observedAt()>1000
                            || !o.active(false) || !RailSignAccess.entered(o.header(),o.facing(),p.motion())) continue;
                    double distance=probe.distance()+new Vector(p.railX+.5-p.x,p.railY+.0625-p.y,p.railZ+.5-p.z).dot(p.motion());
                    if(distance < -.02) continue;
                    if(positions.putIfAbsent(pos.key(),pos)==null) persist();
                    return new StationAhead(pos.key(),Math.max(0,distance),p.motion());
                }
            }
        }
        var p=path.activeLeaderTrackPosition(train.reversed);
        if(p==null || plugin.telemetrySink()==null) return null;
        var forecast=plugin.telemetrySink().stationAhead(p,lookAhead());
        return forecast==null ? null : new StationAhead(forecast.signKey(),forecast.distanceBlocks(),p.motion());
    }
    static double localLookAhead(double value) { return Double.isFinite(value)?Math.max(0,Math.min(1024,value)):256; }
    private double lookAhead() { return Math.max(16,Math.min(10000,plugin.getConfig().getDouble("settings.station-look-ahead-blocks",8192))); }
    private static String plain(String value) { return ChatColor.stripColor(value==null?"":value).trim(); }
}
