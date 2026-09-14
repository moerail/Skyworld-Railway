package net.skyworld.sta.api.v1;

@FunctionalInterface
public interface TrainTelemetryListener {
    void onTrainTelemetry(TrainTelemetryEvent event);
}
