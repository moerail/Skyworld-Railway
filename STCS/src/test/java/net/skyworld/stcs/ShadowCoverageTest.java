package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v3.ConsistObservation;
import net.skyworld.sta.api.v4.DriverDeskService;

public final class ShadowCoverageTest {
    static final UUID other=UUID.randomUUID(),member=UUID.randomUUID(),missing=UUID.randomUUID();
    static final UUID otherDriver=UUID.randomUUID(),otherLease=UUID.randomUUID();
    static final UUID unseen=UUID.randomUUID(),unseenDriver=UUID.randomUUID(),unseenLease=UUID.randomUUID();
    static boolean present=true,partial=false,slowInput=false;
    static double x=10000.5,z=.5;
    static long sampleAge=0,memberAge=0;
    static UUID sourceSession=ShadowRuntimeTest.session;
    static ConsistObservation.State state=ConsistObservation.State.OBSERVED;

    static ShadowRuntime.Inputs input() {
        if(slowInput) try { Thread.sleep(10); } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();throw new IllegalStateException(ex);
        }
        var base=ShadowRuntimeTest.input();long now=System.currentTimeMillis();
        var roster=new ArrayList<>(base.roster());var desks=new ArrayList<>(base.desks());
        if(present) {
            roster.add(new ConsistObservation(sourceSession,1,other,"Siding",now-sampleAge,
                    partial?List.of(member,missing):List.of(member),List.of(new ConsistObservation.Member(
                    member,"world",x,64.06,z,now-memberAge,now,state)),false));
            desks.add(new DriverDeskService.Desk(other,otherDriver,otherLease,"SHADOW"));
        }
        roster.add(new ConsistObservation(ShadowRuntimeTest.session,1,unseen,"Not loaded",now,List.of(missing),List.of(),false));
        desks.add(new DriverDeskService.Desk(unseen,unseenDriver,unseenLease,"SHADOW"));
        return new ShadowRuntime.Inputs(base.graph(),Map.of(),base.available(),base.driverSession(),base.rosterSession(),desks,roster,base.reports());
    }
    static ShadowRuntime runtime(Path file) throws Exception {
        return new ShadowRuntime(null,null,file,600,2,ShadowCoverageTest::input,r->CompletableFuture.completedFuture("COMPLETED"));
    }
    static ShadowRuntime.Coverage coverage(ShadowRuntime r,UUID id) {
        return r.diagnostics().coverage().stream().filter(c->c.train().equals(id)).findFirst().orElseThrow();
    }
    static void clear(ShadowRuntime r) {
        assert r.diagnostics().blockers().isEmpty() : r.diagnostics();
        assert ShadowRuntimeTest.authority(r).creditMeters()==80 : ShadowRuntimeTest.authority(r);
    }
    static void blockedLocally(ShadowRuntime r) {
        assert r.diagnostics().blockers().isEmpty();
        assert ShadowRuntimeTest.authority(r).creditMeters()>26 && ShadowRuntimeTest.authority(r).creditMeters()<30 : ShadowRuntimeTest.authority(r);
        assert Set.of("RESOURCE_CONFLICT","EOA_OVERRUN").contains(ShadowRuntimeTest.authority(r).reason()) : ShadowRuntimeTest.authority(r);
        assert coverage(r,other).resources()>0;
    }
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node("b","end",100,0);
        var d=ShadowPlannerTest.node("d","balise",0,20);var e=ShadowPlannerTest.node("e","end",100,20);
        var graph=ShadowPlannerTest.graph(List.of(a,b,d,e),List.of(ShadowPlannerTest.edge(a,b,"east","west"),
                ShadowPlannerTest.edge(d,e,"east","west"))).graph;
        ShadowRuntimeTest.graph=graph;
        UUID archived=UUID.randomUUID();
        Path dir=Files.createTempDirectory("shadow-coverage"),file=dir.resolve("shadow.json"),m1=dir.resolve("occupancy-ledger.json");
        String legacy="{\""+archived+"\":[]}";
        Files.writeString(file,legacy);Files.writeString(m1,"M1 historical evidence must be untouched");
        try {
            try(var r=runtime(file)) {
                r.command(ShadowRuntimeTest.driver,"request");clear(r);
                assert coverage(r,archived).state().equals("ARCHIVED_UNLOCATED");
                assert coverage(r,other).state().equals("OUTSIDE_COVERAGE");
                assert coverage(r,unseen).state().equals("AWAITING_COVERAGE");
                r.command(otherDriver,"request");
                assert r.snapshot().authorities().stream().anyMatch(v->v.trainId().equals(other)&&v.reason().equals("OUTSIDE_COVERAGE")&&v.signedRemainingMeters()==null);
                r.command(unseenDriver,"request");
                assert r.snapshot().authorities().stream().anyMatch(v->v.trainId().equals(unseen)&&v.reason().equals("AWAITING_COVERAGE")&&v.signedRemainingMeters()==null);
                x=50.5;slowInput=true;r.tick();slowInput=false;blockedLocally(r);
                // An unload, partial consist, stale or wrong-session observation is not a proven exit.
                x=10000.5;state=ConsistObservation.State.UNLOADED;r.tick();blockedLocally(r);
                assert coverage(r,other).state().equals("FROZEN_IN_COVERAGE");
                state=ConsistObservation.State.OBSERVED;partial=true;r.tick();blockedLocally(r);partial=false;
                memberAge=10000;r.tick();blockedLocally(r);memberAge=-10000;r.tick();blockedLocally(r);memberAge=0;
                sampleAge=10000;r.tick();blockedLocally(r);sampleAge=0;
                sourceSession=UUID.randomUUID();r.tick();blockedLocally(r);sourceSession=ShadowRuntimeTest.session;
                // A complete, fresh consist outside the unchanged graph can leave the shadow model.
                r.tick();clear(r);assert coverage(r,other).state().equals("OUTSIDE_COVERAGE");
                // A covered train on a physically separate parallel track is only a local obstacle.
                x=50.5;z=20.5;r.tick();clear(r);
                present=false;r.tick();clear(r);
                assert coverage(r,other).state().equals("FROZEN_IN_COVERAGE");
                assert r.snapshot().sections().stream().anyMatch(s->s.edgeId().equals("d-e")&&s.state().equals("UNCERTAIN")&&s.occupants().contains(other));
            }
            var saved=ShadowOccupancyStore.load(file).get(other);
            assert saved.name().equals("Siding") && saved.positions().getFirst().x()==50.5 && saved.positions().getFirst().z()==20.5;
            assert !saved.resources().isEmpty();
            try(var r=runtime(file)) {
                r.command(ShadowRuntimeTest.driver,"request");clear(r);
                assert coverage(r,other).state().equals("FROZEN_IN_COVERAGE");
                assert coverage(r,other).positions().getFirst().z()==20.5;
                present=true;z=.5;r.tick();blockedLocally(r);
                present=false;r.tick();blockedLocally(r);
            }
            try(var r=runtime(file)) {
                r.command(ShadowRuntimeTest.driver,"request");blockedLocally(r);
                present=true;x=10000.5;r.tick();clear(r);
            }
            present=false;
            try(var r=runtime(file)) {
                r.command(ShadowRuntimeTest.driver,"request");clear(r);
                assert coverage(r,other).state().equals("OUTSIDE_COVERAGE");
                // Removal of a resource from RailGraph cannot be mistaken for a physical exit.
                present=true;x=50.5;z=20.5;r.tick();clear(r);
                ShadowRuntimeTest.graph=new RailGraph(2,256,1,List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west")),List.of());
                x=10000.5;r.command(ShadowRuntimeTest.driver,"request");
                assert ShadowRuntimeTest.authority(r).reason().equals("FLEET_UNCERTAIN");
                assert coverage(r,other).state().equals("GRAPH_MISMATCH");
                assert coverage(r,other).resources()>0;
            }
            assert Files.readString(file.resolveSibling("shadow.json.v1.bak")).equals(legacy);
            assert Files.readString(m1).equals("M1 historical evidence must be untouched");
            assert ShadowOccupancyStore.load(file).containsKey(archived) : "Do not delete empty historical records";
        } finally {
            for(String name:List.of("shadow.json","shadow.json.tmp","shadow.json.v1.bak","occupancy-ledger.json")) Files.deleteIfExists(dir.resolve(name));
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS empty/outside admission, local frozen occupancy across restart, proven exit, untrusted observations and graph mismatch");
    }
}
