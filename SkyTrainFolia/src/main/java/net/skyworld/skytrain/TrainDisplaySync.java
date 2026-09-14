package net.skyworld.skytrain;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftMinecart;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;

/** Does not schedule/move trains. All entity reads happen in their existing owning tasks. */
final class TrainDisplaySync implements Listener, AutoCloseable {
    final Map<UUID, TrainDisplayFrame> frames = new ConcurrentHashMap<>();
    final Map<UUID, Member> members = new ConcurrentHashMap<>();
    private final Map<UUID, TrainDisplayConnection> connections = new ConcurrentHashMap<>();
    private final SkyTrainPlugin plugin;
    private volatile boolean enabled;
    private volatile boolean closed;
    final long staleNanos;
    final double maxDeviation;
    final LongAdder suppressed = new LongAdder(), batches = new LongAdder(), fallbacks = new LongAdder();

    record Member(int id, UUID uuid, UUID train, UUID world, double x, double y, double z,
                  boolean newMinecart, long observedNanos) { }

    /** Protocol harness constructor; no Bukkit server, entity, or scheduler access. */
    TrainDisplaySync(long staleNanos, double maxDeviation) {
        plugin = null;
        this.staleNanos = staleNanos;
        this.maxDeviation = maxDeviation;
        enabled = true;
    }

    TrainDisplaySync(SkyTrainPlugin plugin) {
        this.plugin = plugin;
        staleNanos = Math.max(100L, Math.min(2000L,
                plugin.getConfig().getLong("settings.display-sync-stale-ms", 500L))) * 1_000_000L;
        maxDeviation = Math.max(1.0, Math.min(16.0,
                plugin.getConfig().getDouble("settings.display-sync-max-deviation", 8.0)));
        enabled = plugin.getConfig().getBoolean("settings.display-sync-enabled", true);
        if (enabled && !Bukkit.getMinecraftVersion().equals("26.2")) {
            enabled = false;
            plugin.getLogger().warning("Train display sync requires the tested 26.2 protocol; using vanilla sync.");
        }
        if (enabled) {
            TrainDisplayConnection.checkCompatibility();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            // Existing players are accessed on their own entity scheduler, not on the global thread.
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, task -> attach(player), null);
            }
            plugin.getLogger().info("Whole-train display sync active (26.2); existing Folia train tasks unchanged.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { attach(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        TrainDisplayConnection connection = connections.remove(event.getPlayer().getUniqueId());
        if (connection != null) connection.detach();
    }

    private void attach(Player player) {
        if (!enabled || closed) return;
        try {
            Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
            TrainDisplayConnection connection = new TrainDisplayConnection(this, channel);
            TrainDisplayConnection previous = connections.put(player.getUniqueId(), connection);
            if (previous != null) previous.detach();
            connection.attach();
        } catch (RuntimeException | LinkageError ex) { reportFailure(ex); }
    }

    /** Called only from tickMember, after validation, on this minecart's owning region. */
    void observe(UUID train, Minecart cart) {
        if (!enabled || closed) return;
        Location loc = cart.getLocation();
        boolean modern = ((CraftMinecart) cart).getHandle().getBehavior() instanceof NewMinecartBehavior;
        members.put(cart.getUniqueId(), new Member(cart.getEntityId(), cart.getUniqueId(), train,
                loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(), modern, System.nanoTime()));
    }

    /** Copies already computed targets; never looks up another cart/world/player. */
    void publish(UUID train, Map<UUID, TrainMemberTarget> targets) {
        if (!enabled || closed) return;
        long now = System.nanoTime();
        var carts = new ArrayList<TrainDisplayFrame.Cart>(targets.size());
        for (var entry : targets.entrySet()) {
            Member member = members.get(entry.getKey());
            Location target = entry.getValue().location;
            if (member == null || !member.train.equals(train) || now - member.observedNanos > staleNanos
                    || !member.world.equals(target.getWorld().getUID())) { return; }
            double dx = target.getX() - member.x, dy = target.getY() - member.y, dz = target.getZ() - member.z;
            if (dx * dx + dy * dy + dz * dz > maxDeviation * maxDeviation) { return; }
            var state = new TrainDisplayFrame.Cart(member.id, member.uuid, member.world,
                    target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch(),
                    entry.getValue().speed, member.newMinecart);
            if (!state.finite()) { return; }
            carts.add(state);
        }
        if (!carts.isEmpty()) frames.put(train, new TrainDisplayFrame(train, now, carts));
    }

    boolean isFresh(TrainDisplayFrame frame, long now) {
        if (closed || !enabled || frame == null || now - frame.createdNanos() > staleNanos) return false;
        for (var cart : frame.carts()) {
            Member member = members.get(cart.uuid());
            if (member == null || member.id != cart.id() || !member.train.equals(frame.trainId())
                    || !member.world.equals(cart.world()) || now - member.observedNanos > staleNanos) return false;
        }
        return true;
    }

    void forget(UUID uuid) {
        Member old = members.remove(uuid);
        if (old != null) frames.remove(old.train);
    }

    void reset() { frames.clear(); members.clear(); }

    void reportFailure(Throwable ex) {
        if (plugin == null) throw new IllegalStateException("Protocol harness failed", ex);
        plugin.getLogger().log(Level.WARNING, "Train display connection reverted to vanilla synchronization.", ex);
    }

    String status() {
        return "display=" + (enabled && !closed ? "enabled" : "vanilla") + ", connections=" + connections.size()
                + ", frames=" + frames.size() + ", bundles=" + batches.sum()
                + ", suppressed=" + suppressed.sum() + ", fallbacks=" + fallbacks.sum();
    }

    @Override public void close() {
        closed = true;
        for (var connection : connections.values()) connection.detach();
        connections.clear();
        reset();
        if (plugin != null) plugin.getLogger().info("Train display sync stopped: " + status());
    }
}
