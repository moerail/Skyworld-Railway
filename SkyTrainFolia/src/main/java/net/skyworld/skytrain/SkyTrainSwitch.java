package net.skyworld.skytrain;

import java.util.Map;
import java.util.UUID;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;

final class SkyTrainSwitch {
    final UUID id;
    final SwitchBlockPosition sign;
    final SwitchBlockPosition support;
    final SwitchBlockPosition pivot;
    final BlockFace mountFace;
    final BlockFace commonFace;
    final BlockFace straightFace;
    final BlockFace divergingFace;
    final SwitchGeometry geometry;
    final Rail.Shape straightShape;
    final Rail.Shape divergingShape;
    final String localName;

    private SwitchBlockPosition actuator;
    private int actuatorCandidates;
    private SwitchState commandedState;
    private SwitchState physicalState;
    private SwitchState pendingState;
    private long mutationVersion;
    private long redstoneVersion;
    private long commandVersion;
    private UUID lockedTrainId;
    private SwitchPort lockedEntry;
    private SwitchState lockedRoute;
    private boolean occupied;
    private long reservedAtMillis;

    SkyTrainSwitch(UUID id, SwitchBlockPosition sign, SwitchBlockPosition support,
            SwitchBlockPosition pivot, BlockFace mountFace, BlockFace commonFace,
            BlockFace straightFace, BlockFace divergingFace, SwitchGeometry geometry,
            Rail.Shape straightShape, Rail.Shape divergingShape,
            SwitchState commandedState, SwitchState physicalState, String localName,
            SwitchBlockPosition actuator, int actuatorCandidates) {
        this.id = id;
        this.sign = sign;
        this.support = support;
        this.pivot = pivot;
        this.mountFace = mountFace;
        this.commonFace = commonFace;
        this.straightFace = straightFace;
        this.divergingFace = divergingFace;
        this.geometry = geometry;
        this.straightShape = straightShape;
        this.divergingShape = divergingShape;
        this.localName = localName == null ? "" : localName;
        this.actuator = actuator;
        this.actuatorCandidates = actuatorCandidates;
        this.commandedState = commandedState;
        this.physicalState = physicalState;
    }

    BlockFace commonFace() {
        return commonFace;
    }

    BlockFace divergingFace() {
        return divergingFace;
    }

    Rail.Shape shape(SwitchState state) {
        return state == SwitchState.DIVERGING ? divergingShape : straightShape;
    }

    SwitchNodeSnapshot nodeSnapshot() {
        return new SwitchNodeSnapshot(id, pivot, Map.of(
                SwitchPort.COMMON, commonFace(),
                SwitchPort.STRAIGHT, straightFace,
                SwitchPort.DIVERGING, divergingFace()));
    }

    synchronized SwitchState commandedState() {
        return commandedState;
    }

    synchronized SwitchState physicalState() {
        return physicalState;
    }

    synchronized long observationVersion() {
        return physicalState == null && pendingState == null ? mutationVersion : -1L;
    }

    synchronized boolean confirmObservedShape(long version, Rail.Shape shape) {
        if (version < 0 || version != mutationVersion || physicalState != null || pendingState != null) {
            return false;
        }
        SwitchState observed = shape == straightShape ? SwitchState.STRAIGHT
                : shape == divergingShape ? SwitchState.DIVERGING : null;
        if (observed == null) {
            return false;
        }
        physicalState = observed;
        return true;
    }

    synchronized SwitchBlockPosition actuator() {
        return actuator;
    }

    synchronized void setActuator(SwitchBlockPosition actuator, int candidates) {
        if (!java.util.Objects.equals(this.actuator, actuator) || actuatorCandidates != Math.max(0, candidates)) {
            commandVersion++;
        }
        this.actuator = actuator;
        this.actuatorCandidates = Math.max(0, candidates);
    }

    synchronized String actuatorStatus() {
        if (actuatorCandidates > 1) {
            return "conflict";
        }
        return actuator == null ? "waiting" : "bound";
    }

    synchronized SwitchState setCommandedState(SwitchState state) {
        commandVersion++;
        commandedState = state;
        return lockedTrainId == null ? state : null;
    }

    synchronized boolean externallyChangeable(SwitchState expected) {
        return expected != null && lockedTrainId == null && pendingState == null && physicalState == expected;
    }

    synchronized ActuatorCommand actuatorCommand() {
        return actuator == null || actuatorCandidates != 1 ? null
                : new ActuatorCommand(commandVersion, actuator, commandedState);
    }

    synchronized boolean actuatorCommandCurrent(ActuatorCommand command) {
        return command != null && command.version() == commandVersion
                && command.state() == commandedState && actuatorCandidates == 1
                && command.position().equals(actuator);
    }

    synchronized Mutation beginMutation(SwitchState state) {
        if (state == physicalState && pendingState == null) {
            return null;
        }
        if (state == pendingState) {
            return null;
        }
        pendingState = state;
        return new Mutation(++mutationVersion, state);
    }

    synchronized void finishMutation(long version, SwitchState state, boolean success) {
        if (version != mutationVersion || pendingState != state) {
            return;
        }
        if (success) {
            physicalState = state;
        }
        pendingState = null;
    }

    synchronized boolean mutationCurrent(long version, SwitchState state) {
        return version == mutationVersion && pendingState == state;
    }

    synchronized long nextRedstoneVersion() {
        return ++redstoneVersion;
    }

    synchronized boolean isRedstoneVersion(long version) {
        return redstoneVersion == version;
    }

    synchronized Reservation reserve(UUID trainId, SwitchPort entry, SwitchState route, long nowMillis) {
        if (lockedTrainId != null) {
            return lockedTrainId.equals(trainId) ? Reservation.OWNED : Reservation.BUSY;
        }
        lockedTrainId = trainId;
        lockedEntry = entry;
        lockedRoute = route;
        occupied = false;
        reservedAtMillis = nowMillis;
        return Reservation.ACQUIRED;
    }

    synchronized boolean isLockedBy(UUID trainId) {
        return trainId != null && trainId.equals(lockedTrainId);
    }

    synchronized boolean routeReady(UUID trainId) {
        return trainId != null
                && trainId.equals(lockedTrainId)
                && lockedRoute == physicalState
                && pendingState == null;
    }

    synchronized SwitchState lockedRoute(UUID trainId) {
        return trainId != null && trainId.equals(lockedTrainId) ? lockedRoute : null;
    }

    synchronized SwitchState effectiveState() {
        return lockedTrainId == null ? commandedState : lockedRoute;
    }

    synchronized void invalidatePhysicalState() {
        mutationVersion++;
        physicalState = null;
        pendingState = null;
    }

    synchronized boolean observe(UUID trainId, double nearestDistanceSquared, long nowMillis,
            double occupiedDistanceSquared, double releaseDistanceSquared, double abandonedDistanceSquared) {
        if (trainId == null || !trainId.equals(lockedTrainId)) {
            return false;
        }
        if (nearestDistanceSquared <= occupiedDistanceSquared) {
            occupied = true;
        }
        if (occupied) {
            return nearestDistanceSquared > releaseDistanceSquared;
        }
        return nowMillis - reservedAtMillis > 1000L && nearestDistanceSquared > abandonedDistanceSquared;
    }

    synchronized SwitchState release(UUID trainId) {
        if (trainId == null || !trainId.equals(lockedTrainId)) {
            return null;
        }
        lockedTrainId = null;
        lockedEntry = null;
        lockedRoute = null;
        occupied = false;
        reservedAtMillis = 0L;
        return commandedState;
    }

    synchronized String status() {
        String physical = physicalState == null ? "unknown" : physicalState.storageName();
        String lock = lockedTrainId == null
                ? "none"
                : lockedTrainId.toString().substring(0, 8) + "/" + lockedEntry.name().toLowerCase()
                        + "/" + lockedRoute.storageName();
        return "id=" + id + " geometry=" + geometry.code
                + " commanded=" + commandedState.storageName()
                + " physical=" + physical + " lock=" + lock
                + " actuator=" + actuatorStatus()
                + (actuator == null ? "" : "@" + actuator.x() + "," + actuator.y() + "," + actuator.z());
    }

    record Mutation(long version, SwitchState state) {
    }

    record ActuatorCommand(long version, SwitchBlockPosition position, SwitchState state) {
    }

    enum Reservation {
        ACQUIRED,
        OWNED,
        BUSY
    }
}
