package net.skyworld.sta.api.v2;

/** Registered by STCS. Snapshot quality is refreshed independently of incoming telemetry. */
public interface TrackingService extends MessageSource {
    boolean sourceAvailable();
}
