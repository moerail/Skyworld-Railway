package net.skyworld.sta.api.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StaContractsTest {
    private StaContractsTest() {
    }

    public static void main(String[] args) {
        UUID trainId = UUID.randomUUID();
        TrainTelemetrySnapshot telemetry = new TrainTelemetrySnapshot(
                trainId, "Freight 01", 7L, 1_000L, "world",
                10, 64, 20, 10.5, 64.1, 20.5,
                1.0, 0.0, 0.0, 8.0, 12.0, 10,
                true, false, TrainMode.MANUAL, "Driver");
        TrackPositionSnapshot track = new TrackPositionSnapshot(
                42L, "a:east:b:west", "a", "b", 25.0, 100.0,
                "main:up", 1_250.0, 1_000L, true, false);
        TrackedTrainSnapshot tracked = new TrackedTrainSnapshot(telemetry, track);
        require(tracked.telemetry().trainId().equals(trainId), "tracked train ID");

        FreightContractSnapshot contract = new FreightContractSnapshot(
                UUID.randomUUID(), "warehouse_a", "shop_b",
                List.of(new CargoItem("minecraft:iron_ingot", 64)),
                1_000L, 10_000L, 20_000L, 12_500L, "vault:money",
                FreightContractStatus.AVAILABLE, null, null,
                Map.of("generator", "test"));
        require(contract.cargo().getFirst().amount() == 64, "cargo amount");
        require(StaApi.VERSION == 1, "API version");
        System.out.println("StaContractsTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
