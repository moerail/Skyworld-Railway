package net.skyworld.stcs;

import java.nio.file.*;
import java.util.*;

public final class ProtectionLedgerTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("stcs-ledger-test");
        Path file = dir.resolve("ledger.json");
        try {
            var ledger = new ProtectionLedger(file);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            var conflicts = Map.of("E1", Set.<String>of(), "E2", Set.<String>of(), "X", Set.of("E1"));
            ledger.observe(a, 1, ProtectionLedger.Quality.LIVE_CONFIRMED, Set.of("E1"));
            ledger.observe(b, 1, ProtectionLedger.Quality.LIVE_CONFIRMED, Set.of());
            check(!ledger.reserve(b, 1, Set.of("E1"), conflicts));
            check(!ledger.reserve(b, 1, Set.of("X"), conflicts));
            check(ledger.reserve(a, 1, Set.of("E2"), conflicts));
            ledger.observe(a, 2, ProtectionLedger.Quality.FROZEN_CONFIRMED, Set.of());
            check(!ledger.reserve(b, 1, Set.of("E2"), conflicts));
            rejects(() -> ledger.releaseCleared(a, 2, Set.of("E1")));
            var recovered = new ProtectionLedger(file);
            check(recovered.snapshot().get(a).quality() == ProtectionLedger.Quality.RECOVERING);
            check(!recovered.reserve(b, 1, Set.of("E1"), conflicts));
            recovered.observe(a, 3, ProtectionLedger.Quality.LIVE_CONFIRMED, Set.of("E2"));
            recovered.releaseCleared(a, 3, Set.of("E1"));
            recovered.observe(b, 2, ProtectionLedger.Quality.LIVE_CONFIRMED, Set.of());
            check(recovered.reserve(b, 2, Set.of("E1"), conflicts));
            check(!recovered.reserve(b, 2, Set.of("UNKNOWN"), conflicts));
            Files.writeString(file, "broken");
            try { new ProtectionLedger(file); throw new AssertionError(); } catch (java.io.IOException expected) {}
            System.out.println("Ledger conflicts, frozen retention, restore and corruption checks passed");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
    }
    interface Op { void run() throws Exception; }
    static void rejects(Op r) throws Exception { try { r.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(); }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
