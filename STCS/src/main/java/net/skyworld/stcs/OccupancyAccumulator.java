package net.skyworld.stcs;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** No release path exists in this observational adapter. */
final class OccupancyAccumulator {
    static void retain(ProtectionLedger ledger, UUID train, Set<String> occupied) throws IOException {
        var old = ledger.snapshot().get(train);
        var quality = old != null && old.quality() == ProtectionLedger.Quality.RECOVERING
                ? old.quality() : ProtectionLedger.Quality.UNCERTAIN;
        if (old == null || old.quality() != quality || !old.occupied().containsAll(occupied))
            ledger.observe(train, old == null ? 1 : old.sequence() + 1, quality, occupied);
    }
}
