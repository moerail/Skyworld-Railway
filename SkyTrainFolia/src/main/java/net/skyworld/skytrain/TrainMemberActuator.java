package net.skyworld.skytrain;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import io.papermc.paper.entity.TeleportFlag;

/** Applies targets on the owning entity thread and guards in-flight asynchronous relocation. */
final class TrainMemberActuator {
    private final SkyTrainPlugin plugin;
    private final TrainSettings settings;
    private final ConcurrentMap<UUID, CompletableFuture<Boolean>> pendingTeleports = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PassengerRecovery> passengerRecoveries = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.LongAdder passengerRecoveryTeleports = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedMoves = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedPassengerMoves = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder ownedMoveFallbacks = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder physicalTeleports = new java.util.concurrent.atomic.LongAdder();
    private volatile boolean ownedMoverFailed;

    TrainMemberActuator(SkyTrainPlugin plugin, TrainSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    String motionSyncStatus() {
        return (plugin.displaySync() == null ? "display=vanilla" : plugin.displaySync().status())
                + ", passenger-recoveries=" + passengerRecoveryTeleports.sum()
                + ", owned-moves=" + ownedMoves.sum() + ", owned-passenger-moves=" + ownedPassengerMoves.sum()
                + ", owned-fallbacks=" + ownedMoveFallbacks.sum() + ", physical-teleports=" + physicalTeleports.sum()
                + ", pending-teleports=" + pendingTeleports.size();
    }

    void forgetDisplay(UUID entityId) {
        passengerRecoveries.remove(entityId);
        if (plugin.displaySync() != null) plugin.displaySync().forget(entityId);
    }

    void applyTrainTarget(Train train, Minecart cart, UUID entityId, TrainMemberTarget target,
            Location currentLocation, long now) {
        if (currentLocation.getWorld() == null || !currentLocation.getWorld().equals(target.location.getWorld())) {
            return;
        }

        Vector trackVelocity = target.direction.lengthSquared() < 0.0001
                ? new Vector()
                : target.direction.clone().normalize().multiply(target.speed);
        if (settings.trainPhysicsHardLock() && tryOwnedMove(cart, target.location)) {
            passengerRecoveries.remove(entityId);
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, cart.getLocation(), trackVelocity, now));
            return;
        }
        if (hasPlayerPassenger(cart) && (settings.trainPhysicsHardLock() || settings.passengerSmoothingEnabled())) {
            applyPassengerTarget(train, cart, entityId, target, currentLocation, trackVelocity, now);
            return;
        }
        if (settings.trainPhysicsHardLock()) {
            Location lockedLocation = target.location.clone();
            boolean completed = teleportTrainMember(cart, lockedLocation, false);
            if (completed) cart.setVelocity(new Vector());
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId,
                    completed ? cart.getLocation() : currentLocation, trackVelocity, now));
            return;
        }

        double distance = currentLocation.distance(target.location);
        if (distance > settings.trainPhysicsTeleportDistance() || target.speed <= 0.001) {
            try {
                cart.teleport(target.location);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to move train member to rail target.", ex);
            }
            cart.setVelocity(new Vector());
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId, target.location, new Vector(), now));
            return;
        }

        Vector velocity = trackVelocity;
        Vector correction = target.location.toVector().subtract(currentLocation.toVector())
                .multiply(settings.trainPhysicsPositionCorrection());
        double maxCorrection = settings.trainPhysicsMaxCorrectionPerTick();
        correction.setX(RailMath.clamp(correction.getX(), -maxCorrection, maxCorrection));
        correction.setY(RailMath.clamp(correction.getY(), -maxCorrection, maxCorrection));
        correction.setZ(RailMath.clamp(correction.getZ(), -maxCorrection, maxCorrection));
        velocity.add(correction);

        cart.setVelocity(velocity);
        train.memberSpeed(entityId, horizontalSpeed(velocity));
        train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, velocity, now));
    }

    void applyPassengerTarget(Train train, Minecart cart, UUID entityId, TrainMemberTarget target,
            Location currentLocation, Vector trackVelocity, long now) {
        // Zero commanded speed is a hold, not permission to drive backwards toward an unreachable
        // arc target. This also prevents recovery teleports after EB has already stopped the train.
        if (target.speed <= 0.001) {
            passengerRecoveries.remove(entityId);
            cart.setMaxSpeed(0.0);
            cart.setVelocity(new Vector());
            train.memberSpeed(entityId, 0.0);
            train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, new Vector(), now));
            return;
        }
        double distance = currentLocation.distance(target.location);
        PassengerRecovery recovery = passengerRecoveries.computeIfAbsent(entityId, ignored -> new PassengerRecovery());
        boolean guarded = plugin.getConfig().getBoolean("settings.passenger-recovery-guard-enabled", true);
        double hardLimit = Math.max(settings.passengerTeleportDistance(),
                plugin.getConfig().getDouble("settings.passenger-recovery-hard-distance", 4.0));
        double softLimit = Math.min(hardLimit * 0.75,
                Math.max(settings.passengerTeleportDistance(), target.speed * 2.0));
        long graceNanos = Math.max(0L, Math.min(40L,
                plugin.getConfig().getLong("settings.passenger-recovery-grace-ticks", 6L))) * 50_000_000L;
        boolean recover = guarded
                ? recovery.shouldRecover(distance, softLimit, hardLimit, graceNanos, System.nanoTime())
                : distance > settings.passengerTeleportDistance();
        if (recover) {
            recovery.reset();
            passengerRecoveryTeleports.increment();
            boolean completed = teleportTrainMember(cart, target.location, true);
            Vector compensatedVelocity = trackVelocity.clone().multiply(1.0 / settings.passengerVanillaMotionFactor());
            if (completed) cart.setVelocity(compensatedVelocity);
            train.memberSpeed(entityId, target.speed);
            train.snapshot(entityId, new MemberSnapshot(entityId,
                    completed ? cart.getLocation() : currentLocation, compensatedVelocity, now));
            return;
        }

        Vector correction = target.location.toVector().subtract(currentLocation.toVector())
                .multiply(settings.passengerPositionCorrection());
        double maxCorrection = settings.passengerMaxCorrectionPerTick();
        correction.setX(RailMath.clamp(correction.getX(), -maxCorrection, maxCorrection));
        correction.setY(RailMath.clamp(correction.getY(), -maxCorrection, maxCorrection));
        correction.setZ(RailMath.clamp(correction.getZ(), -maxCorrection, maxCorrection));

        // The previous per-axis cap allowed sqrt(3) times the configured correction on diagonals.
        if (guarded && correction.lengthSquared() > maxCorrection * maxCorrection) {
            correction.normalize().multiply(maxCorrection);
        }

        Vector velocity = trackVelocity.clone().add(correction);
        if (velocity.lengthSquared() > 0.0000001) {
            velocity.multiply(1.0 / settings.passengerVanillaMotionFactor());
        }
        cart.setVelocity(velocity);
        train.memberSpeed(entityId, target.speed);
        train.snapshot(entityId, new MemberSnapshot(entityId, currentLocation, velocity, now));
    }

    boolean tryOwnedMove(Minecart cart, Location target) {
        if (ownedMoverFailed || !plugin.getConfig().getBoolean("settings.owned-region-movement-enabled", true)
                || !Bukkit.getMinecraftVersion().equals("26.2")) return false;
        try {
            boolean passenger = hasPlayerPassenger(cart);
            if (OwnedTrainMover.move(cart, target, passenger)) {
                ownedMoves.increment();
                if (passenger) ownedPassengerMoves.increment();
                return true;
            }
            ownedMoveFallbacks.increment();
        } catch (RuntimeException | LinkageError ex) {
            ownedMoverFailed = true;
            plugin.getLogger().log(Level.WARNING,
                    "Owned-region movement disabled after failure; retaining original Folia relocation path.", ex);
        }
        return false;
    }

    boolean teleportTrainMember(Minecart cart, Location target, boolean preserveView) {
        UUID entityId = cart.getUniqueId();
        CompletableFuture<Boolean> pending = pendingTeleports.get(entityId);
        if (pending != null && !pending.isDone()) {
            return false;
        }
        if (pending != null) {
            pendingTeleports.remove(entityId, pending);
        }

        Location destination = target.clone();
        physicalTeleports.increment();
        if (preserveView) {
            destination.setYaw(cart.getYaw());
            destination.setPitch(cart.getPitch());
        }

        try {
            if (cart.teleport(destination, TeleportFlag.EntityState.RETAIN_PASSENGERS)) {
                return true;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.FINEST,
                    "Synchronous train-member teleport crossed a region boundary; using async teleport.", ex);
        }

        CompletableFuture<Boolean> reservation = new CompletableFuture<>();
        if (pendingTeleports.putIfAbsent(entityId, reservation) != null) {
            return false;
        }
        try {
            cart.teleportAsync(destination, TeleportFlag.EntityState.RETAIN_PASSENGERS)
                    .whenComplete((success, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.FINE,
                                    "Failed to move train member " + entityId + " across a region boundary.", error);
                            reservation.completeExceptionally(error);
                        } else {
                            reservation.complete(Boolean.TRUE.equals(success));
                        }
                        pendingTeleports.remove(entityId, reservation);
                    });
        } catch (RuntimeException ex) {
            pendingTeleports.remove(entityId, reservation);
            reservation.completeExceptionally(ex);
            plugin.getLogger().log(Level.FINE, "Failed to schedule train-member teleport.", ex);
        }
        return false;
    }

    Vector bindVelocityToRail(Minecart cart, RailInfo rail, Location location, Vector velocity, boolean stopped) {
        if (!settings.railBindEnabled() || rail == null || location == null) {
            return velocity;
        }

        double centerX = rail.block.getX() + 0.5;
        double centerZ = rail.block.getZ() + 0.5;
        double offsetX = centerX - location.getX();
        double offsetZ = centerZ - location.getZ();
        double distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
        if (distanceSquared < 0.0004) {
            return velocity;
        }
        if (stopped) {
            return new Vector();
        }

        double teleportDistance = settings.railBindTeleportDistance();
        if (distanceSquared > teleportDistance * teleportDistance) {
            Location target = location.clone();
            target.setX(centerX);
            target.setZ(centerZ);
            target.setYaw(yawFromDirection(velocity.lengthSquared() < 0.0001 ? trainDirectionFallback(cart) : velocity));
            try {
                cart.teleportAsync(target);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.FINE, "Failed to bind minecart to rail center.", ex);
            }
            return velocity;
        }

        double strength = settings.railBindStrength();
        double maxCorrection = settings.railBindMaxCorrectionPerTick();
        double correctionX = RailMath.clamp(offsetX * strength, -maxCorrection, maxCorrection);
        double correctionZ = RailMath.clamp(offsetZ * strength, -maxCorrection, maxCorrection);
        return velocity.clone().add(new Vector(correctionX, 0.0, correctionZ));
    }

    static void removeNonPlayerPassengers(Minecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (!(passenger instanceof Player)) {
                cart.removePassenger(passenger);
            }
        }
    }

    static boolean hasPlayerPassenger(Minecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    static Vector trainDirectionFallback(Minecart cart) {
        Vector velocity = cart.getVelocity();
        if (velocity.lengthSquared() > 0.0001) {
            return velocity;
        }
        return RailMath.yawDirection(cart.getLocation().getYaw());
    }

    static double horizontalSpeed(Vector velocity) {
        if (velocity == null) {
            return 0.0;
        }
        return Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
    }

    void scheduleTeleportAndStop(Minecart cart, Location target) {
        try {
            cart.getScheduler().run(
                    plugin,
                    task -> {
                        if (!cart.isValid() || cart.isDead()) {
                            return;
                        }

                        cart.teleportAsync(target).whenComplete((success, error) -> {
                            if (error != null) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Failed to arrange train cart " + cart.getUniqueId(), error);
                                return;
                            }
                            if (!Boolean.TRUE.equals(success)) {
                                return;
                            }
                            try {
                                cart.getScheduler().run(
                                        plugin,
                                        followUp -> {
                                            if (cart.isValid() && !cart.isDead()) {
                                                cart.setVelocity(new Vector());
                                            }
                                        },
                                        () -> {
                                        });
                            } catch (RuntimeException ex) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Failed to stop arranged train cart " + cart.getUniqueId(), ex);
                            }
                        });
                    },
                    () -> {
                    });
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to arrange train cart " + cart.getUniqueId(), ex);
        }
    }

    static float yawFromDirection(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    boolean relocationPending(UUID entityId) {
        CompletableFuture<Boolean> pending = pendingTeleports.get(entityId);
        return pending != null && !pending.isDone();
    }

    void forgetPending(UUID entityId) { pendingTeleports.remove(entityId); }

    void clearRecoveries() { passengerRecoveries.clear(); }

    void clearPending() { pendingTeleports.clear(); }
}
