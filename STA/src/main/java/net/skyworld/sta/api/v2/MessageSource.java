package net.skyworld.sta.api.v2;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import net.skyworld.sta.api.v1.Subscription;

/** Read-only latest-state stream, NOT a durable event log. Intermediate updates coalesce.
 * Callbacks run away from entity/region threads and must return quickly. A snapshot may
 * contain a removal tombstone; consumers must process it. Session changes reset ordering. */
public interface MessageSource {
    default int protocolVersion() { return StaMessage.VERSION; }
    UUID sessionId();
    Collection<StaMessage> snapshots();
    Subscription subscribe(Consumer<StaMessage> listener);
}
