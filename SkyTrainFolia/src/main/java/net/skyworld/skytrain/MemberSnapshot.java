package net.skyworld.skytrain;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

final class MemberSnapshot {
    final UUID entityId;
    final String worldName;
    final double x;
    final double y;
    final double z;
    final Vector velocity;
    final long timeMillis;

    MemberSnapshot(UUID entityId, Location location, Vector velocity, long timeMillis) {
        this.entityId = entityId;
        World world = location.getWorld();
        this.worldName = world == null ? "" : world.getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.velocity = velocity == null ? new Vector() : velocity.clone();
        this.timeMillis = timeMillis;
    }

    Vector toVector() {
        return new Vector(x, y, z);
    }
}
