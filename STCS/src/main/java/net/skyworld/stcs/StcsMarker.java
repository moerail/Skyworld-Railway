package net.skyworld.stcs;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.util.Vector;

record StcsMarker(UUID id, MarkerType type, BlockPosition sign, BlockPosition rail,
        String line, String name, double directionX, double directionZ) {

    String lineKey() {
        return line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
    }

    Vector direction() {
        Vector direction = new Vector(directionX, 0.0, directionZ);
        return direction.lengthSquared() < 0.0001 ? new Vector() : direction.normalize();
    }
}
