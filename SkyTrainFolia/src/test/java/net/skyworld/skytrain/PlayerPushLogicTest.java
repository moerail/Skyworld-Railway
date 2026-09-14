package net.skyworld.skytrain;

import java.util.UUID;

public final class PlayerPushLogicTest {
    public static void main(String[] args) {
        Train train = new Train(UUID.randomUUID(), "push-test", 0.35, 0.90, 1.10);
        check(train.properties().pushable, "New trains must be pushable by default");

        train.applyPlayerPush(0.055, 0.22);
        check(train.playerPushActive, "A player push must start coasting");
        check(Math.abs(train.currentSpeed() - 0.055) < 0.000001,
                "The first push must seed the configured impulse");

        for (int i = 0; i < 10; i++) {
            train.applyPlayerPush(0.055, 0.22);
        }
        check(Math.abs(train.currentSpeed() - 0.22) < 0.000001,
                "Repeated pushes must respect the low-speed cap");

        double coasting = train.updateCurrentSpeed(
                System.currentTimeMillis(), 0.0, 0.02, 0.004, 0.08, false);
        check(coasting < 0.22 && coasting > 0.0,
                "A pushed train must coast down instead of stopping in one tick");

        train.clearPlayerPush();
        check(!train.playerPushActive, "Drive control must be able to cancel player coasting");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
