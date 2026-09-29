package net.skyworld.skytrain;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.PositionMoveRotation;

import java.util.List;

/** Version-specific, stateless packet operations. All mutable codec state belongs to one viewer. */
interface TrainDisplayPacketAdapter {
    void checkCompatibility();

    /** Null means this is not a removal packet. An empty array is still a removal packet. */
    int[] removedEntityIds(Packet<?> packet);

    int movementId(Packet<?> packet) throws IllegalAccessException;

    PositionMoveRotation absoluteBaseline(Packet<?> packet);

    void observeVanilla(Packet<?> packet, TrainDisplayTracked state);

    void encode(TrainDisplayFrame.Cart cart, TrainDisplayTracked state,
                List<Packet<? super ClientGamePacketListener>> packets);

    Packet<? super ClientGamePacketListener> restorePosition(TrainDisplayTracked state);
}
