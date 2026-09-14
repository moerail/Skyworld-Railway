package net.skyworld.skytrain;

record TrainProtectionStatus(Double distanceToNextBaliseBlocks, Double rbcPermittedSpeedBlocksPerTick) {
    static TrainProtectionStatus unavailable() {
        return new TrainProtectionStatus(null, null);
    }
}
