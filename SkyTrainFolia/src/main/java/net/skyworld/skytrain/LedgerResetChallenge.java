package net.skyworld.skytrain;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** One-use, operator-bound proof of a recent empty /st list. Not proof of physical track clearance. */
final class LedgerResetChallenge {
    private static final long VALID_NANOS = TimeUnit.MINUTES.toNanos(1);
    private record Entry(String code, long issuedAtNanos) {}

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final IntSupplier digits;
    private final LongSupplier clock;

    LedgerResetChallenge() {
        SecureRandom random = new SecureRandom();
        digits = () -> random.nextInt(1_000_000);
        clock = System::nanoTime;
    }

    LedgerResetChallenge(IntSupplier digits, LongSupplier clock) {
        this.digits = digits;
        this.clock = clock;
    }

    String issue(String operator, boolean trainListEmpty) {
        entries.remove(operator);
        if (!trainListEmpty) return null;
        String code = String.format(java.util.Locale.ROOT, "%06d", digits.getAsInt());
        entries.put(operator, new Entry(code, clock.getAsLong()));
        return code;
    }

    boolean consume(String operator, String code, boolean trainListEmpty) {
        Entry entry = entries.remove(operator);
        if (!trainListEmpty || entry == null || code == null || !code.matches("[0-9]{6}")) return false;
        long age = clock.getAsLong() - entry.issuedAtNanos();
        return age >= 0 && age < VALID_NANOS && entry.code().equals(code);
    }

    void clear() { entries.clear(); }
}
