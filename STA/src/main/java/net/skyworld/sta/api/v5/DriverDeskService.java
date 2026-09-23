package net.skyworld.sta.api.v5;

import java.util.*;

/** Live driver declarations, not passenger names or a zero/nonzero speed heuristic. */
public interface DriverDeskService {
    UUID sessionId();
    Collection<Desk> driverDesks();
    record Desk(UUID trainId, UUID driverId, UUID leaseId, String atpMode) {
        public Desk { Objects.requireNonNull(trainId); Objects.requireNonNull(driverId);
            Objects.requireNonNull(leaseId); Objects.requireNonNull(atpMode); }
    }
}
