package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.Gson;
import net.skyworld.sta.api.v3.ConsistObservation;

public final class ShadowDiagnosticsTest {
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node("b","end",100,0);
        ShadowRuntimeTest.graph=ShadowPlannerTest.graph(List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west"))).graph;
        UUID absent=UUID.randomUUID(),outdated=UUID.randomUUID(),unloaded=UUID.randomUUID(),outside=UUID.randomUUID();
        Path dir=Files.createTempDirectory("shadow-diagnostics"),file=dir.resolve("shadow.json");
        String saved=new Gson().toJson(Map.of(absent,List.of(),outdated,List.of("old-graph-resource")));
        Files.writeString(file,saved);
        java.util.function.Supplier<ShadowRuntime.Inputs> source=()->{
            var original=ShadowRuntimeTest.input();long now=System.currentTimeMillis();
            var roster=new ArrayList<>(original.roster());
            roster.add(new ConsistObservation(ShadowRuntimeTest.session,1,unloaded,"unloaded",now,List.of(UUID.randomUUID()),List.of(),false));
            UUID member=UUID.randomUUID();
            roster.add(new ConsistObservation(ShadowRuntimeTest.session,1,outside,"outside",now,List.of(member),
                    List.of(new ConsistObservation.Member(member,"world",10000,64,0,now,now,ConsistObservation.State.OBSERVED)),false));
            return new ShadowRuntime.Inputs(original.graph(),original.switchStates(),original.available(),original.driverSession(),
                    original.rosterSession(),original.desks(),roster,original.reports());
        };
        try {
            try(var runtime=new ShadowRuntime(null,null,file,600,2,source,r->CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.command(ShadowRuntimeTest.driver,"request");
                assert ShadowRuntimeTest.authority(runtime).reason().equals("FLEET_UNCERTAIN");
                var data=runtime.diagnostics();
                assert data.sourceAvailable() && data.blockers().size()==1 : data;
                Map<UUID,String> issues=new HashMap<>();data.blockers().forEach(x->issues.put(x.train(),x.issue()));
                assert issues.get(outdated).equals("RESOURCES_NOT_IN_GRAPH");
                Map<UUID,String> coverage=new HashMap<>();data.coverage().forEach(x->coverage.put(x.train(),x.state()));
                assert coverage.get(absent).equals("ARCHIVED_UNLOCATED");
                assert coverage.get(unloaded).equals("AWAITING_COVERAGE");
                assert coverage.get(outside).equals("OUTSIDE_COVERAGE");
                assert coverage.get(outdated).equals("GRAPH_MISMATCH");
                assert coverage.get(ShadowRuntimeTest.train).equals("LIVE_IN_COVERAGE");
                assert data.drivenTrains().get(ShadowRuntimeTest.driver).equals(ShadowRuntimeTest.train);
                assert data.trainNames().get(unloaded).equals("unloaded");
                long sequence=runtime.snapshot().sequence();String disk=Files.readString(file);
                for(int i=0;i<10;i++) assert runtime.diagnostics()==data;
                assert runtime.snapshot().sequence()==sequence && Files.readString(file).equals(disk) : "Diagnostics must not mutate state";
                try { data.blockers().clear();throw new AssertionError("Mutable diagnostics"); } catch(UnsupportedOperationException expected) {}
                runtime.command(ShadowRuntimeTest.driver,"release");
                assert runtime.diagnostics().blockers().size()==1 : "Release must not clear graph mismatch evidence";
                assert Files.readString(file).contains(absent.toString()) && Files.readString(file).contains("old-graph-resource");
                ShadowRuntimeTest.available=false;runtime.tick();
                assert !runtime.diagnostics().sourceAvailable();
            }
        } finally {
            ShadowRuntimeTest.available=true;
            Files.deleteIfExists(file);Files.deleteIfExists(file.resolveSibling("shadow.json.tmp"));
            assert Files.readString(file.resolveSibling("shadow.json.v1.bak")).equals(saved);
            Files.deleteIfExists(file.resolveSibling("shadow.json.v1.bak"));Files.deleteIfExists(dir);
        }
        System.out.println("PASS coverage categories, graph mismatch blocking, immutable diagnostics, migration backup and source failure");
    }
}
