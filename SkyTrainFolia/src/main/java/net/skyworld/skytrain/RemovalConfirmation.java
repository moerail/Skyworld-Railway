package net.skyworld.skytrain;

import java.util.*;
import java.util.function.Consumer;

/** Missing entities and scheduler retirement are not proof of physical removal. */
final class RemovalConfirmation implements Consumer<UUID> {
    private final Set<UUID> pending;
    private final Runnable complete;
    private boolean finished;

    RemovalConfirmation(Collection<UUID> expected, Runnable complete) {
        pending = new HashSet<>(expected);
        this.complete = complete;
        finished = pending.isEmpty();
    }

    public synchronized void accept(UUID member) {
        if (finished || !pending.remove(member)) return;
        if (pending.isEmpty()) {
            finished = true;
            complete.run();
        }
    }
}
