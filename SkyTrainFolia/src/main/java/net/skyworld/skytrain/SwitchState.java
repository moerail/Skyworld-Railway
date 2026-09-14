package net.skyworld.skytrain;

import java.util.Locale;

enum SwitchState {
    STRAIGHT,
    DIVERGING;

    static SwitchState parse(String value, SwitchState fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "straight", "normal", "direct" -> STRAIGHT;
            case "diverging", "branch", "reverse" -> DIVERGING;
            default -> fallback;
        };
    }

    String storageName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
