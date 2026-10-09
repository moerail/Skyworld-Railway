package net.skyworld.skytrain;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Viewer-local packet baseline; used only by the display packet layer. */
final class TrainDisplayTracked {
    final int id;
    final UUID uuid;
    final VecDeltaCodec vanilla = new VecDeltaCodec();
    final VecDeltaCodec display = new VecDeltaCodec();
    Vec3 velocity;
    float yaw;
    float pitch;
    boolean active;
    long lastFrame = Long.MIN_VALUE;

    TrainDisplayTracked(ClientboundAddEntityPacket spawn) {
        id = spawn.getId();
        uuid = spawn.getUUID();
        vanilla.setBase(new Vec3(spawn.getX(), spawn.getY(), spawn.getZ()));
        velocity = spawn.getMovement();
        yaw = spawn.getYRot();
        pitch = spawn.getXRot();
    }

    TrainDisplayTracked(int id, UUID uuid, PositionMoveRotation baseline) {
        this.id = id;
        this.uuid = uuid;
        setVanilla(baseline);
    }

    void setVanilla(PositionMoveRotation values) {
        vanilla.setBase(values.position());
        velocity = values.deltaMovement();
        yaw = values.yRot();
        pitch = values.xRot();
    }
}
