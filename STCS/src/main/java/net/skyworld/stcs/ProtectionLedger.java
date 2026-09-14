package net.skyworld.stcs;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Experimental M1 kernel. Only consumes proven physical-resource identities, never display edges. */
final class ProtectionLedger {
    enum Quality { LIVE_CONFIRMED, FROZEN_CONFIRMED, UNCERTAIN, RECOVERING }
    record Entry(UUID train, long sequence, Quality quality, Set<String> occupied, Set<String> reserved) {
        Entry {
            Objects.requireNonNull(train); Objects.requireNonNull(quality);
            if (sequence < 0) throw new IllegalArgumentException("negative sequence");
            occupied = checked(occupied); reserved = checked(reserved);
        }
        private static Set<String> checked(Set<String> values) {
            if (values == null || values.stream().anyMatch(s -> s == null || s.isBlank())) throw new IllegalArgumentException("invalid resources");
            return Set.copyOf(values);
        }
    }
    private final Path file;
    private Map<UUID, Entry> entries;
    private long revision;

    ProtectionLedger(Path file) throws IOException {
        this.file = file;
        entries = new HashMap<>();
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("schema");
            revision = root.get("revision").getAsLong();
            if (revision < 0) throw new IllegalArgumentException("revision");
            for (JsonElement element : root.getAsJsonArray("trains")) {
                JsonObject e = element.getAsJsonObject();
                UUID id = UUID.fromString(e.get("train").getAsString());
                Entry entry = new Entry(id, e.get("sequence").getAsLong(), Quality.RECOVERING,
                        strings(e.getAsJsonArray("occupied")), strings(e.getAsJsonArray("reserved")));
                if (entries.put(id, entry) != null) throw new IllegalArgumentException("duplicate train");
            }
        } catch (RuntimeException ex) { throw new IOException("Invalid protection ledger; do not open protected operation", ex); }
    }

    synchronized Map<UUID, Entry> snapshot() { return Map.copyOf(entries); }

    /** Observations can only add occupancy. Clearing requires a separate tail-clear proof. */
    synchronized void observe(UUID train, long sequence, Quality quality, Set<String> occupied) throws IOException {
        Entry old = entries.get(train);
        if (old != null && sequence <= old.sequence()) throw new IllegalArgumentException("stale observation");
        Set<String> combined = new HashSet<>(occupied);
        if (old != null) combined.addAll(old.occupied());
        commit(new Entry(train, sequence, quality, combined, old == null ? Set.of() : old.reserved()));
    }

    synchronized boolean reserve(UUID train, long basedOnSequence, Set<String> requested, Map<String, Set<String>> conflicts) throws IOException {
        Entry owner = entries.get(train);
        if (owner == null || owner.sequence() != basedOnSequence || owner.quality() != Quality.LIVE_CONFIRMED) return false;
        Entry.checked(requested);
        // Unknown conflict tables are not evidence of independent resources.
        if (requested.stream().anyMatch(r -> !conflicts.containsKey(r))) return false;
        for (Entry other : entries.values()) if (!other.train().equals(train)) {
            Set<String> used = new HashSet<>(other.occupied()); used.addAll(other.reserved());
            for (String r : requested) for (String u : used) {
                if (!conflicts.containsKey(u) || r.equals(u) || conflicts.get(r).contains(u) || conflicts.get(u).contains(r)) return false;
            }
        }
        Set<String> reserved = new HashSet<>(owner.reserved()); reserved.addAll(requested);
        commit(new Entry(train, owner.sequence(), owner.quality(), owner.occupied(), reserved));
        return true;
    }

    /** Caller must validate full rear-envelope evidence against the same observation. */
    synchronized void releaseCleared(UUID train, long observation, Set<String> cleared) throws IOException {
        Entry old = entries.get(train);
        if (old == null || old.sequence() != observation || old.quality() != Quality.LIVE_CONFIRMED)
            throw new IllegalArgumentException("No current live tail-clear evidence");
        Set<String> occupied = new HashSet<>(old.occupied()); occupied.removeAll(cleared);
        Set<String> reserved = new HashSet<>(old.reserved()); reserved.removeAll(cleared);
        commit(new Entry(train, old.sequence(), old.quality(), occupied, reserved));
    }

    private void commit(Entry entry) throws IOException {
        Map<UUID, Entry> candidate = new HashMap<>(entries); candidate.put(entry.train(), entry);
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("revision", revision + 1);
        JsonArray rows = new JsonArray();
        for (Entry e : candidate.values().stream().sorted(Comparator.comparing(v -> v.train().toString())).toList()) {
            JsonObject row = new JsonObject(); row.addProperty("train", e.train().toString()); row.addProperty("sequence", e.sequence());
            row.addProperty("quality", e.quality().name());
            row.add("occupied", array(e.occupied())); row.add("reserved", array(e.reserved())); rows.add(row);
        }
        root.add("trains", rows);
        Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "ledger-", ".tmp");
        try {
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            entries = candidate; revision++;
        } finally { Files.deleteIfExists(temp); }
    }
    private static JsonArray array(Set<String> values) { JsonArray a = new JsonArray(); values.stream().sorted().forEach(a::add); return a; }
    private static Set<String> strings(JsonArray a) { Set<String> s = new HashSet<>(); for (JsonElement e : a) if (!s.add(e.getAsString())) throw new IllegalArgumentException("duplicate resource"); return s; }
}
