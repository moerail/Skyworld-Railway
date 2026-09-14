package net.skyworld.skytrain;

public final class ProtectionModeTest {
    public static void main(String[] args) {
        UiMessages ui = new UiMessages(null);
        for (UiLanguage language : UiLanguage.values()) {
            check(!ui.text(language, "protection.tractionBlocked").equals("protection.tractionBlocked"));
            for (String key : new String[]{"atp", "eoa", "ma", "rbc", "train", "notImplemented", "isolated", "noStcs", "noRbc", "alpha", "permission", "select", "stop", "invalid", "compatible", "disableFirst"}) {
                String id = "protection." + key;
                check(!ui.text(language, id).equals(id));
            }
            for (ProtectionMode mode : ProtectionMode.values()) {
                String key = "protection.mode." + mode;
                check(!ui.text(language, key).equals(key));
                check(ui.text(language, key).length() <= 30);
            }
        }
        check(CabSidebar.LINE_COUNT == 14);
        for (ProtectionMode mode : ProtectionMode.values()) {
            if (mode == ProtectionMode.RECOVERING) rejectsKey(mode::requireTraction, "protection.tractionBlocked");
            else mode.requireTraction();
            check(mode.controlChannel() == (mode != ProtectionMode.ISOLATED));
            for (String action : new String[]{"isolate", "bypass", "shadow"}) {
                ProtectionMode target = switch (action) {
                    case "isolate" -> ProtectionMode.ISOLATED;
                    case "bypass" -> ProtectionMode.BYPASS;
                    default -> ProtectionMode.SHADOW;
                };
                for (boolean stopped : new boolean[]{false, true}) {
                    check(mode.change(action, false, stopped) == ProtectionMode.RECOVERING);
                    if (mode == target) check(mode.change(action, true, stopped) == mode);
                    else if (mode == ProtectionMode.RECOVERING && stopped) check(mode.change(action, true, true) == target);
                    else rejectsKey(() -> mode.change(action, true, stopped),
                            mode == ProtectionMode.RECOVERING ? "protection.stop" : "protection.disableFirst");
                }
            }
            rejectsKey(() -> mode.change("mode shadow", true, true), "protection.invalid");
            rejectsKey(() -> mode.change("unknown", false, false), "protection.invalid");
        }
        check(ProtectionMode.ISOLATED.change("isolate", false, true) == ProtectionMode.RECOVERING);
        check(ProtectionMode.BYPASS.change("bypass", false, true) == ProtectionMode.RECOVERING);
        check(ProtectionMode.ISOLATED.change("bypass", false, true) == ProtectionMode.RECOVERING);
        check(ProtectionMode.restore("broken") == ProtectionMode.RECOVERING);
        System.out.println("Protection mode exclusivity, restore and stop gates passed");
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void rejectsKey(Runnable r, String key) {
        try { r.run(); } catch (IllegalArgumentException expected) { check(key.equals(expected.getMessage())); return; }
        throw new AssertionError("Expected rejection: " + key);
    }
    static void rejects(Runnable r) { try { r.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(); }
}
