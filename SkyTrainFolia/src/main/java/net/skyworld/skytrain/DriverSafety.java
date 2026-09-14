package net.skyworld.skytrain;

import java.util.UUID;

final class DriverSafety {
    static boolean neutralHotbarSlot(int slot) { return slot == 4; }
    static boolean sameSession(UUID expected, UUID current) {
        return expected != null && expected.equals(current);
    }
    static boolean ownsSeat(UUID owner, UUID requester, UUID seat, UUID actualSeat, boolean online) {
        return online && owner != null && owner.equals(requester) && seat != null && seat.equals(actualSeat);
    }
    static void brake(Train train) {
        train.driverEmergencyHold = true;
        train.automaticRun = null;
        train.clearStationMotion();
        train.pauseUntilMillis = 0;
        train.clearPlayerPush();
        train.manualTakeover = true;
        train.manualReleaseConfirmed = false;
        maintainBrake(train);
    }
    static void maintainBrake(Train train) {
        train.automaticRun = null;
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = 7;
        train.emergencyBrake = true;
        train.moving = true;
    }
}
