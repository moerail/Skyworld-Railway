package net.skyworld.stcs;

import java.util.*;
import java.util.concurrent.TimeUnit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.skyworld.sta.api.v3.*;

/** Single-writer observational adapter. Intentionally grants no clearance or authority. */
final class OccupancyRuntime implements OccupancyMonitor {
    private final StcsPlugin plugin;
    private final RailGraphManager manager;
    private final ProtectionLedger ledger;
    private final ScheduledTask task;
    private final long staleMillis;
    private RailGraph indexedGraph;
    private PhysicalResources resources;
    private boolean closed, failed;
    private volatile Map<String, Object> status;
    OccupancyRuntime(StcsPlugin plugin, RailGraphManager manager) throws java.io.IOException {
        this.plugin = plugin; this.manager = manager;
        ledger = new ProtectionLedger(plugin.getDataFolder().toPath().resolve("occupancy-ledger.json"));
        staleMillis = Math.max(1000, plugin.getConfig().getLong("occupancy.stale-after-ms", 3000));
        status = describe("STARTING", List.of());
        long interval = Math.max(250, Math.min(5000, plugin.getConfig().getLong("occupancy.poll-interval-ms", 500)));
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, unused -> tick(), interval, interval, TimeUnit.MILLISECONDS);
    }
    private synchronized void tick() {
        if (closed || failed) return;
        try {
            var service = plugin.getServer().getServicesManager().load(ConsistObservationService.class);
            if (service == null || service.consistProtocolVersion() != 3) {
                status = describe("NO_PROVIDER", List.of()); return;
            }
            RailGraph graph = manager.occupancyGraph();
            if (indexedGraph != graph) { resources = new PhysicalResources(graph); indexedGraph = graph; }
            long now = System.currentTimeMillis();
            List<Map<String, Object>> observations = new ArrayList<>();
            for (var train : service.consistObservations()) {
                if (!service.sessionId().equals(train.session())) continue;
                Set<String> occupied = new HashSet<>();
                int stale = 0, unmapped = 0, inactive = 0;
                Set<UUID> seen = new HashSet<>();
                for (var member : train.members()) {
                    seen.add(member.id());
                    var candidates = resources.candidates(member.world(), member.x(), member.y(), member.z());
                    occupied.addAll(candidates);
                    if (candidates.isEmpty()) unmapped++;
                    if (member.observedAtMillis() > now || now - member.observedAtMillis() > staleMillis) stale++;
                    if (member.state() != ConsistObservation.State.OBSERVED) inactive++;
                }
                // No atomic full-consist/swept-path proof yet. Even fresh data stays UNCERTAIN.
                OccupancyAccumulator.retain(ledger, train.train(), occupied);
                observations.add(Map.of("train", train.train().toString(), "name", train.name(),
                        "expected", train.expectedMembers().size(), "observed", train.members().size(),
                        "missing", train.expectedMembers().stream().filter(id -> !seen.contains(id)).count(),
                        "stale", stale, "inactive", inactive, "unmapped", unmapped,
                        "sampleFresh", train.sampledAtMillis() <= now && now - train.sampledAtMillis() <= staleMillis,
                        "removed", train.removed()));
            }
            status = describe("OBSERVATIONAL_ONLY", observations);
        } catch (LinkageError ex) {
            status = describe("INCOMPATIBLE_STA", List.of());
        } catch (Exception ex) {
            failed = true;
            status = describe("FAILED", List.of());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "M1 occupancy stopped after failure. Retained data is NOT track-clear evidence.", ex);
        }
    }
    private Map<String, Object> describe(String state, List<Map<String, Object>> observations) {
        List<Map<String, Object>> entries = ledger.snapshot().values().stream()
                .sorted(Comparator.comparing(e -> e.train().toString()))
                .map(e -> Map.<String, Object>of("train", e.train().toString(), "quality", e.quality().name(),
                        "sequence", e.sequence(), "occupied", e.occupied().stream().sorted().toList(),
                        "reserved", e.reserved().stream().sorted().toList())).toList();
        return Map.of("schemaVersion", 1, "state", state, "maAvailable", false, "clearanceAvailable", false,
                "graphRevision", indexedGraph == null ? 0 : indexedGraph.revision,
                "trains", entries, "observations", List.copyOf(observations));
    }
    public Map<String, Object> status() { return status; }
    public synchronized void close() { closed = true; task.cancel(); }
}
