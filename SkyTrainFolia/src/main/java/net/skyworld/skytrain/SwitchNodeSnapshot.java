package net.skyworld.skytrain;

import java.util.Map;
import java.util.UUID;

import org.bukkit.block.BlockFace;

/** Immutable pathfinding-facing view. Runtime locking stays inside SwitchManager. */
record SwitchNodeSnapshot(UUID id, SwitchBlockPosition pivot, Map<SwitchPort, BlockFace> ports) {
    SwitchNodeSnapshot {
        ports = Map.copyOf(ports);
    }
}
