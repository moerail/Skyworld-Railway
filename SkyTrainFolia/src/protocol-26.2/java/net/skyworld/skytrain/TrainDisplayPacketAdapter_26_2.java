package net.skyworld.skytrain;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;

/** Shiroha 26.2 packet ABI. This class is never loaded on 26.3. */
final class TrainDisplayPacketAdapter_26_2 implements TrainDisplayPacketAdapter {
    private static final Field MOVE_ID = moveIdField();

    private static Field moveIdField() {
        try {
            Field field = ClientboundMoveEntityPacket.class.getDeclaredField("entityId");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Override public void checkCompatibility() { /* Initializes the reflective packet field. */ }

    @Override public int[] removedEntityIds(Packet<?> packet) {
        return packet instanceof ClientboundRemoveEntitiesPacket p ? p.getEntityIds().toIntArray() : null;
    }

    @Override public int movementId(Packet<?> packet) throws IllegalAccessException {
        if (packet instanceof ClientboundMoveEntityPacket) return MOVE_ID.getInt(packet);
        if (packet instanceof ClientboundEntityPositionSyncPacket p) return p.id();
        if (packet instanceof ClientboundTeleportEntityPacket p) return p.id();
        if (packet instanceof ClientboundMoveMinecartPacket p) return p.entityId();
        if (packet instanceof ClientboundSetEntityMotionPacket p) return p.id();
        return Integer.MIN_VALUE;
    }

    @Override public PositionMoveRotation absoluteBaseline(Packet<?> packet) {
        if (packet instanceof ClientboundEntityPositionSyncPacket p) return p.values();
        if (packet instanceof ClientboundTeleportEntityPacket p && p.relatives().isEmpty()) return p.change();
        if (packet instanceof ClientboundMoveMinecartPacket p && !p.lerpSteps().isEmpty()) {
            var step = p.lerpSteps().getLast();
            return new PositionMoveRotation(step.position(), step.movement(), step.yRot(), step.xRot());
        }
        return null;
    }

    @Override public void observeVanilla(Packet<?> packet, TrainDisplayTracked state) {
        if (packet instanceof ClientboundMoveEntityPacket p) {
            if (p.hasPosition()) state.vanilla.setBase(state.vanilla.decode(p.getXa(), p.getYa(), p.getZa()));
            if (p.hasRotation()) { state.yaw = p.getYRot(); state.pitch = p.getXRot(); }
        } else if (packet instanceof ClientboundEntityPositionSyncPacket p) state.setVanilla(p.values());
        else if (packet instanceof ClientboundTeleportEntityPacket p) {
            state.setVanilla(PositionMoveRotation.calculateAbsolute(
                    new PositionMoveRotation(state.vanilla.getBase(), state.velocity, state.yaw, state.pitch),
                    p.change(), p.relatives()));
        } else if (packet instanceof ClientboundSetEntityMotionPacket p) state.velocity = p.movement();
        else if (packet instanceof ClientboundMoveMinecartPacket p && !p.lerpSteps().isEmpty()) {
            var step = p.lerpSteps().getLast();
            state.vanilla.setBase(step.position()); state.velocity = step.movement();
            state.yaw = step.yRot(); state.pitch = step.xRot();
        }
    }

    @Override public void encode(TrainDisplayFrame.Cart cart, TrainDisplayTracked state,
                                 List<Packet<? super ClientGamePacketListener>> packets) {
        Vec3 position = new Vec3(cart.x(), cart.y(), cart.z());
        double angle = Math.toRadians(cart.yaw());
        Vec3 motion = new Vec3(-Math.sin(angle) * cart.speed(), 0, Math.cos(angle) * cart.speed());
        if (!state.active) {
            packets.add(new ClientboundEntityPositionSyncPacket(state.id,
                    new PositionMoveRotation(position, motion, cart.yaw(), cart.pitch()), false));
            state.display.setBase(position);
            state.active = true;
        }
        if (cart.newMinecart()) {
            packets.add(new ClientboundMoveMinecartPacket(state.id, List.of(new NewMinecartBehavior.MinecartStep(
                    position, motion, cart.yaw(), cart.pitch(), 1.0F))));
            state.display.setBase(position);
        } else {
            long dx = state.display.encodeX(position), dy = state.display.encodeY(position);
            long dz = state.display.encodeZ(position);
            if (fits(dx) && fits(dy) && fits(dz)) {
                packets.add(new ClientboundMoveEntityPacket.PosRot(state.id, (short) dx, (short) dy,
                        (short) dz, rotation(cart.yaw()), rotation(cart.pitch()), false));
                state.display.setBase(state.display.decode(dx, dy, dz));
            } else {
                packets.add(new ClientboundEntityPositionSyncPacket(state.id,
                        new PositionMoveRotation(position, motion, cart.yaw(), cart.pitch()), false));
                state.display.setBase(position);
            }
            packets.add(new ClientboundSetEntityMotionPacket(state.id, motion));
        }
    }

    @Override public Packet<? super ClientGamePacketListener> restorePosition(TrainDisplayTracked state) {
        return new ClientboundEntityPositionSyncPacket(state.id,
                new PositionMoveRotation(state.vanilla.getBase(), state.velocity, state.yaw, state.pitch), false);
    }

    private static boolean fits(long delta) { return delta >= Short.MIN_VALUE && delta <= Short.MAX_VALUE; }
    private static byte rotation(float angle) { return (byte) Math.floor(angle * 256.0 / 360.0); }
}
