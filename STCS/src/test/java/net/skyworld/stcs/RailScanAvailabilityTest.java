package net.skyworld.stcs;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;

public final class RailScanAvailabilityTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        AtomicBoolean loaded = new AtomicBoolean(false), owned = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger(), ownershipChecks = new AtomicInteger();
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getLogger" -> Logger.getLogger("RailScanAvailabilityTest");
            case "getName", "getVersion", "getBukkitVersion" -> "test";
            case "isOwnedByCurrentRegion" -> { ownershipChecks.incrementAndGet(); yield owned.get(); }
            default -> throw new AssertionError("Unexpected server access: " + m.getName());
        });
        // Avoid setServer's full game-version bootstrap in this isolated JVM test.
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        World world = proxy(World.class, (p, m, a) -> {
            if (m.getName().equals("isChunkLoaded")) return loaded.get();
            throw new AssertionError("Unexpected world access: " + m.getName());
        });
        Location location = new Location(world, 429, -58, -7);
        Rail rail = proxy(Rail.class, (p, m, a) -> {
            if (m.getName().equals("getShape")) return Rail.Shape.EAST_WEST;
            throw new AssertionError("Unexpected rail access: " + m.getName());
        });
        Block block = proxy(Block.class, (p, m, a) -> switch (m.getName()) {
            case "getLocation" -> location;
            case "getBlockData" -> { reads.incrementAndGet(); yield rail; }
            default -> throw new AssertionError("Unexpected block access: " + m.getName());
        });
        expectUnloaded(() -> RailGeometry.shape(block));
        assert reads.get() == 0 && ownershipChecks.get() == 0;
        loaded.set(true);
        assert RailGeometry.shape(block) == Rail.Shape.EAST_WEST;
        assert reads.get() == 1;
        // Simulate unload after an earlier scheduling preflight.
        loaded.set(false);
        expectUnloaded(() -> RailGeometry.requireAvailable(location));
        assert reads.get() == 1;
        loaded.set(true); owned.set(false);
        boolean wrongOwner = false;
        try { RailGeometry.shape(block); }
        catch (RailGeometry.ChunkUnavailableException ex) { throw new AssertionError("Ownership was silenced", ex); }
        catch (IllegalStateException expected) { wrongOwner = true; }
        assert wrongOwner && reads.get() == 1;
        owned.set(true);
        assert RailGeometry.shape(block) == Rail.Shape.EAST_WEST;
        assert reads.get() == 2;
        assert RailGeometry.shape(null) == null;
        System.out.println("PASS unloaded/race/reload guards; no world reads or loads while unavailable; ownership errors distinct");
    }

    private static void expectUnloaded(Runnable action) {
        boolean caught = false;
        try { action.run(); } catch (RailGeometry.ChunkUnavailableException expected) { caught = true; }
        assert caught;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }
}
