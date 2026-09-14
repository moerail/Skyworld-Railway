package net.skyworld.skytrain;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

public final class SwitchApproachTest {
    public static void main(String[] args) {
        // Incoming tangent at the beginning of a curve need not point toward its eventual exit.
        assert VanillaRailWalker.exitFace(Rail.Shape.SOUTH_EAST, new Vector(0,0,-1)) == BlockFace.EAST;
        assert VanillaRailWalker.exitFace(Rail.Shape.SOUTH_WEST, new Vector(0,0,-1)) == BlockFace.WEST;
        assert VanillaRailWalker.exitFace(Rail.Shape.NORTH_EAST, new Vector(0,0,1)) == BlockFace.EAST;
        assert VanillaRailWalker.exitFace(Rail.Shape.NORTH_WEST, new Vector(0,0,1)) == BlockFace.WEST;
        assert VanillaRailWalker.exitFace(Rail.Shape.SOUTH_EAST, new Vector(-1,0,0)) == BlockFace.SOUTH;
        assert VanillaRailWalker.exitFace(Rail.Shape.NORTH_WEST, new Vector(1,0,0)) == BlockFace.NORTH;
        assert VanillaRailWalker.exitFace(Rail.Shape.ASCENDING_EAST, new Vector(1,1,0)) == BlockFace.EAST;
        assert VanillaRailWalker.exitFace(null, new Vector()) == null;
        var p = new SwitchBlockPosition("world", 0,64,0);
        var sw = new SkyTrainSwitch(UUID.randomUUID(),p,p,p,BlockFace.NORTH,BlockFace.WEST,BlockFace.EAST,
                BlockFace.SOUTH,SwitchGeometry.LEFT,Rail.Shape.EAST_WEST,Rail.Shape.SOUTH_WEST,
                SwitchState.STRAIGHT,SwitchState.STRAIGHT,"203",null,0);
        UUID train = UUID.randomUUID();
        assert sw.reserve(train,SwitchPort.DIVERGING,SwitchState.DIVERGING,0) == SkyTrainSwitch.Reservation.ACQUIRED;
        assert sw.reserve(UUID.randomUUID(),SwitchPort.COMMON,SwitchState.STRAIGHT,0) == SkyTrainSwitch.Reservation.BUSY;
        var mutation = sw.beginMutation(SwitchState.DIVERGING);
        assert !sw.routeReady(train) && sw.mutationCurrent(mutation.version(),mutation.state());
        sw.finishMutation(mutation.version(),mutation.state(),true);
        assert sw.routeReady(train) && !sw.mutationCurrent(mutation.version(),mutation.state());
        World world = RailSignAccessTest.proxy(World.class,(m,a)->m.equals("getName")?"world":null);
        UUID member = UUID.randomUUID();
        var monitor = new SwitchPassageMonitor();
        List<SwitchPassageMonitor.Suspect> events = new ArrayList<>();
        var straight = sw.beginMutation(SwitchState.STRAIGHT);
        sw.finishMutation(straight.version(),straight.state(),true);
        monitor.observe(List.of(sample(world,member,.5,1.2,1000)),List.of(sw),1000,events::add);
        monitor.observe(List.of(sample(world,member,.5,.9,1050)),List.of(sw),1050,events::add);
        assert events.size()==1 && events.getFirst().entry()==SwitchPort.DIVERGING;
        monitor.observe(List.of(sample(world,member,.5,.8,1100)),List.of(sw),1100,events::add);
        assert events.size()==1;
        monitor.observe(List.of(sample(world,member,4,.5,1200)),List.of(sw),1200,events::add);
        var correct = sw.beginMutation(SwitchState.DIVERGING);
        sw.finishMutation(correct.version(),correct.state(),true);
        monitor.observe(List.of(sample(world,member,.5,1.2,1300)),List.of(sw),1300,events::add);
        monitor.observe(List.of(sample(world,member,.5,.9,1350)),List.of(sw),1350,events::add);
        assert events.size()==1 : "Successful early conversion must not be run-through";
        var other = new SwitchPassageMonitor();
        other.observe(List.of(sample(world,member,-.2,.5,2000)),List.of(sw),2000,events::add);
        other.observe(List.of(sample(world,member,.1,.5,2050)),List.of(sw),2050,events::add);
        assert events.size()==1 : "Common-side entry is not trailing run-through";
        other.observe(List.of(sample(world,member,1.2,.5,2100)),List.of(sw),2100,events::add);
        other.observe(List.of(sample(world,member,.9,.5,3000)),List.of(sw),3000,events::add);
        assert events.size()==1 : "Do not infer crossings across sampling gaps";
        System.out.println("PASS curve exits, reversed approach, slope, lock readiness, stale mutation, observed passage dedup");
    }
    private static MemberSnapshot sample(World world,UUID id,double x,double z,long now) {
        return new MemberSnapshot(id,new Location(world,x,64.06,z),new Vector(0,0,-1),now);
    }
}
