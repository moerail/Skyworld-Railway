package net.skyworld.skytrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.craftbukkit.CraftWorld;

/** 26.2 adapter: same two neighbour notifications as vanilla LeverBlock. */
final class OwnedLeverPhysics {
    static void notifyNeighbours(Block lever, Switch data) {
        var world = ((CraftWorld) lever.getWorld()).getHandle();
        Block support = StationOutput.support(lever, data);
        var position = new BlockPos(lever.getX(), lever.getY(), lever.getZ());
        var attached = new BlockPos(support.getX(), support.getY(), support.getZ());
        Direction towardSupport = direction(support.getX() - lever.getX(),
                support.getY() - lever.getY(), support.getZ() - lever.getZ());
        BlockFace facing = data.getFacing();
        Direction up = towardSupport.getAxis().isHorizontal() ? Direction.UP
                : direction(facing.getModX(), facing.getModY(), facing.getModZ());
        var orientation = ExperimentalRedstoneUtils.initialOrientation(world, towardSupport, up);
        world.updateNeighborsAt(position, Blocks.LEVER, orientation);
        world.updateNeighborsAt(attached, Blocks.LEVER, orientation);
    }

    private static Direction direction(int x, int y, int z) {
        if (x != 0) return x > 0 ? Direction.EAST : Direction.WEST;
        if (y != 0) return y > 0 ? Direction.UP : Direction.DOWN;
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
