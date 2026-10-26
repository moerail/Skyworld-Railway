package net.skyworld.skytrain;

/** SkyRail manual-train operating modes. Automatic trains do not enter this state machine. */
enum OperatingMode {
    SB, FS, SH, SR, TR, PT;

    static OperatingMode restore(String value) {
        try { return valueOf(value); } catch (RuntimeException ex) { return SB; }
    }

    OperatingMode grant(String granted, boolean stopped) {
        OperatingMode target = switch (granted) {
            case "FS" -> FS;
            case "SH" -> SH;
            case "SR" -> SR;
            default -> throw new UiException("Invalid permission mode", "Недопустимый режим разрешения");
        };
        if (this == target) return this;
        if (!stopped || (this != SB && !(this == FS && target == SH)))
            throw new UiException("Operating mode change requires a stopped, eligible train", "Смена режима требует остановленного поезда, отвечающего условиям");
        return target;
    }

    OperatingMode release(boolean stopped) {
        if (!stopped || this == TR) throw new UiException("Stop and acknowledge Trip before release", "Перед освобождением остановитесь и подтвердите TR");
        return SB;
    }

    OperatingMode trip() {
        return this == FS || this == SH || this == SR ? TR : this;
    }

    OperatingMode acknowledge(boolean stopped) {
        if (this != TR || !stopped) throw new UiException("Acknowledge Trip after stopping", "Подтвердите TR после остановки");
        return PT;
    }

    boolean permitsTraction() { return this == FS || this == SH || this == SR; }
}
