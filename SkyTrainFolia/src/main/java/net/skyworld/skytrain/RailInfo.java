package net.skyworld.skytrain;

import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;

final class RailInfo {
    final Block block;
    final Rail rail;
    final boolean poweredRail;
    final boolean powered;

    RailInfo(Block block, Rail rail, boolean poweredRail, boolean powered) {
        this.block = block;
        this.rail = rail;
        this.poweredRail = poweredRail;
        this.powered = powered;
    }
}
