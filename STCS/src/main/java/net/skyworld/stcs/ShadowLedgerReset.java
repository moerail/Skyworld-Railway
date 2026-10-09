package net.skyworld.stcs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Offline shadow-ledger replacement after an operator has verified track clearance. */
final class ShadowLedgerReset {
    private ShadowLedgerReset() {}

    static Path reset(Path file, String actor) throws IOException {
        Path backup = null;
        if (Files.exists(file)) {
            backup = file.resolveSibling(file.getFileName() + ".reset-" + UUID.randomUUID() + ".bak");
            Files.copy(file, backup); // Abort before replacement if evidence cannot be preserved.
        }
        Path audit = file.resolveSibling("shadow-ledger-reset-audit.log");
        Files.writeString(audit, Instant.now() + " actor=" + actor + " operatorAttestedAllFormerCartsAbsent=true backup="
                + (backup == null ? "NONE_FILE_ABSENT" : backup.getFileName())
                + " action=PREPARE_EMPTY_SHADOW_LEDGER\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        ShadowOccupancyStore.save(file, ShadowOccupancyStore.encode(Map.of()));
        Files.writeString(audit, Instant.now() + " actor=" + actor
                + " action=EMPTY_SHADOW_LEDGER_WRITTEN_RESTART_REQUIRED\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return backup;
    }
}
