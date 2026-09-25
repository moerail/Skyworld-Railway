package net.skyworld.skytrain;

public final class OperatingModeTest {
    public static void main(String[] args) {
        assert OperatingMode.restore("invalid") == OperatingMode.SB;
        assert OperatingMode.SB.grant("FS", true) == OperatingMode.FS;
        assert OperatingMode.SB.grant("SH", true) == OperatingMode.SH;
        assert OperatingMode.SB.grant("SR", true) == OperatingMode.SR;
        assert OperatingMode.FS.trip() == OperatingMode.TR;
        assert OperatingMode.TR.acknowledge(true) == OperatingMode.PT;
        assert OperatingMode.PT.release(true) == OperatingMode.SB;
        rejects(() -> OperatingMode.TR.release(true));
        rejects(() -> OperatingMode.TR.acknowledge(false));
        rejects(() -> OperatingMode.SB.grant("FS", false));
        rejects(() -> OperatingMode.FS.grant("SR", true));
        for (OperatingMode mode : OperatingMode.values())
            assert mode.permitsTraction() == (mode == OperatingMode.FS || mode == OperatingMode.SH
                    || mode == OperatingMode.SR);
        System.out.println("Manual operating-mode transition gates passed");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected operating-mode transition rejection");
    }
}
