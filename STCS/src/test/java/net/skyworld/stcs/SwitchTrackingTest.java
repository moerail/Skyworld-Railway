package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

/** A point's first curved tick still carries the incoming tangent (307 depot regression). */
public final class SwitchTrackingTest {
    static RailGraph graph;
    static RailGraph.Node a,sw,b,c;
    static void fixture() {
        a=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",0,0);
        sw=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"switch","",10,0);
        b=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",110,0);
        c=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",10,10);
        graph=new RailGraph(1,256,1,List.of(a,sw,b,c),List.of(
            LineBoundaryExitTest.edge("approach",a,sw,"east","common"),
            LineBoundaryExitTest.edge("back",sw,a,"common","east"),
            LineBoundaryExitTest.edge("straight",sw,b,"straight","west"),
            LineBoundaryExitTest.edge("straight-in",b,sw,"west","straight"),
            LineBoundaryExitTest.edge("branch",sw,c,"diverging","north"),
            LineBoundaryExitTest.edge("branch-in",c,sw,"north","diverging")),List.of());
    }
    static void geometry() {
        var facing=graph.query("world",10,64,0,1,0,0,n->"diverging");
        assert "approach".equals(facing.get("edgeId")) : "Incoming tangent must not jump onto disconnected straight: "+facing;
        assert "branch".equals(graph.query("world",10,64,0,0,0,1,n->"diverging").get("edgeId"));
        assert "back".equals(graph.query("world",10,64,0,-1,0,0,n->"diverging").get("edgeId")) : "Trailing movement must not jump onto disconnected straight-in";
        assert "straight".equals(graph.query("world",10,64,0,1,0,0,n->"straight").get("edgeId"));
        // The physical confirmation, not a saved/commanded state, owns localization.
        assert "approach".equals(graph.query("world",10,64,0,1,0,0,n->"straight",n->"diverging").get("edgeId"));
        for(String state:List.of("UNKNOWN","PENDING","FAILED")) {
            assert !"straight".equals(graph.query("world",10,64,0,1,0,0,n->"straight",n->state).get("edgeId"));
            assert graph.query("world",10,64,0,0,0,1,n->"diverging",n->state).isEmpty();
        }
        assert "branch".equals(graph.query("world",10,64,1,0,0,1,n->"straight").get("edgeId")) : "Do not relocate an already departed train when points change behind it";
    }
    static void runtime(boolean trailing) throws Exception {
        ShadowRuntimeTest.graph=graph;ShadowRuntimeTest.mode="ACTIVE";ShadowRuntimeTest.trainMode=TrainMode.AUTOMATIC;
        var x=new AtomicInteger(trailing?10:9);var z=new AtomicInteger(trailing?1:0);
        var dx=new AtomicInteger(trailing?0:1);var dz=new AtomicInteger(trailing?-1:0);var sequence=new AtomicLong();
        java.util.function.Supplier<ShadowRuntime.Inputs> source=()->{
            var base=ShadowRuntimeTest.input();long now=System.currentTimeMillis(),seq=sequence.incrementAndGet();
            var navigation=graph.query("world",x.get(),64,z.get(),dx.get(),0,dz.get(),n->"diverging");
            String edge=(String)navigation.get("edgeId");
            var p=graph.edgePosition(1,edge,((Number)navigation.get("edgeOffsetMeters")).doubleValue());
            var position=new TrackPositionSnapshot(1,p.edgeId(),p.edgeFromNodeId(),p.edgeToNodeId(),p.edgeOffsetMeters(),p.edgeLengthMeters(),p.line(),p.mileageMeters(),now,true,false);
            var physical=new TrainTelemetrySnapshot(ShadowRuntimeTest.train,"T",seq,now,"world",x.get(),64,z.get(),x.get()+.5,64.06,z.get()+.5,
                dx.get(),0,dz.get(),0,1,1,false,false,TrainMode.AUTOMATIC,"");
            var tracking=new StaMessage.Tracking(base.driverSession(),seq,now,now,15000,60000,1,StaMessage.Quality.VALID,position,"");
            var report=new StaMessage(new StaMessage.Header(5,StaMessage.Kind.TRACK_REPORT,StaMessage.Source.STCS,base.driverSession(),seq,now,ShadowRuntimeTest.train),new StaMessage.Physical(physical,1),tracking);
            var roster=new ConsistObservation(base.rosterSession(),seq,ShadowRuntimeTest.train,"T",now,List.of(ShadowRuntimeTest.member),
                List.of(new ConsistObservation.Member(ShadowRuntimeTest.member,"world",x.get()+.5,64.06,z.get()+.5,now,now,ConsistObservation.State.OBSERVED)),false,
                ShadowRuntimeTest.completeIntegrity(List.of(ShadowRuntimeTest.member),now));
            return new ShadowRuntime.Inputs(graph,Map.of(sw.id(),"diverging"),true,base.driverSession(),base.rosterSession(),base.desks(),List.of(roster),List.of(report));
        };
        var dir=Files.createTempDirectory("point-tracking");
        try(var runtime=new ShadowRuntime(null,null,dir.resolve("ledger.json"),new MaSettings(600,150,300,0),source,
                r->CompletableFuture.completedFuture("COMPLETED"))) {
            var route=trailing?List.of("branch-in","back"):List.of("approach","branch");
            assert runtime.automaticRequest(ShadowRuntimeTest.train,ShadowRuntimeTest.driver,ShadowRuntimeTest.lease,1,route,1).equals("REQUESTED_ACTIVE");
            assert !runtime.operationalSnapshot().grants().isEmpty();
            x.set(10);z.set(0);runtime.tick();
            assert !runtime.operationalSnapshot().grants().isEmpty() : "First tick on point must retain executable FS";
            dx.set(trailing?-1:0);dz.set(trailing?0:1);runtime.tick();
            assert !runtime.operationalSnapshot().grants().isEmpty() : "Changed tangent at point must remain on route";
            if(trailing)x.set(9);else z.set(1);runtime.tick();
            assert !runtime.operationalSnapshot().grants().isEmpty();
            // Real travel on a different branch remains a route mismatch; never project onto the requested route.
            x.set(11);z.set(0);dx.set(1);dz.set(0);runtime.tick();
            assert runtime.operationalSnapshot().grants().isEmpty() : "Real deviation must revoke authority";
            assert runtime.snapshot().authorities().stream().anyMatch(v->v.reason().equals("ROUTE_MISMATCH"));
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    public static void main(String[] args) throws Exception {
        fixture();geometry();runtime(false);runtime(true);
        System.out.println("PASS facing/trailing point localization, live confirmation, continuous FS and real route deviation");
    }
}
