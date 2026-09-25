package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MaRestartTest {
    public static void main(String[] args) throws Exception {
        var a=ShadowPlannerTest.node("a","balise",0,0);
        var b=ShadowPlannerTest.node("b","end",100,0);
        ShadowRuntimeTest.graph=ShadowPlannerTest.graph(List.of(a,b),List.of(ShadowPlannerTest.edge(a,b,"east","west"))).graph;
        Path dir=Files.createTempDirectory("ma-restart"),file=dir.resolve("shadow.json"),tmp=dir.resolve("shadow.json.tmp");
        try {
            try(var runtime=new ShadowRuntime(null,null,file,600,2,ShadowRuntimeTest::input,
                    r -> CompletableFuture.completedFuture("COMPLETED"))) {
                runtime.tick();
                assert runtime.restartMa("admin").equals("ALREADY_RUNNING");
                runtime.command(ShadowRuntimeTest.driver,"request");
                var cache=ShadowRuntime.class.getDeclaredField("persisted");
                cache.setAccessible(true);cache.set(runtime,"");
                var lastSave=ShadowRuntime.class.getDeclaredField("lastPersistedAt");
                lastSave.setAccessible(true);lastSave.setLong(runtime,0);
                Files.createDirectory(tmp);
                try { runtime.tick(); throw new AssertionError("expected write failure"); }
                catch(IllegalStateException expected) { assert expected.getCause() instanceof java.io.IOException; }
                assert runtime.snapshot().status().equals("FAILED");
                ShadowRuntimeTest.available=false;
                assert runtime.restartMa("admin").equals("SOURCE_UNAVAILABLE");
                ShadowRuntimeTest.available=true;
                assert runtime.restartMa("admin").equals("FAILED");
                assert runtime.command(ShadowRuntimeTest.driver,"request").equals("UNAVAILABLE");
                Files.delete(tmp);
                var grantsField=ShadowRuntime.class.getDeclaredField("liveGrants");
                grantsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                var grants=(Map<UUID,net.skyworld.sta.api.v6.OperationalAuthorityService.Grant>)grantsField.get(runtime);
                grants.put(ShadowRuntimeTest.train,new net.skyworld.sta.api.v6.OperationalAuthorityService.Grant(
                        UUID.randomUUID(),ShadowRuntimeTest.train,ShadowRuntimeTest.lease,ShadowRuntimeTest.session,
                        1,"FS",true,ShadowRuntimeTest.graph.revision,"a-b",100,80,130,
                        List.of(new net.skyworld.sta.api.v6.OperationalAuthorityService.Segment("a-b",20,100)),1003,1015));
                ShadowRuntimeTest.stale=true;
                assert runtime.restartMa("admin").equals("STOP_TRAINS_FIRST");
                assert grants.size()==1 : "rejected restart must retain executable authority";
                ShadowRuntimeTest.stale=false;
                assert runtime.restartMa("admin").equals("RESTARTED");
                assert grants.isEmpty();
                assert runtime.snapshot().sections().stream().anyMatch(s -> s.occupants().contains(ShadowRuntimeTest.train));
                assert runtime.snapshot().sections().stream().allMatch(s -> s.reservations().isEmpty());
                assert ShadowRuntimeTest.authority(runtime).state().equals("INACTIVE");
                assert Files.readString(dir.resolve("ma-restart-audit.log")).contains("actor=admin");
                assert runtime.command(ShadowRuntimeTest.driver,"request").equals("REQUESTED");
                assert ShadowRuntimeTest.authority(runtime).state().equals("ALLOCATED_SHADOW");
            }
            try(var runtime=new ShadowRuntime(null,null,dir.resolve("other.json"),600,2,
                    () -> { throw new IllegalStateException("not a disk fault"); },r -> CompletableFuture.completedFuture("COMPLETED"))) {
                try { runtime.tick(); } catch(IllegalStateException expected) { }
                assert runtime.restartMa("admin").equals("SERVER_RESTART_REQUIRED");
                runtime.close();
                assert runtime.restartMa("admin").equals("CLOSED");
            }
        } finally {
            ShadowRuntimeTest.available=true;
            ShadowRuntimeTest.stale=false;
            try(var paths=Files.walk(dir)) {
                for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        System.out.println("PASS MA restart: retained occupancy, reset requests, disk failure, source failure, non-I/O rejection and closed service");
    }
}
