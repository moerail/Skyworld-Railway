package net.skyworld.skytrain;

import java.util.Locale;

enum SpeedUnit {
    KPH("kph", "km/h", 72.0),
    MPH("mph", "mph", 44.73872584),
    BLOCK_PER_TICK("block/tick", "block/tick", 1.0);

    final String code;
    final String label;
    private final double multiplier;

    SpeedUnit(String code, String label, double multiplier) {
        this.code = code;
        this.label = label;
        this.multiplier = multiplier;
    }

    double convert(double blocksPerTick) {
        return Math.max(0.0, blocksPerTick) * multiplier;
    }

    static SpeedUnit fromCode(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        return switch (clean) {
            case "kph", "kmh", "km/h" -> KPH;
            case "mph" -> MPH;
            case "blocktick", "block/tick", "bpt", "blockspertick" -> BLOCK_PER_TICK;
            default -> throw new IllegalArgumentException("Unknown speed unit: " + value);
        };
    }
}
