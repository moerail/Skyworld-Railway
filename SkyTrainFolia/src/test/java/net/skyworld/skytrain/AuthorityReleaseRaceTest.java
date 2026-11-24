package net.skyworld.skytrain;

import java.util.UUID;

/** Deterministically runs motion ticks in the gap between onboard release and STCS completion. */
public final class AuthorityReleaseRaceTest {
    private static final ActiveAtpState.Settings SETTINGS =
            new ActiveAtpState.Settings(1, 5, 5 / 3.6, 1, 1, 10 / 3.6, 10000, 5000);
    private static final UUID LEASE = UUID.randomUUID(), SESSION = UUID.randomUUID();

    public static void main(String[] args) {
        var fs = grant("FS", 280);
        var sr = grant("SR", 60);

        // The former clear-only sequence reproduces the report: FS returns and SR conflicts.
        var old = new ActiveAtpState();
        tick(old, fs, OperatingMode.SB);
        old.clear();
        var restored = tick(old, fs, OperatingMode.SB);
        assert restored.mode() == OperatingMode.FS && old.remainingMeters() == 280.;
        assert tick(old, sr, restored.mode()).reason().equals("MODE_CONFLICT");

        var state = new ActiveAtpState();
        assert tick(state, fs, OperatingMode.SB).tractionAllowed();
        UUID token = state.beginRelease();
        assert state.remainingMeters() == null;
        // Old FS can still be renewed by STCS while the async release is queued.
        for (var permission : new OperationalPermission[] {fs, grant("FS", 280),
                OperationalPermission.unavailable("STALE", 1, SESSION), sr}) {
            var held = tick(state, permission, OperatingMode.SB);
            assert held.mode() == OperatingMode.SB && held.emergency() && !held.tractionAllowed();
            assert held.reason().equals("RELEASE_PENDING") && state.remainingMeters() == null;
        }
        state.clear();
        assert state.releasePending() : "Other state clearing cannot bypass an unconfirmed release";
        for (var trip : new OperatingMode[] {OperatingMode.TR, OperatingMode.PT})
            assert tick(state, fs, trip).mode() == trip : "Release pending must not acknowledge a late trip";
        assert !state.completeRelease(UUID.randomUUID(), true) : "Unrelated receipt cannot lift the fence";
        assert !state.completeRelease(token, false) && state.releasePending() : "Failed release keeps brakes held";

        UUID retry = state.beginRelease();
        assert !state.completeRelease(token, true) : "Late completion must not finish the retry";
        assert state.releasePending();
        assert state.completeRelease(retry, true) && !state.releasePending();
        var waiting = tick(state, OperationalPermission.unavailable("NO_GRANT", 1, SESSION), OperatingMode.SB);
        assert waiting.mode() == OperatingMode.SB && !waiting.tractionAllowed() && state.remainingMeters() == null;
        var approved = tick(state, sr, OperatingMode.SB);
        assert approved.mode() == OperatingMode.SR && approved.tractionAllowed();
        assert state.remainingMeters() == 60. : "SR must use its approved distance, never old FS credit";
        assert !state.completeRelease(retry, true) : "Duplicate completion cannot erase the new SR";
        assert state.remainingMeters() == 60.;
        assert tick(state, sr, OperatingMode.SR).tractionAllowed() : "No second release or hotbar toggle needed";

        // The reverse switch uses the same protocol.
        token = state.beginRelease();
        assert !tick(state, sr, OperatingMode.SB).tractionAllowed();
        assert state.completeRelease(token, true);
        assert tick(state, fs, OperatingMode.SB).mode() == OperatingMode.FS;
        assert state.remainingMeters() == 280.;
        System.out.println("PASS reproduced old FS reinstall; fenced FS -> release -> SR, distances, failure/retry and late receipts");
    }

    private static OperationalPermission grant(String mode, double remaining) {
        return new OperationalPermission(UUID.randomUUID(), LEASE, SESSION, mode, remaining, 40 / 3.6, 1, "VALID");
    }

    private static ActiveAtpState.Decision tick(ActiveAtpState state, OperationalPermission permission, OperatingMode mode) {
        return state.evaluate(permission, LEASE, mode, true, false, "FORWARD", 0, 7, 1000, SETTINGS);
    }
}
