package net.skyworld.skytrain;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Applies driving inputs and control-mode rules without owning player leases or entity tasks. */
final class TrainDrivingControls {
    private final TrainSettings settings;
    private final Object driverLock;
    private final Function<String, Train> trainLookup;
    private final Runnable saveAction;
    private final Consumer<Train> refreshIndexes;

    TrainDrivingControls(TrainSettings settings, Object driverLock,
            Function<String, Train> trainLookup, Runnable saveAction, Consumer<Train> refreshIndexes) {
        this.settings = settings;
        this.driverLock = driverLock;
        this.trainLookup = trainLookup;
        this.saveAction = saveAction;
        this.refreshIndexes = refreshIndexes;
    }

    void start(String name, Double speed) {
        Train train = trainLookup.apply(name);
        train.driverEmergencyHold = false;
        startTrain(train, speed);
        saveAction.run();
    }

    void stop(String name) {
        Train train = trainLookup.apply(name);
        stopTrain(train);
        saveAction.run();
    }

    void reverse(String name) {
        Train train = trainLookup.apply(name);
        reverseTrain(train, System.currentTimeMillis());
        saveAction.run();
    }

    void speed(String name, double speed) {
        Train train = trainLookup.apply(name);
        train.targetSpeed = RailMath.clamp(speed, 0.0, train.maxSpeed);
        saveAction.run();
    }

    void setMaxSpeed(String name, double speed) {
        Train train = trainLookup.apply(name);
        setTrainMaxSpeed(train, speed);
        saveAction.run();
    }

    void setTrainMaxSpeed(Train train, double speed) {
        double newMaxSpeed = RailMath.clamp(speed, 0.05, settings.maxAllowedSpeed());
        double currentPhysicalSpeed = Math.max(train.currentSpeed(), train.maxMemberSpeed());
        train.maxSpeed = newMaxSpeed;
        train.targetSpeed = RailMath.clamp(train.targetSpeed, 0.0, train.maxSpeed);
        train.seedEffectiveMaxSpeed(train.moving || currentPhysicalSpeed > 0.001
                ? Math.max(0.05, Math.min(settings.maxAllowedSpeed(), currentPhysicalSpeed + 0.10))
                : newMaxSpeed);
    }

    void setSpacing(String name, double spacing) {
        Train train = trainLookup.apply(name);
        train.spacing = RailMath.clamp(spacing, 0.8, 8.0);
        saveAction.run();
    }

    void applyPlayerPush(Train train, Minecart cart, Player player) {
        double currentSpeed = train == null ? 0.0 : Math.max(train.currentSpeed(), train.maxMemberSpeed());
        if (train == null || cart == null || player == null || !train.properties().pushable
                || train.moving || train.driveControlEnabled
                || (!train.playerPushActive && currentSpeed > settings.playerPushActivationSpeed())) {
            return;
        }

        RailInfo rail = RailMath.findRail(cart.getLocation());
        if (rail == null) {
            return;
        }

        Vector playerVelocity = player.getVelocity().clone().setY(0.0);
        Vector pushPreference = playerVelocity.lengthSquared() > 0.0004
                ? playerVelocity
                : cart.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (pushPreference.lengthSquared() < 0.0001) {
            pushPreference = player.getLocation().getDirection().setY(0.0);
        }
        Vector direction = RailMath.direction(rail.rail.getShape(), pushPreference);
        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        Vector previousDirection = train.rememberedDirection();
        boolean oppositeDirection = previousDirection.lengthSquared() >= 0.0001
                && previousDirection.dot(direction) < 0.0;
        if (oppositeDirection && currentSpeed > settings.playerPushActivationSpeed()) {
            return;
        }
        if (oppositeDirection) {
            train.reversed = !train.reversed;
            train.reverseMileageDirection();
            refreshIndexes.accept(train);
        }

        double movementBonus = Math.min(settings.playerPushMovementBonus(), TrainMemberActuator.horizontalSpeed(playerVelocity));
        train.rememberDirection(direction);
        train.clearSnapshots();
        train.clearMemberSpeeds();
        train.clearMemberTargets();
        train.clearTrackPath();
        train.applyPlayerPush(settings.playerPushImpulse() + movementBonus, settings.playerPushMaxSpeed());
    }

    void setReverser(Train train, Reverser reverser) {
        Reverser target = reverser == null ? Reverser.NEUTRAL : reverser;
        if (target != Reverser.NEUTRAL
                && train.reverser != target
                && !canChangeReverser(train)) {
            throw new IllegalArgumentException("列车未停稳，不能切换换向器。");
        }
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.reverser = target;
        if (train.reverser == Reverser.NEUTRAL) {
            train.powerNotch = 0;
        }
        train.moving = true;
        saveAction.run();
    }

    boolean canChangeReverser(Train train) {
        return Math.max(train.currentSpeed(), train.maxMemberSpeed()) <= settings.driveDirectionChangeSpeed();
    }

    Reverser reverseReverserTarget(Train train) {
        if (train.reverser == Reverser.FORWARD) {
            return Reverser.BACKWARD;
        }
        if (train.reverser == Reverser.BACKWARD) {
            return Reverser.FORWARD;
        }
        return train.reversed ? Reverser.FORWARD : Reverser.BACKWARD;
    }

    Reverser cycleReverserTarget(Train train) {
        if (train.reverser != Reverser.NEUTRAL) {
            return Reverser.NEUTRAL;
        }
        return train.reversed ? Reverser.FORWARD : Reverser.BACKWARD;
    }

    void setPowerNotch(Train train, int notch) {
        train.protectionMode.requireTraction(train.operatingMode);
        if (train.driverEmergencyHold) throw new IllegalArgumentException("error.drive-declare");
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = Math.max(0, Math.min(4, notch));
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.moving = true;
        saveAction.run();
    }

    void setBrakeNotch(Train train, int notch) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = Math.max(0, Math.min(7, notch));
        train.emergencyBrake = false;
        train.moving = true;
        saveAction.run();
    }

    void neutralHandle(Train train) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.moving = true;
        saveAction.run();
    }

    void emergencyBrake(Train train) {
        takeManualControl(train);
        train.clearPlayerPush();
        train.driveControlEnabled = true;
        train.powerNotch = 0;
        train.brakeNotch = 7;
        train.emergencyBrake = true;
        train.moving = true;
        saveAction.run();
    }

    String driveStatus(Train train) {
        return "&e" + train.name()
                + " &7| 换向器: &f" + train.reverser.displayName()
                + " &7| 牵引: &fP" + train.powerNotch
                + " &7| 制动: &f" + (train.emergencyBrake ? "EB" : "B" + train.brakeNotch)
                + " &7| 速度: &f" + formatSpeed(Math.max(train.currentSpeed(), train.maxMemberSpeed()));
    }

    void takeManualControl(Train train) {
        synchronized (driverLock) {
            train.manualTakeover = true;
            train.manualReleaseConfirmed = false;
            cancelStationMotion(train);
        }
    }

    void confirmManualRelease(Train train) {
        if (train == null) return;
        synchronized (driverLock) { train.manualReleaseConfirmed = true; }
        saveAction.run();
    }

    static void authorizeAutomatic(Train train, boolean hasDriver, double stopThreshold) {
        if (hasDriver) throw new IllegalArgumentException("请先 /st release 释放司机控制权，再设置 auto。");
        if (train.manualTakeover && !train.manualReleaseConfirmed)
            throw new IllegalArgumentException("手动接管尚未明确释放。请先 /st release，或 /st admin release <列车名>。");
        if (Math.max(train.currentSpeed(), train.maxMemberSpeed()) > stopThreshold)
            throw new IllegalArgumentException("列车尚未停稳，不能启用 auto。");
        stopTrain(train);
        train.driverEmergencyHold = false;
        train.manualTakeover = false;
        train.reverser = train.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
        train.properties().conductionMode = ConductionMode.AUTOMATIC;
    }

    void startTrain(Train train, Double speed) {
        if (speed != null) {
            train.targetSpeed = RailMath.clamp(speed, 0.0, train.maxSpeed);
        }
        cancelStationMotion(train);
        train.driveControlEnabled = false;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.reverser = train.reversed ? Reverser.BACKWARD : Reverser.FORWARD;
        train.pauseUntilMillis = 0L;
        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        train.reversePending = false;
        train.clearPlayerPush();
        train.moving = true;
    }

    static void stopTrain(Train train) {
        cancelStationMotion(train);
        train.driveControlEnabled = false;
        train.powerNotch = 0;
        train.brakeNotch = 0;
        train.emergencyBrake = false;
        train.pauseUntilMillis = 0L;
        train.reverseSettleUntilMillis = 0L;
        train.reverseBrakeDeadlineMillis = 0L;
        train.reversePending = false;
        train.clearPlayerPush();
        train.moving = false;
    }

    static void cancelStationMotion(Train train) {
        train.automaticRun = null;
        train.clearStationMotion();
        train.pauseUntilMillis = 0L;
    }

    void reverseTrain(Train train, long now) {
        setReverser(train, reverseReverserTarget(train));
    }

    private static String formatSpeed(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
