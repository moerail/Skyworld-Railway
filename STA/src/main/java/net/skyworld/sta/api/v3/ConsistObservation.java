package net.skyworld.sta.api.v3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Member centres may have different observation times; this is NOT a swept envelope. */
public record ConsistObservation(UUID session, long sequence, UUID train, String name,
        long sampledAtMillis, List<UUID> expectedMembers, List<Member> members, boolean removed) {
    public ConsistObservation {
        Objects.requireNonNull(session); Objects.requireNonNull(train); Objects.requireNonNull(name);
        if (sequence < 0 || sampledAtMillis < 0) throw new IllegalArgumentException("Invalid observation header");
        expectedMembers = List.copyOf(expectedMembers); members = List.copyOf(members);
        if (expectedMembers.stream().distinct().count() != expectedMembers.size()
                || members.stream().map(Member::id).distinct().count() != members.size())
            throw new IllegalArgumentException("Duplicate member");
    }
    public enum State { OBSERVED, UNLOADED, PLAYER_QUIT, REMOVED }
    public record Member(UUID id, String world, double x, double y, double z,
            long observedAtMillis, long stateAtMillis, State state) {
        public Member {
            Objects.requireNonNull(id); Objects.requireNonNull(state);
            if (world == null || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || observedAtMillis < 0 || stateAtMillis < 0)
                throw new IllegalArgumentException("Invalid member observation");
        }
    }
}
