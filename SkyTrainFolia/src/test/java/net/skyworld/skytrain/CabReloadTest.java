package net.skyworld.skytrain;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class CabReloadTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var cab = new CabUiManager(null, null, null, null);
        UUID driver = UUID.randomUUID(), hotbarOnly = UUID.randomUUID(), passenger = UUID.randomUUID();
        Set<UUID> hotbar = field(cab, "hotbarDrivers");
        Map<UUID, Long> swings = field(cab, "reverserSwingTicks");
        Set<UUID> pending = field(cab, "pendingSidebarRefreshes");
        Map<UUID, Object> sessions = field(cab, "sessions");
        Class<?> sessionType = Class.forName(CabUiManager.class.getName() + "$CabSession");
        var constructor = sessionType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object oldSession = constructor.newInstance(driver, UUID.randomUUID(), null, null, null, null, null);
        sessions.put(driver, oldSession);
        sessions.put(passenger, constructor.newInstance(passenger, UUID.randomUUID(), null, null, null, null, null));
        hotbar.add(driver);
        hotbar.add(hotbarOnly);
        swings.put(driver, 123L);
        pending.add(driver);
        long before = field(cab, "sessionGeneration");
        var reset = cab.detachForReload(Set.of(driver));
        assert reset.sessions().size() == 2;
        assert reset.drivers().equals(Set.of(driver, hotbarOnly)) : "Notify control holders, not passengers";
        assert sessions.isEmpty() && hotbar.isEmpty() && swings.isEmpty() && pending.isEmpty();
        assert (long) field(cab, "sessionGeneration") != before : "Queued activations must be invalidated";
        Object fresh = constructor.newInstance(driver, UUID.randomUUID(), null, null, null, null, null);
        sessions.put(driver, fresh);
        assert !sessions.remove(driver, oldSession) : "Old retirement must not remove a new session";
        assert sessions.get(driver) == fresh;
        assert cab.detachForReload(Set.of()).drivers().isEmpty() : "Hotbar does not silently return";

        // Model an already-detached train: shutdown still must remove every lease/index/task.
        var drivers = new DriverControlService(null, new Object(), ignored -> null, ignored -> null, () -> {});
        UUID train = UUID.randomUUID(), lease = UUID.randomUUID();
        CabReloadTest.<Map<UUID, UUID>>field(drivers, "driverTargets").put(driver, train);
        CabReloadTest.<Map<UUID, UUID>>field(drivers, "trainDrivers").put(train, driver);
        CabReloadTest.<Map<UUID, UUID>>field(drivers, "driverSeats").put(driver, UUID.randomUUID());
        CabReloadTest.<Map<UUID, UUID>>field(drivers, "driverLeaseIds").put(driver, lease);
        CabReloadTest.<Map<UUID, String>>field(drivers, "driverNames").put(driver, "driver");
        AtomicInteger cancelled = new AtomicInteger();
        ScheduledTask task = (ScheduledTask) Proxy.newProxyInstance(ScheduledTask.class.getClassLoader(),
                new Class<?>[] {ScheduledTask.class}, (self, method, arguments) -> {
                    if (!method.getName().equals("cancel")) throw new AssertionError(method.getName());
                    cancelled.incrementAndGet();
                    return null;
                });
        CabReloadTest.<Map<UUID, ScheduledTask>>field(drivers, "driverChecks").put(driver, task);
        drivers.shutdown();
        for (String name : new String[] {"driverTargets", "trainDrivers", "driverSeats", "driverLeaseIds", "driverNames", "driverChecks"})
            assert CabReloadTest.<Map<?, ?>>field(drivers, name).isEmpty() : name;
        assert cancelled.get() == 1 && drivers.driverDesks().isEmpty();
        drivers.revokeDriverLease(driver, lease);
        assert cancelled.get() == 1 : "Late retirement callback is harmless";
        System.out.println("PASS reload cab/hotbar reset, generation invalidation, notification roster, new-session retention and driver lease cleanup");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }
}
