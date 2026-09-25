package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v5.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

public final class ShadowRuntimeTest {
    static final UUID train=UUID.randomUUID(),driver=UUID.randomUUID(),member=UUID.randomUUID(),session=UUID.randomUUID();
    static UUID lease=UUID.randomUUID();
    static RailGraph graph;
    static boolean hasDriver=true,available=true,reverse=false,stale=false;
    static String mode="SHADOW";
    static TrainMode trainMode=TrainMode.MANUAL;
    static ConsistObservation.State memberState=ConsistObservation.State.OBSERVED;
    static ShadowRuntime.Inputs input() {
        long now=System.currentTimeMillis(),at=now-(stale?10000:0);
        var desk=new DriverDeskService.Desk(train,driver,lease,mode);
        var observation=new ConsistObservation(session,1,train,"T",at,List.of(member),List.of(
                new ConsistObservation.Member(member,"world",20.5,64.06,.5,at,at,memberState)),false);
        var physical=new TrainTelemetrySnapshot(train,"T",1,at,"world",20,64,0,20.5,64.06,.5,1,0,0,
                0,1,1,false,reverse,trainMode,"Name is not a lease");
        var position=new TrackPositionSnapshot(graph.revision,"a-b","a","b",20.,100.,"L",20.,at,true,false);
        var message=new StaMessage(new StaMessage.Header(StaMessage.VERSION,StaMessage.Kind.TRACK_REPORT,StaMessage.Source.STCS,
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
            available=true; mode="ACTIVE"; hasDriver=true; reverse=false; stale=false;
            memberState=ConsistObservation.State.OBSERVED;
            Path activeDir=Files.createTempDirectory("active-authority");
            Path activeFile=activeDir.resolve("shadow.json");
            try {
                try(var runtime=new ShadowRuntime(null,null,activeFile,600,2,
                        ShadowRuntimeTest::input,r->CompletableFuture.completedFuture("COMPLETED"))) {
                    List<ShadowRuntime.MaAction> activeEvents=new ArrayList<>();
                    runtime.eventSink=activeEvents::add;
                    trainMode=TrainMode.AUTOMATIC;
                    assert runtime.command(driver,"request").equals("AUTOMATIC_TRAIN");
                    trainMode=TrainMode.MANUAL;
                    assert runtime.command(driver,"request").equals("REQUESTED_ACTIVE");
                    var grants=runtime.operationalSnapshot().grants();
                    assert grants.size()==1 && grants.getFirst().executable();
                    var grant=grants.getFirst();
                    assert runtime.command(driver,"sr").equals("RELEASE_FIRST")
                            : "FS to SR requires releasing the old authority first";
                    assert !runtime.acknowledgeGrant(train,UUID.randomUUID(),grant.id(),graph.revision);
                    assert runtime.acknowledgeGrant(train,lease,grant.id(),graph.revision);
                    assert !runtime.acknowledgeGrant(train,lease,grant.id(),graph.revision+1);
                    runtime.revokeForChannelChange(train);
                    assert runtime.operationalSnapshot().grants().isEmpty()
                            : "old executable grant must disappear immediately";
                    runtime.tick();
                    assert runtime.operationalSnapshot().grants().isEmpty();
                    assert runtime.snapshot().sections().stream().allMatch(s -> s.reservations().isEmpty());
                    assert runtime.snapshot().sections().stream().anyMatch(s -> s.occupants().contains(train));
                    assert runtime.command(driver,"request").equals("REQUESTED_ACTIVE");
                    activeEvents.clear();
                    mode="SHADOW";runtime.tick();
                    assert runtime.operationalSnapshot().grants().isEmpty()
                            : "channel change must revoke an old active demand";
                    mode="ACTIVE";runtime.tick();
                    assert runtime.operationalSnapshot().grants().isEmpty()
                            : "returning to active must require a new demand";
                    assert runtime.command(driver,"request").equals("REQUESTED_ACTIVE");
                    activeEvents.clear();
                    trainMode=TrainMode.AUTOMATIC;
                    runtime.tick();
                    assert runtime.operationalSnapshot().grants().isEmpty()
                            : "an existing manual-train intent must be revoked on automatic conversion";
                    assert activeEvents.stream().filter(e -> e.action().equals("unavailable")).count()==1;
                    trainMode=TrainMode.MANUAL;
                    assert runtime.command(driver,"request").equals("REQUESTED_ACTIVE");
                    activeEvents.clear();
                    available=false;
                    runtime.tick();runtime.tick();
                    assert activeEvents.stream().filter(e -> e.action().equals("unavailable")).count()==1
                            : "active MA loss must warn once, not every poll: " + activeEvents;
                    available=true;
                }
                var srA=ShadowPlannerTest.node("a","balise",0,0);
                var srB=ShadowPlannerTest.node("b","balise",100,0);
                var srC=ShadowPlannerTest.node("c","balise",200,0);
                var srTarget=ShadowPlannerTest.node(UUID.randomUUID().toString(),"end",300,0);
                graph=ShadowPlannerTest.graph(List.of(srA,srB,srC,srTarget),List.of(
                        ShadowPlannerTest.edge(srA,srB,"east","west"),
                        ShadowPlannerTest.edge(srB,srC,"east","west"),
                        ShadowPlannerTest.edge(srC,srTarget,"east","west"))).graph;
                Path srFile=activeDir.resolve("sr.json");
                try(var runtime=new ShadowRuntime(null,null,srFile,600,2,
                        ShadowRuntimeTest::input,r->CompletableFuture.completedFuture("COMPLETED"))) {
                    assert runtime.command(driver,"sr").equals("SR_PENDING");
                    assert runtime.approveSr(train,UUID.fromString(srTarget.id()),"dispatcher").equals("APPROVED");
                    var granted=runtime.operationalSnapshot().grants();
                    assert granted.size()==1 && granted.getFirst().mode().equals("SR")
                            && granted.getFirst().remainingMeters()>0 && granted.getFirst().remainingMeters()<=120
                            : "Distant SR target needs a short, locally verified initial MA: "+granted;
                } finally {Files.deleteIfExists(srFile);Files.deleteIfExists(srFile.resolveSibling("sr.json.tmp"));}
            } finally {Files.deleteIfExists(activeFile);Files.deleteIfExists(activeFile.resolveSibling("shadow.json.tmp"));Files.deleteIfExists(activeDir);}
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(file.resolveSibling("shadow.json.tmp"));Files.deleteIfExists(dir);}
        System.out.println("PASS driver lease, stopped request, loss/reacquire, release, modes, stale/quit/rebuild and restart retention");
    }
}
