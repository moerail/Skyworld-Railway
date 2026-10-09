package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

/** One steep Minecraft rail block represents only one car's share of train resistance. */
public final class SlopeLaunchTest {
    public static void main(String[] args) throws Exception {
        RailSignAccessTest.main(args);
        RailSignAccessTest.loaded = true;
        RailSignAccessTest.owned = true;
        UUID worldId = UUID.randomUUID();
        RailSignAccessTest.world = RailSignAccessTest.proxy(org.bukkit.World.class, (method, values) -> switch (method) {
            case "getName" -> "slope-world";
            case "getUID" -> worldId;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> true;
            case "getBlockAt" -> values[0] instanceof Location location
                    ? RailSignAccessTest.block(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                    : RailSignAccessTest.block((int) values[0], (int) values[1], (int) values[2]);
            default -> null;
        });
        RailSignAccessTest.data.clear();
        for (int z = -12; z <= 12; z++) {
            Rail.Shape shape = z == 0 ? Rail.Shape.ASCENDING_SOUTH : Rail.Shape.NORTH_SOUTH;
            int y = z <= 0 ? 64 : 65;
            RailSignAccessTest.data.put(RailSignAccessTest.key(0, y, z),
                    RailSignAccessTest.proxy(Rail.class,
                            (method, values) -> method.equals("getShape") ? shape : null));
        }
        double cap = 0.04;
        for (boolean reversed : new boolean[] {false, true}) {
            Vector direction = new Vector(0, 0, reversed ? -1 : 1);
            Location leader = new Location(RailSignAccessTest.world, .5, 64.5625, .5);
            TrainRailPath path = TrainRailPath.create(leader, direction, reversed, 1.15 * 5);
            assert path != null : "Consist path across a one-block ramp";
            double grade = path.effectiveGrade(6, 1.15, reversed, cap);
            assert (reversed ? grade < -0.001 : grade > 0.001) : "Slope must be assigned to the travelling train";
            assert Math.abs(grade) <= cap / 6.0 + 1e-9 : "Only one member may bear slope resistance: " + grade;
            assert path.move(.01, reversed) : "Train must be able to launch without a run-up";
            double moved = path.lastMoveDistance();
            assert moved > .009 : "The first startup increment must advance";
        }
        System.out.println("PASS one-car slope grade share and forward/reverse startup path");
    }
}
