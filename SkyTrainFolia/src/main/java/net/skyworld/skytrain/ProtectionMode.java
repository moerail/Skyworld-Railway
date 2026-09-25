package net.skyworld.skytrain;

/** Game-specific modes; not ETCS wire mode identifiers. */
enum ProtectionMode {
    SHADOW, ACTIVE, ISOLATED, BYPASS, RECOVERING;

    static ProtectionMode restore(String value) {
        try { return valueOf(value); } catch (RuntimeException ex) { return RECOVERING; }
    }
    ProtectionMode change(String action, boolean enabled, boolean stopped) {
        ProtectionMode target = switch (action) {
            case "isolate" -> ISOLATED;
            case "bypass" -> BYPASS;
            case "shadow" -> SHADOW;
            case "enforce" -> ACTIVE;
            default -> throw new IllegalArgumentException("protection.invalid");
        };
        if (!enabled) return RECOVERING;
        if (this == target) return this;
        if (this != RECOVERING) throw new IllegalArgumentException("protection.disableFirst");
        if (!stopped) throw new IllegalArgumentException("protection.stop");
        return target;
    }
    boolean controlChannel() { return this != ISOLATED; }
    void requireTraction(OperatingMode operatingMode) {
        if (this == RECOVERING || (this == ACTIVE && !operatingMode.permitsTraction()))
            throw new IllegalArgumentException("protection.tractionBlocked");
    }
}
