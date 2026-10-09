package net.skyworld.skytrain;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Pure onboard latch. Requests are consumed only by the leader's motion tick. */
final class DispatcherBrake {
    private UUID operation;
    private boolean trip, held;
    private long deadline;
    private CompletableFuture<String> reply;

    synchronized CompletableFuture<String> request(UUID id, boolean stoppedOnly, long now) {
        if (operation != null) {
            if (operation.equals(id)) return reply;
            return CompletableFuture.completedFuture("BUSY");
        }
        operation = id; trip = !stoppedOnly; deadline = now + 5000;
        reply = new CompletableFuture<>();
        return reply;
    }
    void apply(boolean manual, boolean stopped, boolean tripped, long now,
            java.util.function.Consumer<Boolean> action) {
        CompletableFuture<String> result;
        String status;
        synchronized (this) {
        if (operation == null || reply.isDone()) return;
        String rejection = now > deadline ? "EXPIRED" : !manual ? "MANUAL_ONLY"
                : !trip && (tripped || !stopped) ? "STOP_FIRST" : null;
        result=reply;
        if (rejection != null) { operation=null; status=rejection; }
        else { held = true; action.accept(trip); status=trip ? "TR" : "HELD"; }
        }
        result.complete(status);
    }
    synchronized boolean held() { return held; }
    synchronized boolean held(UUID id) { return held && id.equals(operation); }
    synchronized boolean trip() { return held && trip; }
    synchronized boolean resume(UUID id) {
        if (!held || trip || !operation.equals(id)) return false;
        clear(); return true;
    }
    synchronized void clear() {
        if(operation!=null && reply!=null && !reply.isDone()) return;
        operation=null; held=false; trip=false;
    }
}
