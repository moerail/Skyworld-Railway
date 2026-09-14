package net.skyworld.skytrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

record SwitchBlockPosition(String worldName, int x, int y, int z) {
    static SwitchBlockPosition of(Block block) {
        return new SwitchBlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    Location location() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    Block block() {
        Location location = location();
        return location == null ? null : location.getBlock();
    }

    String key() {
        return worldName.toLowerCase(java.util.Locale.ROOT) + ':' + x + ':' + y + ':' + z;
    }
}
