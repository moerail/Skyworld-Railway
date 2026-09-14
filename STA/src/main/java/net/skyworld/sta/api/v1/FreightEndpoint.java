package net.skyworld.sta.api.v1;

import java.util.Set;

/** Cargo terminal linked to a stable STCS RailGraph node. */
public record FreightEndpoint(String endpointId, String displayName, String railNodeId,
        String world, double x, double y, double z, Set<String> tags) {
    public FreightEndpoint {
        endpointId = required(endpointId, "endpointId");
        displayName = required(displayName, "displayName");
        railNodeId = required(railNodeId, "railNodeId");
        world = required(world, "world");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Endpoint coordinates must be finite.");
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        return normalized;
    }
}
