package net.skyworld.stcs;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ShadowLedgerResetTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shadow-ledger-reset");
        Path file = dir.resolve("shadow-occupancy.json");
        try {
            String corrupt = "\0\0\0\0";
            Files.writeString(file, corrupt);
            Path backup = ShadowLedgerReset.reset(file, "CONSOLE");
            assert backup != null && Files.readString(backup).equals(corrupt);
            assert ShadowOccupancyStore.load(file).isEmpty();
            assert Files.readString(file).contains("\"version\":2");
            assert Files.readString(dir.resolve("shadow-ledger-reset-audit.log"))
                    .contains("EMPTY_SHADOW_LEDGER_WRITTEN_RESTART_REQUIRED");
            Files.delete(file);
            Path absentBackup = ShadowLedgerReset.reset(file, "CONSOLE");
            assert absentBackup == null && ShadowOccupancyStore.load(file).isEmpty();
        } finally {
            try (var files = Files.list(dir)) {
                for (Path path : files.toList()) Files.deleteIfExists(path);
            }
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS corrupt ledger preserved, empty v2 replacement, audit and missing source");
    }
}
