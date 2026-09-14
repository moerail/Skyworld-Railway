package net.skyworld.sta.api.v1;

import java.util.Objects;
import java.util.UUID;

/** Immutable physical train state measured by the train implementation. */
public record TrainTelemetrySnapshot(
        UUID trainId,
        String trainName,
        long sequence,
        long observedAtMillis,
        String world,
        int railX,
        int railY,
        int railZ,
        double x,
        double y,
        double z,
        double motionX,
        double motionY,
        double motionZ,
        double speedMetersPerSecond,
        double lengthMeters,
        int memberCount,
        boolean moving,
        boolean reversed,
        TrainMode mode,
        String driverName,
        CabState cab,
        String trainNumber) {

    public TrainTelemetrySnapshot(UUID trainId, String trainName, long sequence, long observedAtMillis,
            String world, int railX, int railY, int railZ, double x, double y, double z,
            double motionX, double motionY, double motionZ, double speedMetersPerSecond,
            double lengthMeters, int memberCount, boolean moving, boolean reversed,
            TrainMode mode, String driverName, CabState cab) {
        this(trainId, trainName, sequence, observedAtMillis, world, railX, railY, railZ, x, y, z,
                motionX, motionY, motionZ, speedMetersPerSecond, lengthMeters, memberCount,
                moving, reversed, mode, driverName, cab, "");
    }

    /** Optional display-only inputs. Not a control command or proof of ATP supervision. */
    public record CabState(String atpMode, String reverser, int powerNotch, int brakeNotch,
                           boolean emergencyBrake, boolean brakeHold) {
        public CabState {
            atpMode = required(atpMode, "atpMode");
            reverser = required(reverser, "reverser");
            if (powerNotch < 0 || powerNotch > 4 || brakeNotch < 0 || brakeNotch > 7) {
                throw new IllegalArgumentException("Invalid cab notch");
            }
        }
    }

    // Preserve the existing constructor for older in-process providers and tests.
    public TrainTelemetrySnapshot(UUID trainId, String trainName, long sequence, long observedAtMillis,
            String world, int railX, int railY, int railZ, double x, double y, double z,
            double motionX, double motionY, double motionZ, double speedMetersPerSecond,
            double lengthMeters, int memberCount, boolean moving, boolean reversed,
            TrainMode mode, String driverName) {
        this(trainId, trainName, sequence, observedAtMillis, world, railX, railY, railZ, x, y, z,
                motionX, motionY, motionZ, speedMetersPerSecond, lengthMeters, memberCount,
                moving, reversed, mode, driverName, null);
    }

    public TrainTelemetrySnapshot {
        trainNumber = trainNumber == null ? "" : trainNumber.trim();
        if (trainNumber.length() > 32 || trainNumber.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '|'))
            throw new IllegalArgumentException("Invalid train number");
        Objects.requireNonNull(trainId, "trainId");
        trainName = required(trainName, "trainName");
        world = required(world, "world");
        mode = mode == null ? TrainMode.UNKNOWN : mode;
        driverName = driverName == null ? "" : driverName.trim();
        if (sequence < 0 || observedAtMillis < 0 || memberCount < 1) {
            throw new IllegalArgumentException("Invalid sequence, timestamp, or member count.");
        }
        requireFinite(x, y, z, motionX, motionY, motionZ, speedMetersPerSecond, lengthMeters);
        if (speedMetersPerSecond < 0 || lengthMeters < 0) {
            throw new IllegalArgumentException("Speed and length cannot be negative.");
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        return normalized;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Telemetry values must be finite.");
            }
        }
    }
}
