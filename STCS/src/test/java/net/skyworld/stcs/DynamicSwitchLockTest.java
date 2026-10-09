package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.YamlConfiguration;
import net.skyworld.sta.api.v1.*;
import net.skyworld.sta.api.v3.*;
import net.skyworld.sta.api.v5.*;

public final class DynamicSwitchLockTest {
    public static void main(String[] args) throws Exception {
        var config = new YamlConfiguration();
        var dynamic = DynamicSwitchLock.load(config);
        var base = new MaSettings(600, 150, 500, 2);
        assert dynamic.forSpeed(base, 0).equals(base);
        assert dynamic.forSpeed(base, 10).equals(base);
        assert dynamic.forSpeed(base, 20).lockDistanceMeters() == 325;
        assert dynamic.forSpeed(base, -20).equals(dynamic.forSpeed(base, 20));
        assert dynamic.forSpeed(base, Double.NaN).equals(base);
        assert dynamic.forSpeed(base, Double.POSITIVE_INFINITY).equals(base);
        assert dynamic.forSpeed(base, Double.MAX_VALUE).lockDistanceMeters() == 10000;
        config.set("ma.dynamic-lock.enabled", false);
        assert DynamicSwitchLock.load(config).forSpeed(base, 100).equals(base);
        config.set("ma.dynamic-lock.enabled", true);
        config.set("ma.dynamic-lock.lead-seconds", 2);
        config.set("ma.dynamic-lock.deceleration-mps2", 2);
        config.set("ma.dynamic-lock.buffer-meters", 10);
        assert DynamicSwitchLock.load(config).forSpeed(base, 30).lockDistanceMeters() == 295;
        for (var entry : Map.<String,Object>of("enabled", "true", "lead-seconds", -1,
                "deceleration-mps2", 0, "buffer-meters", Double.NaN).entrySet()) {
            var invalid = new YamlConfiguration();
            invalid.set("ma.dynamic-lock." + entry.getKey(), entry.getValue());
            try { DynamicSwitchLock.load(invalid); throw new AssertionError("Invalid " + entry.getKey()); }
            catch (IllegalArgumentException ex) { assert ex.getMessage().contains(entry.getKey()); }
        }

        var a = ShadowPlannerTest.node("a", "balise", 0, 0);
        var sw = ShadowPlannerTest.node("b", "switch", 200, 0);
        var end = ShadowPlannerTest.node("end", "end", 600, 0);
        var approach = ShadowPlannerTest.edge(a, sw, "east", "common");
        var exit = ShadowPlannerTest.edge(sw, end, "straight", "west");
        var model = ShadowPlannerTest.graph(List.of(a, sw, end), List.of(approach, exit));
        var states = Map.of("b", "straight");
        var slow = ShadowPlanner.plan(model, approach.id(), 20, 10, dynamic.forSpeed(base, 0), states, Set.of());
        assert slow.reason().equals("SWITCH_LOCK_DISTANCE") && slow.remaining() == 179 : slow;
        var fastSettings = dynamic.forSpeed(base, 30);
        var fast = ShadowPlanner.plan(model, approach.id(), 20, 10, fastSettings, states, Set.of());
        assert fast.remaining() == 500 && fast.path().size() == 2 : fast;
        var unknown = ShadowPlanner.plan(model, approach.id(), 20, 10, fastSettings, Map.of(), Set.of());
        assert unknown.reason().equals("SWITCH_UNKNOWN") && unknown.remaining() == 179 : unknown;
        var blocked = ShadowPlanner.plan(model, approach.id(), 20, 10, fastSettings, states, Set.of(model.resource.get(exit.id())));
        assert blocked.remaining() <= 179 : blocked;
        assert fastSettings.lookAheadMeters() == 600 && fastSettings.maxAuthorityDistanceMeters() == 500;

        // Real telemetry speed must reach both the shadow snapshot and executable FS grant.
        UUID train = UUID.randomUUID(), driver = UUID.randomUUID(), lease = UUID.randomUUID(),
                member = UUID.randomUUID(), session = UUID.randomUUID();
        double[] speed = {0};
        Path dir = Files.createTempDirectory("dynamic-lock"), file = dir.resolve("occupancy.json");
        try (var runtime = new ShadowRuntime(null, null, file, base, () -> {
            long now = System.currentTimeMillis();
            var physical = new TrainTelemetrySnapshot(train, "T", 1, now, "world", 20, 64, 0,
                    20.5, 64.06, .5, 1, 0, 0, speed[0], 1, 1, false, false, TrainMode.MANUAL, "T");
            var position = new TrackPositionSnapshot(model.graph.revision, approach.id(), "a", "b", 20., 200.,
                    "L", 20., now, true, false);
            var message = new StaMessage(new StaMessage.Header(StaMessage.VERSION, StaMessage.Kind.TRACK_REPORT,
                    StaMessage.Source.STCS, session, 2, now, train), new StaMessage.Physical(physical, 1),
                    new StaMessage.Tracking(session, 1, now, now, 15000, 60000, model.graph.revision,
                            StaMessage.Quality.VALID, position, "test"));
            var roster = new ConsistObservation(session, 1, train, "T", now, List.of(member), List.of(
                    new ConsistObservation.Member(member, "world", 20.5, 64.06, .5, now, now, ConsistObservation.State.OBSERVED)), false);
            return new ShadowRuntime.Inputs(model.graph, states, true, session, session,
                    List.of(new DriverDeskService.Desk(train, driver, lease, "ACTIVE")), List.of(roster), List.of(message));
        }, request -> CompletableFuture.completedFuture("COMPLETED"))) {
            assert runtime.command(driver, "request").equals("REQUESTED_ACTIVE");
            assert runtime.operationalSnapshot().grants().getFirst().remainingMeters() == 179;
            speed[0] = 30;
            runtime.tick();
            assert runtime.snapshot().authorities().getFirst().creditMeters() == 500;
            assert runtime.operationalSnapshot().grants().getFirst().remainingMeters() == 500;
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling("occupancy.json.tmp"));
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS dynamic point range, configuration, caps, unknown/conflicting points and telemetry-to-FS grant");
    }
}
