package net.skyworld.skytrain;

public final class TrainSoundTest {
    public static void main(String[] args) {
        TrainSoundState s = new TrainSoundState();
        check(s.brakeEvent(0, 0, 300, true) == 0);
        check(s.brakeEvent(1, 50, 300, true) == 1);
        check(s.brakeEvent(3, 100, 300, true) == 0);
        check(s.brakeEvent(4, 400, 300, true) == 1);
        check(s.brakeEvent(4, 800, 300, true) == 0);
        check(s.brakeEvent(1, 850, 300, true) == 0);
        check(s.brakeEvent(0, 900, 300, true) == -1);
        check(s.brakeEvent(7, 1000, 300, false) == 0);
        check(s.brakeEvent(7, 1100, 300, true) == 0);
        check(s.brakeEvent(8, 1400, 300, true) == 1);
        check(new TrainSoundState().brakeEvent(7, 0, 300, true) == 0);
        double last = 0;
        for (int v = 0; v <= 500; v++) {
            double level = TrainSoundState.level(v, 10, 120);
            check(level >= last && level <= 1);
            if (v <= 10) check(level == 0);
            if (v >= 120) check(level == 1);
            last = level;
        }
        System.out.println("Sound transitions and bounded speed curve passed");
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
}
