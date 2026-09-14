package net.skyworld.sta.api.v1;

import java.util.UUID;
import net.skyworld.sta.api.v2.*;

public final class CabTelemetryTest {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID(), session = UUID.randomUUID();
        var cab = new TrainTelemetrySnapshot.CabState("RECOVERING", "NEUTRAL", 4, 0, false, true);
        var state = new TrainTelemetrySnapshot(id, "test", 1, 1000, "world", 0, 64, 0,
                0.5, 64, 0.5, 1, 0, 0, 0, 10, 3, false, false, TrainMode.MANUAL, "Driver", cab, "G001");
        var tracking = new StaMessage.Tracking(session, 1, 1000, 1000, 2000, 10000, 1,
                StaMessage.Quality.UNLOCATED, null, "test");
        var message = new StaMessage(new StaMessage.Header(2, StaMessage.Kind.TRACK_REPORT,
                StaMessage.Source.STCS, session, 2, 1000, id), new StaMessage.Physical(state, 1), tracking);
        var decoded = StaJson.decode(StaJson.encode(message));
        assert decoded.physical().state().cab().equals(cab);
        assert decoded.physical().state().trainNumber().equals("G001");
        assert PccProjection.train(decoded, 1000).get("trainNumber").equals("G001");
        assert PccProjection.train(decoded, 1000).get("cab").equals(cab);
        assert PccProjection.train(decoded, 1000).get("direction").equals("forward");
        var tree = StaJson.tree(message);
        var physical = tree.getAsJsonArray("packets").get(0).getAsJsonObject().getAsJsonObject("state");
        physical.remove("cab");
        physical.remove("trainNumber");
        assert StaJson.decode(tree.toString()).physical().state().trainNumber().isEmpty();
        assert StaJson.decode(tree.toString()).physical().state().cab() == null;
        assert PccProjection.train(StaJson.decode(tree.toString()), 1000).get("cab") == null;
        var legacy = new TrainTelemetrySnapshot(id, "old", 1, 1000, "world", 0, 64, 0,
                0.5, 64, 0.5, 1, 0, 0, 0, 10, 3, false, false, TrainMode.MANUAL, "Driver");
        assert legacy.cab() == null;
        assert legacy.trainNumber().isEmpty();
        for (String field : new String[] { "powerNotch", "brakeNotch", "brakeHold" }) {
            var invalid = StaJson.tree(message);
            invalid.getAsJsonArray("packets").get(0).getAsJsonObject().getAsJsonObject("state")
                    .getAsJsonObject("cab").addProperty(field, "invalid");
            boolean rejected = false;
            try { StaJson.decode(invalid.toString()); } catch (IllegalArgumentException expected) { rejected = true; }
            assert rejected : field;
        }
        boolean rejected = false;
        try { new TrainTelemetrySnapshot.CabState("SHADOW", "FORWARD", 5, 0, false, false); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected;
        System.out.println("CabTelemetryTest passed: optional legacy field, forwarding, JSON validation, input vs brake hold");
    }
}
