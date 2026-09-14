package net.skyworld.skytrain;

import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static net.skyworld.skytrain.RailSignAccessTest.*;

public final class SignPowerTest {
    public static void main(String[] args) throws Exception {
        RailSignAccessTest.main(args);
        loaded=true; owned=true; data.clear();
        data.put(key(0,62,0),proxy(org.bukkit.block.data.type.Sign.class,(m,a)->switch(m) {
            case "getMaterial" -> Material.OAK_SIGN;
            case "getRotation" -> BlockFace.NORTH;
            default -> null;
        }));
        Block sign=block(0,62,0);
        data.put(key(1,62,0),repeater(BlockFace.EAST));
        check(SignPower.get(sign,BlockFace.EAST,true)==SignPower.State.NONE,"Repeater facing away cannot power sign");
        data.put(key(1,62,0),repeater(BlockFace.WEST));
        check(SignPower.get(sign,BlockFace.EAST,true)==SignPower.State.ON,"Repeater output facing sign");
        data.put(key(1,62,0),proxy(RedstoneWire.class,(m,a)->switch(m) {
            case "getMaterial" -> Material.REDSTONE_WIRE;
            case "getPower" -> 15;
            default -> null;
        }));
        check(SignPower.read(sign).sides().contains(BlockFace.EAST),"Adjacent sign wire powers sign");
        data.remove(key(1,62,0));
        AtomicBoolean power=new AtomicBoolean(true);
        data.put(key(1,61,0),lever(power));
        check(!SignPower.read(sign).powered(),"Support lever is output, not indirect input");
        data.put(key(1,62,0),lever(power));
        check(SignPower.read(sign).powered(),"Lever beside sign is direct input");
        data.remove(key(1,62,0));
        power.set(false);
        check(StationOutput.apply(sign,true,b->false) && power.get(),"Station wait energizes attached output");
        check(!SignPower.read(sign).powered(),"Energized output cannot feed back to station");
        check(StationOutput.apply(sign,false,b->false) && !power.get(),"Departure releases output");
        check(StationOutput.apply(sign,true,b->true) && !power.get(),"Switch actuator is excluded");
        owned=false;
        check(SignPower.read(sign)==null,"Unknown region is unknown, not unpowered");
        check(!StationOutput.apply(sign,true,b->false),"Output refuses foreign region");
        System.out.println("Directional redstone, output feedback isolation and station output tests passed");
    }
    static Repeater repeater(BlockFace facing) {
        return proxy(Repeater.class,(m,a)->switch(m) {
            case "getMaterial" -> Material.REPEATER;
            case "getFacing" -> facing;
            case "isPowered" -> true;
            default -> null;
        });
    }
    static Switch lever(AtomicBoolean power) {
        return proxy(Switch.class,(m,a)->switch(m) {
            case "getMaterial" -> Material.LEVER;
            case "getFacing" -> BlockFace.EAST;
            case "getAttachedFace" -> FaceAttachable.AttachedFace.WALL;
            case "isPowered" -> power.get();
            case "setPowered" -> { power.set((boolean)a[0]); yield null; }
            default -> null;
        });
    }
}
