package net.skyworld.sta.api.v3;

import java.util.Collection;
import java.util.UUID;

/** Cached observations only. No world access, MA or train-integrity guarantee. */
public interface ConsistObservationService {
    default int consistProtocolVersion() { return 3; }
    UUID sessionId();
    Collection<ConsistObservation> consistObservations();
}
