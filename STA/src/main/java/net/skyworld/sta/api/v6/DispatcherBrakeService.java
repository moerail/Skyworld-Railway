package net.skyworld.sta.api.v6;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Onboard acknowledgement. HELD means traction is latched off on the owning train tick. */
public interface DispatcherBrakeService {
    CompletionStage<String> hold(UUID trainId, UUID operationId, boolean stoppedOnly);
    /** Releases only the matching temporary reroute hold, never a dispatcher Trip. */
    boolean resume(UUID trainId, UUID operationId);
    default boolean stoppedHeld(UUID trainId, UUID operationId) { return false; }
}
