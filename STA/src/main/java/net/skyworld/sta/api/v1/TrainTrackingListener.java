package net.skyworld.sta.api.v1;

@FunctionalInterface
public interface TrainTrackingListener {
    void onTrainTracking(TrainTrackingEvent event);
}
