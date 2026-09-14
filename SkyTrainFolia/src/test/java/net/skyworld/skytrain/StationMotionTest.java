package net.skyworld.skytrain;

public final class StationMotionTest {
    public static void main(String[] args) {
        long now = 1_000L;
        StationMotion motion = new StationMotion("world:0:64:0", 10.0, 0.40,
                5_000L, true, false, now);

        check(motion.phase() == StationMotion.Phase.DOCKING, "Motion must begin by docking");
        double initialSpeed = motion.commandedSpeed(0.02);
        motion.advance(5.0, now + 500L);
        double middleSpeed = motion.commandedSpeed(0.02);
        check(middleSpeed < initialSpeed && middleSpeed >= 0.02,
                "Docking curve must slow down while retaining a creep floor");
        check(Math.abs(motion.remainingDistance() - 5.0) < 0.000001,
                "Docking distance must track actual movement");

        StationMotion.AdvanceResult docked = motion.advance(20.0, now + 1_000L);
        check(docked == StationMotion.AdvanceResult.DOCKED,
                "Final movement must be capped at the stop point");
        check(motion.phase() == StationMotion.Phase.DWELL, "Motion must enter dwell after docking");
        check(!motion.dwellExpired(now + 5_999L), "Dwell must retain the configured duration");
        check(motion.dwellExpired(now + 6_000L), "Dwell must expire at the configured time");
        check(motion.needsDepartureReverse(false), "Reverse departure must wait for direction change");
        check(!motion.needsDepartureReverse(true), "Reverse departure must release after direction change");

        motion.beginDeparture(0.40, 8.0);
        check(motion.phase() == StationMotion.Phase.DEPARTING, "Motion must enter departure phase");
        double departureInitial = motion.commandedSpeed(0.012);
        motion.advance(4.0, now + 6_500L);
        double departureMiddle = motion.commandedSpeed(0.012);
        check(departureMiddle > departureInitial,
                "Departure curve must accelerate as distance is covered");
        check(motion.advance(4.0, now + 7_000L) == StationMotion.AdvanceResult.COMPLETE,
                "Departure must complete at its configured distance");
        check(motion.phase() == StationMotion.Phase.COMPLETE, "Completed departure must release control");

        StationMotion alreadyCentered = new StationMotion("station", 0.0, 0.30,
                0L, false, false, now);
        check(alreadyCentered.phase() == StationMotion.Phase.DWELL,
                "A centered train must enter dwell immediately");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
