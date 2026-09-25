package net.skyworld.skytrain;

import java.util.UUID;

public final class AtpDriverFeedbackTest {
    public static void main(String[] args) {
        assert AtpDriverFeedback.speedWarningChannel("SHADOW");
        assert AtpDriverFeedback.speedWarningChannel("ACTIVE");
        assert !AtpDriverFeedback.speedWarningChannel("ISOLATED");
        assert !AtpDriverFeedback.speedWarningChannel("RECOVERING");
        assert AtpDriverFeedback.soundChannel("NEAR_LIMIT").equals("SPEED");
        assert AtpDriverFeedback.soundChannel("OVERSPEED").equals("SPEED");
        assert AtpDriverFeedback.soundChannel("ATP_SERVICE").equals("ATP");
        assert AtpDriverFeedback.soundChannel("ATP_EMERGENCY").equals("ATP");
        assert AtpDriverFeedback.soundChannel("SHRINKING").equals("MA");
        var notice = new AtpDriverFeedback();
        var lease = UUID.randomUUID();
        assert !AtpDriverFeedback.interventionVisible(8, OperatingMode.SB, "NO_OPERATING_PERMISSION", 0);
        assert AtpDriverFeedback.interventionVisible(7, OperatingMode.FS, "EOA_CURVE", 0.5);
        assert AtpDriverFeedback.interventionVisible(8, OperatingMode.TR, "EOA_OVERRUN", 0);
        assert notice.update(lease, true, 8, false) == null;
        assert "ATP_SERVICE".equals(notice.update(lease, true, 7, true));
        assert notice.update(lease, true, 7, true) == null;
        assert "ATP_EMERGENCY".equals(notice.update(lease, true, 8, true));
        assert notice.update(lease, true, 8, true) == null;
        assert notice.update(lease, true, 0, false) == null;
        assert "ATP_SERVICE".equals(notice.update(lease, true, 7, true));
        assert notice.update(lease, false, 8, true) == null;
        assert "ATP_EMERGENCY".equals(notice.update(UUID.randomUUID(), true, 8, true));
        System.out.println("PASS active/shadow speed-warning gate, ATP B7/EB display and one-shot cues");
    }
}
