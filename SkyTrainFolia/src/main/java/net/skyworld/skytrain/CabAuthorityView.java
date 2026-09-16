package net.skyworld.skytrain;

/** Already validated display data, never executable permission to move. */
record CabAuthorityView(boolean live, String reason, Double remainingMeters, double creditMeters, String eoaLocation) {
    static CabAuthorityView unavailable() { return new CabAuthorityView(false, "STALE", null, 0, null); }
}
