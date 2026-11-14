package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HotbarAuthorityRecoveryTest {
    public static void main(String[] args) {
        var train = new Train(UUID.randomUUID(), "SR recovery", .4, 1, 1.1);
        UUID member = UUID.randomUUID(), lease = UUID.randomUUID(), session = UUID.randomUUID();
        train.addMember(member);
        var controls = new TrainDrivingControls(new TrainSettings(YamlConfiguration::new, () -> null, () -> 1.),
                new Object(), name -> train, () -> {}, ignored -> {});
        var enabled = new AtomicBoolean(true);
        var owner = new AtomicBoolean(true);
        Runnable ownership = () -> { if (!owner.get()) throw new IllegalArgumentException("error.drive-declare"); };
        Runnable power = () -> {
            if (!enabled.get()) return;
            HotbarInputGate.requireDriver(ownership, () -> enabled.set(false));
            controls.setPowerNotch(train, 4);
        };
        train.protectionMode = ProtectionMode.ACTIVE;
        train.operatingMode = OperatingMode.SB;
        controls.emergencyBrake(train);
        refreshIntegrity(train, member);
        rejects(power);
        assert enabled.get() : "SR pending must keep hotbar enabled";
        assert train.powerNotch == 0 && train.emergencyBrake : "Rejected input must keep brakes";
        grant(train, lease, session, "SR");
        refreshIntegrity(train, member);
        power.run();
        assert train.operatingMode == OperatingMode.SR && train.powerNotch == 4 && !train.emergencyBrake;

        // Releasing MA retains the same driver and input device. No reboarding or replay of P4.
        train.operatingMode = train.operatingMode.release(true);
        train.activeAtp.clear();
        controls.setBrakeNotch(train, 7);
        rejects(power);
        assert enabled.get() && train.powerNotch == 0 && train.brakeNotch == 7;
        grant(train, lease, session, "FS");
        assert train.powerNotch == 0 : "New MA must not replay a previously rejected handle input";
        refreshIntegrity(train, member);
        power.run();
        assert train.operatingMode == OperatingMode.FS && train.powerNotch == 4;

        train.protectionMode = ProtectionMode.RECOVERING;
        rejects(power);
        assert enabled.get() : "Other traction interlocks must also retain the input device";
        owner.set(false);
        rejects(power);
        assert !enabled.get() : "Loss of driver ownership must still disable hotbar";
        System.out.println("PASS pending SR -> approval -> power, release -> FS -> power, and ownership loss");
    }

    private static void refreshIntegrity(Train train, UUID member) {
        long now = System.currentTimeMillis();
        long[] observations = train.tims.manifest().isEmpty() ? new long[] {now - 1000, now} : new long[] {now};
        for (long at : observations)
            train.tims.evaluate(train.members(), List.of(new TrainIntegrityMonitor.Sample(
                    member, "w", 0, 0, 0, at, "OBSERVED")), List.of(), 1.1, at);
    }

    private static void grant(Train train, UUID lease, UUID session, String mode) {
        var decision = train.activeAtp.evaluate(new OperationalPermission(UUID.randomUUID(), lease, session,
                mode, 100, 40 / 3.6, 1, "VALID"), lease, train.operatingMode, true, false, "FORWARD",
                0, train.brakeNotch, System.currentTimeMillis(),
                new ActiveAtpState.Settings(1, 5, 5 / 3.6, 1, 1, 10 / 3.6, 10000, 5000));
        assert decision.tractionAllowed();
        train.operatingMode = decision.mode();
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejected input"); }
        catch (IllegalArgumentException expected) { }
    }
}
