package net.skyworld.skytrain;

import java.util.Set;

/** TIMS recovery must not erase an independent ATP/dispatcher Trip or an overrun. */
final class AutomaticRecoveryPolicy {
    static boolean mayAcknowledgeTims(String reason,OperatingMode mode,boolean integrityTrip,Double remaining) {
        return reason!=null&&Set.of("TIMS_STALE","MEMBER_UNAVAILABLE","SAMPLING_SKEW","PATH_UNAVAILABLE").contains(reason)
                && (mode!=OperatingMode.TR&&mode!=OperatingMode.PT || mode==OperatingMode.TR&&integrityTrip)
                && (remaining==null || Double.isFinite(remaining)&&remaining>=-.2);
    }
}
