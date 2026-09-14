package net.skyworld.stcs;

import java.util.List;
import java.util.Locale;

/** The single grammar for protection commands and their completion choices. */
record ProtectionCommand(String action, boolean enabled) {
    static List<String> actions() { return List.of("isolate", "bypass", "shadow", "status"); }
    static List<String> values(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "mode" -> List.of("status");
            case "isolate", "bypass", "shadow" -> List.of("true", "false");
            default -> List.of();
        };
    }
    static ProtectionCommand parse(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("status"))
            return new ProtectionCommand("status", false);
        if (args.length != 3 || !args[0].equalsIgnoreCase("admin")) throw new IllegalArgumentException("protection.invalid");
        String action = args[1].toLowerCase(Locale.ROOT), value = args[2].toLowerCase(Locale.ROOT);
        if (!values(action).contains(value)) throw new IllegalArgumentException("protection.invalid");
        return new ProtectionCommand(action.equals("mode") ? "status" : action, value.equals("true"));
    }
}
