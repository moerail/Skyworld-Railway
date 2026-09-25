package net.skyworld.skytrain;

/** One owning-entity tick per call. Cue changes preempt the current melody immediately. */
final class SpeedWarningPlayback {
    private String cue;
    private int note;
    private long remainingTicks;

    Float tick(String nextCue, MaSoundSettings.Tone tone) {
        if (!java.util.Objects.equals(cue, nextCue)) {
            cue = nextCue;
            note = 0;
            remainingTicks = 0;
        }
        if (cue == null || tone == null || !tone.enabled()) return null;
        if (remainingTicks > 0 && --remainingTicks > 0) return null;
        float pitch = tone.pitchAt(note);
        note = (note + 1) % tone.notes();
        remainingTicks = tone.interval();
        return pitch;
    }
}
