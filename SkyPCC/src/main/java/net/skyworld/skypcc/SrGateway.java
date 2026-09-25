package net.skyworld.skypcc;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

/** Idempotent local dispatcher request; STCS retains final authority over reachability and conflicts. */
final class SrGateway {
    record Request(UUID requestId, UUID trainId, UUID targetNodeId, long graphRevision) {
        Request {
            Objects.requireNonNull(requestId); Objects.requireNonNull(trainId); Objects.requireNonNull(targetNodeId);
            if (graphRevision < 0) throw new IllegalArgumentException("Invalid graph revision");
        }
    }
    record Result(UUID requestId, String status, String reason) { }
    private record Job(Request request, long createdAt, Result result) { }
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    Result submit(Request request, long currentRevision, Set<UUID> pending,
            OperationalAuthorityService service, Consumer<Runnable> scheduler, long now) {
        jobs.entrySet().removeIf(e -> now - e.getValue().createdAt() > 60000);
        Job prior = jobs.get(request.requestId());
        if (prior != null) return prior.request().equals(request) ? prior.result()
                : new Result(request.requestId(), "REJECTED", "REQUEST_ID_CONFLICT");
        if (jobs.size() >= 256) return new Result(request.requestId(), "REJECTED", "BUSY");
        if (service == null || currentRevision < 0)
            return new Result(request.requestId(), "REJECTED", "UNAVAILABLE");
        if (request.graphRevision() != currentRevision)
            return new Result(request.requestId(), "REJECTED", "GRAPH_CHANGED");
        if (!pending.contains(request.trainId()))
            return new Result(request.requestId(), "REJECTED", "NO_PENDING_SR");
        Job job = new Job(request, now, new Result(request.requestId(), "PENDING", "VALIDATING"));
        Job race = jobs.putIfAbsent(request.requestId(), job);
        if (race != null) return race.request().equals(request) ? race.result()
                : new Result(request.requestId(), "REJECTED", "REQUEST_ID_CONFLICT");
        try {
            scheduler.accept(() -> {
                String reason;
                try { reason = service.approveSr(request.trainId(), request.targetNodeId(), "SkyPCC"); }
                catch (RuntimeException | LinkageError ex) { reason = "UNAVAILABLE"; }
                Result completed = new Result(request.requestId(),
                        reason.equals("APPROVED") ? "APPLIED" : "REJECTED", reason);
                jobs.computeIfPresent(request.requestId(), (id, existing) ->
                        new Job(existing.request(), existing.createdAt(), completed));
            });
        } catch (RuntimeException ex) {
            jobs.remove(request.requestId(), job);
            return new Result(request.requestId(), "REJECTED", "UNAVAILABLE");
        }
        return job.result();
    }
}
