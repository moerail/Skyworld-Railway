package net.skyworld.sta.api.v1;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Service normally registered by STF through Bukkit ServicesManager. */
public interface TrainTelemetryService {
    default int apiVersion() {
        return StaApi.VERSION;
    }

    Optional<TrainTelemetrySnapshot> snapshot(UUID trainId);

    Collection<TrainTelemetrySnapshot> snapshots();

    Subscription subscribe(TrainTelemetryListener listener);
}
