package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;
import net.skyworld.sta.api.v6.DispatcherBrakeService;

public final class DispatcherMaControlTest {
    static final UUID train=UUID.randomUUID(),driver=UUID.randomUUID(),lease=UUID.randomUUID(),
            member=UUID.randomUUID(),session=UUID.randomUUID(),point=UUID.randomUUID();
    static RailGraph graph;
    static double speed=0;
    static boolean stale=false;
    static class Brake implements DispatcherBrakeService {
        CompletableFuture<String> ack;
        boolean held, stopped=true;
        int resumes, requests;
        public CompletionStage<String> hold(UUID trainId,UUID operationId,boolean stoppedOnly) {
            requests++; ack=new CompletableFuture<>(); return ack;
        }
        public boolean resume(UUID trainId,UUID operationId) {held=false;resumes++;return true;}
        public boolean stoppedHeld(UUID trainId,UUID operationId) {return held&&stopped;}
        void confirm(String result) {held=true;ack.complete(result);}
    }
    static ShadowRuntime.Inputs input() {
        long now=System.currentTimeMillis(), at=now-(stale?10000:0);
        String edge="a-"+point;
        var roster=new ConsistObservation(session,1,train,"T",at,List.of(member),List.of(
                new ConsistObservation.Member(member,"world",20.5,64.06,.5,at,at,ConsistObservation.State.OBSERVED)),false);
        var physical=new TrainTelemetrySnapshot(train,"T",1,at,"world",20,64,0,20.5,64.06,.5,1,0,0,
                speed,1,1,false,false,TrainMode.MANUAL,"Driver");
        var position=new TrackPositionSnapshot(graph.revision,edge,"a",point.toString(),20.,100.,"L",20.,at,true,false);
        var message=new StaMessage(new StaMessage.Header(StaMessage.VERSION,StaMessage.Kind.TRACK_REPORT,
                StaMessage.Source.STCS,session,2,now,train),new StaMessage.Physical(physical,1),
                new StaMessage.Tracking(session,1,at,now,15000,60000,graph.revision,StaMessage.Quality.VALID,position,"test"));
        return new ShadowRuntime.Inputs(graph,Map.of(point.toString(),"straight"),true,session,session,
                List.of(new DriverDeskService.Desk(train,driver,lease,"ACTIVE")),List.of(roster),List.of(message));
    }
    static SwitchControlService.Request pointRequest() {
        return new SwitchControlService.Request(UUID.randomUUID(),point,graph.revision,"straight","diverging",
                new SwitchControlService.Position("world",100,64,0));
    }
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var b=ShadowPlannerTest.node(point.toString(),"switch",100,0);
        var c=ShadowPlannerTest.node("c","end",200,0);
        graph=ShadowPlannerTest.graph(List.of(a,b,c),List.of(ShadowPlannerTest.edge(a,b,"east","common"),
                ShadowPlannerTest.edge(b,c,"straight","west"))).graph;
        Path dir=Files.createTempDirectory("dispatcher-ma"), file=dir.resolve("occupancy.json");
        AtomicInteger changes=new AtomicInteger(); Brake brake=new Brake();
        try(var runtime=new ShadowRuntime(null,null,file,new MaSettings(600,150,300,2),DispatcherMaControlTest::input,
                (request,guard,progress)->{
                    assert guard.getAsBoolean(); changes.incrementAndGet();return CompletableFuture.completedFuture("COMPLETED");
                })) {
            runtime.brakeSource=()->brake;
            assert runtime.command(driver,"request").equals("REQUESTED_ACTIVE");
            assert runtime.operationalSnapshot().grants().getFirst().remainingMeters()>80;
            speed=3;
            assert runtime.change(pointRequest()).toCompletableFuture().join().reason().equals("MA_CONFLICT");
            assert brake.requests==0;
            speed=0;
            var request=pointRequest();var reply=runtime.change(request).toCompletableFuture();
            assert !reply.isDone() && changes.get()==0 : "Must wait for onboard hold";
            assert runtime.status(request.requestId()).reason().equals("WITHDRAWING_MA");
            assert runtime.snapshot().sections().stream().anyMatch(s->s.reservations().contains(train));
            brake.confirm("HELD");
            assert reply.join().status().equals("COMPLETED") : reply.join();
            assert changes.get()==1 && brake.resumes==1;
            assert runtime.change(request).toCompletableFuture().join().status().equals("COMPLETED");
            assert changes.get()==1;
            assert runtime.operationalSnapshot().grants().size()==1 : "Replan without driver release/request";

            var moved=runtime.change(pointRequest()).toCompletableFuture();
            speed=2; brake.confirm("HELD");
            assert moved.join().status().equals("REJECTED");
            assert changes.get()==1 : "Movement during handshake prevents point motion";
            speed=0; runtime.command(driver,"request");
            UUID revoke=UUID.randomUUID();
            speed=8; brake.stopped=false;
            var revoked=runtime.revokeMa(revoke,train,"test").toCompletableFuture();
            assert runtime.revokeMa(revoke,train,"test").toCompletableFuture()==revoked;
            assert runtime.revokeMa(revoke,UUID.randomUUID(),"test").toCompletableFuture().join().equals("REQUEST_ID_CONFLICT");
            assert !revoked.isDone(); brake.confirm("TR");
            assert revoked.join().equals("REVOKED_TR");
            assert runtime.operationalSnapshot().grants().isEmpty();
            assert runtime.snapshot().sections().stream().anyMatch(s->s.reservations().contains(train))
                    : "Moving Trip must retain old route reservations";
            assert runtime.command(driver,"request").equals("DISPATCHER_HOLD");
            graph=new RailGraph(graph.revision+1,256,1,graph.nodes,graph.edges,graph.unresolved);
            runtime.tick();
            assert runtime.change(pointRequest()).toCompletableFuture().join().reason().equals("MA_CONFLICT")
                    : "Graph rebuild must not discard moving revoked train's point protection";
            speed=0; brake.stopped=true; runtime.tick();
            assert runtime.snapshot().sections().stream().allMatch(s->s.reservations().isEmpty());
            assert runtime.snapshot().sections().stream().anyMatch(s->s.occupants().contains(train));
            assert runtime.command(driver,"release").equals("RELEASED");
            runtime.command(driver,"request");
            runtime.eventSink=event->{throw new IllegalStateException("notification unavailable");};
            revoked=runtime.revokeMa(UUID.randomUUID(),train,"test").toCompletableFuture();
            brake.confirm("TR"); assert revoked.join().equals("REVOKED_TR");
        } finally {
            Files.deleteIfExists(file);Files.deleteIfExists(dir.resolve("occupancy.json.tmp"));Files.deleteIfExists(dir);
        }
        System.out.println("PASS stopped reroute handshake, movement guard, idempotent revoke, moving reservation retention and stopped release");
    }
}
