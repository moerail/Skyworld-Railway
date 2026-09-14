package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import net.skyworld.sta.api.v1.TrackPositionSnapshot;
import net.skyworld.sta.api.v2.StaMessage;
import net.skyworld.sta.api.v4.SwitchControlService;

public final class LocalSwitchControlTest {
    public static void main(String[] args) throws Exception {
        UUID id=UUID.randomUUID(),empty=UUID.randomUUID(),broken=UUID.randomUUID();
        var a=ShadowPlannerTest.node("a","balise",0,0);var point=ShadowPlannerTest.node(id.toString(),"switch",100,0);
        var end=ShadowPlannerTest.node("end","end",200,0);
        var entry=ShadowPlannerTest.edge(a,point,"east","common");var exit=ShadowPlannerTest.edge(point,end,"straight","west");
        var graph=ShadowPlannerTest.graph(List.of(a,point,end),List.of(entry,exit)).graph;
        var through=ShadowPlannerTest.edge(a,end,"east","west");
        var geometric=ShadowPlannerTest.graph(List.of(a,point,end),List.of(through));
        assert ShadowRuntime.pointsInPath(geometric,List.of(new net.skyworld.sta.api.v4.ShadowAuthorityService.PathPart(through.id(),20,99))).isEmpty();
        assert ShadowRuntime.pointsInPath(geometric,List.of(new net.skyworld.sta.api.v4.ShadowAuthorityService.PathPart(through.id(),20,101))).contains(id.toString())
                : "A geometric MA crossing must protect the point even without its node in the directed path";
        ShadowRuntimeTest.graph=graph;
        var position=new SwitchControlService.Position("world",100,64,0);
        var next=new AtomicReference<>(new CompletableFuture<String>());var guard=new AtomicReference<BooleanSupplier>();
        var trackingEdge=new AtomicReference<>(entry);
        var progress=new AtomicReference<Consumer<String>>();var calls=new AtomicInteger();
        Supplier<ShadowRuntime.Inputs> source=()->{
            var original=ShadowRuntimeTest.input();var msg=original.reports().iterator().next();var t=msg.tracking();
            var edge=trackingEdge.get();
            var p=new TrackPositionSnapshot(original.graph().revision,edge.id(),edge.from(),edge.to(),20.,edge.distanceMeters(),"L",20.,msg.header().emittedAtMillis(),true,false);
            var report=new StaMessage(msg.header(),msg.physical(),new StaMessage.Tracking(t.telemetrySessionId(),t.telemetrySequence(),
                    t.receivedAtMillis(),t.resolvedAtMillis(),t.staleAfterMillis(),t.expireAfterMillis(),original.graph().revision,StaMessage.Quality.VALID,p,"test"));
            return new ShadowRuntime.Inputs(original.graph(),Map.of(id.toString(),"straight"),true,original.driverSession(),original.rosterSession(),
                    original.desks(),original.roster(),List.of(report));
        };
        Path dir=Files.createTempDirectory("local-switch"),file=dir.resolve("shadow.json");
        Files.writeString(file,"{\""+empty+"\":[]}");
        try(var runtime=new ShadowRuntime(null,null,file,MaSettings.legacy(600,2),source,(request,check,stage)->{
            calls.incrementAndGet();guard.set(check);progress.set(stage);return next.get();
        })) {
            runtime.command(ShadowRuntimeTest.driver,"request");
            assert runtime.change(new SwitchControlService.Request(UUID.randomUUID(),id,1,"straight","diverging",position))
                    .toCompletableFuture().join().reason().equals("MA_CONFLICT");
            runtime.command(ShadowRuntimeTest.driver,"release");
            var wrong=new SwitchControlService.Request(UUID.randomUUID(),id,1,"straight","diverging",new SwitchControlService.Position("world",101,64,0));
            assert runtime.change(wrong).toCompletableFuture().join().reason().equals("POSITION_MISMATCH");
            var legacy=new SwitchControlService.Request(UUID.randomUUID(),id,1,"straight","diverging");
            assert runtime.change(legacy).toCompletableFuture().join().reason().equals("POSITION_REQUIRED");
            assert calls.get()==0;
            var request=new SwitchControlService.Request(UUID.randomUUID(),id,1,"straight","diverging",position);
            var reply=runtime.change(request).toCompletableFuture();
            assert !reply.isDone() && calls.get()==1 && runtime.change(request)==reply;
            progress.get().accept("LOADING_CHUNKS");
            assert runtime.status(request.requestId()).status().equals("PENDING") && runtime.status(request.requestId()).reason().equals("LOADING_CHUNKS");
            assert runtime.change(new SwitchControlService.Request(UUID.randomUUID(),id,1,"straight","diverging",position))
                    .toCompletableFuture().join().reason().equals("POINT_BUSY");
            runtime.command(ShadowRuntimeTest.driver,"request");
            assert ShadowRuntimeTest.authority(runtime).creditMeters()==79 : "Pending point must stop new MA one block before it";
            assert guard.get().getAsBoolean() : "An approach-only MA is not a point conflict";
            trackingEdge.set(through);
            ShadowRuntimeTest.graph=new RailGraph(2,256,1,graph.nodes,List.of(through),graph.unresolved);runtime.tick();
            runtime.command(ShadowRuntimeTest.driver,"request");
            assert ShadowRuntimeTest.authority(runtime).creditMeters()==0 : "Pending also blocks geometric crossings without a point endpoint";
            assert !guard.get().getAsBoolean() : "Recheck graph after loading";
            next.get().complete("REJECTED_REVALIDATION");
            assert reply.join().status().equals("REJECTED") && reply.join().reason().equals("UNAVAILABLE_OR_GRAPH_CHANGED");
            runtime.command(ShadowRuntimeTest.driver,"release");
            next.set(new CompletableFuture<>());
            request=new SwitchControlService.Request(UUID.randomUUID(),id,2,"straight","diverging",position);
            reply=runtime.change(request).toCompletableFuture();assert !reply.isDone();
            runtime.close();assert !guard.get().getAsBoolean() && reply.join().reason().equals("SERVICE_STOPPED");
        } finally {
            for(String name:List.of("shadow.json","shadow.json.tmp","shadow.json.v1.bak")) Files.deleteIfExists(dir.resolve(name));
            Files.deleteIfExists(dir);
        }
        // Missing unrelated graph resources still veto MA, but must not globally veto a local point command.
        dir=Files.createTempDirectory("local-switch-broken");file=dir.resolve("shadow.json");
        Files.writeString(file,"{\""+broken+"\":[\"unrelated-resource\"]}");
        try(var runtime=new ShadowRuntime(null,null,file,600,2,source,r->CompletableFuture.completedFuture("COMPLETED"))) {
            var request=new SwitchControlService.Request(UUID.randomUUID(),id,2,"straight","diverging",position);
            assert runtime.change(request).toCompletableFuture().join().status().equals("COMPLETED");
        } finally {
            for(String name:List.of("shadow.json","shadow.json.tmp","shadow.json.v1.bak")) Files.deleteIfExists(dir.resolve(name));Files.deleteIfExists(dir);
        }
        System.out.println("PASS local/MA guards, position CAS, empty/unrelated records, pending idempotence, revalidation and shutdown");
    }
}
