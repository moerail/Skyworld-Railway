package net.skyworld.sta.api.v1;

public record CargoItem(String itemKey, int amount) {
    public CargoItem {
        itemKey = itemKey == null ? "" : itemKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (itemKey.isEmpty() || amount < 1) {
            throw new IllegalArgumentException("Cargo item key and amount are required.");
        }
    }
}
