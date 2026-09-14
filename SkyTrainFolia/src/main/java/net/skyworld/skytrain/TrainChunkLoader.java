package net.skyworld.skytrain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Keeps a small moving corridor loaded around every active consist. */
final class TrainChunkLoader {
    private final SkyTrainPlugin plugin;
    private final Object lock = new Object();
    private final Map<UUID, Set<ChunkKey>> trainTickets = new HashMap<>();
    private final Map<ChunkKey, Integer> references = new HashMap<>();

    TrainChunkLoader(SkyTrainPlugin plugin) {
        this.plugin = plugin;
    }

    void update(Train train, Location leaderLocation, int radius) {
        if (train == null || leaderLocation == null || leaderLocation.getWorld() == null) {
            return;
        }

        Set<ChunkKey> desired = new HashSet<>();
        addCorridor(desired, leaderLocation.getWorld().getName(), leaderLocation.getBlockX() >> 4,
                leaderLocation.getBlockZ() >> 4, radius);
        for (MemberSnapshot snapshot : train.snapshots()) {
            if (snapshot.worldName == null || snapshot.worldName.isBlank()) {
                continue;
            }
            addCorridor(desired, snapshot.worldName, ((int) Math.floor(snapshot.x)) >> 4,
                    ((int) Math.floor(snapshot.z)) >> 4, radius);
        }
        replace(train.id(), desired);
    }

    void release(UUID trainId) {
        replace(trainId, Set.of());
    }

    void shutdown() {
        synchronized (lock) {
            trainTickets.clear();
            references.clear();
        }
        for (World world : Bukkit.getWorlds()) {
            world.removePluginChunkTickets(plugin);
        }
    }

    private void replace(UUID trainId, Set<ChunkKey> desired) {
        synchronized (lock) {
            Set<ChunkKey> previous = trainTickets.getOrDefault(trainId, Set.of());
            if (previous.equals(desired)) {
                return;
            }

            for (ChunkKey key : desired) {
                if (previous.contains(key)) {
                    continue;
                }
                int count = references.getOrDefault(key, 0);
                references.put(key, count + 1);
                if (count == 0) {
                    World world = Bukkit.getWorld(key.worldName());
                    if (world != null) {
                        world.addPluginChunkTicket(key.x(), key.z(), plugin);
                    }
                }
            }
            for (ChunkKey key : previous) {
                if (desired.contains(key)) {
                    continue;
                }
                int count = references.getOrDefault(key, 0) - 1;
                if (count <= 0) {
                    references.remove(key);
                    World world = Bukkit.getWorld(key.worldName());
                    if (world != null) {
                        world.removePluginChunkTicket(key.x(), key.z(), plugin);
                    }
                } else {
                    references.put(key, count);
                }
            }

            if (desired.isEmpty()) {
                trainTickets.remove(trainId);
            } else {
                trainTickets.put(trainId, Set.copyOf(desired));
            }
        }
    }

    private static void addCorridor(Set<ChunkKey> result, String worldName, int centerX, int centerZ, int radius) {
        int safeRadius = Math.max(0, radius);
        for (int x = centerX - safeRadius; x <= centerX + safeRadius; x++) {
            for (int z = centerZ - safeRadius; z <= centerZ + safeRadius; z++) {
                result.add(new ChunkKey(worldName, x, z));
            }
        }
    }

    private record ChunkKey(String worldName, int x, int z) {
    }
}
