package net.skyworld.stcs;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

final class RailGeometry {
    private RailGeometry() {
    }

    static Rail.Shape shape(Block block) {
        if (block != null) requireAvailable(block.getLocation());
        BlockData data = block == null ? null : block.getBlockData();
        return data instanceof Rail rail ? rail.getShape() : null;
    }

    static void requireAvailable(org.bukkit.Location location) {
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            // Normal scan deferral, never evidence that the track was deleted.
            throw new ChunkUnavailableException();
        }
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException("Rail chunk is owned by another region");
        }
    }

    static final class ChunkUnavailableException extends RuntimeException {
        ChunkUnavailableException() { super("Rail chunk is unloaded", null, false, false); }
    }

    static List<BlockFace> endpoints(Rail.Shape shape) {
        if (shape == null) {
            return List.of();
        }
        return switch (shape) {
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> List.of(BlockFace.WEST, BlockFace.EAST);
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> List.of(BlockFace.NORTH, BlockFace.SOUTH);
            case SOUTH_EAST -> List.of(BlockFace.SOUTH, BlockFace.EAST);
            case SOUTH_WEST -> List.of(BlockFace.SOUTH, BlockFace.WEST);
            case NORTH_WEST -> List.of(BlockFace.NORTH, BlockFace.WEST);
            case NORTH_EAST -> List.of(BlockFace.NORTH, BlockFace.EAST);
        };
    }

    static BlockFace chooseEndpoint(Rail.Shape shape, Vector preferredDirection) {
        List<BlockFace> endpoints = endpoints(shape);
        if (endpoints.isEmpty()) {
            return null;
        }
        if (preferredDirection == null || preferredDirection.lengthSquared() < 0.0001) {
            return endpoints.get(0);
        }
        BlockFace best = endpoints.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BlockFace endpoint : endpoints) {
            double score = endpoint.getModX() * preferredDirection.getX()
                    + endpoint.getModZ() * preferredDirection.getZ();
            if (score > bestScore) {
                best = endpoint;
                bestScore = score;
            }
        }
        return best;
    }

    static BlockFace exitFromEntry(Rail.Shape shape, BlockFace entryFace, Vector fallbackDirection) {
        List<BlockFace> endpoints = endpoints(shape);
        if (endpoints.size() != 2) {
            return null;
        }
        if (endpoints.get(0) == entryFace) {
            return endpoints.get(1);
        }
        if (endpoints.get(1) == entryFace) {
            return endpoints.get(0);
        }
        return chooseEndpoint(shape, fallbackDirection);
    }

    static Vector direction(BlockFace face) {
        return face == null ? new Vector() : new Vector(face.getModX(), 0.0, face.getModZ());
    }
}
