package net.skyworld.skytrain;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class TrainMemberTarget {
    final Location location;
    final Vector direction;
    final double speed;
    final long updatedAtMillis;

    TrainMemberTarget(Location location, Vector direction, double speed, long updatedAtMillis) {
        this.location = location;
        this.direction = direction == null ? new Vector() : direction.clone();
        this.speed = Math.max(0.0, speed);
        this.updatedAtMillis = updatedAtMillis;
    }
}
