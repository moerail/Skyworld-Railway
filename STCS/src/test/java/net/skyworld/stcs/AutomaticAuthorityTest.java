package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import net.skyworld.sta.api.v1.TrainMode;
import net.skyworld.sta.api.v3.ConsistObservation;

public final class AutomaticAuthorityTest {
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node(UUID.randomUUID().toString(),"balise",0,0);var b=ShadowPlannerTest.node(UUID.randomUUID().toString(),"end",100,0);
        var raw=ShadowPlannerTest.edge(a,b,"east","west");
        var edge=new RailGraph.Edge("a-b",raw.from(),raw.to(),raw.sourcePort(),raw.targetPort(),raw.distanceMeters(),raw.path());
        ShadowRuntimeTest.graph=ShadowPlannerTest.graph(List.of(a,b),List.of(edge)).graph;
        ShadowRuntimeTest.mode="ACTIVE";ShadowRuntimeTest.trainMode=TrainMode.AUTOMATIC;
        var dir=Files.createTempDirectory("sal-authority");var file=dir.resolve("ledger.json");
        java.util.function.Supplier<ShadowRuntime.Inputs> inputs=()-> {
            var base=ShadowRuntimeTest.input();var old=base.reports().iterator().next();long now=old.physical().state().observedAtMillis();
            var position=new net.skyworld.sta.api.v1.TrackPositionSnapshot(base.graph().revision,"a-b",a.id(),b.id(),20,100,"L",20.,now,true,false);
            var tracking=new net.skyworld.sta.api.v5.StaMessage.Tracking(base.driverSession(),1,now,now,15000,60000,base.graph().revision,
                    net.skyworld.sta.api.v5.StaMessage.Quality.VALID,position,"test");
            var message=new net.skyworld.sta.api.v5.StaMessage(old.header(),old.physical(),tracking);
            return new ShadowRuntime.Inputs(base.graph(),base.switchStates(),base.available(),base.driverSession(),base.rosterSession(),base.desks(),base.roster(),List.of(message));
        };
        try(var runtime=new ShadowRuntime(null,null,file,new MaSettings(600,150,300,2),inputs,
                request->CompletableFuture.completedFuture("COMPLETED"))) {
            var train=ShadowRuntimeTest.train;var driver=ShadowRuntimeTest.driver;var lease=ShadowRuntimeTest.lease;
            var events=new ArrayList<ShadowRuntime.MaAction>();runtime.eventSink=events::add;
            var revision=ShadowRuntimeTest.graph.revision;long sequence=0;
            assert runtime.command(driver,"request").equals("AUTOMATIC_TRAIN");
            assert runtime.automaticRequest(train,driver,UUID.randomUUID(),revision,List.of("a-b"),++sequence).equals("NOT_OWNER");
            assert runtime.automaticRequest(train,driver,lease,revision+1,List.of("a-b"),++sequence).equals("GRAPH_CHANGED");
            assert runtime.automaticRequest(train,driver,lease,revision,List.of("missing"),++sequence).equals("UNKNOWN_EDGE");
            assert runtime.automaticRequest(train,driver,lease,revision,List.of("a-b"),++sequence).equals("REQUESTED_ACTIVE");
            var grant=runtime.operationalSnapshot().grants().getFirst();assert grant.executable()&&grant.mode().equals("FS")&&grant.driverLeaseId().equals(lease);
            int requests=events.size();
            for(int i=0;i<3;i++) {
                Thread.sleep(600);
                assert runtime.automaticRenew(train,driver,lease,revision,++sequence).equals("RENEWED");runtime.tick();
                assert !runtime.operationalSnapshot().grants().isEmpty():"Heartbeat must sustain automatic MA extension";
            }
            assert events.size()==requests:"Route heartbeat must not emit repeated request events";
            assert runtime.automaticRequest(train,driver,lease,revision,List.of("a-b"),++sequence).equals("ROUTE_ACTIVE");
            assert events.size()==requests:"Idempotent path demand must not emit request events";
            assert runtime.automaticRenew(train,UUID.randomUUID(),lease,revision,++sequence).equals("NOT_OWNER");
            Thread.sleep(1600);runtime.tick();assert runtime.operationalSnapshot().grants().isEmpty():"Expired route must not grant";
            assert runtime.automaticRenew(train,driver,lease,revision,++sequence).equals("NO_ROUTE");
            runtime.automaticRequest(train,driver,lease,revision,List.of("a-b"),++sequence);
            assert !runtime.operationalSnapshot().grants().isEmpty();
            ShadowRuntimeTest.memberState=ConsistObservation.State.UNLOADED;runtime.tick();
            assert runtime.operationalSnapshot().grants().isEmpty():"TIMS fault must revoke auto authority";
            ShadowRuntimeTest.memberState=ConsistObservation.State.OBSERVED;
            runtime.automaticRequest(train,driver,lease,revision,List.of("a-b"),++sequence);
            ShadowRuntimeTest.speed=.2;
            assert runtime.automaticRelease(train,driver,lease,++sequence).equals("STOP_FIRST");
            ShadowRuntimeTest.speed=0;
            assert runtime.automaticRelease(train,driver,lease,++sequence).equals("RELEASED");
            assert runtime.operationalSnapshot().grants().isEmpty();
            assert runtime.automaticRequest(train,driver,lease,revision,List.of("a-b"),sequence-1).equals("STALE_REQUEST");
            assert runtime.automaticRenew(train,driver,lease,revision,sequence-1).equals("STALE_REQUEST");
            assert runtime.operationalSnapshot().grants().isEmpty():"Late pre-release request must not restore MA";
            assert runtime.automaticSr(train,driver,lease,revision,++sequence).equals("SR_PENDING");
            assert runtime.operationalSnapshot().pendingSr().size()==1&&runtime.operationalSnapshot().grants().isEmpty();
            assert runtime.approveSr(train,UUID.fromString(b.id()),"PCC operator").equals("APPROVED");
            assert runtime.operationalSnapshot().grants().getFirst().mode().equals("SR");
            assert runtime.automaticSr(train,driver,lease,revision,++sequence).equals("ALREADY_APPROVED");
            assert runtime.automaticRelease(train,driver,lease,++sequence).equals("RELEASED");
            assert runtime.automaticSr(train,driver,lease,revision,++sequence).equals("SR_PENDING");
            assert runtime.operationalSnapshot().grants().isEmpty():"Next SR segment needs another operator approval";
        } finally {
            try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
        System.out.println("PASS automatic FS identity, graph/path rejection, expiry, TIMS revocation and release");
    }
}
