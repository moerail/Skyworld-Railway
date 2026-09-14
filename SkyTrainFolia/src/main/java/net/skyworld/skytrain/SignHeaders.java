package net.skyworld.skytrain;

final class SignHeaders {
    private SignHeaders() {
    }

    static boolean isSkyTrain(String value) {
        String name = name(value);
        return "skytrain".equals(name) || "stf".equals(name);
    }

    static boolean isAlwaysActive(String value) {
        return mode(value) == Mode.ALWAYS;
    }

    static boolean isNeverActive(String value) {
        return mode(value) == Mode.NEVER;
    }

    static boolean isInverted(String value) {
        return mode(value) == Mode.INVERTED;
    }

    static String display(String value) {
        String name = "stf".equals(name(value)) ? "STF" : "SkyTrain";
        return "[" + mode(value).prefix + name + "]";
    }

    private static Mode mode(String value) {
        String inner = inner(value);
        if (inner.isEmpty()) {
            return Mode.NORMAL;
        }
        return switch (inner.charAt(0)) {
            case '+' -> Mode.ALWAYS;
            case '-' -> Mode.NEVER;
            case '!' -> Mode.INVERTED;
            default -> Mode.NORMAL;
        };
    }

    private static String name(String value) {
        String inner = inner(value);
        while (!inner.isEmpty() && "+-!".indexOf(inner.charAt(0)) >= 0) {
            inner = inner.substring(1);
        }
        return inner.toLowerCase(java.util.Locale.ROOT);
    }

    private static String inner(String value) {
        String stripped = org.bukkit.ChatColor.stripColor(value == null ? "" : value);
        String trimmed = stripped == null ? "" : stripped.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return "";
        }
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private enum Mode {
        NORMAL(""),
        INVERTED("!"),
        ALWAYS("+"),
        NEVER("-");

        final String prefix;

        Mode(String prefix) {
            this.prefix = prefix;
        }
    }
}
