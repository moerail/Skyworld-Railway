package net.skyworld.sta.api.v1;

@FunctionalInterface
public interface FreightContractListener {
    void onFreightContract(FreightContractEvent event);
}
