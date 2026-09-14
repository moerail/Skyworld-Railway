package net.skyworld.skypcc;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.skyworld.sta.api.v4.SwitchControlService;

/** Bounded idempotent jobs. HTTP threads only enqueue; the service runs on the async scheduler. */
final class SwitchGateway {
    record Job(SwitchControlService.Request request, long at, CompletableFuture<SwitchControlService.Reply> result) {}
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    synchronized CompletableFuture<SwitchControlService.Reply> submit(SwitchControlService.Request request,
            SwitchControlService service, Consumer<Runnable> schedule, long now) {
        Job existing = jobs.get(request.requestId());
        if (existing != null) return existing.request().equals(request) ? existing.result()
                : reply(request, "REJECTED", "ID_CONFLICT");
        jobs.entrySet().removeIf(e -> e.getValue().result().isDone() && now - e.getValue().at() > 600_000);
        if (jobs.size() >= 256 || jobs.values().stream().anyMatch(j -> now - j.at() < 1000))
            return reply(request, "REJECTED", "RATE_LIMIT");
        if (service == null) return reply(request, "REJECTED", "SERVICE_UNAVAILABLE");
        var result = new CompletableFuture<SwitchControlService.Reply>();
        jobs.put(request.requestId(), new Job(request, now, result));
        try {
            schedule.accept(() -> {
                try { service.change(request).whenComplete((r, e) -> {
                    if (e == null && r != null) result.complete(r);
                    else result.complete(new SwitchControlService.Reply(request.requestId(), "FAILED", "SERVICE_ERROR"));
                }); } catch (RuntimeException ex) { result.complete(new SwitchControlService.Reply(request.requestId(), "FAILED", "SERVICE_ERROR")); }
            });
        } catch (RuntimeException ex) { result.complete(new SwitchControlService.Reply(request.requestId(), "FAILED", "SCHEDULER_UNAVAILABLE")); }
        return result.completeOnTimeout(new SwitchControlService.Reply(request.requestId(), "UNCONFIRMED", "TIMEOUT"), 45, TimeUnit.SECONDS);
    }
    SwitchControlService.Reply progress(SwitchControlService.Request request,SwitchControlService service) {
        if(service!=null) {
            var progress=service.status(request.requestId());
            if(progress!=null && progress.requestId().equals(request.requestId()))return progress;
        }
        return new SwitchControlService.Reply(request.requestId(),"PENDING","QUEUED");
    }
    private static CompletableFuture<SwitchControlService.Reply> reply(SwitchControlService.Request r, String status, String reason) {
        return CompletableFuture.completedFuture(new SwitchControlService.Reply(r.requestId(), status, reason));
    }
}
