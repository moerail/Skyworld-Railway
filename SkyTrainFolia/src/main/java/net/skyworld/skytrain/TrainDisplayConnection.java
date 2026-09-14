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
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** All mutable viewer/codec state is confined to this channel's event loop. No Bukkit access. */
final class TrainDisplayConnection extends ChannelDuplexHandler {
    static final String HANDLER = "skytrain_display_1_4";
    private static final Field MOVE_ID;
    static {
        try {
            MOVE_ID = ClientboundMoveEntityPacket.class.getDeclaredField("entityId");
            MOVE_ID.setAccessible(true);
        } catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }
    static void checkCompatibility() { /* triggers the protocol field check before enabling */ }

    private final TrainDisplaySync owner;
    private final Channel channel;
    private final Map<Integer, Tracked> tracked = new HashMap<>();
    private ChannelHandlerContext context;
    private ScheduledFuture<?> timer;
    private boolean stopped;

    TrainDisplayConnection(TrainDisplaySync owner, Channel channel) {
        this.owner = owner;
        this.channel = channel;
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
        if (channel.isActive()) for (Tracked state : tracked.values()) restore(state);
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
            if (isMinecart(spawn.getType())) tracked.put(spawn.getId(), new Tracked(spawn));
            else tracked.remove(spawn.getId());
            return packet;
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            for (int id : remove.getEntityIds()) tracked.remove(id);
            return packet;
        }
        int id = movementId(packet);
        Tracked state = tracked.get(id);
        if (state == null && id != Integer.MIN_VALUE) {
            // A player may already be watching a cart when the handler is installed. An outgoing
            // absolute update proves visibility and supplies a codec baseline; never guess one
            // from an entity snapshot or from a relative packet.
            PositionMoveRotation baseline = absoluteBaseline(packet);
            if (baseline != null) {
                for (var member : owner.members.values()) {
                    if (member.id() == id) {
                        state = new Tracked(id, member.uuid(), baseline);
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
        state.observeVanilla(packet);
        if (state.active) { owner.suppressed.increment(); return null; }
        return packet;
    }

    private boolean eligible(Tracked state, long now) {
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
        for (Tracked state : tracked.values()) if (state.active && !eligible(state, now)) restore(state);
        for (TrainDisplayFrame frame : owner.frames.values()) {
            if (!owner.isFresh(frame, now)) continue;
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (var cart : frame.carts()) {
                Tracked state = tracked.get(cart.id());
                // Never create fake visibility or take over an entity whose spawn was not observed.
                if (state == null || !state.uuid.equals(cart.uuid())) continue;
                if (state.active && state.lastFrame == frame.createdNanos()) continue;
                state.encode(cart, packets);
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

    private void restore(Tracked state) {
        if (!state.active) return;
        state.active = false;
        state.lastFrame = Long.MIN_VALUE;
        context.writeAndFlush(new ClientboundEntityPositionSyncPacket(state.id,
                new PositionMoveRotation(state.vanilla.getBase(), state.velocity, state.yaw, state.pitch), false));
        context.writeAndFlush(new ClientboundSetEntityMotionPacket(state.id, state.velocity));
        owner.fallbacks.increment();
    }

    private void fail(Throwable ex) {
        if (stopped) return;
        stopped = true;
        if (timer != null) timer.cancel(false);
        for (Tracked state : tracked.values()) restore(state);
        owner.reportFailure(ex);
    }

    private static boolean isMinecart(EntityType<?> type) {
        return type == EntityTypes.MINECART || type == EntityTypes.CHEST_MINECART
                || type == EntityTypes.FURNACE_MINECART || type == EntityTypes.HOPPER_MINECART
                || type == EntityTypes.TNT_MINECART || type == EntityTypes.SPAWNER_MINECART
                || type == EntityTypes.COMMAND_BLOCK_MINECART;
    }

    private static int movementId(Packet<?> packet) throws IllegalAccessException {
        if (packet instanceof ClientboundMoveEntityPacket) return MOVE_ID.getInt(packet);
        if (packet instanceof ClientboundEntityPositionSyncPacket p) return p.id();
        if (packet instanceof ClientboundTeleportEntityPacket p) return p.id();
        if (packet instanceof ClientboundMoveMinecartPacket p) return p.entityId();
        if (packet instanceof ClientboundSetEntityMotionPacket p) return p.id();
        return Integer.MIN_VALUE;
    }

    private static PositionMoveRotation absoluteBaseline(Packet<?> packet) {
        if (packet instanceof ClientboundEntityPositionSyncPacket p) return p.values();
        if (packet instanceof ClientboundTeleportEntityPacket p && p.relatives().isEmpty()) return p.change();
        if (packet instanceof ClientboundMoveMinecartPacket p && !p.lerpSteps().isEmpty()) {
            var step = p.lerpSteps().getLast();
            return new PositionMoveRotation(step.position(), step.movement(), step.yRot(), step.xRot());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientGamePacketListener> asGamePacket(Packet<?> packet) {
        return (Packet<? super ClientGamePacketListener>) packet;
    }

    private static final class Tracked {
        final int id;
        final UUID uuid;
        final VecDeltaCodec vanilla = new VecDeltaCodec(), display = new VecDeltaCodec();
        Vec3 velocity;
        float yaw, pitch;
        boolean active;
        long lastFrame = Long.MIN_VALUE;

        Tracked(ClientboundAddEntityPacket spawn) {
            id = spawn.getId(); uuid = spawn.getUUID();
            vanilla.setBase(new Vec3(spawn.getX(), spawn.getY(), spawn.getZ()));
            velocity = spawn.getMovement(); yaw = spawn.getYRot(); pitch = spawn.getXRot();
        }

        Tracked(int id, UUID uuid, PositionMoveRotation baseline) {
            this.id = id; this.uuid = uuid;
            setVanilla(baseline);
        }

        void observeVanilla(Packet<?> packet) {
            if (packet instanceof ClientboundMoveEntityPacket p) {
                if (p.hasPosition()) vanilla.setBase(vanilla.decode(p.getXa(), p.getYa(), p.getZa()));
                if (p.hasRotation()) { yaw = p.getYRot(); pitch = p.getXRot(); }
            } else if (packet instanceof ClientboundEntityPositionSyncPacket p) setVanilla(p.values());
            else if (packet instanceof ClientboundTeleportEntityPacket p) {
                setVanilla(PositionMoveRotation.calculateAbsolute(
                        new PositionMoveRotation(vanilla.getBase(), velocity, yaw, pitch), p.change(), p.relatives()));
            } else if (packet instanceof ClientboundSetEntityMotionPacket p) velocity = p.movement();
            else if (packet instanceof ClientboundMoveMinecartPacket p && !p.lerpSteps().isEmpty()) {
                var step = p.lerpSteps().getLast();
                vanilla.setBase(step.position()); velocity = step.movement(); yaw = step.yRot(); pitch = step.xRot();
            }
        }

        void setVanilla(PositionMoveRotation values) {
            vanilla.setBase(values.position()); velocity = values.deltaMovement();
            yaw = values.yRot(); pitch = values.xRot();
        }

        void encode(TrainDisplayFrame.Cart cart, List<Packet<? super ClientGamePacketListener>> packets) {
            Vec3 position = new Vec3(cart.x(), cart.y(), cart.z());
            double angle = Math.toRadians(cart.yaw());
            Vec3 motion = new Vec3(-Math.sin(angle) * cart.speed(), 0, Math.cos(angle) * cart.speed());
            if (!active) {
                // A single absolute update establishes a known codec base at takeover, not every tick.
                packets.add(new ClientboundEntityPositionSyncPacket(id,
                        new PositionMoveRotation(position, motion, cart.yaw(), cart.pitch()), false));
                display.setBase(position);
                active = true;
            }
            if (cart.newMinecart()) {
                packets.add(new ClientboundMoveMinecartPacket(id, List.of(new NewMinecartBehavior.MinecartStep(
                        position, motion, cart.yaw(), cart.pitch(), 1.0F))));
                display.setBase(position);
            } else {
                long dx = display.encodeX(position), dy = display.encodeY(position), dz = display.encodeZ(position);
                if (fits(dx) && fits(dy) && fits(dz)) {
                    // Always PosRot for all visible members of this frame, including zero deltas.
                    packets.add(new ClientboundMoveEntityPacket.PosRot(id, (short) dx, (short) dy, (short) dz,
                            rotation(cart.yaw()), rotation(cart.pitch()), false));
                    display.setBase(display.decode(dx, dy, dz));
                } else {
                    packets.add(new ClientboundEntityPositionSyncPacket(id,
                            new PositionMoveRotation(position, motion, cart.yaw(), cart.pitch()), false));
                    display.setBase(position);
                }
                packets.add(new ClientboundSetEntityMotionPacket(id, motion));
            }
        }

        private static boolean fits(long delta) { return delta >= Short.MIN_VALUE && delta <= Short.MAX_VALUE; }
        private static byte rotation(float angle) { return (byte) Math.floor(angle * 256.0 / 360.0); }
    }
}
