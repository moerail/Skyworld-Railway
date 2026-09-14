package net.skyworld.skytrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

final class VanillaRailWalker {
    private static final double RAIL_Y = 0.0625;

    private RailInfo rail;
    private Location position;
    private Vector direction;
    private Rail.Shape shapeOverride;

    private VanillaRailWalker(RailInfo rail, Location position, Vector direction, Rail.Shape shapeOverride) {
        this.rail = rail;
        this.position = position;
        this.direction = direction.lengthSquared() < 0.0001 ? new Vector(0, 0, 1) : direction.clone().normalize();
        this.shapeOverride = shapeOverride;
    }

    static VanillaRailWalker at(Location location, Vector preference) {
        RailInfo rail = RailMath.findRail(location);
        if (rail == null) {
            return null;
        }

        Path path = path(rail);
        Vector direction = RailMath.direction(rail.rail.getShape(), preference);
        if (direction.lengthSquared() < 0.0001) {
            direction = chord(path);
        }
        Location projected = project(location, rail.block, path, direction);
        return new VanillaRailWalker(rail, projected, direction, null);
    }

    static VanillaRailWalker from(TrainTrackPosition trackPosition) {
        if (trackPosition == null) {
            return null;
        }

        World world = Bukkit.getWorld(trackPosition.worldName);
        if (world == null) {
            return null;
        }

        Block block = world.getBlockAt(trackPosition.railX, trackPosition.railY, trackPosition.railZ);
        RailInfo rail = RailMath.findRail(block);
        if (rail == null) {
            return null;
        }

        Vector direction = trackPosition.motion();
        if (direction.lengthSquared() < 0.0001) {
            direction = RailMath.direction(rail.rail.getShape(), new Vector());
        }
        Location location = trackPosition.location(world);
        Rail.Shape shape = trackPosition.railShape == null ? rail.rail.getShape() : trackPosition.railShape;
        Location projected = project(location, rail.block, path(shape), direction);
        return new VanillaRailWalker(rail, projected, direction, shape);
    }

    VanillaRailWalker copy() {
        return new VanillaRailWalker(rail, position.clone(), direction.clone(), shapeOverride);
    }

    void invert() {
        direction.multiply(-1.0);
    }

    Location position() {
        return position.clone();
    }

    Vector direction() {
        return direction.clone();
    }

    TrainTrackPosition trackPosition() {
        return new TrainTrackPosition(rail.block, position, direction, currentShape());
    }

    static BlockFace exitFace(Rail.Shape shape, Vector direction) {
        return shape == null ? null : chooseExit(path(shape), direction).face();
    }

    boolean move(double distance) {
        double remaining = Math.max(0.0, distance);
        int guard = 0;
        while (remaining > 0.0001 && guard++ < 128) {
            Path path = path(currentShape());
            Vector local = position.toVector().subtract(blockOrigin(rail.block));
            Endpoint exit = chooseExit(path, direction);
            Vector[] points = path.pointsToward(exit);
            Projection projection = projectToPolyline(local, points);
            double toExitDistance = Math.max(0.0, pathLength(points) - projection.distance());

            if (toExitDistance >= remaining) {
                WalkPoint next = pointAtDistance(points, projection.distance() + remaining);
                position = toWorldLocation(rail.block, next.point());
                if (next.tangent().lengthSquared() > 0.0001) {
                    direction = next.tangent().clone().normalize();
                }
                return true;
            }

            position = toWorldLocation(rail.block, exit.point());
            remaining -= toExitDistance;
            RailInfo nextRail = nextRail(rail.block, exit.face());
            if (nextRail == null) {
                return false;
            }
            if (!enterRail(nextRail, exit.face().getOppositeFace())) {
                return false;
            }
        }
        return remaining <= 0.0001;
    }

    private boolean enterRail(RailInfo next, BlockFace entryFace) {
        Path nextPath = path(next);
        Endpoint entry = nextPath.endpoint(entryFace);
        if (entry != null) {
            rail = next;
            shapeOverride = null;
            position = toWorldLocation(next.block, entry.point());
            direction = initialDirection(nextPath, entry);
            return direction.lengthSquared() >= 0.0001;
        }

        rail = next;
        shapeOverride = null;
        direction = RailMath.direction(rail.rail.getShape(), direction);
        if (direction.lengthSquared() < 0.0001) {
            return false;
        }
        position = project(position, rail.block, nextPath, direction);
        return true;
    }

    private static Location project(Location location, Block railBlock, Path path, Vector direction) {
        Vector local = location.toVector().subtract(blockOrigin(railBlock));
        Endpoint exit = chooseExit(path, direction);
        Projection projection = projectToPolyline(local, path.pointsToward(exit));
        if (projection.tangent().lengthSquared() > 0.0001) {
            direction.copy(projection.tangent().clone().normalize());
        }
        return toWorldLocation(railBlock, projection.point());
    }

    private static RailInfo nextRail(Block block, BlockFace face) {
        Block base = block.getRelative(face);
        RailInfo info = RailMath.findRail(base);
        if (info != null) {
            return info;
        }
        info = RailMath.findRail(base.getRelative(BlockFace.UP));
        if (info != null) {
            return info;
        }
        return RailMath.findRail(base.getRelative(BlockFace.DOWN));
    }

    private static Endpoint chooseExit(Path path, Vector direction) {
        Vector axis = chord(path);
        return direction != null && axis.dot(direction) < 0.0 ? path.from() : path.to();
    }

    private static Vector initialDirection(Path path, Endpoint entry) {
        Vector[] points = path.points();
        if (points.length < 2) {
            return new Vector();
        }
        if (entry == path.from()) {
            return points[1].clone().subtract(points[0]).normalize();
        }
        return points[points.length - 2].clone().subtract(points[points.length - 1]).normalize();
    }

    private static Projection projectToPolyline(Vector local, Vector[] points) {
        if (points.length == 0) {
            return new Projection(new Vector(0.5, RAIL_Y, 0.5), new Vector(0, 0, 1), 0.0);
        }
        if (points.length == 1) {
            return new Projection(points[0].clone(), new Vector(0, 0, 1), 0.0);
        }

        Projection best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        double walked = 0.0;
        for (int i = 0; i < points.length - 1; i++) {
            Vector start = points[i];
            Vector end = points[i + 1];
            Vector segment = end.clone().subtract(start);
            double segmentLengthSquared = segment.lengthSquared();
            double segmentLength = Math.sqrt(segmentLengthSquared);
            double t = segmentLengthSquared < 0.0001
                    ? 0.0
                    : local.clone().subtract(start).dot(segment) / segmentLengthSquared;
            t = RailMath.clamp(t, 0.0, 1.0);

            Vector point = start.clone().add(segment.clone().multiply(t));
            double distanceSquared = distanceSquared(point, local);
            if (distanceSquared < bestDistanceSquared) {
                Vector tangent = segmentLengthSquared < 0.0001 ? new Vector() : segment.clone().normalize();
                best = new Projection(point, tangent, walked + segmentLength * t);
                bestDistanceSquared = distanceSquared;
            }
            walked += segmentLength;
        }
        return best == null ? new Projection(points[0].clone(), new Vector(0, 0, 1), 0.0) : best;
    }

    private static WalkPoint pointAtDistance(Vector[] points, double distance) {
        if (points.length == 0) {
            return new WalkPoint(new Vector(0.5, RAIL_Y, 0.5), new Vector(0, 0, 1));
        }
        if (points.length == 1) {
            return new WalkPoint(points[0].clone(), new Vector(0, 0, 1));
        }

        double remaining = RailMath.clamp(distance, 0.0, pathLength(points));
        for (int i = 0; i < points.length - 1; i++) {
            Vector start = points[i];
            Vector end = points[i + 1];
            Vector segment = end.clone().subtract(start);
            double length = segment.length();
            if (length < 0.0001) {
                continue;
            }
            if (remaining <= length) {
                Vector tangent = segment.clone().normalize();
                Vector point = start.clone().add(segment.multiply(remaining / length));
                return new WalkPoint(point, tangent);
            }
            remaining -= length;
        }

        Vector tangent = points[points.length - 1].clone().subtract(points[points.length - 2]);
        if (tangent.lengthSquared() > 0.0001) {
            tangent.normalize();
        }
        return new WalkPoint(points[points.length - 1].clone(), tangent);
    }

    private static double pathLength(Vector[] points) {
        double length = 0.0;
        for (int i = 0; i < points.length - 1; i++) {
            length += points[i].distance(points[i + 1]);
        }
        return length;
    }

    private static double distanceSquared(Vector a, Vector b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Location toWorldLocation(Block block, Vector local) {
        World world = block.getWorld();
        return new Location(world,
                block.getX() + local.getX(),
                block.getY() + local.getY(),
                block.getZ() + local.getZ());
    }

    private static Vector blockOrigin(Block block) {
        return new Vector(block.getX(), block.getY(), block.getZ());
    }

    private static Vector chord(Path path) {
        return path.to().point().clone().subtract(path.from().point());
    }

    private static Path path(RailInfo rail) {
        return path(rail.rail.getShape());
    }

    private Rail.Shape currentShape() {
        return shapeOverride == null ? rail.rail.getShape() : shapeOverride;
    }

    private static Path path(Rail.Shape shape) {
        return switch (shape) {
            case EAST_WEST -> path(e(0.0, RAIL_Y, 0.5, BlockFace.WEST), e(1.0, RAIL_Y, 0.5, BlockFace.EAST));
            case ASCENDING_EAST -> path(e(0.0, RAIL_Y, 0.5, BlockFace.WEST), e(1.0, 1.0 + RAIL_Y, 0.5, BlockFace.EAST));
            case ASCENDING_WEST -> path(e(1.0, RAIL_Y, 0.5, BlockFace.EAST), e(0.0, 1.0 + RAIL_Y, 0.5, BlockFace.WEST));
            case ASCENDING_NORTH -> path(e(0.5, RAIL_Y, 1.0, BlockFace.SOUTH), e(0.5, 1.0 + RAIL_Y, 0.0, BlockFace.NORTH));
            case ASCENDING_SOUTH -> path(e(0.5, RAIL_Y, 0.0, BlockFace.NORTH), e(0.5, 1.0 + RAIL_Y, 1.0, BlockFace.SOUTH));
            case SOUTH_EAST -> curve(
                    e(0.5, RAIL_Y, 1.0, BlockFace.SOUTH),
                    e(1.0, RAIL_Y, 0.5, BlockFace.EAST),
                    1.0, 1.0, Math.PI, Math.PI * 1.5);
            case SOUTH_WEST -> curve(
                    e(0.5, RAIL_Y, 1.0, BlockFace.SOUTH),
                    e(0.0, RAIL_Y, 0.5, BlockFace.WEST),
                    0.0, 1.0, 0.0, -Math.PI * 0.5);
            case NORTH_WEST -> curve(
                    e(0.5, RAIL_Y, 0.0, BlockFace.NORTH),
                    e(0.0, RAIL_Y, 0.5, BlockFace.WEST),
                    0.0, 0.0, 0.0, Math.PI * 0.5);
            case NORTH_EAST -> curve(
                    e(0.5, RAIL_Y, 0.0, BlockFace.NORTH),
                    e(1.0, RAIL_Y, 0.5, BlockFace.EAST),
                    1.0, 0.0, Math.PI, Math.PI * 0.5);
            case NORTH_SOUTH -> path(e(0.5, RAIL_Y, 0.0, BlockFace.NORTH), e(0.5, RAIL_Y, 1.0, BlockFace.SOUTH));
            default -> path(e(0.5, RAIL_Y, 0.0, BlockFace.NORTH), e(0.5, RAIL_Y, 1.0, BlockFace.SOUTH));
        };
    }

    private static Path path(Endpoint from, Endpoint to) {
        return new Path(from, to, new Vector[] { from.point(), to.point() });
    }

    private static Path curve(Endpoint from, Endpoint to, double centerX, double centerZ,
            double startAngle, double endAngle) {
        final int steps = 8;
        Vector[] points = new Vector[steps + 1];
        points[0] = from.point();
        for (int i = 1; i < steps; i++) {
            double ratio = i / (double) steps;
            double angle = startAngle + (endAngle - startAngle) * ratio;
            points[i] = new Vector(
                    centerX + 0.5 * Math.cos(angle),
                    RAIL_Y,
                    centerZ + 0.5 * Math.sin(angle));
        }
        points[steps] = to.point();
        return new Path(from, to, points);
    }

    private static Endpoint e(double x, double y, double z, BlockFace face) {
        return new Endpoint(new Vector(x, y, z), face);
    }

    private record Endpoint(Vector point, BlockFace face) {
    }

    private record Path(Endpoint from, Endpoint to, Vector[] points) {
        private Endpoint endpoint(BlockFace face) {
            if (from.face() == face) {
                return from;
            }
            if (to.face() == face) {
                return to;
            }
            return null;
        }

        private Endpoint other(Endpoint endpoint) {
            return endpoint == from ? to : from;
        }

        private Vector[] pointsToward(Endpoint exit) {
            Vector[] oriented = new Vector[points.length];
            if (exit == to) {
                for (int i = 0; i < points.length; i++) {
                    oriented[i] = points[i].clone();
                }
            } else {
                for (int i = 0; i < points.length; i++) {
                    oriented[i] = points[points.length - 1 - i].clone();
                }
            }
            return oriented;
        }
    }

    private record Projection(Vector point, Vector tangent, double distance) {
    }

    private record WalkPoint(Vector point, Vector tangent) {
    }
}
