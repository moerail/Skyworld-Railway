package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Owns driving leases and player tasks; uses the manager's shared driver/automatic-operation gate. */
final class DriverControlService {
    private final SkyTrainPlugin plugin;
    private final Object driverLock;
    private final Function<UUID, Train> trainLookup;
    private final Function<Minecart, Train> cartLookup;
    private final Runnable saveAction;
    private final ConcurrentMap<UUID, UUID> driverTargets = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> trainDrivers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> driverSeats = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> driverNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> driverLeaseIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScheduledTask> driverChecks = new ConcurrentHashMap<>();

    DriverControlService(SkyTrainPlugin plugin, Object driverLock, Function<UUID, Train> trainLookup,
            Function<Minecart, Train> cartLookup, Runnable saveAction) {
        this.plugin = plugin;
        this.driverLock = driverLock;
        this.trainLookup = trainLookup;
        this.cartLookup = cartLookup;
        this.saveAction = saveAction;
    }

    boolean setDrivingTarget(Player player, Train train) {
        if (player == null || train == null) {
            return false;
        }
        synchronized (driverLock) {
            UUID playerId = player.getUniqueId();
            if (!player.isOnline() || player.isDead() || !(player.getVehicle() instanceof Minecart seat)
                    || cartLookup.apply(seat) != train) throw new IllegalArgumentException("error.drive-board");
            if (trainLookup.apply(train.id()) != train) return false;
            UUID currentDriver = trainDrivers.get(train.id());
            if (currentDriver != null && !currentDriver.equals(playerId)) {
                Player currentPlayer = Bukkit.getPlayer(currentDriver);
                if (currentPlayer != null && currentPlayer.isOnline()) {
                    return false;
                }
                revokeDriver(currentDriver, null, "DISCONNECTED");
            }
            if (isDriver(player, train)) return true;
            revokeDriver(playerId, null, "SEAT_CHANGED");
            driverTargets.put(playerId, train.id());
            trainDrivers.put(train.id(), playerId);
            driverSeats.put(playerId, seat.getUniqueId());
            driverNames.put(playerId, player.getName());
            UUID leaseId = UUID.randomUUID();
            driverLeaseIds.put(playerId, leaseId);
            train.lastManualDriver = playerId;
            // A fresh declaration never inherits traction from an earlier driver/session.
            DriverSafety.brake(train);
            ScheduledTask check = player.getScheduler().runAtFixedRate(plugin, task -> {
                synchronized (driverLock) {
                    if (!DriverSafety.sameSession(leaseId, driverLeaseIds.get(playerId))) { task.cancel(); return; }
                    if (!isDriver(player, train)) revokeDriver(playerId, null, "SEAT_LOST");
                }
            }, () -> revokeDriverLease(playerId, leaseId), 1L, 1L);
            if (check == null) {
                revokeDriver(playerId, null, "DISCONNECTED");
                throw new IllegalArgumentException("error.drive-board");
            }
            driverChecks.put(playerId, check);
            publishDriverEvent(train, playerId, player.getName(), "DRIVER_ACQUIRED", "EXPLICIT_DRIVE");
            train.driverEmergencyHold = false;
            saveAction.run();
            return true;
        }
    }

    void clearDrivingTarget(Player player) {
        if (player != null) revokeDriver(player.getUniqueId(), null, "SEAT_LOST");
    }

    void revokeDriver(UUID playerId, UUID expectedSeat, String reason) {
        synchronized (driverLock) {
            if (expectedSeat != null && !expectedSeat.equals(driverSeats.get(playerId))) return;
            UUID trainId = driverTargets.remove(playerId);
            driverSeats.remove(playerId);
            cancelDriverCheck(playerId);
            String name = driverNames.remove(playerId);
            if (trainId == null || !trainDrivers.remove(trainId, playerId)) return;
            Train train = trainLookup.apply(trainId);
            if (train == null) return;
            DriverSafety.brake(train);
            publishDriverEvent(train, playerId, name, "DRIVER_UNAVAILABLE", reason);
            saveAction.run();
        }
    }

    void revokeDriverLease(UUID playerId, UUID leaseId) {
        synchronized (driverLock) {
            if (DriverSafety.sameSession(leaseId, driverLeaseIds.get(playerId))) revokeDriver(playerId, null, "DISCONNECTED");
        }
    }

    void cancelDriverCheck(UUID playerId) {
        driverLeaseIds.remove(playerId);
        ScheduledTask task = driverChecks.remove(playerId);
        if (task != null) task.cancel();
    }

    void publishDriverEvent(Train train, UUID driver, String name, String type, String reason) {
        plugin.getLogger().info(type + " train=" + train.name() + " driver=" + driver + " reason=" + reason);
        TelemetrySink sink = plugin.telemetrySink();
        if (sink != null) sink.driverEvent(train, driver, name, type, reason);
    }

    Player clearDriver(Train train) {
        if (train == null) {
            return null;
        }
        synchronized (driverLock) {
            UUID playerId = trainDrivers.remove(train.id());
            if (playerId == null) {
                return null;
            }
            driverTargets.remove(playerId, train.id());
            driverSeats.remove(playerId);
            cancelDriverCheck(playerId);
            String name = driverNames.remove(playerId);
            DriverSafety.brake(train);
            publishDriverEvent(train, playerId, name, "DRIVER_RELEASED", "ADMIN_RELEASE");
            return Bukkit.getPlayer(playerId);
        }
    }

    boolean isDriver(Player player, Train train) {
        if (player == null || train == null) return false;
        Entity seat = player.getVehicle();
        return DriverSafety.ownsSeat(trainDrivers.get(train.id()), player.getUniqueId(),
                driverSeats.get(player.getUniqueId()), seat == null ? null : seat.getUniqueId(),
                player.isOnline() && !player.isDead());
    }

    List<net.skyworld.sta.api.v4.DriverDeskService.Desk> driverDesks() {
        synchronized (driverLock) {
            List<net.skyworld.sta.api.v4.DriverDeskService.Desk> result = new ArrayList<>();
            trainDrivers.forEach((trainId, driverId) -> {
                Train train = trainLookup.apply(trainId);
                UUID lease = driverLeaseIds.get(driverId);
                if (train != null && lease != null) result.add(new net.skyworld.sta.api.v4.DriverDeskService.Desk(
                        trainId, driverId, lease, train.protectionMode.name()));
            });
            return List.copyOf(result);
        }
    }

    void requireDriver(Player player, Train train) {
        if (isDriver(player, train)) return;
        Train old = drivingTarget(player);
        if (old != null && !isDriver(player, old)) clearDrivingTarget(player);
        throw new IllegalArgumentException("error.drive-declare");
    }

    String driverName(Train train) {
        if (train == null) {
            return null;
        }
        synchronized (driverLock) {
            UUID playerId = trainDrivers.get(train.id());
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                if (playerId != null) {
                    revokeDriver(playerId, null, "DISCONNECTED");
                }
                return null;
            }
            return player.getName();
        }
    }

    Train drivingTarget(Player player) {
        if (player == null) {
            return null;
        }
        UUID trainId = driverTargets.get(player.getUniqueId());
        Train train = trainId == null ? null : trainLookup.apply(trainId);
        if (train == null && trainId != null) {
            driverTargets.remove(player.getUniqueId());
            driverSeats.remove(player.getUniqueId());
            cancelDriverCheck(player.getUniqueId());
            driverNames.remove(player.getUniqueId());
        }
        return train;
    }

    static boolean mayRelease(UUID requester, UUID driver, UUID lastDriver, boolean admin) {
        if (driver != null) return driver.equals(requester);
        return admin || requester.equals(lastDriver);
    }

    void releaseManualControl(Player player, Train train) {
        if (train == null) throw new IllegalArgumentException("没有找到要释放的列车，请靠近列车后重试。");
        synchronized (driverLock) {
            if (trainLookup.apply(train.id()) != train) throw new IllegalArgumentException("列车已不存在。");
            UUID playerId = player.getUniqueId();
            UUID driver = trainDrivers.get(train.id());
            if (!mayRelease(playerId, driver, train.lastManualDriver, player.hasPermission("skytrain.admin"))) {
                throw new IllegalArgumentException(driver != null
                        ? "这列车由其他司机控制；管理员请使用 /st admin release <列车名>。"
                        : "无法确认你是上一位司机；请重新 /st drive 后 /st release，或由管理员释放列车。");
            }
            DriverSafety.brake(train);
            train.manualReleaseConfirmed = true;
            trainDrivers.remove(train.id(), playerId);
            driverTargets.remove(playerId, train.id());
            driverSeats.remove(playerId);
            driverNames.remove(playerId);
            cancelDriverCheck(playerId);
            if (driver != null) publishDriverEvent(train, playerId, player.getName(), "DRIVER_RELEASED", "EXPLICIT_RELEASE");
        }
        saveAction.run();
    }

    UUID driverId(UUID trainId) { return trainDrivers.get(trainId); }

    boolean hasDriver(UUID trainId) { return trainDrivers.containsKey(trainId); }

    void removeTrainDriver(UUID trainId) { trainDrivers.remove(trainId); }

    void forgetTrainTargets(UUID trainId) {
        driverTargets.entrySet().removeIf(entry -> trainId.equals(entry.getValue()));
    }

    void shutdown() {
        synchronized (driverLock) {
            for (UUID driver : List.copyOf(driverTargets.keySet())) revokeDriver(driver, null, "SERVER_STOP");
            driverTargets.clear();
            trainDrivers.clear();
            driverSeats.clear();
            driverNames.clear();
            driverLeaseIds.clear();
            driverChecks.values().forEach(ScheduledTask::cancel);
            driverChecks.clear();
        }
    }
}
