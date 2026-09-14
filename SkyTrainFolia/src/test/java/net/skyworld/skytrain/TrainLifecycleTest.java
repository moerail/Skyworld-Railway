package net.skyworld.skytrain;

import java.util.UUID;
import org.bukkit.event.entity.EntityRemoveEvent;

public final class TrainLifecycleTest {
    public static void main(String[] args) {
        for (var cause : EntityRemoveEvent.Cause.values()) {
            boolean temporary = cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT;
            check(TrainManager.permanentRemoval(cause) != temporary, "Removal classification: " + cause);
        }
        Train train = new Train(UUID.randomUUID(), "lifecycle", .4, 1, 1.1);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        train.addMember(first); train.addMember(second);
        var observation = new MemberSnapshot(first, new org.bukkit.Location(null, 10, 64, 20), null, 100);
        train.snapshot(first, observation);
        train.recordMemberRemoval(first, "UNLOADED", 200);
        train.snapshot(first, observation);
        check(train.memberEvidence().get(first).state().equals("UNLOADED"), "Late observation cannot undo unload");
        train.moving = true; train.powerNotch = 4; train.driveControlEnabled = true;
        train.seedCurrentSpeed(.8); train.memberSpeed(second, .8);
        train.automaticRun = new AutomaticRun("station",
                AutomaticSignSpec.parse("station", "5", "continue", .4), 100, false, false);
        TrainManager.haltForMissingMember(train);
        check(train.snapshot(first) == null, "Ordinary driving snapshots cleared");
        check(train.memberEvidence().get(first).position().x == 10, "Occupancy evidence survives safe stop");
        check(train.memberEvidence().get(first).state().equals("UNLOADED"), "Unload retained for observation consumer");
        train.snapshot(first, new MemberSnapshot(first, new org.bukkit.Location(null, 11, 64, 20), null, 300));
        check(train.memberEvidence().get(first).state().equals("OBSERVED"), "Fresh return updates evidence");
        check(train.currentSpeed() == 0 && train.maxMemberSpeed() == 0, "HMI speed is cleared");
        check(!train.moving && train.powerNotch == 0 && train.emergencyBrake, "Missing member stops traction with EB");
        check(train.automaticRun == null, "Old station action cannot restart the train");
        check(train.memberCount() == 2 && train.contains(first), "Temporary removal retains original member identities");
        check(!train.addMember(first), "Returning original entity does not duplicate a slot");
        check(train.removeMember(first), "Permanent removal clears the member");
        check(train.memberEvidence().containsKey(first), "Removing a slot cannot erase retained occupancy evidence");
        check(train.indexOf(second) == 0, "Surviving member can become leader");
        check(!train.removeMember(first), "Duplicate removal is idempotent");
        TrainManager.haltForMissingMember(train);
        TrainManager.authorizeAutomatic(train, false, .001);
        check(!train.emergencyBrake && !train.manualTakeover, "Stationary surviving train can be explicitly rearmed");
        System.out.println("Train lifecycle removal and safe-stop tests passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
