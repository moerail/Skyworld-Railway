package net.skyworld.skytrain;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.HangingSign;

public final class RailSignAccessTest {
    static boolean loaded=true, owned=true;
    static final Map<String,Block> blocks=new HashMap<>();
    static final Map<String,BlockData> data=new HashMap<>();
    static World world;
    public static void main(String[] args) throws Exception {
        Server server=proxy(Server.class,(m,a)->switch(m) {
            case "getLogger" -> java.util.logging.Logger.getLogger("rail-sign-test");
            case "getName","getVersion","getBukkitVersion" -> "test";
            case "isOwnedByCurrentRegion" -> owned;
            default -> null;
        });
        // Install only the mock API surface, without bootstrapping the NMS version banner.
        var serverField=Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null,server);
        world=proxy(World.class,(m,a)->switch(m) {
            case "getName" -> "world";
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> loaded;
            case "getBlockAt" -> block((int)a[0],(int)a[1],(int)a[2]);
            default -> null;
        });
        data.put(key(0,64,0),proxy(Rail.class,(m,a)->m.equals("getShape")?Rail.Shape.NORTH_SOUTH:null));
        data.put(key(0,62,0),proxy(org.bukkit.block.data.type.Sign.class,(m,a)->m.equals("getRotation")?BlockFace.NORTH:null));
        check(RailSignAccess.railFor(block(0,62,0))==block(0,64,0),"Sign/support/rail with one intervening support block");
        data.put(key(1,63,0),proxy(WallSign.class,(m,a)->m.equals("getFacing")?BlockFace.EAST:null));
        check(RailSignAccess.railFor(block(1,63,0))==block(0,64,0),"Wall sign attached to rail support");
        data.put(key(-1,63,0),proxy(WallSign.class,(m,a)->m.equals("getFacing")?BlockFace.EAST:null));
        check(RailSignAccess.railFor(block(-1,63,0))==null,"Do not associate unrelated nearby wall sign");
        data.put(key(0,61,0),proxy(HangingSign.class,(m,a)->m.equals("getRotation")?BlockFace.NORTH:null));
        check(RailSignAccess.signsFor(block(0,64,0)).contains(block(0,61,0)),"Consecutive sign column");
        data.remove(key(0,62,0));
        check(!RailSignAccess.signsFor(block(0,64,0)).contains(block(0,61,0)),"Stop after gap in sign column");
        data.put(key(0,62,0),proxy(HangingSign.class,(m,a)->m.equals("getRotation")?BlockFace.NORTH:null));
        check(RailSignAccess.railFor(block(0,62,0))==block(0,64,0),"Hanging sign below rail support");
        owned=false;
        check(RailSignAccess.railFor(block(0,62,0))==null,"Do not read another region");
        owned=true; loaded=false;
        check(RailSignAccess.railFor(block(0,62,0))==null,"Do not synchronously load chunks");
        System.out.println("Rail sign installation and ownership tests passed");
    }
    static String key(int x,int y,int z) { return x+":"+y+":"+z; }
    static Block block(int x,int y,int z) {
        String key=key(x,y,z);
        return blocks.computeIfAbsent(key,k->proxy(Block.class,(m,a)->switch(m) {
            case "getWorld" -> world;
            case "getX" -> x; case "getY" -> y; case "getZ" -> z;
            case "getLocation" -> new Location(world,x,y,z);
            case "getRelative" -> a[0] instanceof BlockFace f
                    ?block(x+f.getModX(),y+f.getModY(),z+f.getModZ())
                    :block(x+(int)a[0],y+(int)a[1],z+(int)a[2]);
            case "getBlockData" -> readData(key);
            case "getType" -> readData(key).getMaterial();
            case "getState" -> {
                BlockData d=readData(key);
                boolean sign=d instanceof WallSign || d instanceof WallHangingSign || d instanceof HangingSign || d instanceof org.bukkit.block.data.type.Sign;
                yield sign?proxy(org.bukkit.block.Sign.class,(n,b)->null):proxy(BlockState.class,(n,b)->null);
            }
            default -> null;
        }));
    }
    static BlockData readData(String key) {
        if(!loaded || !owned) throw new AssertionError("Unsafe world read");
        return data.getOrDefault(key,proxy(BlockData.class,(m,a)->null));
    }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type,BiFunction<String,Object[],Object> call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(self,method,args)->{
            if(method.getName().equals("equals")) return self==args[0];
            if(method.getName().equals("hashCode")) return System.identityHashCode(self);
            if(method.getName().equals("toString")) return type.getSimpleName();
            Object value=call.apply(method.getName(),args==null?new Object[0]:args);
            if(value!=null || !method.getReturnType().isPrimitive()) return value;
            if(method.getReturnType()==boolean.class) return false;
            if(method.getReturnType()==int.class) return 0;
            if(method.getReturnType()==long.class) return 0L;
            if(method.getReturnType()==double.class) return 0D;
            return null;
        });
    }
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); }
}
