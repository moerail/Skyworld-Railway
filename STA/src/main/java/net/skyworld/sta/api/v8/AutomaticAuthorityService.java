package net.skyworld.sta.api.v8;

import java.util.*;
import java.util.concurrent.CompletionStage;

/** STCS is the sole authority. A requested route is never itself a movement permission. */
public interface AutomaticAuthorityService {
    CompletionStage<String> request(UUID train, UUID driver, UUID lease, long revision, List<String> edges, long sequence);
    CompletionStage<String> release(UUID train, UUID driver, UUID lease, long sequenceFence);
    /** Creates a pending SR request; only OperationalAuthorityService.approveSr grants its destination. */
    CompletionStage<String> requestSr(UUID train, UUID driver, UUID lease, long revision, long sequence);
    /** Manual default-FS request; validated against the current declared driver desk. */
    CompletionStage<String> requestFs(UUID train, UUID driver, UUID lease);
    /** Silent route-lease renewal. Cannot create a path, grant MA, change the route or revive a released lease. */
    default CompletionStage<String> renew(UUID train,UUID driver,UUID lease,long revision,long sequence) {
        return java.util.concurrent.CompletableFuture.completedFuture("UNSUPPORTED");
    }
    default CompletionStage<String> renew(AtoProtocol.Header h,UUID train,UUID driver,UUID lease,long revision,long sequence) {
        h.require(AtoProtocol.Packet.ROUTE_HEARTBEAT);return renew(train,driver,lease,revision,sequence);
    }

    default CompletionStage<String> request(AtoProtocol.Header h,UUID train,UUID driver,UUID lease,long revision,List<String> edges,long sequence) {
        h.require(AtoProtocol.Packet.AUTHORITY_REQUEST);return request(train,driver,lease,revision,List.copyOf(edges),sequence);
    }
    default CompletionStage<String> release(AtoProtocol.Header h,UUID train,UUID driver,UUID lease,long sequenceFence) {
        h.require(AtoProtocol.Packet.AUTHORITY_RELEASE);return release(train,driver,lease,sequenceFence);
    }
    default CompletionStage<String> requestSr(AtoProtocol.Header h,UUID train,UUID driver,UUID lease,long revision,long sequence) {
        h.require(AtoProtocol.Packet.SR_REQUEST);return requestSr(train,driver,lease,revision,sequence);
    }
    default CompletionStage<String> requestFs(AtoProtocol.Header h,UUID train,UUID driver,UUID lease) {
        h.require(AtoProtocol.Packet.FS_REQUEST);return requestFs(train,driver,lease);
    }
}
