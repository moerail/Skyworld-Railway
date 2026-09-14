package net.skyworld.skytrain;

enum Reverser {
    FORWARD,
    NEUTRAL,
    BACKWARD;

    boolean wantsBackward() {
        return this == BACKWARD;
    }

    String displayName() {
        return switch (this) {
            case FORWARD -> "前进";
            case NEUTRAL -> "中立";
            case BACKWARD -> "反向";
        };
    }

    static Reverser fromStorage(String value, boolean fallbackReversed) {
        if (value == null || value.isBlank()) {
            return fallbackReversed ? BACKWARD : FORWARD;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "forward", "forwards", "f", "前进" -> FORWARD;
            case "neutral", "n", "中立" -> NEUTRAL;
            case "backward", "backwards", "reverse", "reversed", "b", "反向" -> BACKWARD;
            default -> fallbackReversed ? BACKWARD : FORWARD;
        };
    }
}
