package net.skyworld.skytrain;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.bukkit.configuration.file.FileConfiguration;

/** Reads live configuration/profile suppliers; defaults and clamps stay compatible across reload. */
final class TrainSettings {
    private final Supplier<FileConfiguration> configuration;
    private final Supplier<VehicleProfile> vehicle;
    private final DoubleSupplier serverSpeedLimit;

    TrainSettings(SkyTrainPlugin plugin) {
        this(plugin::getConfig, plugin::vehicleProfile, plugin::serverSpeedLimit);
    }

    TrainSettings(Supplier<FileConfiguration> configuration, Supplier<VehicleProfile> vehicle,
            DoubleSupplier serverSpeedLimit) {
        this.configuration = configuration;
        this.vehicle = vehicle;
        this.serverSpeedLimit = serverSpeedLimit;
    }

    double defaultSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.default-speed", 0.35), 0.0, maxSpeed());
    }

    double maxSpeed() {
        return Math.min(vehicle.get().defaultMaxSpeed(), serverSpeedLimit.getAsDouble());
    }

    double maxAllowedSpeed() {
        return Math.min(vehicle.get().maxSpeed(), serverSpeedLimit.getAsDouble());
    }

    double maxSpeedChangePerTick() {
        return RailMath.clamp(configuration.get().getDouble("settings.max-speed-change-per-tick", 0.035), 0.001, 0.5);
    }

    double poweredRailBoostSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.powered-rail-boost-speed", 0.6), 0.0, maxSpeed());
    }

    double defaultSpacing() {
        return RailMath.clamp(configuration.get().getDouble("settings.default-spacing", 1.10), 0.8, 8.0);
    }

    double spacingCorrection() {
        return RailMath.clamp(configuration.get().getDouble("settings.spacing-correction", 0.045), 0.0, 0.5);
    }

    double spacingFollowCorrection() {
        return RailMath.clamp(configuration.get().getDouble("settings.spacing-follow-correction", 0.35), 0.0, 2.0);
    }

    boolean forceTightSpacing() {
        return configuration.get().getBoolean("settings.force-tight-spacing", true);
    }

    double tightSpacing() {
        return RailMath.clamp(configuration.get().getDouble("settings.tight-spacing", 1.10), 0.6, 2.0);
    }

    long reverseSettleTicks() {
        return Math.max(1L, configuration.get().getLong("settings.reverse-settle-ticks", 12L));
    }

    long reverseMaxBrakeTicks() {
        return Math.max(reverseSettleTicks(), configuration.get().getLong("settings.reverse-max-brake-ticks", 40L));
    }

    double reverseReleaseSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.reverse-release-speed", 0.04), 0.0, maxSpeed());
    }

    double minimumFollowDistanceRatio() {
        return RailMath.clamp(configuration.get().getDouble("settings.minimum-follow-distance-ratio", 0.72), 0.25, 0.95);
    }

    boolean railBindEnabled() {
        return configuration.get().getBoolean("settings.rail-bind-enabled", true);
    }

    double railBindStrength() {
        return RailMath.clamp(configuration.get().getDouble("settings.rail-bind-strength", 0.45), 0.0, 1.0);
    }

    double railBindMaxCorrectionPerTick() {
        return RailMath.clamp(configuration.get().getDouble("settings.rail-bind-max-correction-per-tick", 0.035),
                0.0,
                0.2);
    }

    double railBindTeleportDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.rail-bind-teleport-distance", 0.55), 0.1, 2.0);
    }

    boolean trainPhysicsHardLock() {
        return configuration.get().getBoolean("settings.track-coordinate-physics", true);
    }

    double trainPhysicsPositionCorrection() {
        return RailMath.clamp(configuration.get().getDouble("settings.train-physics-position-correction", 0.75), 0.0, 2.0);
    }

    double trainPhysicsMaxCorrectionPerTick() {
        return RailMath.clamp(configuration.get().getDouble("settings.train-physics-max-correction-per-tick", 0.20), 0.0, 1.0);
    }

    double trainPhysicsTeleportDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.train-physics-teleport-distance", 0.45), 0.05, 2.0);
    }

    boolean passengerSmoothingEnabled() {
        return configuration.get().getBoolean("settings.passenger-smoothing-enabled", true);
    }

    double passengerPositionCorrection() {
        return RailMath.clamp(configuration.get().getDouble("settings.passenger-position-correction", 0.30), 0.0, 1.0);
    }

    double passengerMaxCorrectionPerTick() {
        return RailMath.clamp(configuration.get().getDouble("settings.passenger-max-correction-per-tick", 0.10), 0.0, 0.5);
    }

    double passengerTeleportDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.passenger-teleport-distance", 1.25), 0.25, 4.0);
    }

    double passengerSettleDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.passenger-settle-distance", 0.04), 0.005, 0.25);
    }

    double passengerVanillaMotionFactor() {
        return RailMath.clamp(configuration.get().getDouble("settings.passenger-vanilla-motion-factor", 0.75), 0.25, 1.0);
    }

    int tracksideRunningSoundIntervalTicks() {
        return Math.max(4, Math.min(100,
                configuration.get().getInt("settings.trackside-running-sound-interval-ticks", 16)));
    }

    double tracksideRunningSoundMinSpeed() {
        return RailMath.clamp(
                configuration.get().getDouble("settings.trackside-running-sound-min-speed", 0.025),
                0.0,
                1.0);
    }

    double tracksideRunningSoundVolume() {
        return RailMath.clamp(
                configuration.get().getDouble("settings.trackside-running-sound-volume", 0.85),
                0.0,
                4.0);
    }

    double tracksideRunningSoundMinPitch() {
        return RailMath.clamp(
                configuration.get().getDouble("settings.trackside-running-sound-min-pitch", 0.75),
                0.5,
                2.0);
    }

    double tracksideRunningSoundMaxPitch() {
        double minPitch = tracksideRunningSoundMinPitch();
        return RailMath.clamp(
                configuration.get().getDouble("settings.trackside-running-sound-max-pitch", 1.20),
                minPitch,
                2.0);
    }

    double playerPushImpulse() {
        return RailMath.clamp(configuration.get().getDouble("settings.player-push-impulse", 0.055), 0.005, 0.5);
    }

    double playerPushMovementBonus() {
        return RailMath.clamp(configuration.get().getDouble("settings.player-push-movement-bonus", 0.045), 0.0, 0.5);
    }

    double playerPushMaxSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.player-push-max-speed", 0.22), 0.01, 1.0);
    }

    double playerPushActivationSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.player-push-activation-speed", 0.035), 0.0, 0.25);
    }

    double playerPushDecelerationPerTick() {
        return RailMath.clamp(configuration.get().getDouble("settings.player-push-deceleration-per-tick", 0.004), 0.0001, 0.1);
    }

    double driveDirectionChangeSpeed() { return vehicle.get().reverseSpeed(); }

    boolean frontMinecartDetectionEnabled() {
        return configuration.get().getBoolean("settings.front-minecart-detection-enabled", true);
    }

    double frontMinecartDetectionDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.front-minecart-detection-distance", 8.0), 2.0, 32.0);
    }

    double frontMinecartStopDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.front-minecart-stop-distance", 1.65), 0.6,
                Math.max(0.7, frontMinecartDetectionDistance() - 0.1));
    }

    double frontMinecartLateralDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.front-minecart-lateral-distance", 1.15), 0.3, 4.0);
    }

    double coupledHardStopRatio() {
        return RailMath.clamp(configuration.get().getDouble("settings.coupled-hard-stop-ratio", 0.78), 0.3, 0.98);
    }

    double coupledSlowRatio() {
        return RailMath.clamp(configuration.get().getDouble("settings.coupled-slow-ratio", 1.08), coupledHardStopRatio() + 0.02,
                1.6);
    }

    double coupledBrakeSpeedBuffer() {
        return RailMath.clamp(configuration.get().getDouble("settings.coupled-brake-speed-buffer", 0.015), 0.0, 0.2);
    }

    boolean brakeOnUnpoweredRail() {
        return configuration.get().getBoolean("settings.unpowered-powered-rail-brake", true);
    }

    long signCooldownMillis() {
        return Math.max(100L, configuration.get().getLong("settings.sign-cooldown-ms", 1000L));
    }

    long reverseSignCooldownMillis() {
        return Math.max(signCooldownMillis(), configuration.get().getLong("settings.reverse-sign-cooldown-ms", 3000L));
    }

    long reverseSignBlockMillis() {
        return reverseSignCooldownMillis() + reverseMaxBrakeTicks() * 50L;
    }

    long defaultStationWaitTicks() {
        return Math.max(0L, configuration.get().getLong("settings.default-station-wait-ticks", 100L));
    }

    double stationDockingStopOffset() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-docking-stop-offset", 0.0),
                -8.0, 8.0);
    }

    double stationDockingMaxDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-docking-max-distance", 64.0),
                1.0, 256.0);
    }

    double stationDockingMinSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-docking-min-speed", 0.020),
                0.001, 0.20);
    }

    double stationDepartureDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-departure-distance", 8.0),
                0.25, 128.0);
    }

    double stationDepartureMinSpeed() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-departure-min-speed", 0.012),
                0.001, 0.20);
    }

    double stationReleaseDistance() {
        return RailMath.clamp(configuration.get().getDouble("settings.station-release-distance", 4.0), 2.0, 16.0);
    }

    long tickInterval() {
        return Math.max(1L, configuration.get().getLong("settings.tick-interval", 1L));
    }

    boolean autoLinkEnabled() {
        return configuration.get().getBoolean("settings.auto-link-enabled", true);
    }

    double autoLinkRadius() {
        return RailMath.clamp(configuration.get().getDouble("settings.auto-link-radius", 2.75), 0.8, 8.0);
    }

    long autoLinkDelayTicks() {
        return Math.max(1L, configuration.get().getLong("settings.auto-link-delay-ticks", 2L));
    }

    boolean autoLinkCreateSingleCartTrains() {
        return configuration.get().getBoolean("settings.auto-link-create-single-cart-trains", false);
    }

    long maxAutoLinkCarts() {
        return Math.max(2L, configuration.get().getLong("settings.auto-link-max-carts-per-pass", 16L));
    }

    boolean autoArrangeEnabled() {
        return configuration.get().getBoolean("settings.auto-arrange-enabled", true);
    }

    double autoArrangeSpacing() {
        return RailMath.clamp(configuration.get().getDouble("settings.auto-arrange-spacing", 1.10), 0.8, 4.0);
    }

    long consistLabelVisibleTicks() {
        return Math.max(0L, configuration.get().getLong("settings.consist-label-visible-ticks", 120L));
    }

    double signActivationRadius() {
        return RailMath.clamp(configuration.get().getDouble("settings.sign-activation-radius", 6.0), 1.0, 16.0);
    }

    boolean chunkLoadingEnabled() {
        return configuration.get().getBoolean("settings.chunk-loading-enabled", true);
    }

    int chunkLoadingRadius() {
        return Math.max(0, Math.min(2, configuration.get().getInt("settings.chunk-loading-radius", 1)));
    }
}
