package net.skyworld.sta.api.v1;

import java.util.Objects;

public record FreightContractEvent(FreightContractSnapshot contract, long emittedAtMillis) {
    public FreightContractEvent {
        Objects.requireNonNull(contract, "contract");
    }
}
