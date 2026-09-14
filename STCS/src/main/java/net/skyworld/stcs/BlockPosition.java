package net.skyworld.stcs;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

record BlockPosition(String world, int x, int y, int z) {
    static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    String key() {
        return world.toLowerCase(Locale.ROOT) + ':' + x + ':' + y + ':' + z;
    }

    Location location() {
        World loadedWorld = Bukkit.getWorld(world);
        return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
    }

    Block block() {
        Location location = location();
        return location == null ? null : location.getBlock();
    }
}
