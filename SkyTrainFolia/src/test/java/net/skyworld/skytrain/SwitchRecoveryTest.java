package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;

public final class SwitchRecoveryTest {
    public static void main(String[] args) {
        var p = new SwitchBlockPosition("world", 0, 64, 0);
        var sw = new SkyTrainSwitch(UUID.randomUUID(), p, p, p, BlockFace.NORTH,
                BlockFace.WEST, BlockFace.EAST, BlockFace.SOUTH, SwitchGeometry.LEFT,
                Rail.Shape.EAST_WEST, Rail.Shape.SOUTH_WEST, SwitchState.STRAIGHT,
                null, "203", null, 0);
        long initial = sw.observationVersion();
        assert initial >= 0 && sw.physicalState() == null;
        assert !sw.confirmObservedShape(initial, Rail.Shape.NORTH_SOUTH);
        assert sw.physicalState() == null : "Unexpected rail shape must remain unknown";
        assert sw.confirmObservedShape(initial, Rail.Shape.SOUTH_WEST);
        assert sw.physicalState() == SwitchState.DIVERGING;
        assert sw.commandedState() == SwitchState.STRAIGHT : "Observation must not issue a command";
        assert sw.observationVersion() < 0;
        assert !sw.confirmObservedShape(initial, Rail.Shape.EAST_WEST);

        sw.invalidatePhysicalState();
        long beforeMutation = sw.observationVersion();
        var mutation = sw.beginMutation(SwitchState.STRAIGHT);
        assert sw.observationVersion() < 0;
        assert !sw.confirmObservedShape(beforeMutation, Rail.Shape.SOUTH_WEST);
        sw.finishMutation(mutation.version(), mutation.state(), false);
        assert !sw.confirmObservedShape(beforeMutation, Rail.Shape.SOUTH_WEST);
        assert sw.confirmObservedShape(sw.observationVersion(), Rail.Shape.EAST_WEST);

        sw.invalidatePhysicalState();
        long beforeInvalidation = sw.observationVersion();
        sw.invalidatePhysicalState();
        assert !sw.confirmObservedShape(beforeInvalidation, Rail.Shape.EAST_WEST);
        UUID train = UUID.randomUUID();
        sw.reserve(train, SwitchPort.COMMON, SwitchState.STRAIGHT, 0);
        assert sw.confirmObservedShape(sw.observationVersion(), Rail.Shape.SOUTH_WEST);
        assert !sw.routeReady(train) : "Observation must not fake readiness of a locked route";
        assert sw.lockedRoute(train) == SwitchState.STRAIGHT;
        System.out.println("PASS switch recovery: actual shape, invalid shape, mutation race, invalidation, lock preservation");
    }
}
