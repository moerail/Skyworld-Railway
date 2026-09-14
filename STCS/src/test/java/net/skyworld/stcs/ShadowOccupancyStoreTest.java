package net.skyworld.stcs;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.skyworld.sta.api.v3.ConsistObservation;

public final class ShadowOccupancyStoreTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("shadow-store"),file=dir.resolve("shadow.json"),backup=dir.resolve("shadow.json.v1.bak");
        UUID train=UUID.randomUUID(),session=UUID.randomUUID();
        try {
            assert ShadowOccupancyStore.load(file).isEmpty();
            String legacy="{\""+train+"\":[\"occupied-resource\"]}";
            Files.writeString(file,legacy);
            var old=ShadowOccupancyStore.load(file).get(train);
            assert old.resources().equals(Set.of("occupied-resource")) && old.positions().isEmpty() && !old.outsideConfirmed();
            assert Files.readString(backup).equals(legacy);
            Files.writeString(file,"{}");ShadowOccupancyStore.load(file);
            assert Files.readString(backup).equals(legacy) : "First migration backup is never overwritten";
            for(String invalid:List.of("{", "null", "[]", "{\"version\":3,\"trains\":{}}", "{\"version\":2.1,\"trains\":{}}",
                    "{\"version\":2}", "{\"version\":2,\"trains\":{\""+train+"\":{}}}")) {
                Files.writeString(file,invalid);
                try { ShadowOccupancyStore.load(file);throw new AssertionError("Accepted invalid store: "+invalid); }
                catch(IOException expected) { assert Files.readString(file).equals(invalid); }
            }
            long now=System.currentTimeMillis();
            var positions=List.of(
                    new ConsistObservation.Member(UUID.randomUUID(),"world",50.5,64.06,-4.5,now,now,ConsistObservation.State.OBSERVED),
                    new ConsistObservation.Member(UUID.randomUUID(),"world",50.5,64.06,4.5,now,now,ConsistObservation.State.OBSERVED));
            var record=new ShadowOccupancyStore.TrainRecord("Last parked train",Set.of(),1,session,positions,true);
            ShadowOccupancyStore.save(file,ShadowOccupancyStore.encode(Map.of(train,record)));
            assert ShadowOccupancyStore.load(file).get(train).equals(record);
            var a=ShadowPlannerTest.node("a","balise",0,0);var b=ShadowPlannerTest.node("b","end",100,0);
            var graph=new RailGraph(2,256,1,List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west")),List.of());
            var model=new ShadowGraph(graph);
            assert positions.stream().allMatch(p->model.near(p.world(),p.x(),p.y(),p.z()).isEmpty());
            var inputs=new ShadowRuntime.Inputs(graph,Map.of(),false,null,null,List.of(),List.of(),List.of());
            try(var runtime=new ShadowRuntime(null,null,file,600,2,()->inputs,r->CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.tick();
                assert runtime.snapshot().sections().stream().anyMatch(s->s.occupants().contains(train)&&s.state().equals("UNCERTAIN"))
                        : "Graph expansion must map the saved body, not just its member centres";
                assert runtime.diagnostics().coverage().getFirst().state().equals("FROZEN_IN_COVERAGE");
                assert runtime.snapshot().authorities().stream().allMatch(v->v.signedRemainingMeters()==null);
            }
            var expanded=ShadowOccupancyStore.load(file).get(train);
            assert !expanded.resources().isEmpty() && !expanded.outsideConfirmed() && expanded.positions().equals(positions);
            assert Files.readString(backup).equals(legacy);
        } finally {
            Files.deleteIfExists(file);Files.deleteIfExists(backup);Files.deleteIfExists(dir.resolve("shadow.json.tmp"));Files.deleteIfExists(dir);
        }
        System.out.println("PASS schema migration, immutable first backup, invalid file retention, full snapshot roundtrip and unloaded graph expansion");
    }
}
