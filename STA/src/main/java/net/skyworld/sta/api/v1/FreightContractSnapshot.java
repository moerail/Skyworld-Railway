package net.skyworld.sta.api.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FreightContractSnapshot(
        UUID contractId,
        String originEndpointId,
        String destinationEndpointId,
        List<CargoItem> cargo,
        long createdAtMillis,
        long availableUntilMillis,
        long deliveryDeadlineMillis,
        long rewardMinorUnits,
        String currencyKey,
        FreightContractStatus status,
        UUID assigneePlayerId,
        UUID assignedTrainId,
        Map<String, String> metadata) {

    public FreightContractSnapshot {
        if (contractId == null || status == null) {
            throw new IllegalArgumentException("Contract ID and status are required.");
        }
        originEndpointId = required(originEndpointId, "originEndpointId");
        destinationEndpointId = required(destinationEndpointId, "destinationEndpointId");
        currencyKey = required(currencyKey, "currencyKey");
        cargo = cargo == null ? List.of() : List.copyOf(cargo);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (cargo.isEmpty() || createdAtMillis < 0 || availableUntilMillis < createdAtMillis
                || deliveryDeadlineMillis < availableUntilMillis || rewardMinorUnits < 0) {
            throw new IllegalArgumentException("Invalid freight contract values.");
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        return normalized;
    }
}
