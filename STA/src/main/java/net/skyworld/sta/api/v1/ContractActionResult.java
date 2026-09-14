package net.skyworld.sta.api.v1;

public record ContractActionResult(boolean success, String code,
        FreightContractSnapshot contract) {
    public ContractActionResult {
        code = code == null || code.isBlank() ? (success ? "ok" : "failed") : code.trim();
    }
}
