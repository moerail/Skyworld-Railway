package net.skyworld.skytrain;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

final class TrainTrackPosition {
    final String worldName;
    final int railX;
    final int railY;
    final int railZ;
    final double x;
    final double y;
    final double z;
    final double motionX;
    final double motionY;
    final double motionZ;
    final Rail.Shape railShape;

    TrainTrackPosition(Block railBlock, Location location, Vector motion) {
        this(railBlock, location, motion, railShape(railBlock));
    }

    TrainTrackPosition(Block railBlock, Location location, Vector motion, Rail.Shape railShape) {
        World world = railBlock.getWorld();
        this.worldName = world.getName();
        this.railX = railBlock.getX();
        this.railY = railBlock.getY();
        this.railZ = railBlock.getZ();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();

        Vector normalized = motion == null ? new Vector() : motion.clone();
        if (normalized.lengthSquared() > 0.0001) {
            normalized.normalize();
        }
        this.motionX = normalized.getX();
        this.motionY = normalized.getY();
        this.motionZ = normalized.getZ();
        this.railShape = railShape;
    }

    Location location(World world) {
        return new Location(world, x, y, z);
    }

    Vector motion() {
        return new Vector(motionX, motionY, motionZ);
    }

    TrainTrackPosition with(Location location, Vector motion) {
        return new TrainTrackPosition(
                worldName,
                railX,
                railY,
                railZ,
                location,
                motion,
                railShape);
    }

    private TrainTrackPosition(String worldName, int railX, int railY, int railZ,
            Location location, Vector motion, Rail.Shape railShape) {
        this.worldName = worldName;
        this.railX = railX;
        this.railY = railY;
        this.railZ = railZ;
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();

        Vector normalized = motion == null ? new Vector() : motion.clone();
        if (normalized.lengthSquared() > 0.0001) {
            normalized.normalize();
        }
        this.motionX = normalized.getX();
        this.motionY = normalized.getY();
        this.motionZ = normalized.getZ();
        this.railShape = railShape;
    }

    private static Rail.Shape railShape(Block railBlock) {
        BlockData data = railBlock.getBlockData();
        return data instanceof Rail rail ? rail.getShape() : null;
    }
}
