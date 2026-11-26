package net.skyworld.skytrain;

import java.util.UUID;

public final class DriverSafetyTest {
    public static void main(String[] args) {
        var waiting=new Train(UUID.randomUUID(),"direction",.4,1,1.1);waiting.operatingMode=OperatingMode.SB;waiting.protectionMode=ProtectionMode.ACTIVE;
        var waitingMember=UUID.randomUUID();waiting.addMember(waitingMember);for(long at:new long[]{0,1000})waiting.tims.evaluate(waiting.members(),java.util.List.of(new TrainIntegrityMonitor.Sample(waitingMember,"w",0,0,0,at,"OBSERVED")),java.util.List.of(),1.1,at);
        waiting.reverser=Reverser.NEUTRAL;assert !DriverSafety.defaultFsReady(waiting,1000):"Declaring a driver is not selecting direction";
        waiting.reverser=Reverser.FORWARD;assert DriverSafety.defaultFsReady(waiting,1000);
        waiting.reverser=Reverser.BACKWARD;assert DriverSafety.defaultFsReady(waiting,1000);
        waiting.reverser=Reverser.NEUTRAL;assert !DriverSafety.defaultFsReady(waiting,1000):"Returning to neutral cannot request default FS";
        waiting.reverser=Reverser.FORWARD;waiting.operatingMode=OperatingMode.TR;assert !DriverSafety.defaultFsReady(waiting,1000);
        waiting.operatingMode=OperatingMode.SB;waiting.protectionMode=ProtectionMode.ISOLATED;assert !DriverSafety.defaultFsReady(waiting,1000);
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
        DriverSafety.brake(train);
        assert train.powerNotch == 0 && train.brakeNotch == 7 && train.emergencyBrake;
        assert train.driverEmergencyHold && train.driveControlEnabled && train.manualTakeover && !train.manualReleaseConfirmed;
        assert train.pauseUntilMillis == 0;
        assert train.currentSpeed() == .4 : "EB uses physics, must not fake zero speed";
        train.powerNotch = 4; train.emergencyBrake = false;
        DriverSafety.maintainBrake(train);
        assert train.powerNotch == 0 && train.emergencyBrake : "A late motion update cannot remove the hold";
        train.manualReleaseConfirmed = true;
        DriverSafety.maintainBrake(train);
        assert train.manualReleaseConfirmed : "Brake maintenance must not revoke explicit release";
        train.seedCurrentSpeed(0);
        assert train.driverEmergencyHold && train.manualReleaseConfirmed;
        System.out.println("Ride ownership, driver-loss braking, late updates and explicit release passed");
    }
}
