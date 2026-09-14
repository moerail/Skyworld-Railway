package net.skyworld.skytrain;

record NextBaliseSnapshot(String name, String position, double distanceMeters) {
    static NextBaliseSnapshot unavailable() {
        return new NextBaliseSnapshot(null, null, Double.NaN);
    }

    boolean available() {
        return name != null && position != null && Double.isFinite(distanceMeters);
    }
}
