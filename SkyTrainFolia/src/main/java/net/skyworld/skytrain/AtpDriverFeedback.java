package net.skyworld.skytrain;

import java.util.UUID;

/** Driver-facing warning gate and one-shot intervention transitions. */
final class AtpDriverFeedback {
    private UUID lease;
    private int previousBrake;

    static boolean speedWarningChannel(String channel) {
        return "SHADOW".equals(channel) || "ACTIVE".equals(channel);
    }

    static String soundChannel(String cue) {
        return cue.equals("NEAR_LIMIT") || cue.equals("OVERSPEED") ? "SPEED"
                : cue.startsWith("ATP_") ? "ATP" : "MA";
    }

    static boolean interventionVisible(int brake, OperatingMode mode, String reason, double speedMps) {
        return brake >= 7 && (speedMps > 0.025 || mode == OperatingMode.TR
                || "OVERSPEED_UNRESPONSIVE".equals(reason));
    }

    String update(UUID currentLease, boolean activeManual, int brake, boolean visible) {
        if (currentLease == null || !activeManual) {
            lease = null;
            previousBrake = 0;
            return null;
        }
        if (!currentLease.equals(lease)) {
            lease = currentLease;
            previousBrake = 0;
        }
        int currentBrake = visible ? brake : 0;
        String cue = currentBrake > previousBrake
                ? currentBrake >= 8 ? "ATP_EMERGENCY" : "ATP_SERVICE" : null;
        previousBrake = currentBrake;
        return cue;
    }
}
