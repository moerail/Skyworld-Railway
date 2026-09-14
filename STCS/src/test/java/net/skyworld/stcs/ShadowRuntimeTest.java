package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v2.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v4.*;

public final class ShadowRuntimeTest {
    static final UUID train=UUID.randomUUID(),driver=UUID.randomUUID(),member=UUID.randomUUID(),session=UUID.randomUUID();
    static UUID lease=UUID.randomUUID();
    static RailGraph graph;
    static boolean hasDriver=true,available=true,reverse=false,stale=false;
    static String mode="SHADOW";
    static ConsistObservation.State memberState=ConsistObservation.State.OBSERVED;
    static ShadowRuntime.Inputs input() {
        long now=System.currentTimeMillis(),at=now-(stale?10000:0);
        var desk=new DriverDeskService.Desk(train,driver,lease,mode);
        var observation=new ConsistObservation(session,1,train,"T",at,List.of(member),List.of(
                new ConsistObservation.Member(member,"world",20.5,64.06,.5,at,at,memberState)),false);
        var physical=new TrainTelemetrySnapshot(train,"T",1,at,"world",20,64,0,20.5,64.06,.5,1,0,0,
                0,1,1,false,reverse,TrainMode.MANUAL,"Name is not a lease");
        var position=new TrackPositionSnapshot(graph.revision,"a-b","a","b",20.,100.,"L",20.,at,true,false);
        var message=new StaMessage(new StaMessage.Header(2,StaMessage.Kind.TRACK_REPORT,StaMessage.Source.STCS,
                session,2,now,train),new StaMessage.Physical(physical,1),new StaMessage.Tracking(session,1,at,now,15000,60000,
                graph.revision,StaMessage.Quality.VALID,position,"test"));
        return new ShadowRuntime.Inputs(graph,Map.of(),available,session,session,hasDriver?List.of(desk):List.of(),List.of(observation),List.of(message));
    }
    static ShadowAuthorityService.Authority authority(ShadowRuntime runtime) {
        return runtime.snapshot().authorities().stream().filter(a->a.trainId().equals(train)).findFirst().orElseThrow();
    }
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node("b","end",100,0);
        graph=ShadowPlannerTest.graph(List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west"))).graph;
        Path dir=Files.createTempDirectory("shadow-test"),file=dir.resolve("shadow.json");
        try {
            try(var runtime=new ShadowRuntime(null,null,file,600,2,ShadowRuntimeTest::input,r->CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.tick();assert authority(runtime).state().equals("INACTIVE");
                List<ShadowRuntime.MaAction> events = new ArrayList<>();
                List<ShadowRuntime.SoundNotice> sounds = new ArrayList<>();
                runtime.soundSink = sounds::add;
                runtime.eventSink = events::add;
                assert runtime.command(driver,"request","Driver").equals("REQUESTED");
                assert events.size()==1 && events.getFirst().driverName().equals("Driver");
                assert sounds.size()==1 && sounds.getFirst().cue().equals("GRANTED");
                assert sounds.getFirst().driver().equals(driver) && sounds.getFirst().lease().equals(lease);
                assert events.getFirst().train().equals(train) && events.getFirst().state().equals("ALLOCATED_SHADOW");
                runtime.tick();runtime.tick();assert events.size()==1 : "refresh must not repeat driver events";
                assert sounds.size()==1 : "refresh must not repeat grant sound";
                assert authority(runtime).state().equals("ALLOCATED_SHADOW") : "stationary driver can request";
                assert authority(runtime).creditMeters()==80;
                assert !runtime.snapshot().executable();
                hasDriver=false;runtime.tick();assert authority(runtime).reason().equals("NO_DRIVER");
                assert authority(runtime).signedRemainingMeters()==null;
                assert runtime.command(driver,"request").equals("DECLARE_DRIVE");
                assert events.size()==1 : "rejected command must not announce an accepted request";
                assert sounds.size()==1 : "no sound for rejected demand";
                hasDriver=true;lease=UUID.randomUUID();runtime.tick();assert authority(runtime).state().equals("INACTIVE");
                runtime.command(driver,"request");assert authority(runtime).creditMeters()==80;
                runtime.command(driver,"release");assert runtime.snapshot().sections().stream().allMatch(s->s.reservations().isEmpty());
                assert events.getLast().action().equals("release") && events.getLast().reason().equals("RELEASED");
                assert sounds.getLast().cue().equals("RELEASED");
                assert runtime.snapshot().sections().stream().anyMatch(s->s.occupants().contains(train));
                runtime.command(driver,"request");lease=UUID.randomUUID();runtime.tick();assert authority(runtime).state().equals("INACTIVE");
                mode="ISOLATED";assert runtime.command(driver,"request").equals("ISOLATED");
                mode="RECOVERING";assert runtime.command(driver,"request").equals("RECOVERING");
                mode="BYPASS";runtime.command(driver,"request");assert authority(runtime).state().equals("ALLOCATED_SHADOW");
                reverse=true;runtime.tick();assert authority(runtime).reason().equals("DIRECTION_CHANGED");
                runtime.command(driver,"request");assert authority(runtime).state().equals("ALLOCATED_SHADOW");
                stale=true;runtime.tick();assert authority(runtime).state().equals("WAITING");
                runtime.command(driver,"request");assert events.getLast().state().equals("WAITING");
                runtime.eventSink = event -> {throw new IllegalStateException("event service unavailable");};
                assert runtime.command(driver,"request").equals("REQUESTED") : "notification failure must not break controls";
                runtime.eventSink = events::add;
                assert runtime.snapshot().sections().stream().anyMatch(s->s.state().equals("UNCERTAIN"));
                stale=false;memberState=ConsistObservation.State.PLAYER_QUIT;runtime.tick();assert authority(runtime).state().equals("WAITING");
                memberState=ConsistObservation.State.OBSERVED;runtime.tick();assert authority(runtime).state().equals("ALLOCATED_SHADOW");
                graph=new RailGraph(2,256,1,graph.nodes,graph.edges,graph.unresolved);runtime.tick();
                assert authority(runtime).state().equals("INACTIVE") : "rebuild invalidates intent";
            }
            var config=new org.bukkit.configuration.file.YamlConfiguration();
            config.loadFromString("ma: {look-ahead-meters: 600, lock-distance-meters: 150, max-authority-distance-meters: 30}");
            try(var runtime=new ShadowRuntime(null,null,file,MaSettings.load(config),ShadowRuntimeTest::input,r->CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.command(driver,"request");
                assert authority(runtime).creditMeters()==30 : "Configured MA cap must reach the published STA snapshot";
                assert authority(runtime).reason().equals("MA_DISTANCE_LIMIT");
                assert !runtime.snapshot().executable();
            }
            available=false;
            try(var runtime=new ShadowRuntime(null,null,file,600,2,ShadowRuntimeTest::input,r->CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.tick();assert runtime.snapshot().sections().stream().anyMatch(s->s.state().equals("UNCERTAIN"));
                assert authority(runtime).state().equals("INACTIVE");
            }
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(file.resolveSibling("shadow.json.tmp"));Files.deleteIfExists(dir);}
        System.out.println("PASS driver lease, stopped request, loss/reacquire, release, modes, stale/quit/rebuild and restart retention");
    }
}
