package net.skyworld.skytrain;

import java.util.List;
import java.util.UUID;

/** Value-only handoff from the existing train task to a connection's event loop. */
record TrainDisplayFrame(UUID trainId, long createdNanos, List<Cart> carts) {
    TrainDisplayFrame { carts = List.copyOf(carts); }

    record Cart(int id, UUID uuid, UUID world, double x, double y, double z,
                float yaw, float pitch, double speed, boolean newMinecart) {
        boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Float.isFinite(yaw) && Float.isFinite(pitch) && Double.isFinite(speed);
        }
    }
}
