package net.skyworld.skypcc;

import java.util.*;
import net.skyworld.sta.api.v3.ConsistObservation;

public final class PccTrainRosterTest {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        var roster = new ConsistObservation(UUID.randomUUID(), 1, id, "unloaded train", 1000,
                List.of(UUID.randomUUID()), List.of(), false);
        var rows = PccTrainRoster.merge(List.of(), List.of(roster));
        assert rows.size() == 1;
        var row = rows.getFirst();
        assert row.get("quality").equals("AWAITING_POSITION");
        assert row.get("stale").equals(true);
        assert !row.containsKey("edgeId") && !row.containsKey("speedMetersPerSecond") && !row.containsKey("cab");
        Map<String, Object> live = Map.of("trainId", id.toString(), "quality", "VALID", "edgeId", "edge-1");
        assert PccTrainRoster.merge(List.of(live), List.of(roster)).equals(List.of(live));
        assert PccTrainRoster.merge(List.of(live), List.of()).equals(List.of(live));
        var removed = new ConsistObservation(roster.session(), 2, id, roster.name(), 1001,
                roster.expectedMembers(), List.of(), true);
        assert PccTrainRoster.merge(List.of(live), List.of(removed)).isEmpty();
        assert PccTrainRoster.merge(List.of(), List.of()).isEmpty();
        System.out.println("PASS PCC startup roster, unknown position, live replacement and explicit removal");
    }
}
