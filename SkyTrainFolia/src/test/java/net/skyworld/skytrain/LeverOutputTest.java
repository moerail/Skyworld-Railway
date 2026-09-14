package net.skyworld.skytrain;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.FaceAttachable.AttachedFace;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Switch;
import static net.skyworld.skytrain.RailSignAccessTest.*;

public final class LeverOutputTest {
    public static void main(String[] args) throws Exception {
        RailSignAccessTest.main(args);
        loaded = true; owned = true;
        for (AttachedFace mount : AttachedFace.values()) {
            for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                data.clear(); blocks.clear();
                AtomicBoolean power = new AtomicBoolean(false);
                List<String> calls = new ArrayList<>();
                Switch lever = proxy(Switch.class, (m,a) -> switch (m) {
                    case "getMaterial" -> Material.LEVER;
                    case "getAttachedFace" -> mount;
                    case "getFacing" -> facing;
                    case "isPowered" -> power.get();
                    case "setPowered" -> { power.set((boolean)a[0]); yield null; }
                    default -> null;
                });
                Block base = block(0,64,0);
                data.put(key(0,64,0), lever);
                Block output = proxy(Block.class, (m,a) -> switch (m) {
                    case "getWorld" -> world;
                    case "getX", "getZ" -> 0;
                    case "getY" -> 64;
                    case "getLocation" -> base.getLocation();
                    case "getType" -> Material.LEVER;
                    case "getBlockData" -> lever;
                    case "getRelative" -> base.getRelative((BlockFace)a[0]);
                    case "setBlockData" -> {
                        check((boolean)a[1], "Normal lever block physics retained");
                        calls.add("write"); yield null;
                    }
                    default -> null;
                });
                var notify = (java.util.function.BiConsumer<Block, Switch>)(b,d) -> {
                    check(b == output, "Notify lever position");
                    Block expected = base.getRelative(switch (mount) {
                        case FLOOR -> BlockFace.DOWN;
                        case CEILING -> BlockFace.UP;
                        case WALL -> facing.getOppositeFace();
                    });
                    check(StationOutput.support(b,d).equals(expected), "Notify correct support: " + mount + facing);
                    calls.add(power.get() ? "on" : "off");
                };
                check(LeverOutput.write(output,true,notify)==LeverOutput.Result.APPLIED, "Power on");
                check(calls.equals(List.of("write","on")), "State precedes neighbour notification");
                calls.clear();
                LeverOutput.write(output,true,notify);
                check(calls.equals(List.of("on")), "Matching state still repairs stale signal");
                calls.clear();
                LeverOutput.write(output,false,notify);
                check(calls.equals(List.of("write","off")), "Power off also notifies support");
                calls.clear();
                owned = false;
                check(LeverOutput.write(output,true,notify)==LeverOutput.Result.UNAVAILABLE, "Foreign region refused");
                check(calls.isEmpty() && !power.get(), "No write/notification in foreign region");
                owned = true; loaded = false;
                check(LeverOutput.write(output,true,notify)==LeverOutput.Result.UNAVAILABLE, "Unloaded chunk refused");
                loaded = true;
                check(calls.isEmpty() && !power.get(), "No synchronous loading/output");
                var savedWorld = world;
                world = proxy(org.bukkit.World.class, (m,a) -> switch (m) {
                    case "getName" -> "world";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "isChunkLoaded" -> (int)a[0] >= 0 && (int)a[1] >= 0;
                    default -> null;
                });
                check(LeverOutput.write(output,true,notify)==LeverOutput.Result.UNAVAILABLE,
                        "Loaded lever with unavailable neighbour refuses partial update");
                check(calls.isEmpty() && !power.get(), "Neighbour guard runs before mutation");
                world = savedWorld;
            }
        }
        data.clear();
        check(LeverOutput.write(block(8,64,8),true,(b,d)->{throw new AssertionError();})
                == LeverOutput.Result.MISSING, "Removed lever not recreated");
        var p = new SwitchBlockPosition("world", 0,64,0);
        var sw = new SkyTrainSwitch(UUID.randomUUID(),p,p,p,BlockFace.NORTH,BlockFace.WEST,BlockFace.EAST,
                BlockFace.SOUTH,SwitchGeometry.LEFT,Rail.Shape.EAST_WEST,Rail.Shape.SOUTH_WEST,
                SwitchState.STRAIGHT,SwitchState.STRAIGHT,"203",p,1);
        sw.setCommandedState(SwitchState.DIVERGING);
        var old = sw.actuatorCommand();
        check(sw.actuatorCommandCurrent(old), "Current output accepted");
        sw.setCommandedState(SwitchState.STRAIGHT);
        sw.setCommandedState(SwitchState.DIVERGING);
        check(!sw.actuatorCommandCurrent(old), "Old command rejected even after A-B-A");
        var current = sw.actuatorCommand();
        sw.setActuator(null,0);
        check(!sw.actuatorCommandCurrent(current), "Removed binding invalidates pending output");
        sw.setActuator(p,1);
        check(!sw.actuatorCommandCurrent(current), "Replaced lever at same position is a new binding");
        sw.setActuator(p,2);
        check(sw.actuatorCommand()==null, "Multiple levers are not driven");
        sw.setActuator(p,1);
        current = sw.actuatorCommand();
        sw.setActuator(new SwitchBlockPosition("world",1,64,0),1);
        check(!sw.actuatorCommandCurrent(current), "Rebinding invalidates pending output");
        System.out.println("PASS lever on/off, 12 mounts, support notification ordering, stale commands and ownership");
    }
}
