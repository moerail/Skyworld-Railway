package net.skyworld.sta.api.v3;

import java.util.*;
import java.util.concurrent.*;

public final class RailwayEventLogTest {
    public static void main(String[] args) throws Exception {
        var log = new RailwayEventLog(32);
        UUID train = UUID.randomUUID(), driver = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) futures.add(executor.submit(() -> {
                for (int j = 0; j < 50; j++) log.publish("STF", RailwayEvent.Type.DRIVER_UNAVAILABLE,
                        train, "train", driver, "driver", "DISCONNECTED");
            }));
            for (var future : futures) future.get();
        }
        var history = log.history();
        assert history.latestSequence() == 200 && history.evictedCount() == 168 && history.events().size() == 32;
        for (int i = 0; i < 32; i++) assert history.events().get(i).sequence() == 169 + i;
        assert history.events().stream().allMatch(e -> e.session().equals(history.session()));
        boolean immutable = false;
        try { history.events().clear(); } catch (UnsupportedOperationException ex) { immutable = true; }
        assert immutable;
        log.publish("STF", RailwayEvent.Type.DRIVER_RELEASED, train, "train", driver, "driver", "EXPLICIT_RELEASE");
        assert history.events().getLast().sequence() == 200 : "Old snapshot mutated";
        assert !new RailwayEventLog(32).history().session().equals(history.session());
        boolean invalid = false;
        try { log.publish("STF", RailwayEvent.Type.DRIVER_ACQUIRED, train, "x".repeat(257), driver, "driver", "EXPLICIT_DRIVE"); }
        catch (IllegalArgumentException ex) { invalid = true; }
        assert invalid && log.history().latestSequence() == 201;
        var details = new HashMap<String,String>();
        details.put("switchId", "sw1"); details.put("entry", "DIVERGING");
        var change = log.publishDetailed("STF", RailwayEvent.Type.SWITCH_CHANGED, null, "", null, "",
                "CONTROL_OR_RESTORE", details);
        details.put("entry", "COMMON");
        assert change.trainId() == null && change.details().get("entry").equals("DIVERGING");
        log.publishDetailed("STF", RailwayEvent.Type.SWITCH_RUN_THROUGH_SUSPECTED, train, "train", null, "",
                "OBSERVED_TRAILING_ENTRY", details);
        log.publish("STF", RailwayEvent.Type.EMERGENCY_BRAKE_APPLIED, train, "train", null, "", "EB_INPUT");
    }
}
