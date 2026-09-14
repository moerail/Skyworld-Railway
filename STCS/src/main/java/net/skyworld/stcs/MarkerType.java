package net.skyworld.stcs;

import java.util.Locale;

enum MarkerType {
    ORIGIN,
    END,
    BALISE,
    SIGNAL,
    STATION;

    static MarkerType parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    String storageName() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean scansBothDirections() {
        // Boundary direction belongs to line inference, not physical track discovery.
        return true;
    }
}
