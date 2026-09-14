package net.skyworld.skytrain;

import java.util.Locale;

enum InfrastructureMarkerType {
    ORIGIN,
    BALISE,
    END;

    static InfrastructureMarkerType parse(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "origin" -> ORIGIN;
            case "balise", "eurobalise" -> BALISE;
            case "end" -> END;
            default -> null;
        };
    }

    String storageName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
