package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.skyworld.sta.api.v3.*;

public final class TimsAuthorityTest {
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var b=ShadowPlannerTest.node("b","end",100,0);
        ShadowRuntimeTest.graph=ShadowPlannerTest.graph(List.of(a,b),
                List.of(ShadowPlannerTest.edge(a,b,"east","west"))).graph;
        var bad=new AtomicBoolean();
        var sampled=new AtomicLong();
        Path dir=Files.createTempDirectory("tims-authority"),file=dir.resolve("occupancy.json");
        try(var runtime=new ShadowRuntime(null,null,file,600,2,()->{
            var base=ShadowRuntimeTest.input();
            var c=base.roster().iterator().next();
            long now=System.currentTimeMillis();sampled.set(now);
            var integrity=bad.get()?TrainIntegrity.unknown()
                    :new TrainIntegrity(TrainIntegrity.State.COMPLETE,"CONFIRMED",now,now,
                            c.expectedMembers(),List.of(),false);
            var updated=new ConsistObservation(c.session(),c.sequence(),c.train(),c.name(),now,
                    c.expectedMembers(),c.members(),false,integrity);
            return new ShadowRuntime.Inputs(base.graph(),base.switchStates(),base.available(),base.driverSession(),
                    base.rosterSession(),base.desks(),List.of(updated),base.reports());
        },request->CompletableFuture.completedFuture("COMPLETED"))) {
            assert runtime.command(ShadowRuntimeTest.driver,"request").equals("REQUESTED");
            var held=runtime.snapshot().sections().stream().filter(s->!s.reservations().isEmpty()).toList();
            assert !held.isEmpty();
            bad.set(true);runtime.tick();
            assert ShadowRuntimeTest.authority(runtime).state().equals("INACTIVE");
            assert runtime.snapshot().sections().stream().anyMatch(s->!s.reservations().isEmpty())
                    : "Old forward protection must survive unknown integrity";
            assert runtime.command(ShadowRuntimeTest.driver,"release").equals("TIMS_HOLD")
                    : "Driver release cannot discard a possibly detached member";
            bad.set(false);runtime.tick();
            assert ShadowRuntimeTest.authority(runtime).state().equals("INACTIVE")
                    : "Fresh evidence cannot silently restore withdrawn MA";
        } finally {
            for(var p:List.of(file,file.resolveSibling("occupancy.json.tmp")))Files.deleteIfExists(p);
            Files.deleteIfExists(dir);
        }
        System.out.println("TIMS unknown retains reservations, denies release and requires a new MA request");
    }
}
