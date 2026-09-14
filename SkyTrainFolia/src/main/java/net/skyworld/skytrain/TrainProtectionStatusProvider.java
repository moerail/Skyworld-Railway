package net.skyworld.skytrain;

@FunctionalInterface
interface TrainProtectionStatusProvider {
    TrainProtectionStatus status(Train train);
}
