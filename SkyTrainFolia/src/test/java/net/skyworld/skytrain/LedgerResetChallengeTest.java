package net.skyworld.skytrain;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LedgerResetChallengeTest {
    public static void main(String[] args) {
        AtomicLong now = new AtomicLong(100);
        LedgerResetChallenge gate = new LedgerResetChallenge(() -> 42, now::get);
        assert gate.issue("alice", false) == null;
        assert !gate.consume("alice", "000042", true);
        assert gate.issue("alice", true).equals("000042");
        assert !gate.consume("bob", "000042", true) : "The challenge belongs to one operator";
        assert !gate.consume("alice", "000042", false) : "A newly registered train blocks reset";
        assert gate.issue("alice", true).equals("000042");
        assert !gate.consume("alice", "123456", true) : "A wrong attempt consumes the challenge";
        assert !gate.consume("alice", "000042", true);
        assert gate.issue("alice", true).equals("000042");
        now.addAndGet(TimeUnit.MINUTES.toNanos(1));
        assert !gate.consume("alice", "000042", true) : "Expired at exactly one minute";
        assert gate.issue("alice", true).equals("000042");
        assert gate.consume("alice", "000042", true);
        assert !gate.consume("alice", "000042", true) : "One use only";
        assert gate.issue("alice", true).equals("000042");
        assert gate.issue("alice", false) == null : "Nonempty /st list revokes an older challenge";
        assert !gate.consume("alice", "000042", true);
        System.out.println("PASS six digits, empty list gate, operator binding, expiry and one-use consumption");
    }
}
