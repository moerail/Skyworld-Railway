package net.skyworld.skytrain;

record TrainMileageSnapshot(String lineName, boolean known, double meters, int travelSign,
        String lastBaliseName, double distanceSinceBaliseMeters, boolean inSignalRange) {
    static TrainMileageSnapshot unknown() {
        return new TrainMileageSnapshot(null, false, 0.0, 0, null, Double.POSITIVE_INFINITY, false);
    }
}
