package net.skyworld.sta.api.v1;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Reserved service for a future SkyFreight implementation. */
public interface FreightContractService {
    default int apiVersion() {
        return StaApi.VERSION;
    }

    Collection<FreightEndpoint> endpoints();

    Collection<FreightContractSnapshot> availableContracts();

    Optional<FreightContractSnapshot> contract(UUID contractId);

    ContractActionResult accept(UUID contractId, UUID playerId, UUID trainId);

    ContractActionResult confirmPickup(UUID contractId, UUID playerId, UUID trainId);

    ContractActionResult confirmDelivery(UUID contractId, UUID playerId, UUID trainId,
            String endpointId);

    ContractActionResult abandon(UUID contractId, UUID playerId);

    Subscription subscribe(FreightContractListener listener);
}
