package net.skyworld.skytrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftMinecart;
import org.bukkit.entity.Minecart;
import net.minecraft.world.phys.Vec3;

/** Ordinary movement only. Never changes entity scheduling, ownership or passenger relationships. */
final class OwnedTrainMover {
    private OwnedTrainMover() { }

    /** False means the caller must use the existing Folia relocation/fallback path. */
    static boolean move(Minecart cart, Location target, boolean preserveRotation) {
        if (!Bukkit.isOwnedByCurrentRegion(cart)) return false;
        Location source = cart.getLocation();
        if (!finite(target) || source.getWorld() == null || !source.getWorld().equals(target.getWorld())
                || source.distanceSquared(target) > 64.0) return false;
        // Check the destination footprint, including space for a seated passenger. This does not
        // load chunks and cannot obtain ownership of a different region.
        for (int x : new int[]{target.getBlockX() - 1, target.getBlockX() + 1}) {
            for (int z : new int[]{target.getBlockZ() - 1, target.getBlockZ() + 1}) {
                if (!Bukkit.isOwnedByCurrentRegion(target.getWorld(), x >> 4, z >> 4)) return false;
            }
        }
        var handle = ((CraftMinecart) cart).getHandle();
        var passengers = handle.getPassengers();
        for (var passenger : passengers) {
            if (!Bukkit.isOwnedByCurrentRegion(passenger.getBukkitEntity())
                    || !passenger.getPassengers().isEmpty()) return false;
        }
        // setPos updates the ordinary position/section index. Unlike Bukkit teleportAsync this
        // does not detach the passenger tree, destroy/respawn the cart or send a player teleport.
        cart.setMaxSpeed(0.0);
        handle.setDeltaMovement(Vec3.ZERO);
        handle.setPos(target.getX(), target.getY(), target.getZ());
        if (!preserveRotation) {
            handle.setYRot(target.getYaw());
            handle.setXRot(target.getPitch());
        }
        for (var passenger : passengers) handle.positionRider(passenger);
        handle.syncPosition = true;
        return true;
    }

    private static boolean finite(Location location) {
        return location != null && location.getWorld() != null && Double.isFinite(location.getX())
                && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw()) && Float.isFinite(location.getPitch());
    }
}
