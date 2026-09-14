package net.skyworld.skytrain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Runs on member entity schedulers; plans motion before delegating physical application. */
final class TrainMotionController {
    interface Host {
        boolean validateMemberTick(Train train, Minecart cart, ScheduledTask task);
        boolean automaticEligible(Train train);
        boolean tickAutomaticSigns(Train train, Block rail, Location location, Vector direction, long now);
        void refreshMemberIndexes(Train train);
        boolean isSameTrain(Minecart minecart, Entity other);
        String driverName(Train train);
    }

    private final SkyTrainPlugin plugin;
    private final TrainSettings settings;
    private final Host host;
    private final SwitchManager switchManager;
    private final LineInfrastructureManager infrastructureManager;
    private final TrainChunkLoader chunkLoader;
    private final StcsBridge stcsTelemetry;
    private final TrainSignActions signActions;
    private final TrainMemberActuator actuator;
    private final TrainAudio audio;

    TrainMotionController(SkyTrainPlugin plugin, TrainSettings settings, Host host,
            SwitchManager switchManager, LineInfrastructureManager infrastructureManager,
            TrainChunkLoader chunkLoader, StcsBridge stcsTelemetry, TrainSignActions signActions,
            TrainMemberActuator actuator, TrainAudio audio) {
        this.plugin = plugin;
        this.settings = settings;
        this.host = host;
        this.switchManager = switchManager;
        this.infrastructureManager = infrastructureManager;
        this.chunkLoader = chunkLoader;
        this.stcsTelemetry = stcsTelemetry;
        this.signActions = signActions;
        this.actuator = actuator;
        this.audio = audio;
    }

    void tickMember(Train train, Minecart cart, ScheduledTask task) {
        UUID entityId = cart.getUniqueId();
        if (!host.validateMemberTick(train, cart, task)) return;

        // Never mutate a cart while its asynchronous relocation is still in flight.
        if (actuator.relocationPending(entityId)) return;
        if (plugin.displaySync() != null) plugin.displaySync().observe(train.id(), cart);
        long now = System.currentTimeMillis();
        long currentTick = Bukkit.getCurrentTick();
        if (train.driverEmergencyHold) DriverSafety.maintainBrake(train);
        if (train.protectionMode == ProtectionMode.RECOVERING) {
            train.automaticRun = null;
            train.powerNotch = 0;
            train.brakeNotch = 7;
            train.emergencyBrake = true;
            train.driveControlEnabled = true;
        }
        applyForcedSpacing(train);
        int index = train.indexOf(entityId);
        if (index == activeLeaderIndex(train)) {
            if (settings.chunkLoadingEnabled()) {
                chunkLoader.update(train, cart.getLocation(), settings.chunkLoadingRadius());
            } else {
                chunkLoader.release(train.id());
            }
        }
        cart.setSlowWhenEmpty(false);
        TrainMemberActuator.removeNonPlayerPassengers(cart);
        boolean playerPassenger = TrainMemberActuator.hasPlayerPassenger(cart);
        boolean smoothPassenger = playerPassenger
                && (settings.trainPhysicsHardLock() || settings.passengerSmoothingEnabled());
        double trainSpeedLimit = plugin.trainSpeedLimit(train);
        double entityMaxSpeed = train.updateEffectiveMaxSpeed(now, trainSpeedLimit, settings.maxSpeedChangePerTick());
        double passengerMotionFactor = settings.passengerVanillaMotionFactor();
        double controlledEntityMaxSpeed = smoothPassenger
                ? RailMath.clamp(
                        Math.max(0.4, (entityMaxSpeed + settings.passengerMaxCorrectionPerTick()) / passengerMotionFactor),
                        0.05,
                        VehicleProfile.MAX_SPEED * 2)
                : 0.0;
        cart.setMaxSpeed(smoothPassenger
                ? controlledEntityMaxSpeed
                : (settings.trainPhysicsHardLock() ? 0.0 : RailMath.clamp(entityMaxSpeed, 0.05, VehicleProfile.MAX_SPEED)));
        cart.setGravity(!settings.trainPhysicsHardLock() && train.properties().gravity > 0.0);
        cart.setInvulnerable(train.properties().invincible);
        cart.setSilent(!train.properties().soundEnabled);

        Location location = cart.getLocation();
        RailInfo rail = RailMath.findRail(location);

        if (rail == null) {
            TrainMemberTarget target = train.memberTarget(entityId, currentTick);
            if (settings.trainPhysicsHardLock()) {
                if (target != null && now - target.updatedAtMillis <= 1000L) {
                    actuator.applyTrainTarget(train, cart, entityId, target, location, now);
                } else {
                    cart.setVelocity(new Vector());
                    train.memberSpeed(entityId, 0.0);
                    train.snapshot(entityId, new MemberSnapshot(entityId, location, new Vector(), now));
                }
                return;
            }

            Vector velocity = cart.getVelocity().multiply(0.7 * RailMath.clamp(train.properties().friction, 0.0, 4.0));
            if (velocity.lengthSquared() < 0.001) {
                velocity.zero();
            }
            cart.setVelocity(velocity);
            train.memberSpeed(entityId, TrainMemberActuator.horizontalSpeed(velocity));
            train.snapshot(entityId, new MemberSnapshot(entityId, location, velocity, now));
            return;
        }

        applyReverserDirection(train, now);
        Vector preference = cart.getVelocity();
        if (preference.lengthSquared() < 0.0001) {
            preference = train.rememberedDirection();
        }
        if (preference.lengthSquared() < 0.0001) {
            preference = RailMath.yawDirection(location.getYaw());
            if (train.reversed) {
                preference.multiply(-1.0);
            }
        }
        Vector direction = RailMath.direction(rail.rail.getShape(), preference);

        if (index == activeLeaderIndex(train)) {
            signActions.refreshStationLatches(train);
            if (host.tickAutomaticSigns(train, rail.block, location, direction, now)) return;
            if (signActions.handleSignActions(train, rail.block, location, direction, now)) {
                return;
            }
            signActions.prepareStationDeparture(train, now);
        }

        StationMotion stationMotion = train.stationMotion();
        boolean stationMoving = stationMotion != null && stationMotion.isMoving();
        boolean stationWaiting = stationMotion != null && stationMotion.isWaiting();
        boolean waiting = stationWaiting || train.pauseUntilMillis > now;
        if (!waiting && train.pauseUntilMillis > 0L) {
            train.pauseUntilMillis = 0L;
        }
        boolean settlingReverse = reverseBraking(train, now);

        boolean controlledStop = (!train.driveControlEnabled && !train.moving) || waiting || settlingReverse
                || train.emergencyBrake;
        boolean playerPushCoasting = controlledStop && train.playerPushActive;
        boolean automaticHandle = train.automaticRun != null && host.automaticEligible(train);
        double desiredTrainSpeed = controlledStop ? 0.0
                : stationMoving ? stationMotion.commandedSpeed(signActions.stationMotionMinimumSpeed(stationMotion))
                : (train.driveControlEnabled || automaticHandle ? train.maxSpeed : train.targetSpeed);

        if (!controlledStop && !stationMoving && index == activeLeaderIndex(train) && rail.poweredRail && rail.powered) {
            desiredTrainSpeed = Math.max(desiredTrainSpeed, settings.poweredRailBoostSpeed());
        }
        if (!controlledStop && !stationMoving && index == activeLeaderIndex(train)
                && rail.poweredRail && !rail.powered && settings.brakeOnUnpoweredRail()) {
            desiredTrainSpeed *= 0.25;
        }
        desiredTrainSpeed *= RailMath.clamp(train.properties().friction, 0.0, 4.0);
        desiredTrainSpeed = RailMath.clamp(desiredTrainSpeed, 0.0, trainSpeedLimit);
        SpeedLimit obstacleLimit = index == activeLeaderIndex(train)
                ? frontMinecartSpeedLimit(train, cart, location, direction, desiredTrainSpeed)
                : new SpeedLimit(desiredTrainSpeed, false);
        VehicleProfile vehicle = plugin.vehicleProfile();
        boolean speedController = index == activeLeaderIndex(train) && !train.speedControlledRecently(now);
        double speed;
        if (speedController && stationMoving && !controlledStop) {
            speed = Math.min(obstacleLimit.speed,
                    stationMotion.commandedSpeed(signActions.stationMotionMinimumSpeed(stationMotion)));
            train.seedCurrentSpeed(speed);
        } else if (speedController && (train.driveControlEnabled || automaticHandle)) {
            boolean tractionAllowed = !controlledStop
                    && train.moving
                    && train.reverser != Reverser.NEUTRAL
                    && train.reverser.wantsBackward() == train.reversed
                    && train.powerNotch > 0
                    && train.brakeNotch == 0
                    && !train.emergencyBrake
                    && train.currentSpeed() <= obstacleLimit.speed + 0.001;
            if (vehicle.forceMode()) {
                speed = train.updateDrivenForceSpeed(
                        now,
                        obstacleLimit.speed,
                        vehicle.tractionForce(train.powerNotch),
                        vehicle.brakeForce(train.brakeNotch),
                        vehicle.mass(),
                        vehicle.rollingForce(),
                        vehicle.airForceFactor(),
                        vehicle.grade(),
                        direction.getY(),
                        vehicle.baseSpeed(),
                        vehicle.weakeningSpeed(),
                        vehicle.minimumRatio(),
                        vehicle.autoDeceleration(),
                        vehicle.emergencyForce(),
                        obstacleLimit.emergencyBrake || settlingReverse,
                        tractionAllowed);
            } else {
                speed = train.updateDrivenSpeed(
                        now,
                        obstacleLimit.speed,
                        vehicle.powerAcceleration(train.powerNotch),
                        vehicle.brakeAcceleration(train.brakeNotch),
                        vehicle.rolling(),
                        vehicle.air(),
                        vehicle.autoDeceleration(),
                        vehicle.autoEmergency(),
                        obstacleLimit.emergencyBrake || settlingReverse,
                        tractionAllowed);
            }
        } else if (speedController) {
            speed = train.updateCurrentSpeed(
                    now,
                    obstacleLimit.speed,
                    vehicle.autoAcceleration(),
                    playerPushCoasting ? settings.playerPushDecelerationPerTick() : vehicle.autoDeceleration(),
                    vehicle.autoEmergency(),
                    obstacleLimit.emergencyBrake || settlingReverse);
            if (playerPushCoasting && speed <= 0.001) {
                train.clearPlayerPush();
            }
        } else {
            speed = train.currentSpeed();
        }

        if (index == activeLeaderIndex(train)) {
            audio.playTracksideRunningSound(train, cart, speed, currentTick);
            audio.playBrakeSound(train, cart, now);
        }

        if (index == activeLeaderIndex(train) && !train.targetLayoutUpdatedRecently(now)) {
            layoutTrainTargets(train, location, direction, speed, now, currentTick);
        }

        TrainMemberTarget target = train.memberTarget(entityId, currentTick);
        if (target == null || now - target.updatedAtMillis > 1000L) {
            if (settings.trainPhysicsHardLock()) {
                cart.setVelocity(new Vector());
                train.memberSpeed(entityId, 0.0);
                train.snapshot(entityId, new MemberSnapshot(entityId, location, new Vector(), now));
                return;
            }
            Vector velocity = direction.lengthSquared() < 0.0001 || speed <= 0.001
                    ? new Vector()
                    : direction.clone().multiply(speed);
            cart.setVelocity(velocity);
            train.memberSpeed(entityId, speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, location, velocity, now));
            return;
        }

        actuator.applyTrainTarget(train, cart, entityId, target, location, now);
    }

    void layoutTrainTargets(Train train, Location leaderLocation, Vector direction, double speed,
            long now, long currentTick) {
        List<UUID> members = train.members();
        if (members.isEmpty()) {
            return;
        }

        double consistLength = Math.max(0.0, train.spacing * (members.size() - 1));
        TrainRailPath trackPath = train.trackPath();
        if (trackPath == null || !trackPath.isCompatible(leaderLocation, train.reversed, consistLength)) {
            trackPath = TrainRailPath.create(leaderLocation, direction, train.reversed, consistLength);
            if (trackPath == null) {
                train.seedCurrentSpeed(0.0);
                return;
            }
            train.trackPath(trackPath);
        }

        double elapsedTicks = train.beginMotionFrame(now);
        double plannedDistance = speed * elapsedTicks;
        AutomaticRun automaticRun = train.automaticRun;
        if (automaticRun != null && automaticRun.phase == AutomaticRun.Phase.APPROACH) {
            plannedDistance = Math.min(plannedDistance, automaticRun.remaining);
        }
        double movedThisFrame = 0.0;
        StationMotion stationMotion = train.stationMotion();
        if (stationMotion != null && stationMotion.isMoving()) {
            plannedDistance = Math.min(plannedDistance, stationMotion.remainingDistance());
        }
        if (speed > 0.001 && !switchManager.prepareTrain(
                train, trackPath, leaderLocation, plannedDistance)) {
            train.seedCurrentSpeed(0.0);
            speed = 0.0;
        }
        if (speed > 0.001) {
            if (!trackPath.move(plannedDistance, train.reversed)) {
                train.seedCurrentSpeed(0.0);
                speed = 0.0;
            }
            movedThisFrame = trackPath.lastMoveDistance();
            if (automaticRun != null && train.automaticRun == automaticRun) {
                automaticRun.moved(movedThisFrame);
                if (automaticRun.phase == AutomaticRun.Phase.APPROACH && automaticRun.remaining <= 0.02) {
                    automaticRun.arrived(now);
                    train.seedCurrentSpeed(0);
                    speed = 0;
                }
            }
            infrastructureManager.observeTrainMovement(
                    train, trackPath.lastMoveProbes(), trackPath.lastMoveDistance());
            if (stationMotion != null && train.stationMotion() == stationMotion) {
                StationMotion.AdvanceResult result = stationMotion.advance(trackPath.lastMoveDistance(), now);
                if (result == StationMotion.AdvanceResult.DOCKED) {
                    train.seedCurrentSpeed(0.0);
                    train.pauseUntilMillis = stationMotion.dwellUntilMillis();
                    speed = 0.0;
                } else if (result == StationMotion.AdvanceResult.COMPLETE) {
                    train.clearStationMotion(stationMotion);
                    train.pauseUntilMillis = 0L;
                }
            }
        } else {
            infrastructureManager.observeTrainMovement(
                    train, trackPath.probeAhead(0.0, train.reversed), 0.0);
        }
        Map<UUID, TrainMemberTarget> targets = new LinkedHashMap<>();
        List<TrainRailPath.MemberPlacement> placements = trackPath.placements(
                members.size(), train.spacing, train.reversed);
        switchManager.observeTrainLayout(train, placements, now);
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            TrainRailPath.MemberPlacement placement = placements.get(memberIndex);
            Location targetLocation = placement.location();
            Vector targetDirection = placement.direction();
            targetLocation.setYaw(TrainMemberActuator.yawFromDirection(targetDirection));
            targetLocation.setPitch(0.0F);
            targets.put(members.get(memberIndex),
                    new TrainMemberTarget(targetLocation, targetDirection, speed, now));
        }
        train.publishMotionFrame(targets, now, currentTick);
        if (plugin.displaySync() != null) plugin.displaySync().publish(train.id(), targets);
        if (speed > 0.001) {
            int leaderIndex = activeLeaderIndex(train);
            train.rememberDirection(placements.get(leaderIndex).direction());
        }
        if (plugin.getConfig().getBoolean("settings.stcs-telemetry-enabled", true)) {
            long publishTicks = Math.max(1L,
                    plugin.getConfig().getLong("settings.stcs-telemetry-publish-interval-ticks", 5L));
            long reconcileTicks = Math.max(20L,
                    plugin.getConfig().getLong("settings.stcs-telemetry-reconcile-interval-ticks",
                            plugin.getConfig().getLong("settings.stcs-telemetry-heartbeat-ticks", 100L)));
            stcsTelemetry.observe(train, host.driverName(train), movedThisFrame,
                    now, publishTicks * 50L, reconcileTicks * 50L);
        }
    }

    void applyReverserDirection(Train train, long now) {
        if (!train.driveControlEnabled || train.reverser == Reverser.NEUTRAL) {
            return;
        }
        boolean desiredReversed = train.reverser.wantsBackward();
        if (train.reversed == desiredReversed) {
            return;
        }
        if (Math.max(train.currentSpeed(), train.maxMemberSpeed()) > settings.driveDirectionChangeSpeed()) {
            return;
        }

        train.reversed = desiredReversed;
        host.refreshMemberIndexes(train);
        train.reverseRememberedDirection();
        train.reverseMileageDirection();
        train.clearSnapshots();
        train.clearMemberSpeeds();
        train.clearMemberTargets();
        train.seedCurrentSpeed(0.0);
        train.reverseSettleUntilMillis = Math.max(train.reverseSettleUntilMillis, now + settings.reverseSettleTicks() * 50L);
    }

    boolean reverseBraking(Train train, long now) {
        if (!train.reversePending && train.reverseSettleUntilMillis <= 0L && train.reverseBrakeDeadlineMillis <= 0L) {
            return false;
        }
        if (now < train.reverseSettleUntilMillis) {
            return true;
        }
        if (now < train.reverseBrakeDeadlineMillis
                && Math.max(train.currentSpeed(), train.maxMemberSpeed()) > settings.reverseReleaseSpeed()) {
            return true;
        }

        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        if (train.reversePending) {
            train.reversePending = false;
            train.reversed = !train.reversed;
            host.refreshMemberIndexes(train);
            train.reverseRememberedDirection();
            train.reverseMileageDirection();
            train.clearSnapshots();
            train.clearMemberSpeeds();
            train.clearMemberTargets();
            train.seedCurrentSpeed(0.0);
            return true;
        }
        return false;
    }

    SpeedLimit frontMinecartSpeedLimit(Train train, Minecart cart, Location location, Vector direction,
            double desiredSpeed) {
        if (!settings.frontMinecartDetectionEnabled() || desiredSpeed <= 0.001 || direction.lengthSquared() < 0.0001) {
            return new SpeedLimit(desiredSpeed, false);
        }

        Vector forward = direction.clone().setY(0.0);
        if (forward.lengthSquared() < 0.0001) {
            return new SpeedLimit(desiredSpeed, false);
        }
        forward.normalize();

        double detectionDistance = settings.frontMinecartDetectionDistance();
        double stopDistance = Math.min(settings.frontMinecartStopDistance(), detectionDistance - 0.1);
        double lateralDistance = settings.frontMinecartLateralDistance();
        double closest = Double.MAX_VALUE;

        for (Entity entity : cart.getNearbyEntities(detectionDistance, 2.0, detectionDistance)) {
            if (!(entity instanceof Minecart other) || !other.isValid() || other.isDead()) {
                continue;
            }
            if (other.getUniqueId().equals(cart.getUniqueId()) || train.contains(other.getUniqueId())
                    || host.isSameTrain(cart, other)) {
                continue;
            }
            if (!other.getWorld().equals(cart.getWorld())) {
                continue;
            }

            Vector offset = other.getLocation().toVector().subtract(location.toVector());
            if (Math.abs(offset.getY()) > 1.75) {
                continue;
            }
            Vector flat = offset.clone().setY(0.0);
            double aheadDistance = flat.dot(forward);
            if (aheadDistance <= 0.0 || aheadDistance > detectionDistance) {
                continue;
            }
            double lateralSquared = Math.max(0.0, flat.lengthSquared() - aheadDistance * aheadDistance);
            if (lateralSquared > lateralDistance * lateralDistance) {
                continue;
            }
            closest = Math.min(closest, aheadDistance);
        }

        if (closest == Double.MAX_VALUE) {
            return new SpeedLimit(desiredSpeed, false);
        }
        if (closest <= stopDistance) {
            return new SpeedLimit(0.0, true);
        }

        double range = Math.max(0.1, detectionDistance - stopDistance);
        double ratio = RailMath.clamp((closest - stopDistance) / range, 0.0, 1.0);
        boolean emergencyBrake = closest <= stopDistance + 0.75;
        return new SpeedLimit(Math.min(desiredSpeed, desiredSpeed * ratio), emergencyBrake);
    }

    double coupledMemberSpeed(Train train, double baseSpeed, MemberSnapshot target, double followDistance,
            boolean controlledStop) {
        if (target == null || followDistance < 0.0) {
            return baseSpeed;
        }

        double spacing = Math.max(0.1, train.spacing);
        double targetSpeed = TrainMemberActuator.horizontalSpeed(target.velocity);
        double hardStopDistance = spacing * settings.coupledHardStopRatio();

        if (followDistance <= hardStopDistance) {
            return 0.0;
        }
        if (controlledStop || train.reversePending) {
            return RailMath.clamp(Math.min(baseSpeed, targetSpeed + settings.coupledBrakeSpeedBuffer()), 0.0, train.maxSpeed);
        }

        return RailMath.clamp(baseSpeed, 0.0, train.maxSpeed);
    }

    int activeLeaderIndex(Train train) {
        return train.reversed ? Math.max(0, train.memberCount() - 1) : 0;
    }

    void applyForcedSpacing(Train train) {
        if (settings.forceTightSpacing()) {
            train.spacing = settings.tightSpacing();
        }
    }

    static boolean sameWorld(MemberSnapshot snapshot, Location location) {
        return location.getWorld() != null && snapshot.worldName.equals(location.getWorld().getName());
    }

    private static final class SpeedLimit {
        private final double speed;
        private final boolean emergencyBrake;
        private SpeedLimit(double speed, boolean emergencyBrake) {
            this.speed = speed;
            this.emergencyBrake = emergencyBrake;
        }
    }
}
