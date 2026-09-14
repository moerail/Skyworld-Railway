package net.skyworld.sta.api.v3;

import java.util.*;

public final class ConsistObservationTest {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        var expected = new ArrayList<>(List.of(id));
        var members = new ArrayList<>(List.of(new ConsistObservation.Member(id, "world", 0, 64, 0,
                100, 200, ConsistObservation.State.UNLOADED)));
        var observation = new ConsistObservation(UUID.randomUUID(), 1, UUID.randomUUID(), "test", 250,
                expected, members, false);
        expected.clear(); members.clear();
        assert observation.expectedMembers().size() == 1;
        assert observation.members().getFirst().observedAtMillis() == 100;
        assert observation.members().getFirst().stateAtMillis() == 200;
        boolean rejected = false;
        try { new ConsistObservation.Member(id, "world", Double.NaN, 0, 0, 1, 1, ConsistObservation.State.OBSERVED); }
        catch (IllegalArgumentException ex) { rejected = true; }
        assert rejected;
    }
}
