package net.skyworld.stcs;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

public final class ShadowSwitchControlTest {
    public static void main(String[] args) throws Exception {
        UUID point=UUID.randomUUID(),session=UUID.randomUUID(),train=UUID.randomUUID(),member=UUID.randomUUID();
        var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node(point.toString(),"switch",100,0);
        var c=ShadowPlannerTest.node("c","end",200,0);
        var graph=ShadowPlannerTest.graph(List.of(a,b,c),List.of(ShadowPlannerTest.edge(a,b,"east","common"),ShadowPlannerTest.edge(b,c,"straight","west"))).graph;
        var x=new AtomicInteger(20);var calls=new AtomicInteger();
        var state=new AtomicReference<>(ConsistObservation.State.OBSERVED);
        var position=new SwitchControlService.Position("world",100,64,0);
        Path dir=Files.createTempDirectory("shadow-point-test"),file=dir.resolve("shadow.json");
        try(var runtime=new ShadowRuntime(null,null,file,600,2,()->{
            long now=System.currentTimeMillis();
            var observation=new ConsistObservation(session,1,train,"T",now,List.of(member),List.of(new ConsistObservation.Member(
                    member,"world",x.get()+.5,64.06,.5,now,now,state.get())),false);
            return new ShadowRuntime.Inputs(graph,Map.of(point.toString(),"straight"),true,session,session,List.of(),List.of(observation),List.of());
        }, request->{calls.incrementAndGet();return CompletableFuture.completedFuture("COMPLETED");})) {
            var r=new SwitchControlService.Request(UUID.randomUUID(),point,1,"straight","diverging",position);
            assert runtime.change(r).toCompletableFuture().join().status().equals("COMPLETED") : "far approach occupancy must not lock the point";
            assert runtime.change(r).toCompletableFuture().join().status().equals("COMPLETED");assert calls.get()==1;
            var conflict=new SwitchControlService.Request(r.requestId(),point,1,"diverging","straight",position);
            assert runtime.change(conflict).toCompletableFuture().join().reason().equals("ID_CONFLICT");
            x.set(99);
            var near=new SwitchControlService.Request(UUID.randomUUID(),point,1,"straight","diverging",position);
            assert runtime.change(near).toCompletableFuture().join().reason().equals("OCCUPIED_OR_UNCERTAIN");assert calls.get()==1;
            x.set(20);state.set(ConsistObservation.State.UNLOADED);
            var uncertain=new SwitchControlService.Request(UUID.randomUUID(),point,1,"straight","diverging",position);
            assert runtime.change(uncertain).toCompletableFuture().join().reason().equals("OCCUPIED_OR_UNCERTAIN");
            var wrongRevision=new SwitchControlService.Request(UUID.randomUUID(),point,2,"straight","diverging",position);
            assert runtime.change(wrongRevision).toCompletableFuture().join().reason().equals("UNAVAILABLE_OR_GRAPH_CHANGED");
            state.set(ConsistObservation.State.OBSERVED);runtime.tick();
            x.set(10000);runtime.tick();
            assert runtime.diagnostics().blockers().isEmpty() : "Outside trains do not globally veto shadow MA";
            var uncovered=new SwitchControlService.Request(UUID.randomUUID(),point,1,"straight","diverging",position);
            assert runtime.change(uncovered).toCompletableFuture().join().status().equals("COMPLETED")
                    : "Unrelated outside records must not veto a local point operation";
            assert calls.get()==2;
            var m1=new ConsistObservation.Member(member,"world",96,64,0.5,1,1,ConsistObservation.State.OBSERVED);
            var m2=new ConsistObservation.Member(UUID.randomUUID(),"world",105,64,.5,1,1,ConsistObservation.State.OBSERVED);
            assert ShadowRuntime.pointNear(b.rail(),m1,m2) : "body spans point despite distant centres";
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        System.out.println("PASS point request identity/revision, far approach, frozen body and independent real-control coverage guards");
    }
}
