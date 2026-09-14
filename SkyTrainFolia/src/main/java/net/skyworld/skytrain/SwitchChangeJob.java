package net.skyworld.skytrain;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Serialises expiry with the physical write so an expired request cannot mutate later. */
final class SwitchChangeJob {
    final CompletableFuture<String> result=new CompletableFuture<>();
    private final LongSupplier clock;
    private final long deadline;
    private boolean applied;
    SwitchChangeJob(LongSupplier clock,long timeoutMillis) {
        this.clock=clock;deadline=clock.getAsLong()+timeoutMillis;
    }
    synchronized boolean active() {
        if(clock.getAsLong()>=deadline) finish(applied?"UNCONFIRMED":"EXPIRED");
        return !result.isDone();
    }
    synchronized void run(Runnable work) { if(active()) work.run(); }
    synchronized boolean mayWrite() { return !result.isDone() && clock.getAsLong()<deadline; }
    synchronized void applied() { applied=true; }
    synchronized void expire() { finish(applied?"UNCONFIRMED":"EXPIRED"); }
    synchronized void fail(String reason) { finish(applied?"UNCONFIRMED":reason); }
    synchronized void finish(String status) { result.complete(status); }
}
