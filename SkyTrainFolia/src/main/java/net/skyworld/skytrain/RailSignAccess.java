package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.util.Vector;

/** All methods touching blocks must run in their owning region. Unknown is not absent. */
final class RailSignAccess {
    static final BlockFace[] SIDES={BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST};
    static boolean readable(Block b) {
        return b.getY()>=b.getWorld().getMinHeight() && b.getY()<b.getWorld().getMaxHeight()
                && b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4)
                && Bukkit.isOwnedByCurrentRegion(b.getLocation());
    }
    static String key(Block b) { return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ(); }
    static Block support(Block b) {
        var data=b.getBlockData();
        if(data instanceof WallSign w) return b.getRelative(w.getFacing().getOppositeFace());
        if(data instanceof WallHangingSign w) return b.getRelative(w.getFacing().getOppositeFace());
        if(data instanceof org.bukkit.block.data.type.HangingSign) return b.getRelative(BlockFace.UP);
        return b.getRelative(BlockFace.DOWN);
    }
    static Vector facing(Block b, Side side) {
        var data=b.getBlockData();
        BlockFace f=data instanceof Rotatable r?r.getRotation():data instanceof Directional d?d.getFacing():BlockFace.NORTH;
        return new Vector(f.getModX(),0,f.getModZ()).multiply(side==Side.BACK?-1:1).normalize();
    }
    static List<Block> signsFor(Block rail) {
        List<Block> result=new ArrayList<>();
        for(int depth=0;depth<64;depth++) {
            Block slice=rail.getRelative(0,-depth,0);
            if(!readable(slice)) break;
            boolean found=false;
            if(slice.getState() instanceof Sign) {result.add(slice);found=true;}
            for(BlockFace face:SIDES) {
                Block b=slice.getRelative(face);
                if(!readable(b)) continue;
                if(b.getState() instanceof Sign && support(b).equals(slice)) {result.add(b);found=true;}
            }
            if(depth>1 && !found) break;
        }
        return result;
    }
    static Block railFor(Block sign) {
        if(!readable(sign)) return null;
        // TC associates signs with the rail's sign column, not the nearest rail in a sphere.
        Block attached=support(sign);
        for(Block column:List.of(sign,attached)) for(int up=0;up<64;up++) {
            Block candidate=column.getRelative(0,up,0);
            if(!readable(candidate)) break;
            if(candidate.getBlockData() instanceof Rail && signsFor(candidate).contains(sign)) return candidate;
        }
        return null;
    }
    static Vector direction(String name, Vector face, Vector incoming) {
        return switch(AutomaticSignSpec.normalizeDirection(name)) {
            case "north"->new Vector(0,0,-1);case "south"->new Vector(0,0,1);
            case "east"->new Vector(1,0,0);case "west"->new Vector(-1,0,0);
            case "left"->new Vector(-face.getZ(),0,face.getX());
            case "right"->new Vector(face.getZ(),0,-face.getX());
            case "up"->new Vector(0,1,0);case "down"->new Vector(0,-1,0);
            case "reverse"->incoming.clone().multiply(-1);default->incoming.clone();
        };
    }
    static boolean entered(TrainSignHeader header, Vector facing, Vector motion) {
        String s=header.directions();
        if(s.isBlank() || s.equals("all") || s.equals("*")) return true;
        if(AutomaticSignSpec.isDirection(s)) return direction(s,facing,facing.clone().multiply(-1)).dot(motion)<-0.1;
        for(char c:s.toCharArray()) if(AutomaticSignSpec.isDirection(String.valueOf(c))
                && direction(String.valueOf(c),facing,facing.clone().multiply(-1)).dot(motion)<-0.1) return true;
        return false;
    }
    static boolean powered(Block sign) {
        if(sign.isBlockPowered()||sign.isBlockIndirectlyPowered()||sign.getBlockPower()>0) return true;
        Block b=support(sign);
        return readable(b) && (b.isBlockPowered()||b.isBlockIndirectlyPowered()||b.getBlockPower()>0);
    }
    static boolean powerReadable(Block sign) {
        if(!readable(sign)) return false;
        for(Block center:List.of(sign,support(sign))) {
            if(!readable(center)) return false;
            for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
                if(!readable(center.getRelative(face))) return false;
            }
        }
        return true;
    }
    static boolean sidePowered(Block sign, BlockFace side) {
        Block b=sign.getRelative(side);
        if(!readable(b)) return false;
        var data=b.getBlockData();
        if(data instanceof org.bukkit.block.data.type.RedstoneWire w) return w.getPower()>0;
        if(data instanceof org.bukkit.block.data.Powerable p && p.isPowered()) return true;
        return b.getType()==org.bukkit.Material.REDSTONE_BLOCK
                || ((b.getType()==org.bukkit.Material.REDSTONE_WALL_TORCH || b.getType()==org.bukkit.Material.REDSTONE_TORCH)
                    && data instanceof org.bukkit.block.data.Lightable l && l.isLit());
    }
}
