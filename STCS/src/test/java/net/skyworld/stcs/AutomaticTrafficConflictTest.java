package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

/** Two live trains demand different positions of a shared depot throat. */
public final class AutomaticTrafficConflictTest {
    record Car(UUID train,UUID driver,UUID lease,UUID member) {
        Car(){this(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());}
    }
    public static void main(String[] args)throws Exception {
        var a=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",0,0);
        var sw=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"switch","",100,0);
        var c=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"end","",200,0);
        var d=LineBoundaryExitTest.node(UUID.randomUUID().toString(),"balise","",100,100);
        var approach=LineBoundaryExitTest.edge("in",a,sw,"east","common");
        var straight=LineBoundaryExitTest.edge("straight",sw,c,"straight","west");
        var side=LineBoundaryExitTest.edge("side",d,sw,"north","diverging");
        var exit=LineBoundaryExitTest.edge("exit",sw,a,"common","east");
        var graph=new RailGraph(1,256,1,List.of(a,sw,c,d),List.of(approach,straight,side,exit),List.of());
        var first=new Car();var second=new Car();var session=UUID.randomUUID();
        var cleared=new AtomicBoolean();var state=new AtomicReference<>("straight");
        var changes=new AtomicInteger();var brake=new DispatcherMaControlTest.Brake();
        java.util.function.Supplier<ShadowRuntime.Inputs> source=()->{
            long now=System.currentTimeMillis();var reports=new ArrayList<StaMessage>();var roster=new ArrayList<ConsistObservation>();
            var desks=new ArrayList<DriverDeskService.Desk>();
            for(var car:List.of(first,second)) {
                boolean one=car==first;
                var edge=one?(cleared.get()?straight:approach):side;
                double offset=one?(cleared.get()?50:80):80;
                double x=one?(cleared.get()?150:80):100,z=one?0:20;
                var physical=new TrainTelemetrySnapshot(car.train(),one?"first":"second",1,now,"world",(int)x,64,(int)z,
                        x+.5,64.0625,z+.5,one?1:0,0,one?0:-1,0,1,1,false,false,TrainMode.AUTOMATIC,"");
                var position=new TrackPositionSnapshot(1,edge.id(),edge.from(),edge.to(),offset,edge.distanceMeters(),"",null,now,true,false);
                reports.add(new StaMessage(new StaMessage.Header(5,StaMessage.Kind.TRACK_REPORT,StaMessage.Source.STCS,session,1,now,car.train()),
                        new StaMessage.Physical(physical,1),new StaMessage.Tracking(session,1,now,now,15000,60000,1,StaMessage.Quality.VALID,position,"")));
                roster.add(new ConsistObservation(session,1,car.train(),one?"first":"second",now,List.of(car.member()),
                        List.of(new ConsistObservation.Member(car.member(),"world",x+.5,64.0625,z+.5,now,now,ConsistObservation.State.OBSERVED)),false,
                        ShadowRuntimeTest.completeIntegrity(List.of(car.member()),now)));
                desks.add(new DriverDeskService.Desk(car.train(),car.driver(),car.lease(),"ACTIVE"));
            }
            return new ShadowRuntime.Inputs(graph,Map.of(sw.id(),state.get()),true,session,session,desks,roster,reports);
        };
        var dir=Files.createTempDirectory("automatic-two-trains");
        try(var runtime=new ShadowRuntime(null,null,dir.resolve("ledger.json"),new MaSettings(600,30,300,2),source,
                (request,guard,progress)->{
                    assert guard.getAsBoolean();changes.incrementAndGet();state.set(request.targetState());return CompletableFuture.completedFuture("COMPLETED");
                })) {
            runtime.brakeSource=()->brake;var events=new ArrayList<ShadowRuntime.MaAction>();runtime.eventSink=events::add;
            assert runtime.automaticRequest(first.train(),first.driver(),first.lease(),1,List.of("in","straight"),1).equals("REQUESTED_ACTIVE");
            var firstGrant=runtime.operationalSnapshot().grants().stream().filter(g->g.trainId().equals(first.train())).findFirst().orElseThrow();
            assert firstGrant.path().stream().anyMatch(p->p.edgeId().equals("straight")):runtime.snapshot().authorities()+" / "+firstGrant;
            assert runtime.automaticRequest(second.train(),second.driver(),second.lease(),1,List.of("side","exit"),1).equals("REQUESTED_ACTIVE");
            for(int i=2;i<8;i++) {
                assert runtime.automaticRenew(first.train(),first.driver(),first.lease(),1,i).equals("RENEWED");
                assert runtime.automaticRenew(second.train(),second.driver(),second.lease(),1,i).equals("RENEWED");runtime.tick();
                assert brake.requests==0:"Automatic alignment must never withdraw the other train's MA";
                assert changes.get()==0&&state.get().equals("straight"):"Wait for existing throat reservation";
                assert runtime.operationalSnapshot().grants().stream().anyMatch(g->g.trainId().equals(first.train())&&g.executable());
            }
            assert events.stream().filter(e->e.action().equals("request")).count()==2:"Waiting traffic uses heartbeats, not repeated MA requests";
            cleared.set(true);runtime.tick();
            assert runtime.automaticRelease(first.train(),first.driver(),first.lease(),8).equals("RELEASED");
            // Rejected alignment has a one-second retry backoff; advance only that
            // timer, keeping the real route lease and runtime conflict checks.
            var timer=ShadowRuntime.class.getDeclaredField("nextAutomaticPoint");timer.setAccessible(true);
            ((Map<?,?>)timer.get(runtime)).clear();runtime.tick();
            assert changes.get()==1&&state.get().equals("diverging"):"Existing second route proceeds once first clears";
            runtime.tick();
            assert runtime.operationalSnapshot().grants().stream().anyMatch(g->g.trainId().equals(second.train())&&g.executable()&&g.path().stream().anyMatch(p->p.edgeId().equals("exit")));
            assert brake.requests==0;
            assert events.stream().filter(e->e.action().equals("request")).count()==2;
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("PASS two-train throat conflict waits without foreign MA withdrawal, then aligns and extends the original route");
    }
}
