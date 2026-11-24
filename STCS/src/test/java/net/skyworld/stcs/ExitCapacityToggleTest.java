package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

public final class ExitCapacityToggleTest {
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var sw=ShadowPlannerTest.node("b","switch",100,0);
        UUID target=UUID.randomUUID();
        var end=ShadowPlannerTest.node(target.toString(),"end",105,0);
        var approach=ShadowPlannerTest.edge(a,sw,"east","common");
        var exit=ShadowPlannerTest.edge(sw,end,"straight","west");
        var graph=ShadowPlannerTest.graph(List.of(a,sw,end),List.of(approach,exit));
        var states=Map.of("b","straight");
        var enabled=new MaSettings(600,150,300,2);
        var disabled=new MaSettings(600,150,300,2,false);
        var r=ShadowPlanner.plan(graph,approach.id(),80,40,enabled,states,Set.of());
        assert r.reason().equals("NO_EXIT_CAPACITY") && r.remaining()==18 : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,disabled,states,Set.of());
        assert r.remaining()==25 && r.path().size()==2 : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,new MaSettings(600,150,22,2,false),states,Set.of());
        assert r.remaining()==22 && r.path().getLast().toMeters()==2 : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,new MaSettings(22,150,300,2,false),states,Set.of());
        assert r.remaining()<=22 : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,new MaSettings(600,10,300,2,false),states,Set.of());
        assert r.remaining()==19 && r.reason().equals("SWITCH_LOCK_DISTANCE") : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,disabled,Map.of(),Set.of());
        assert r.remaining()==19 && r.reason().equals("SWITCH_UNKNOWN") : r;
        r=ShadowPlanner.plan(graph,approach.id(),80,40,disabled,states,Set.of(graph.resource.get(exit.id())));
        assert r.remaining()<=19 && r.reason().equals("RESOURCE_CONFLICT") : r;
        assert !DynamicSwitchLock.DEFAULT.forSpeed(disabled,30).checkExitCapacity();
        assert !MaSettings.legacy(120,0,disabled.checkExitCapacity()).checkExitCapacity();

        // The setting must survive FS dynamic range and SH/SR range substitutions in the runtime.
        UUID train=UUID.randomUUID(),driver=UUID.randomUUID(),lease=UUID.randomUUID(),member=UUID.randomUUID(),session=UUID.randomUUID();
        Path dir=Files.createTempDirectory("exit-capacity-toggle");
        try {
            for(boolean check:List.of(true,false)) {
                var settings=new MaSettings(600,150,300,2,check);
                Path file=dir.resolve(check+".json");
                try(var runtime=new ShadowRuntime(null,null,file,settings,()-> {
                    long now=System.currentTimeMillis();
                    var physical=new TrainTelemetrySnapshot(train,"T",1,now,"world",80,64,0,80.5,64.06,.5,
                            1,0,0,0,40,1,false,false,TrainMode.MANUAL,"T");
                    var position=new TrackPositionSnapshot(graph.graph.revision,approach.id(),"a","b",80.,100.,"L",80.,now,true,false);
                    var message=new StaMessage(new StaMessage.Header(StaMessage.VERSION,StaMessage.Kind.TRACK_REPORT,
                            StaMessage.Source.STCS,session,2,now,train),new StaMessage.Physical(physical,1),
                            new StaMessage.Tracking(session,1,now,now,15000,60000,graph.graph.revision,StaMessage.Quality.VALID,position,"test"));
                    var roster=new ConsistObservation(session,1,train,"T",now,List.of(member),List.of(
                            new ConsistObservation.Member(member,"world",80.5,64.06,.5,now,now,ConsistObservation.State.OBSERVED)),
                            false,ShadowRuntimeTest.completeIntegrity(List.of(member),now));
                    return new ShadowRuntime.Inputs(graph.graph,states,true,session,session,
                            List.of(new DriverDeskService.Desk(train,driver,lease,"ACTIVE")),List.of(roster),List.of(message));
                }, request->CompletableFuture.completedFuture("COMPLETED"))) {
                    for(String mode:List.of("request","sh","sr")) {
                        runtime.command(driver,mode);
                        if(mode.equals("sr")) {
                            assert runtime.operationalSnapshot().pendingSr().size()==1;
                            assert runtime.approveSr(train,target,"test").equals("APPROVED");
                        }
                        var grant=runtime.operationalSnapshot().grants().getFirst();
                        assert grant.remainingMeters()==(check?(mode.equals("sr")?19:18):25) : mode+" "+grant;
                        runtime.command(driver,"release");
                    }
                } finally {Files.deleteIfExists(file);Files.deleteIfExists(file.resolveSibling(file.getFileName()+".tmp"));}
            }
        } finally {Files.deleteIfExists(dir);}
        System.out.println("PASS exit-capacity toggle, default, dynamic/SH propagation, MA/search/point caps and conflicts");
    }
}
