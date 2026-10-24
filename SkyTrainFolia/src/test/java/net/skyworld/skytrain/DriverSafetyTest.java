package net.skyworld.skytrain;

import java.util.UUID;

public final class DriverSafetyTest {
    public static void main(String[] args) {
        for (int slot = -1; slot <= 9; slot++)
            assert DriverSafety.neutralHotbarSlot(slot) == (slot == 4) : "Only player-facing slot 5 may enable hotbar controls";
        UUID driver = UUID.randomUUID(), passenger = UUID.randomUUID(), seat = UUID.randomUUID();
        assert DriverSafety.ownsSeat(driver, driver, seat, seat, true);
        assert !DriverSafety.ownsSeat(driver, passenger, seat, seat, true);
        assert !DriverSafety.ownsSeat(driver, driver, seat, seat, false);
        assert !DriverSafety.ownsSeat(driver, driver, seat, null, true);
        assert !DriverSafety.ownsSeat(driver, driver, seat, UUID.randomUUID(), true);
        assert !DriverSafety.ownsSeat(null, driver, null, seat, true) : "Reboarding cannot grant control";
        UUID firstRide = UUID.randomUUID(), secondRide = UUID.randomUUID();
        assert DriverSafety.sameSession(firstRide, firstRide);
        assert !DriverSafety.sameSession(firstRide, secondRide) : "Old retirement callback must not revoke a new ride";
        assert !DriverSafety.sameSession(firstRide, null) : "Duplicate callback cannot revoke twice";
        for (OperatingMode previous : OperatingMode.values()) {
            var handedOver = new Train(UUID.randomUUID(), "handover", .4, 1, 1.1);
            UUID oldLease = UUID.randomUUID(), controlSession = UUID.randomUUID();
            handedOver.activeAtp.evaluate(new OperationalPermission(UUID.randomUUID(), oldLease,
                    controlSession, "FS", 100, 20, 2, "VALID"), oldLease, OperatingMode.SB,
                    true, false, "FORWARD", 0, 7, 1000,
                    new ActiveAtpState.Settings(1, 5, 5 / 3.6, 1, 1, 10 / 3.6, 10000, 5000));
            handedOver.operatingMode = previous;
            handedOver.activeAtpLimitMps = 20.; handedOver.activeAtpBrakeLevel = 7;
            handedOver.seedCurrentSpeed(.4);
            DriverSafety.revokeAuthority(handedOver);
            assert handedOver.operatingMode == OperatingMode.SB : "Driver loss must reset " + previous;
            assert handedOver.activeAtp.remainingMeters() == null : "Old MA must not survive driver loss";
            assert handedOver.activeAtpLimitMps == null && handedOver.activeAtpBrakeLevel == 0;
            assert handedOver.driverEmergencyHold && handedOver.emergencyBrake && handedOver.powerNotch == 0;
            assert handedOver.currentSpeed() == .4 : "Returning to SB must still brake through physics";
            DriverSafety.revokeAuthority(handedOver);
            assert handedOver.operatingMode == OperatingMode.SB : "Repeated driver loss is harmless";
        }
        var train = new Train(UUID.randomUUID(), "driver-loss", .4, 1, 1.1);
        train.seedCurrentSpeed(.4); train.powerNotch = 4; train.brakeNotch = 0;
        train.pauseUntilMillis = Long.MAX_VALUE;
        train.automaticRun = new AutomaticRun("station", AutomaticSignSpec.parse("station", "5", "continue", .4), 100, false, false);
        DriverSafety.brake(train);
        assert train.powerNotch == 0 && train.brakeNotch == 7 && train.emergencyBrake;
        assert train.driverEmergencyHold && train.driveControlEnabled && train.manualTakeover && !train.manualReleaseConfirmed;
        assert train.pauseUntilMillis == 0 && train.automaticRun == null;
        assert train.currentSpeed() == .4 : "EB uses physics, must not fake zero speed";
        train.powerNotch = 4; train.emergencyBrake = false;
        DriverSafety.maintainBrake(train);
        assert train.powerNotch == 0 && train.emergencyBrake : "A late motion update cannot remove the hold";
        train.manualReleaseConfirmed = true;
        DriverSafety.maintainBrake(train);
        assert train.manualReleaseConfirmed : "Brake maintenance must not revoke explicit release";
        train.seedCurrentSpeed(0);
        TrainManager.authorizeAutomatic(train, false, .001);
        assert !train.driverEmergencyHold && !train.emergencyBrake;
        System.out.println("Ride ownership, driver-loss braking, late updates and explicit auto rearm passed");
    }
}
