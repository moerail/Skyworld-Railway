package net.skyworld.skytrain;

import java.util.UUID;

public final class ActiveAtpStateTest {
    public static void main(String[] args) {
        var settings = new ActiveAtpState.Settings(1, 5, 5 / 3.6, 1, 1, 10 / 3.6, 10000, 5000);
        UUID lease = UUID.randomUUID(), grantId = UUID.randomUUID(), session = UUID.randomUUID();
        var grant = new OperationalPermission(grantId, lease, session, "FS", 100, 20, 2, "VALID");
        var state = new ActiveAtpState();
        var decision = state.evaluate(grant, lease, OperatingMode.SB, true, false, "FORWARD",
                0, 7, 1000, settings);
        assert decision.mode() == OperatingMode.FS && decision.tractionAllowed();
        state.advance(20);
        decision = state.evaluate(OperationalPermission.unavailable("STALE", 2, session), lease,
                OperatingMode.FS, false, false, "FORWARD", 10, 0, 1200, settings);
        assert decision.mode() == OperatingMode.FS && decision.reason().equals("LAST_CONFIRMED_EOA");
        decision = state.evaluate(OperationalPermission.unavailable("STALE", 3, session), lease,
                OperatingMode.FS, false, false, "FORWARD", 10, 0, 1300, settings);
        assert decision.emergency() && !decision.tractionAllowed();
        state = new ActiveAtpState();
        state.evaluate(grant, lease, OperatingMode.SB, true, false, "FORWARD", 0, 7, 1000, settings);
        decision = state.evaluate(OperationalPermission.unavailable("STALE", 2, UUID.randomUUID()), lease,
                OperatingMode.FS, false, false, "FORWARD", 10, 0, 1300, settings);
        assert decision.emergency() && decision.reason().equals("STALE");
        state = new ActiveAtpState();
        state.evaluate(new OperationalPermission(grantId, lease, session, "FS", 2, 20, 2, "VALID"), lease,
                OperatingMode.SB, true, false, "FORWARD", 0, 7, 1000, settings);
        state.advance(2.5);
        decision = state.evaluate(OperationalPermission.unavailable("STALE", 2, session), lease,
                OperatingMode.FS, false, false, "FORWARD", 1, 0, 1200, settings);
        assert decision.mode() == OperatingMode.TR && decision.emergency();
        assert ActiveAtpState.curveLimit(settings, 1, 20) == 0;
        assert ActiveAtpState.curveLimit(settings, 4, 20) <= 5 / 3.6;
        assert ActiveAtpState.curveLimit(settings, 100, 20) > 5 / 3.6;
        state = new ActiveAtpState();
        var distant = new OperationalPermission(UUID.randomUUID(), lease, session, "FS", 1000, 20, 2, "VALID");
        state.evaluate(distant, lease, OperatingMode.SB, true, false, "FORWARD", 0, 7, 1000, settings);
        decision = state.evaluate(distant, lease, OperatingMode.FS, false, false, "FORWARD", 24,
                0, 2000, settings);
        assert decision.reason().equals("OVERSPEED_WARNING");
        decision = state.evaluate(distant, lease, OperatingMode.FS, false, false, "FORWARD", 24,
                0, 12001, settings);
        assert decision.minimumBrakeNotch() == 7 && !decision.emergency()
                && decision.mode() == OperatingMode.FS;
        decision = state.evaluate(distant, lease, OperatingMode.FS, false, false, "FORWARD", 24,
                0, 17001, settings);
        assert decision.emergency() && decision.mode() == OperatingMode.FS;
        decision = state.evaluate(distant, lease, OperatingMode.FS, true, false, "FORWARD", 0,
                7, 18000, settings);
        assert decision.emergency() && decision.reason().equals("OVERSPEED_UNRESPONSIVE");
        state.clear();
        decision = state.evaluate(distant, lease, OperatingMode.SB, true, false, "FORWARD", 0,
                7, 19000, settings);
        assert !decision.emergency();
        for (String restricted : new String[] {"SH", "SR"}) {
            state = new ActiveAtpState();
            var local = new OperationalPermission(UUID.randomUUID(), lease, session, restricted,
                    1000, 40 / 3.6, 2, "VALID");
            state.evaluate(local, lease, OperatingMode.SB, true, false, "FORWARD", 0, 7, 20000, settings);
            decision = state.evaluate(local, lease, OperatingMode.valueOf(restricted), false, false,
                    "FORWARD", 41 / 3.6, 0, 20050, settings);
            assert decision.minimumBrakeNotch() == 7 && !decision.tractionAllowed()
                    && decision.reason().equals("MODE_SPEED_LIMIT") : restricted + ": " + decision;
        }
        System.out.println("Active ATP grant, stale EoA, graph gate, trip, curve and overspeed gates passed");
    }
}
