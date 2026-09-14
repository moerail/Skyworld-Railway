package net.skyworld.skytrain;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import java.util.function.Predicate;

/** Levers attached to the sign support are outputs, as in TC's TrackedSign.setOutput. */
final class StationOutput {
    static Block support(Block lever,Switch data) {
        return lever.getRelative(switch(data.getAttachedFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            case WALL -> data.getFacing().getOppositeFace();
        });
    }
    static boolean apply(Block sign,boolean powered,Predicate<Block> reserved) {
        if(!RailSignAccess.readable(sign)) return false;
        Block support=RailSignAccess.support(sign);
        // Validate the whole output footprint before changing any lever.
        if(!RailSignAccess.readable(support)) return false;
        for(BlockFace face:SignPower.FACES) {
            Block output=support.getRelative(face);
            if(!RailSignAccess.readable(output)) return false;
            for(BlockFace neighbour:SignPower.FACES) if(!RailSignAccess.readable(output.getRelative(neighbour))) return false;
        }
        for(BlockFace face:SignPower.FACES) {
            Block block=support.getRelative(face);
            if(block.getType()!=Material.LEVER || !(block.getBlockData() instanceof Switch lever)
                    || !support(block,lever).equals(support) || reserved.test(block)) continue;
            if(lever.isPowered()!=powered) {
                lever.setPowered(powered);
                block.setBlockData(lever,true);
            }
        }
        return true;
    }
}
