package net.skyworld.sta.api.v5;

/** Registered by STCS. Snapshot quality is refreshed independently of incoming telemetry. */
public interface TrackingService extends MessageSource {
    boolean sourceAvailable();
}
