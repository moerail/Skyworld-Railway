package net.skyworld.sta.api.v5;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.skyworld.sta.api.v1.Subscription;

/** Bounded, per-train coalescing store. offer never runs consumer callbacks.
 * The owning provider calls dispatch from its async task. One store has one session. */
public final class LatestMessageStore implements MessageSource, AutoCloseable {
    private final UUID session;
    private final int capacity;
    private final Map<UUID, StaMessage> latest = new LinkedHashMap<>();
    private final Map<UUID, StaMessage> pending = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<StaMessage>> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> errors;
    private boolean closed;
    public LatestMessageStore(UUID session, int capacity, Consumer<RuntimeException> errors) {
        this.session = Objects.requireNonNull(session);
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity; this.errors = Objects.requireNonNull(errors);
    }
    public UUID sessionId() { return session; }
    public synchronized boolean offer(StaMessage message) {
        if (closed || !session.equals(message.header().sessionId())) return false;
        UUID id = message.header().trainId();
        StaMessage old = latest.get(id);
        if (old != null && old.header().sequence() >= message.header().sequence()) return false;
        if (old == null && latest.size() >= capacity) return false;
        latest.put(id, message); pending.put(id, message); return true;
    }
    public synchronized Collection<StaMessage> snapshots() { return List.copyOf(latest.values()); }
    public Subscription subscribe(Consumer<StaMessage> listener) {
        Objects.requireNonNull(listener);
        synchronized (this) {
            if (closed) throw new IllegalStateException("Provider closed");
            listeners.add(listener);
        }
        return () -> listeners.remove(listener);
    }
    public void dispatch() {
        List<StaMessage> batch;
        synchronized (this) { batch = List.copyOf(pending.values()); pending.clear(); }
        for (StaMessage message : batch) for (Consumer<StaMessage> listener : listeners) {
            try { listener.accept(message); } catch (RuntimeException ex) { errors.accept(ex); }
        }
    }
    /** Forget expired removal tombstones after consumers have had a bounded resync window. */
    public synchronized void pruneRemovals(long beforeMillis) {
        latest.entrySet().removeIf(e -> e.getValue().header().kind() == StaMessage.Kind.TRAIN_REMOVED
                && e.getValue().header().emittedAtMillis() < beforeMillis && !pending.containsKey(e.getKey()));
    }
    public synchronized void close() { closed = true; latest.clear(); pending.clear(); listeners.clear(); }
}
