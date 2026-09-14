package net.skyworld.skytrain;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.Comparator;

/** TC PowerState SIGN/FAR rules adapted to modern Bukkit data and non-loading region reads. */
final class SignPower {
    enum State { NONE, OFF, ON, UNKNOWN }
    static final BlockFace[] FACES={BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN};
    record Snapshot(boolean powered,Set<BlockFace> sides) {}
    static Snapshot read(Block sign) {
        EnumSet<BlockFace> on=EnumSet.noneOf(BlockFace.class);
        for(BlockFace face:FACES) {
            State state=get(sign,face,true);
            if(state==State.UNKNOWN) return null;
            if(state==State.ON) on.add(face);
        }
        return new Snapshot(!on.isEmpty(),Set.copyOf(on));
    }
    static State get(Block target,BlockFace from,boolean nextToSign) {
        Block source=target.getRelative(from);
        if(!RailSignAccess.readable(source)) return State.UNKNOWN;
        BlockData data=source.getBlockData();
        Material material=data.getMaterial();
        if(material==Material.REDSTONE_TORCH || material==Material.REDSTONE_WALL_TORCH) {
            return nextToSign || from==BlockFace.DOWN ? state(data instanceof Lightable l && l.isLit()) : State.NONE;
        }
        if(data instanceof Repeater || data instanceof Comparator) {
            if(from.getModY()!=0 || ((Directional)data).getFacing().getOppositeFace()!=from) return State.NONE;
            return state(((Powerable)data).isPowered());
        }
        if(data instanceof RedstoneWire wire) {
            if(nextToSign || from==BlockFace.UP) return state(wire.getPower()>0);
            if(from==BlockFace.DOWN) return State.NONE;
            Boolean distracted=distracted(source,from);
            return distracted==null?State.UNKNOWN:distracted?State.NONE:state(wire.getPower()>0);
        }
        // Levers attached to the support are station outputs, not indirect sign inputs.
        if(material==Material.LEVER && !nextToSign) return State.NONE;
        if(material==Material.REDSTONE_BLOCK) return State.ON;
        if(data instanceof Observer observer) {
            return observer.getFacing()==from?state(observer.isPowered()):State.NONE;
        }
        if(data instanceof org.bukkit.block.data.type.Switch button) return state(button.isPowered());
        if(data instanceof TripwireHook hook) return state(hook.isPowered());
        if(material==Material.DETECTOR_RAIL && data instanceof Powerable detector) return state(detector.isPowered());
        if(material!=null && material.name().endsWith("PRESSURE_PLATE") && data instanceof Powerable plate) return state(plate.isPowered());
        if(data instanceof AnaloguePowerable analogue) return state(analogue.getPower()>0);
        if(nextToSign && RailSignAccess.readable(target) && RailSignAccess.support(target).equals(source)) {
            State result=State.NONE;
            for(BlockFace face:FACES) {
                if(face==from.getOppositeFace()) continue;
                State indirect=get(source,face,false);
                if(indirect==State.UNKNOWN) return State.UNKNOWN;
                if(indirect==State.ON) result=State.ON;
                else if(indirect==State.OFF && result==State.NONE) result=State.OFF;
            }
            return result;
        }
        return State.NONE;
    }
    private static Boolean distracted(Block wire,BlockFace direction) {
        BlockFace left=direction.getModX()!=0?BlockFace.NORTH:BlockFace.WEST;
        Boolean a=column(wire,left),b=column(wire,left.getOppositeFace());
        return a==null || b==null?null:a||b;
    }
    private static Boolean column(Block wire,BlockFace side) {
        Block beside=wire.getRelative(side), above=wire.getRelative(BlockFace.UP);
        if(!RailSignAccess.readable(beside)||!RailSignAccess.readable(above)) return null;
        BlockData data=beside.getBlockData();
        if(data instanceof Repeater || data instanceof Comparator) return ((Directional)data).getFacing()==side;
        if(source(data)) return true;
        if(data.getMaterial()!=null && data.getMaterial().isAir()) {
            Block below=beside.getRelative(BlockFace.DOWN);
            if(!RailSignAccess.readable(below)) return null;
            if(source(below.getBlockData())) return true;
        }
        Material up=above.getBlockData().getMaterial();
        if(up!=null && up.isAir()) {
            Block top=beside.getRelative(BlockFace.UP);
            if(!RailSignAccess.readable(top)) return null;
            return source(top.getBlockData());
        }
        return false;
    }
    private static boolean source(BlockData data) {
        Material m=data.getMaterial();
        return data instanceof RedstoneWire || data instanceof org.bukkit.block.data.type.Switch
                || data instanceof AnaloguePowerable || data instanceof TripwireHook || data instanceof Observer
                || m==Material.REDSTONE_TORCH || m==Material.REDSTONE_WALL_TORCH || m==Material.REDSTONE_BLOCK
                || m==Material.DETECTOR_RAIL || (m!=null && m.name().endsWith("PRESSURE_PLATE"));
    }
    private static State state(boolean value) { return value?State.ON:State.OFF; }
}
