package net.skyworld.skytrain;

import java.util.UUID;

/** Conservative onboard state for manual trains; no Bukkit or STA types. */
final class ActiveAtpState {
    record Settings(double stopMarginMeters, double approachDistanceMeters, double approachMps,
            double serviceDecelerationMps2, double reactionSeconds, double overspeedToleranceMps,
            long overspeedGraceMillis, long serviceToEmergencyMillis) {
        Settings {
            if (!Double.isFinite(stopMarginMeters) || !Double.isFinite(approachDistanceMeters)
                    || !Double.isFinite(approachMps) || !Double.isFinite(serviceDecelerationMps2)
                    || !Double.isFinite(reactionSeconds) || !Double.isFinite(overspeedToleranceMps)
                    || stopMarginMeters < 0 || approachDistanceMeters <= stopMarginMeters
                    || approachMps <= 0 || serviceDecelerationMps2 <= 0 || reactionSeconds < 0
                    || overspeedToleranceMps < 0 || overspeedGraceMillis < 0
                    || serviceToEmergencyMillis < 0)
                throw new IllegalArgumentException("Invalid active ATP settings");
        }
    }

    record Decision(OperatingMode mode, double limitMps, int minimumBrakeNotch,
            boolean emergency, boolean tractionAllowed, String reason) {
        static Decision hold(OperatingMode mode, String reason) {
            return new Decision(mode, 0, 7, true, false, reason);
        }
    }

    private UUID grantId, lease, controlSession;
    private String mode, reverser;
    private boolean reversed;
    private boolean overspeedEmergencyLatched;
    private long graphRevision, ordinaryOverspeedSince = -1, serviceSince = -1;
    private double remainingMeters, ceilingMps;

    synchronized Decision evaluate(OperationalPermission permission, UUID currentLease,
            OperatingMode operating, boolean stopped, boolean currentReversed, String currentReverser,
            double speedMps, int driverBrakeNotch, long now, Settings settings) {
        if (operating == OperatingMode.TR || operating == OperatingMode.PT)
            return Decision.hold(operating, operating.name());
        if (overspeedEmergencyLatched)
            return Decision.hold(operating, "OVERSPEED_UNRESPONSIVE");
        if (currentLease == null) { clear(); return Decision.hold(OperatingMode.SB, "NO_DRIVER"); }
        if (permission.available()) {
            if (!currentLease.equals(permission.lease()) || permission.controlSession() == null
                    || permission.graphRevision() < 0
                    || !Double.isFinite(permission.remainingMeters()) || permission.ceilingMps() <= 0)
                return Decision.hold(operating, "INVALID_GRANT");
            try {
                operating = operating.grant(permission.mode(), stopped);
            } catch (IllegalArgumentException ex) {
                return Decision.hold(operating, "MODE_CONFLICT");
            }
            if (!permission.id().equals(grantId)) {
                remainingMeters = permission.remainingMeters();
            } else {
                remainingMeters = Math.min(remainingMeters, permission.remainingMeters());
            }
            grantId = permission.id(); lease = currentLease;
            controlSession = permission.controlSession(); mode = permission.mode();
            graphRevision = permission.graphRevision(); ceilingMps = permission.ceilingMps();
            reversed = currentReversed; reverser = currentReverser;
        } else if (permission.reason().equals("NOT_EXECUTABLE")
                || permission.reason().equals("REVOKED")) {
            clear();
            return Decision.hold(operating, permission.reason());
        } else if (grantId == null || !currentLease.equals(lease)
                || !controlSession.equals(permission.controlSession())
                || permission.graphRevision() != graphRevision
                || currentReversed != reversed || !currentReverser.equals(reverser)) {
            clear();
            return Decision.hold(operating, permission.reason());
        }
        if (!operating.permitsTraction() || !operating.name().equals(mode))
            return Decision.hold(operating, "NO_OPERATING_PERMISSION");
        if (remainingMeters < -0.2 && !stopped)
            return Decision.hold(operating.trip(), "EOA_OVERRUN");
        double limit = curveLimit(settings, remainingMeters, ceilingMps);
        if (speedMps > limit + 0.15 && limit < ceilingMps - 0.15)
            return new Decision(operating, limit, 7, false, false, "EOA_CURVE");
        if ((operating == OperatingMode.SH || operating == OperatingMode.SR)
                && speedMps > ceilingMps + 0.15)
            return new Decision(operating, limit, 7, false, false, "MODE_SPEED_LIMIT");
        if (speedMps > ceilingMps + settings.overspeedToleranceMps()) {
            if (driverBrakeNotch > 0) { ordinaryOverspeedSince = -1; serviceSince = -1; }
            else if (ordinaryOverspeedSince < 0) ordinaryOverspeedSince = now;
            if (ordinaryOverspeedSince >= 0 && now - ordinaryOverspeedSince >= settings.overspeedGraceMillis()) {
                if (serviceSince < 0) serviceSince = now;
                if (now - serviceSince >= settings.serviceToEmergencyMillis()) {
                    overspeedEmergencyLatched = true;
                    return Decision.hold(operating, "OVERSPEED_UNRESPONSIVE");
                }
                return new Decision(operating, limit, 7, false, false, "OVERSPEED_SERVICE");
            }
            return new Decision(operating, limit, 0, false, true, "OVERSPEED_WARNING");
        }
        if (serviceSince >= 0 && driverBrakeNotch == 0
                && now - serviceSince >= settings.serviceToEmergencyMillis()) {
            overspeedEmergencyLatched = true;
            return Decision.hold(operating, "OVERSPEED_UNRESPONSIVE");
        }
        ordinaryOverspeedSince = -1;
        if (driverBrakeNotch > 0) serviceSince = -1;
        return new Decision(operating, limit, 0, false, true,
                permission.available() ? "VALID" : "LAST_CONFIRMED_EOA");
    }

    synchronized void advance(double metres) {
        if (grantId != null && Double.isFinite(metres) && metres > 0)
            remainingMeters -= metres;
    }

    synchronized Double remainingMeters() { return grantId == null ? null : remainingMeters; }

    synchronized void clear() {
        grantId = null; lease = null; controlSession = null; mode = null; reverser = null;
        ordinaryOverspeedSince = -1; serviceSince = -1;
        overspeedEmergencyLatched = false;
        remainingMeters = 0; ceilingMps = 0; graphRevision = -1;
    }

    static double curveLimit(Settings settings, double remaining, double ceiling) {
        double available = Math.max(0, remaining - settings.stopMarginMeters());
        double stop = permitted(available, 0, settings);
        double approach = remaining <= settings.approachDistanceMeters()
                ? settings.approachMps()
                : permitted(remaining - settings.approachDistanceMeters(), settings.approachMps(), settings);
        return Math.max(0, Math.min(ceiling, Math.min(stop, approach)));
    }

    private static double permitted(double distance, double target, Settings settings) {
        if (distance <= 0) return target;
        double at = settings.serviceDecelerationMps2() * settings.reactionSeconds();
        return Math.max(target, Math.sqrt(at * at + target * target
                + 2 * settings.serviceDecelerationMps2() * distance) - at);
    }
}
