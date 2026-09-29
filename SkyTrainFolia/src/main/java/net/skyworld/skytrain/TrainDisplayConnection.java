package net.skyworld.skytrain;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** All mutable viewer/codec state is confined to this channel's event loop. No Bukkit access. */
final class TrainDisplayConnection extends ChannelDuplexHandler {
    static final String HANDLER = "skytrain_display_1_4";
    private final TrainDisplaySync owner;
    private final Channel channel;
    private final TrainDisplayPacketAdapter adapter;
    private final Map<Integer, TrainDisplayTracked> tracked = new HashMap<>();
    private ChannelHandlerContext context;
    private ScheduledFuture<?> timer;
    private boolean stopped;

    TrainDisplayConnection(TrainDisplaySync owner, Channel channel, TrainDisplayPacketAdapter adapter) {
        this.owner = owner;
        this.channel = channel;
        this.adapter = adapter;
    }

    void attach() {
        channel.eventLoop().execute(() -> {
            if (!stopped && channel.isActive() && channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().addBefore("packet_handler", HANDLER, this);
            }
        });
    }

    void detach() {
        channel.eventLoop().execute(() -> {
            stopped = true;
            if (context != null && channel.pipeline().context(this) != null) channel.pipeline().remove(this);
        });
    }

    @Override public void handlerAdded(ChannelHandlerContext ctx) {
        context = ctx;
        timer = ctx.executor().scheduleAtFixedRate(this::pulseSafely, 50, 50, TimeUnit.MILLISECONDS);
    }

    @Override public void handlerRemoved(ChannelHandlerContext ctx) {
        stopped = true;
        if (timer != null) timer.cancel(false);
        if (channel.isActive()) for (TrainDisplayTracked state : tracked.values()) restore(state);
        tracked.clear();
    }

    @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        stopped = true;
        if (timer != null) timer.cancel(false);
        tracked.clear();
        super.channelInactive(ctx);
    }

    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
        if (stopped || !(message instanceof Packet<?> packet)) { ctx.write(message, promise); return; }
        try {
            Packet<?> filtered = filter(packet);
            if (filtered == null) promise.trySuccess();
            else ctx.write(filtered, promise);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError ex) {
            fail(ex);
            ctx.write(message, promise);
        }
    }

    private Packet<?> filter(Packet<?> packet) throws IllegalAccessException {
        if (packet instanceof ClientboundBundlePacket bundle) {
            var output = new ArrayList<Packet<? super ClientGamePacketListener>>();
            boolean changed = false;
            for (var child : bundle.subPackets()) {
                Packet<?> next = filter(child);
                if (next != child) changed = true;
                if (next != null) output.add(asGamePacket(next));
            }
            return !changed ? packet : output.isEmpty() ? null : new ClientboundBundlePacket(output);
        }
        if (packet instanceof ClientboundRespawnPacket) { tracked.clear(); return packet; }
        if (packet instanceof ClientboundAddEntityPacket spawn) {
            if (isMinecart(spawn.getType())) tracked.put(spawn.getId(), new TrainDisplayTracked(spawn));
            else tracked.remove(spawn.getId());
            return packet;
        }
        int[] removed = adapter.removedEntityIds(packet);
        if (removed != null) {
            for (int id : removed) tracked.remove(id);
            return packet;
        }
        int id = adapter.movementId(packet);
        TrainDisplayTracked state = tracked.get(id);
        if (state == null && id != Integer.MIN_VALUE) {
            // A player may already be watching a cart when the handler is installed. An outgoing
            // absolute update proves visibility and supplies a codec baseline; never guess one
            // from an entity snapshot or from a relative packet.
            var baseline = adapter.absoluteBaseline(packet);
            if (baseline != null) {
                for (var member : owner.members.values()) {
                    if (member.id() == id) {
                        state = new TrainDisplayTracked(id, member.uuid(), baseline);
                        tracked.put(id, state);
                        break;
                    }
                }
            }
        }
        if (state == null) return packet;
        if (state.active && !eligible(state, System.nanoTime())) restore(state);
        // Keep the VANILLA stream baseline even while its packets are suppressed. This is essential
        // for a correct handback: the next vanilla relative packet is based on this stream, not ours.
        adapter.observeVanilla(packet, state);
        if (state.active) { owner.suppressed.increment(); return null; }
        return packet;
    }

    private boolean eligible(TrainDisplayTracked state, long now) {
        TrainDisplaySync.Member member = owner.members.get(state.uuid);
        if (member == null || member.id() != state.id) return false;
        TrainDisplayFrame frame = owner.frames.get(member.train());
        if (!owner.isFresh(frame, now)) return false;
        return frame.carts().stream().anyMatch(cart -> cart.id() == state.id && cart.uuid().equals(state.uuid));
    }

    private void pulseSafely() {
        // Coalesce to the latest frame instead of building an unbounded slow-client queue.
        if (stopped || !channel.isActive() || !channel.isWritable()) return;
        try { pulse(System.nanoTime()); }
        catch (RuntimeException | LinkageError ex) { fail(ex); }
    }

    // Package-private for deterministic EmbeddedChannel verification.
    void pulse(long now) {
        for (TrainDisplayTracked state : tracked.values()) if (state.active && !eligible(state, now)) restore(state);
        for (TrainDisplayFrame frame : owner.frames.values()) {
            if (!owner.isFresh(frame, now)) continue;
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (var cart : frame.carts()) {
                TrainDisplayTracked state = tracked.get(cart.id());
                // Never create fake visibility or take over an entity whose spawn was not observed.
                if (state == null || !state.uuid.equals(cart.uuid())) continue;
                if (state.active && state.lastFrame == frame.createdNanos()) continue;
                adapter.encode(cart, state, packets);
                state.lastFrame = frame.createdNanos();
            }
            if (!packets.isEmpty()) {
                sendBatch(packets);
                owner.batches.increment();
            }
        }
    }

    private void sendBatch(List<Packet<? super ClientGamePacketListener>> packets) {
        // Stay below the protocol bundle cap. Ordinary consists fit in one batch.
        for (int start = 0; start < packets.size(); start += 4000) {
            context.writeAndFlush(new ClientboundBundlePacket(
                    List.copyOf(packets.subList(start, Math.min(start + 4000, packets.size())))))
                    .addListener(future -> { if (!future.isSuccess()) fail(future.cause()); });
        }
    }

    private void restore(TrainDisplayTracked state) {
        if (!state.active) return;
        state.active = false;
        state.lastFrame = Long.MIN_VALUE;
        context.writeAndFlush(adapter.restorePosition(state));
        context.writeAndFlush(new ClientboundSetEntityMotionPacket(state.id, state.velocity));
        owner.fallbacks.increment();
    }

    private void fail(Throwable ex) {
        if (stopped) return;
        stopped = true;
        if (timer != null) timer.cancel(false);
        for (TrainDisplayTracked state : tracked.values()) restore(state);
        owner.reportFailure(ex);
    }

    private static boolean isMinecart(EntityType<?> type) {
        return type == EntityTypes.MINECART || type == EntityTypes.CHEST_MINECART
                || type == EntityTypes.FURNACE_MINECART || type == EntityTypes.HOPPER_MINECART
                || type == EntityTypes.TNT_MINECART || type == EntityTypes.SPAWNER_MINECART
                || type == EntityTypes.COMMAND_BLOCK_MINECART;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientGamePacketListener> asGamePacket(Packet<?> packet) {
        return (Packet<? super ClientGamePacketListener>) packet;
    }
}
