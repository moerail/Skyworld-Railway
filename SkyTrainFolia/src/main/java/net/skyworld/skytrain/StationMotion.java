package net.skyworld.skytrain;

final class StationMotion {
    private static final double EPSILON = 0.001;

    enum Phase {
        DOCKING,
        DWELL,
        DEPARTING,
        COMPLETE
    }

    enum AdvanceResult {
        NONE,
        DOCKED,
        COMPLETE
    }

    private final String signKey;
    private final long dwellMillis;
    private final boolean reverseOnDeparture;
    private final boolean initialReversed;
    private Phase phase;
    private double totalDistance;
    private double travelledDistance;
    private double startSpeed;
    private double targetSpeed;
    private long dwellUntilMillis;

    StationMotion(String signKey, double dockingDistance, double startSpeed, long dwellMillis,
            boolean reverseOnDeparture, boolean initialReversed, long nowMillis) {
        this.signKey = signKey;
        this.dwellMillis = Math.max(0L, dwellMillis);
        this.reverseOnDeparture = reverseOnDeparture;
        this.initialReversed = initialReversed;
        this.totalDistance = Math.max(0.0, dockingDistance);
        this.startSpeed = Math.max(0.0, startSpeed);
        this.targetSpeed = 0.0;
        if (this.totalDistance <= EPSILON) {
            enterDwell(nowMillis);
        } else {
            this.phase = Phase.DOCKING;
        }
    }

    synchronized String signKey() {
        return signKey;
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized boolean isMoving() {
        return phase == Phase.DOCKING || phase == Phase.DEPARTING;
    }

    synchronized boolean isWaiting() {
        return phase == Phase.DWELL;
    }

    synchronized boolean dwellExpired(long nowMillis) {
        return phase == Phase.DWELL && nowMillis >= dwellUntilMillis;
    }

    synchronized long dwellUntilMillis() {
        return dwellUntilMillis;
    }

    synchronized boolean needsDepartureReverse(boolean reversed) {
        return reverseOnDeparture && reversed == initialReversed;
    }

    synchronized void beginDeparture(double speed, double distance) {
        targetSpeed = Math.max(0.0, speed);
        totalDistance = Math.max(0.0, distance);
        travelledDistance = 0.0;
        startSpeed = 0.0;
        phase = targetSpeed <= EPSILON || totalDistance <= EPSILON
                ? Phase.COMPLETE : Phase.DEPARTING;
    }

    synchronized double commandedSpeed(double minimumSpeed) {
        double remaining = remainingDistance();
        if (!isMoving() || remaining <= EPSILON) {
            return 0.0;
        }

        double progress = totalDistance <= EPSILON
                ? 1.0 : RailMath.clamp(travelledDistance / totalDistance, 0.0, 1.0);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        if (phase == Phase.DOCKING) {
            double initial = Math.max(startSpeed, Math.max(EPSILON, minimumSpeed));
            return Math.max(Math.min(initial, minimumSpeed), initial * (1.0 - eased));
        }

        double floor = Math.min(targetSpeed, Math.max(EPSILON, minimumSpeed));
        return Math.min(targetSpeed, Math.max(floor, targetSpeed * eased));
    }

    synchronized double remainingDistance() {
        return isMoving() ? Math.max(0.0, totalDistance - travelledDistance) : 0.0;
    }

    synchronized double targetSpeed() {
        return targetSpeed;
    }

    synchronized AdvanceResult advance(double distance, long nowMillis) {
        if (!isMoving()) {
            return phase == Phase.COMPLETE ? AdvanceResult.COMPLETE : AdvanceResult.NONE;
        }
        travelledDistance = Math.min(totalDistance,
                travelledDistance + Math.max(0.0, distance));
        if (travelledDistance + EPSILON < totalDistance) {
            return AdvanceResult.NONE;
        }
        if (phase == Phase.DOCKING) {
            enterDwell(nowMillis);
            return AdvanceResult.DOCKED;
        }
        phase = Phase.COMPLETE;
        return AdvanceResult.COMPLETE;
    }

    private void enterDwell(long nowMillis) {
        phase = Phase.DWELL;
        travelledDistance = totalDistance;
        dwellUntilMillis = nowMillis + dwellMillis;
    }
}
