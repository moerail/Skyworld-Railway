package net.skyworld.stcs;

import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.skyworld.sta.api.v3.*;

public final class ShadowRemovalTest {
    static ShadowRuntime.Inputs with(ShadowRuntime.Inputs source, ConsistObservation receipt, UUID session) {
        return new ShadowRuntime.Inputs(source.graph(),source.switchStates(),true,source.driverSession(),session,
                List.of(),receipt==null?List.of():List.of(receipt),List.of());
    }
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node("b","end",100,0);
        ShadowRuntimeTest.graph=ShadowPlannerTest.graph(List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west"))).graph;
        var initial=ShadowRuntimeTest.input();
        var input=new AtomicReference<>(initial);
        Path dir=Files.createTempDirectory("shadow-removal"),file=dir.resolve("shadow.json");
        UUID id=ShadowRuntimeTest.train;
        var live=initial.roster().iterator().next();
        long now=System.currentTimeMillis();
        var members=live.members().stream().map(m -> new ConsistObservation.Member(m.id(),m.world(),m.x(),m.y(),m.z(),
                m.observedAtMillis(),now,ConsistObservation.State.REMOVED)).toList();
        var receipt=new ConsistObservation(live.session(),10,id,live.name(),now,live.expectedMembers(),members,true);
        assert receipt.confirmedDestruction();
        assert !new ConsistObservation(live.session(),11,id,live.name(),now,live.expectedMembers(),List.of(),true).confirmedDestruction();
        try {
            try(var r=new ShadowRuntime(null,null,file,100,2,input::get,q->CompletableFuture.completedFuture("COMPLETED"))) {
                r.tick(); assert ShadowOccupancyStore.load(file).containsKey(id);
                assert r.clearArchived(id,"test").equals("TRAIN_STILL_REPORTED");
                input.set(with(initial,null,live.session())); r.tick();
                assert ShadowOccupancyStore.load(file).containsKey(id) : "Absence retains occupancy";
                input.set(with(initial,receipt,UUID.randomUUID())); r.tick();
                assert ShadowOccupancyStore.load(file).containsKey(id) : "Wrong provider cannot clear";
                input.set(with(initial,receipt,live.session())); r.tick();
                assert !ShadowOccupancyStore.load(file).containsKey(id);
                r.tick(); assert !ShadowOccupancyStore.load(file).containsKey(id) : "Receipt replay is idempotent";
            }
            try(var r=new ShadowRuntime(null,null,file,100,2,input::get,q->CompletableFuture.completedFuture("COMPLETED"))) {
                r.tick(); assert !ShadowOccupancyStore.load(file).containsKey(id) : "Restart must not restore cleared occupancy";
                input.set(initial);r.tick();input.set(with(initial,null,live.session()));r.tick();
                assert r.clearArchived(id,"test-admin").equals("CLEARED_SHADOW_ONLY");
                assert !ShadowOccupancyStore.load(file).containsKey(id);
                assert Files.readString(dir.resolve("shadow-clearance-audit.log")).contains(id.toString());
                try(var paths=Files.list(dir)) { assert paths.anyMatch(p->p.toString().endsWith(".bak")); }
            }
        } finally {
            try(var paths=Files.walk(dir)) { for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p); }
        }
        var g=new ShadowGraph(initial.graph());
        var blocked=ShadowPlanner.plan(g,"a-b",20,5,100,2,Map.of(),g.intervalResources(g.edges.get("a-b"),20,21));
        assert blocked.reason().equals("RESOURCE_CONFLICT") && blocked.remaining()<0 : blocked;
        System.out.println("PASS removal proof, absent/wrong provider retention, replay/restart, manual audit and blocker reason");
    }
}
