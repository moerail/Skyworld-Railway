package net.skyworld.stcs;

public final class ProtectionCommandTest {
    public static void main(String[] args) {
        for (String action : new String[]{"shadow", "bypass", "isolate"}) {
            for (boolean enabled : new boolean[]{true, false}) {
                var request = ProtectionCommand.parse(new String[]{"admin", action, Boolean.toString(enabled)});
                check(request.action().equals(action) && request.enabled() == enabled);
            }
        }
        check(ProtectionCommand.parse(new String[]{"ADMIN", "SHADOW", "TRUE"}).enabled());
        check(ProtectionCommand.parse(new String[]{"admin", "mode", "status"}).action().equals("status"));
        check(ProtectionCommand.parse(new String[]{"ADMIN", "STATUS"}).equals(new ProtectionCommand("status", false)));
        check(ProtectionCommand.actions().contains("status"));
        check(!ProtectionCommand.actions().contains("mode"));
        check(ProtectionCommand.values("status").isEmpty());
        reject("admin", "status", "true");
        reject("admin", "status", "extra");
        reject("admin", "mode", "shadow");
        reject("admin", "mode", "true");
        reject("admin", "shadow");
        reject("admin", "shadow", "yes");
        reject("admin", "shadow", "true", "extra");
        reject("other", "shadow", "true");
        check(ProtectionCommand.actions().contains("shadow"));
        check(ProtectionCommand.values("mode").equals(java.util.List.of("status")));
        check(ProtectionCommand.values("unknown").isEmpty());
        System.out.println("Protection command grammar, completion and removed alias passed");
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void reject(String... args) {
        try { ProtectionCommand.parse(args); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unexpected accepted command");
    }
}
