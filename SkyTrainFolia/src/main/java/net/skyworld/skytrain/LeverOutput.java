package net.skyworld.skytrain;

import java.util.function.BiConsumer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;

/** Explicit lever output, including the strong-power support block's neighbours. */
final class LeverOutput {
    enum Result { APPLIED, MISSING, UNAVAILABLE }

    static Result write(Block block, boolean powered) {
        return write(block, powered, OwnedLeverPhysics::notifyNeighbours);
    }

    static Result write(Block block, boolean powered, BiConsumer<Block, Switch> notify) {
        if (block == null || !RailSignAccess.readable(block)) return Result.UNAVAILABLE;
        if (block.getType() != Material.LEVER || !(block.getBlockData() instanceof Switch lever)) {
            return Result.MISSING;
        }
        Block support = StationOutput.support(block, lever);
        for (Block center : new Block[]{block, support}) {
            if (!RailSignAccess.readable(center)) return Result.UNAVAILABLE;
            for (BlockFace face : SignPower.FACES) {
                Block neighbour = center.getRelative(face);
                // Beyond the build limit is empty, not an unloaded region.
                if (neighbour.getY() < neighbour.getWorld().getMinHeight()
                        || neighbour.getY() >= neighbour.getWorld().getMaxHeight()) continue;
                if (!RailSignAccess.readable(neighbour)) return Result.UNAVAILABLE;
            }
        }
        if (lever.isPowered() != powered) {
            lever.setPowered(powered);
            block.setBlockData(lever, true);
        }
        // Re-notify even if the state matches: this also repairs outputs written by older builds.
        notify.accept(block, lever);
        return Result.APPLIED;
    }
}
