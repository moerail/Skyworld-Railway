package net.skyworld.skytrain;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

final class RailMath {
    private RailMath() {
    }

    static RailInfo findRail(Location location) {
        Block base = location.getBlock();
        RailInfo info = readRail(base);
        if (info != null) {
            return info;
        }
        return readRail(base.getRelative(BlockFace.DOWN));
    }

    static RailInfo findRail(Block block) {
        RailInfo info = readRail(block);
        if (info != null) {
            return info;
        }
        return readRail(block.getRelative(BlockFace.DOWN));
    }

    private static RailInfo readRail(Block block) {
        if (!RailSignAccess.readable(block)) return null;
        BlockData data = block.getBlockData();
        if (!(data instanceof Rail rail)) {
            return null;
        }
        Material type = block.getType();
        boolean poweredRail = type == Material.POWERED_RAIL;
        boolean powered = data instanceof Powerable powerable && powerable.isPowered();
        return new RailInfo(block, rail, poweredRail, powered);
    }

    static Vector direction(Rail.Shape shape, Vector preference) {
        Vector a;
        Vector b;
        switch (shape) {
            case EAST_WEST:
                a = new Vector(1, 0, 0);
                b = new Vector(-1, 0, 0);
                break;
            case ASCENDING_EAST:
                a = new Vector(1, 0.45, 0);
                b = new Vector(-1, -0.45, 0);
                break;
            case ASCENDING_WEST:
                a = new Vector(-1, 0.45, 0);
                b = new Vector(1, -0.45, 0);
                break;
            case ASCENDING_NORTH:
                a = new Vector(0, 0.45, -1);
                b = new Vector(0, -0.45, 1);
                break;
            case ASCENDING_SOUTH:
                a = new Vector(0, 0.45, 1);
                b = new Vector(0, -0.45, -1);
                break;
            case SOUTH_EAST:
                return curveDirection(preference, new Vector(1, 0, -1));
            case SOUTH_WEST:
                return curveDirection(preference, new Vector(-1, 0, -1));
            case NORTH_WEST:
                return curveDirection(preference, new Vector(-1, 0, 1));
            case NORTH_EAST:
                return curveDirection(preference, new Vector(1, 0, 1));
            case NORTH_SOUTH:
            default:
                a = new Vector(0, 0, 1);
                b = new Vector(0, 0, -1);
                break;
        }

        Vector flatPreference = preference == null ? new Vector() : preference.clone().setY(0);
        if (flatPreference.lengthSquared() < 0.0001) {
            return normalize(a);
        }
        return flatPreference.dot(a.clone().setY(0)) >= flatPreference.dot(b.clone().setY(0))
                ? normalize(a)
                : normalize(b);
    }

    static boolean isCurve(Rail.Shape shape) {
        return switch (shape) {
            case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> true;
            default -> false;
        };
    }

    static boolean isSlope(Rail.Shape shape) {
        return switch (shape) {
            case ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH, ASCENDING_SOUTH -> true;
            default -> false;
        };
    }

    static Vector yawDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static Vector normalize(Vector vector) {
        if (vector.lengthSquared() < 0.0001) {
            return new Vector(0, 0, 0);
        }
        return vector.normalize();
    }

    private static Vector curveDirection(Vector preference, Vector forward) {
        Vector a = normalize(forward);
        Vector b = a.clone().multiply(-1.0);
        Vector flatPreference = preference == null ? new Vector() : preference.clone().setY(0);
        if (flatPreference.lengthSquared() < 0.0001) {
            return a;
        }
        return flatPreference.dot(a) >= flatPreference.dot(b) ? a : b;
    }
}
