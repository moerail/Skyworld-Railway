package net.skyworld.sta.api.v5;

import java.util.*;
import java.util.concurrent.CompletionStage;

/** Trusted plugin command surface. HTTP authentication belongs to the caller's gateway. */
public interface SwitchControlService {
    CompletionStage<Reply> change(Request request);
    /** Read-only progress; null means no known job. PENDING is not a completed conversion. */
    default Reply status(UUID requestId) { return null; }
    record Position(String world, int x, int y, int z) {
        public Position { if(world==null || world.isBlank()) throw new IllegalArgumentException("Missing world"); }
    }
    record Request(UUID requestId, UUID switchId, long graphRevision, String expectedState, String targetState, Position position) {
        /** Legacy callers must upgrade: commands without a position are rejected by STCS. */
        public Request(UUID requestId, UUID switchId, long graphRevision, String expectedState, String targetState) {
            this(requestId,switchId,graphRevision,expectedState,targetState,null);
        }
        public Request {
            Objects.requireNonNull(requestId); Objects.requireNonNull(switchId);
            if(graphRevision<0 || !Set.of("straight","diverging").contains(expectedState)
                    || !Set.of("straight","diverging").contains(targetState) || expectedState.equals(targetState))
                throw new IllegalArgumentException("Invalid switch request");
        }
    }
    record Reply(UUID requestId, String status, String reason) {
        public Reply { Objects.requireNonNull(requestId); Objects.requireNonNull(status); Objects.requireNonNull(reason); }
    }
}
