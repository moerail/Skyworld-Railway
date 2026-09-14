package net.skyworld.sta.api.v1;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Service normally registered by STCS after resolving STF telemetry against RailGraph. */
public interface TrainTrackingService {
    default int apiVersion() {
        return StaApi.VERSION;
    }

    Optional<TrackedTrainSnapshot> snapshot(UUID trainId);

    Collection<TrackedTrainSnapshot> snapshots();

    Subscription subscribe(TrainTrackingListener listener);
}
