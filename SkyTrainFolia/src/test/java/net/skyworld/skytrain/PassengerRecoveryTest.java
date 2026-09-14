package net.skyworld.skytrain;

public final class PassengerRecoveryTest {
    public static void main(String[] args) {
        PassengerRecovery recovery = new PassengerRecovery();
        long grace = 300_000_000L;
        check(!recovery.shouldRecover(1.5, 1.25, 4, grace, 0), "brief curve error must not teleport");
        check(!recovery.shouldRecover(1.1, 1.25, 4, grace, grace), "recovering error must not teleport");
        check(!recovery.shouldRecover(0.8, 1.25, 4, grace, grace + 1), "hysteresis clears");
        check(!recovery.shouldRecover(1.5, 1.25, 4, grace, grace + 2), "new excursion gets full grace");
        check(recovery.shouldRecover(1.5, 1.25, 4, grace, 2 * grace + 2), "persistent error recovers");
        recovery.reset();
        check(recovery.shouldRecover(4, 1.25, 4, grace, 0), "hard error immediately recovers");
        check(!recovery.shouldRecover(Double.NaN, 1.25, 4, grace, 0), "invalid error cannot teleport");
        check(recovery.shouldRecover(2, 1.25, 4, 0, 0), "zero grace allows immediate recovery");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
