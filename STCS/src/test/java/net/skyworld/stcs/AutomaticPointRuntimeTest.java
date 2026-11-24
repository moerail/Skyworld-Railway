package net.skyworld.stcs;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

public final class AutomaticPointRuntimeTest {
    public static void main(String[] args) throws Exception {
        var a=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",0,0);
        var sw=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"switch","",100,0);
        var b=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",100,100);
        var approach=LineBoundaryExitTest.edge("a-b",a,sw,"east","common");var branch=LineBoundaryExitTest.edge("branch",sw,b,"diverging","north");
        ShadowRuntimeTest.graph=new RailGraph(1,256,1,List.of(a,sw,b),List.of(approach,branch),List.of());
        ShadowRuntimeTest.mode="ACTIVE";ShadowRuntimeTest.trainMode=TrainMode.AUTOMATIC;
        var x=new AtomicReference<Double>(20.);var state=new AtomicReference<>("straight");var changes=new AtomicInteger();
        var seq=new AtomicLong();
        java.util.function.Supplier<ShadowRuntime.Inputs> source=()->{
            var base=ShadowRuntimeTest.input();var old=base.reports().iterator().next();long now=System.currentTimeMillis(),s=seq.incrementAndGet();
            var physical=new TrainTelemetrySnapshot(ShadowRuntimeTest.train,"T",s,now,"world",x.get().intValue(),64,0,x.get()+.5,64.06,.5,
                1,0,0,0,1,1,false,false,TrainMode.AUTOMATIC,"");
            var p=new TrackPositionSnapshot(1,"a-b",a.id(),sw.id(),x.get(),100,"",null,now,true,false);
            var tracking=new StaMessage.Tracking(base.driverSession(),s,now,now,15000,60000,1,StaMessage.Quality.VALID,p,"");
            var report=new StaMessage(new StaMessage.Header(5,StaMessage.Kind.TRACK_REPORT,StaMessage.Source.STCS,base.driverSession(),s,now,ShadowRuntimeTest.train),new StaMessage.Physical(physical,1),tracking);
            var roster=new ConsistObservation(base.rosterSession(),s,ShadowRuntimeTest.train,"T",now,List.of(ShadowRuntimeTest.member),
                List.of(new ConsistObservation.Member(ShadowRuntimeTest.member,"world",x.get()+.5,64.06,.5,now,now,ConsistObservation.State.OBSERVED)),false,
                ShadowRuntimeTest.completeIntegrity(List.of(ShadowRuntimeTest.member),now));
            return new ShadowRuntime.Inputs(base.graph(),Map.of(sw.id(),state.get()),true,base.driverSession(),base.rosterSession(),base.desks(),List.of(roster),List.of(report));
        };
        var dir=Files.createTempDirectory("automatic-points");
        try(var runtime=new ShadowRuntime(null,null,dir.resolve("ledger.json"),new MaSettings(600,30,300,2),source,
                (request,guard,progress)->{
                    assert guard.getAsBoolean():"Automatic point actuation must retain occupancy/MA guards";
                    changes.incrementAndGet();state.set(request.targetState());return CompletableFuture.completedFuture("COMPLETED");
                })) {
            var events=new ArrayList<ShadowRuntime.MaAction>();runtime.eventSink=events::add;
            assert runtime.automaticRequest(ShadowRuntimeTest.train,ShadowRuntimeTest.driver,ShadowRuntimeTest.lease,1,List.of("a-b","branch"),1).equals("REQUESTED_ACTIVE");
            assert changes.get()==0:"Point is initially beyond lock horizon";
            x.set(80.);runtime.tick();
            assert changes.get()==1&&state.get().equals("diverging"):"Existing route must align points as train approaches, without another request";
            runtime.tick();assert changes.get()==1;
            assert events.stream().filter(e->e.action().equals("request")).count()==1;
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("PASS persistent automatic route aligns approaching points with guards and no repeated MA request");
    }
}
