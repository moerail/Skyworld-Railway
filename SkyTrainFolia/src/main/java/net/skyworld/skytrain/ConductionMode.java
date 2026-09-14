package net.skyworld.skytrain;

import java.util.Locale;

enum ConductionMode {
    MANUAL,
    AUTOMATIC;

    static ConductionMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("-", "").replace("_", "");
        return switch (normalized) {
            case "manual", "man", "human", "driver", "atooff" -> MANUAL;
            case "automatic", "auto", "ato", "driverless" -> AUTOMATIC;
            default -> throw new IllegalArgumentException(
                    "conduction-mode 只能是 manual 或 automatic。");
        };
    }

    String storageName() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean automatic() {
        return this == AUTOMATIC;
    }
}
