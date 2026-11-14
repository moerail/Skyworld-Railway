package net.skyworld.skytrain;

/** Ownership failure retires the input device; a rejected handle command does not. */
final class HotbarInputGate {
    private HotbarInputGate() { }

    static void requireDriver(Runnable ownershipCheck, Runnable disable) {
        try {
            ownershipCheck.run();
        } catch (IllegalArgumentException ex) {
            disable.run();
            throw ex;
        }
    }
}
