package net.skyworld.skytrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.util.Vector;

final class Train {
    private final UUID id;
    private volatile String name;
    private final CopyOnWriteArrayList<UUID> members;
    private final ConcurrentHashMap<UUID, MemberSnapshot> snapshots = new ConcurrentHashMap<>();
    record MemberEvidence(MemberSnapshot position, String state, long stateAtMillis) { }
    private final ConcurrentHashMap<UUID, MemberEvidence> memberEvidence = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> memberSpeeds = new ConcurrentHashMap<>();
    private volatile TrainMotionFrame motionFrame = TrainMotionFrame.empty();
    private volatile TrainMotionFrame pendingMotionFrame = TrainMotionFrame.empty();
    private final ConcurrentHashMap<String, Long> signCooldowns = new ConcurrentHashMap<>();
    private final Set<String> stationLatches = ConcurrentHashMap.newKeySet();
    private final TrainProperties properties;

    volatile boolean moving;
    volatile boolean reversed;
    volatile boolean driveControlEnabled;
    volatile boolean manualTakeover;
    volatile boolean manualReleaseConfirmed = true;
    volatile boolean driverEmergencyHold;
    volatile UUID lastManualDriver;
    volatile AutomaticRun automaticRun;
    volatile Reverser reverser = Reverser.FORWARD;
    volatile int powerNotch;
    volatile ProtectionMode protectionMode = ProtectionMode.SHADOW;
    volatile OperatingMode operatingMode = OperatingMode.SB;
    final ActiveAtpState activeAtp = new ActiveAtpState();
    volatile Double activeAtpLimitMps;
    volatile String activeAtpReason = "UNAVAILABLE";
    volatile int activeAtpBrakeLevel;
    final TrainSoundState soundState = new TrainSoundState();
    final SwitchPassageMonitor switchPassages = new SwitchPassageMonitor();
    volatile int brakeNotch;
    volatile boolean emergencyBrake;
    volatile double targetSpeed;
    volatile double maxSpeed;
    volatile double spacing;
    volatile long pauseUntilMillis;
    volatile long reverseSettleUntilMillis;
    volatile long reverseBrakeDeadlineMillis;
    volatile boolean reversePending;
    volatile long curveSlowUntilMillis;
    volatile boolean playerPushActive;
    private double currentSpeed;
    private double effectiveMaxSpeed;
    private long effectiveMaxSpeedUpdatedAtMillis;
    private long targetLayoutUpdatedAtMillis;
    private long motionUpdatedAtMillis;
    private long controlledSpeedTickMillis;
    private long speedUpdatedAtMillis;
    private TrainRailPath trackPath;
    private volatile StationMotion stationMotion;
    private volatile double directionX;
    private volatile double directionY;
    private volatile double directionZ;
    private String mileageLineName;
    private boolean mileageKnown;
    private double mileageMeters;
    private int mileageTravelSign;
    private double mileageTravelX;
    private double mileageTravelY;
    private double mileageTravelZ;
    private String lastBaliseName;
    private double distanceSinceBaliseMeters = Double.POSITIVE_INFINITY;
    private boolean inSignalRange;
    private final Set<UUID> infrastructureMarkerLatches = new HashSet<>();
    private String pendingEndLineKey;
    private double pendingEndTravelX;
    private double pendingEndTravelY;
    private double pendingEndTravelZ;

    Train(UUID id, String name, double targetSpeed, double maxSpeed, double spacing) {
        this(id, name, targetSpeed, maxSpeed, spacing, TrainProperties.defaults());
    }

    Train(UUID id, String name, double targetSpeed, double maxSpeed, double spacing, TrainProperties properties) {
        this.id = id;
        this.name = name;
        this.targetSpeed = targetSpeed;
        this.maxSpeed = maxSpeed;
        this.spacing = spacing;
        this.properties = properties == null ? TrainProperties.defaults() : properties;
        this.members = new CopyOnWriteArrayList<>();
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    String key() {
        return normalizeName(name);
    }

    void rename(String newName) {
        this.name = newName;
    }

    List<UUID> members() {
        return Collections.unmodifiableList(new ArrayList<>(members));
    }

    int memberCount() {
        return members.size();
    }

    boolean addMember(UUID entityId) {
        if (members.contains(entityId)) {
            return false;
        }
        return members.add(entityId);
    }

    void reorderMembers(List<UUID> orderedMembers) {
        if (orderedMembers == null || orderedMembers.size() != members.size()) {
            return;
        }
        members.clear();
        members.addAll(orderedMembers);
    }

    void reverseMemberOrder() {
        Collections.reverse(members);
    }

    void clearSnapshots() {
        snapshots.clear();
    }

    synchronized void clearMemberTargets() {
        motionFrame = TrainMotionFrame.empty();
        pendingMotionFrame = TrainMotionFrame.empty();
        targetLayoutUpdatedAtMillis = 0L;
        motionUpdatedAtMillis = 0L;
    }

    synchronized void clearTrackPath() {
        trackPath = null;
    }

    synchronized TrainRailPath trackPath() {
        return trackPath;
    }

    synchronized void trackPath(TrainRailPath trackPath) {
        this.trackPath = trackPath;
    }

    StationMotion stationMotion() {
        return stationMotion;
    }

    void beginStationMotion(String signKey, double dockingDistance, double startSpeed,
            long dwellMillis, boolean reverseOnDeparture, long nowMillis) {
        stationMotion = new StationMotion(signKey, dockingDistance, startSpeed, dwellMillis,
                reverseOnDeparture, reversed, nowMillis);
    }

    void clearStationMotion() {
        stationMotion = null;
    }

    void clearStationMotion(StationMotion expected) {
        if (stationMotion == expected) {
            stationMotion = null;
        }
    }

    synchronized void loadMileage(String lineName, boolean known, double meters,
            int travelSign, Vector travelDirection, String lastBaliseName,
            double distanceSinceBaliseMeters, boolean inSignalRange) {
        this.mileageLineName = lineName == null || lineName.isBlank() ? null : lineName;
        this.mileageKnown = known && this.mileageLineName != null;
        this.mileageMeters = meters;
        this.mileageTravelSign = Integer.compare(travelSign, 0);
        rememberMileageTravelDirection(travelDirection);
        this.lastBaliseName = lastBaliseName == null || lastBaliseName.isBlank() ? null : lastBaliseName;
        this.distanceSinceBaliseMeters = Math.max(0.0, distanceSinceBaliseMeters);
        this.inSignalRange = inSignalRange;
    }

    synchronized TrainMileageSnapshot mileageSnapshot() {
        return new TrainMileageSnapshot(
                mileageLineName,
                mileageKnown,
                mileageMeters,
                mileageTravelSign,
                lastBaliseName,
                distanceSinceBaliseMeters,
                inSignalRange);
    }

    synchronized MileagePersistence mileagePersistence() {
        return new MileagePersistence(
                mileageLineName,
                mileageKnown,
                mileageMeters,
                mileageTravelSign,
                new Vector(mileageTravelX, mileageTravelY, mileageTravelZ),
                lastBaliseName,
                distanceSinceBaliseMeters,
                inSignalRange);
    }

    synchronized void advanceMileage(double blocks, double blocksPerMeter,
            Vector travelDirection, double signalRangeMeters) {
        double distanceBlocks = Math.max(0.0, blocks);
        if (distanceBlocks <= 0.000001) {
            return;
        }

        Vector travel = normalized(travelDirection);
        if (pendingEndLineKey != null && mileageLineName != null
                && pendingEndLineKey.equals(InfrastructureMarker.normalizeLine(mileageLineName))) {
            Vector leavingDirection = new Vector(pendingEndTravelX, pendingEndTravelY, pendingEndTravelZ);
            if (travel.lengthSquared() >= 0.0001 && leavingDirection.lengthSquared() >= 0.0001
                    && travel.dot(leavingDirection) > 0.5) {
                mileageLineName = null;
                mileageKnown = false;
                mileageTravelSign = 0;
                lastBaliseName = null;
                distanceSinceBaliseMeters = Double.POSITIVE_INFINITY;
                inSignalRange = false;
            }
            pendingEndLineKey = null;
        }

        updateMileageTravelDirection(travel);
        double meters = distanceBlocks / Math.max(0.01, blocksPerMeter);
        if (mileageKnown && mileageTravelSign != 0) {
            mileageMeters += mileageTravelSign * meters;
        }
        if (mileageLineName != null && Double.isFinite(distanceSinceBaliseMeters)) {
            distanceSinceBaliseMeters += meters;
            inSignalRange = distanceSinceBaliseMeters <= Math.max(1.0, signalRangeMeters);
        }
    }

    synchronized void anchorMileage(String lineName, double meters, Vector positiveDirection,
            Vector travelDirection, UUID markerId, String baliseName, boolean balise) {
        Vector positive = normalized(positiveDirection);
        Vector travel = normalized(travelDirection);
        mileageLineName = lineName;
        mileageKnown = true;
        mileageMeters = meters;
        mileageTravelSign = positive.lengthSquared() < 0.0001 || travel.lengthSquared() < 0.0001
                ? 1
                : (travel.dot(positive) >= 0.0 ? 1 : -1);
        rememberMileageTravelDirection(travel);
        pendingEndLineKey = null;
        if (balise) {
            lastBaliseName = baliseName;
            distanceSinceBaliseMeters = 0.0;
            inSignalRange = true;
        }
    }

    synchronized void setUnknownMileageLine(String lineName, UUID markerId, String baliseName) {
        mileageLineName = lineName;
        mileageKnown = false;
        mileageTravelSign = 0;
        lastBaliseName = baliseName;
        distanceSinceBaliseMeters = 0.0;
        inSignalRange = true;
        pendingEndLineKey = null;
    }

    synchronized boolean clearMileageForLine(String lineKey) {
        if (mileageLineName == null
                || !InfrastructureMarker.normalizeLine(mileageLineName).equals(lineKey)) {
            return false;
        }
        mileageKnown = false;
        mileageMeters = 0.0;
        mileageTravelSign = 0;
        mileageTravelX = 0.0;
        mileageTravelY = 0.0;
        mileageTravelZ = 0.0;
        lastBaliseName = null;
        distanceSinceBaliseMeters = Double.POSITIVE_INFINITY;
        inSignalRange = false;
        pendingEndLineKey = null;
        infrastructureMarkerLatches.clear();
        return true;
    }

    synchronized Vector positiveMileageDirection(Vector currentTravelDirection) {
        Vector travel = normalized(currentTravelDirection);
        if (mileageTravelSign < 0) {
            travel.multiply(-1.0);
        }
        return travel;
    }

    synchronized boolean markInfrastructureMarker(UUID markerId) {
        return markerId != null && infrastructureMarkerLatches.add(markerId);
    }

    synchronized void clearInfrastructureMarkerLatch() {
        infrastructureMarkerLatches.clear();
    }

    synchronized void markLineEnd(String lineKey, Vector travelDirection) {
        if (mileageLineName == null
                || !InfrastructureMarker.normalizeLine(mileageLineName).equals(lineKey)) {
            return;
        }
        Vector travel = normalized(travelDirection);
        pendingEndLineKey = lineKey;
        pendingEndTravelX = travel.getX();
        pendingEndTravelY = travel.getY();
        pendingEndTravelZ = travel.getZ();
    }

    synchronized void reverseMileageDirection() {
        mileageTravelSign = -mileageTravelSign;
        mileageTravelX = -mileageTravelX;
        mileageTravelY = -mileageTravelY;
        mileageTravelZ = -mileageTravelZ;
        pendingEndLineKey = null;
    }

    private void updateMileageTravelDirection(Vector travel) {
        if (travel.lengthSquared() < 0.0001) {
            return;
        }
        Vector previous = new Vector(mileageTravelX, mileageTravelY, mileageTravelZ);
        if (previous.lengthSquared() >= 0.0001 && previous.dot(travel) < -0.5) {
            mileageTravelSign = -mileageTravelSign;
        }
        rememberMileageTravelDirection(travel);
    }

    private void rememberMileageTravelDirection(Vector direction) {
        Vector normalized = normalized(direction);
        mileageTravelX = normalized.getX();
        mileageTravelY = normalized.getY();
        mileageTravelZ = normalized.getZ();
    }

    private static Vector normalized(Vector direction) {
        if (direction == null || direction.lengthSquared() < 0.0001) {
            return new Vector();
        }
        return direction.clone().normalize();
    }

    synchronized double currentSpeed() {
        return currentSpeed;
    }

    synchronized boolean speedControlledRecently(long nowMillis) {
        return controlledSpeedTickMillis > 0L && nowMillis - controlledSpeedTickMillis < 40L;
    }

    synchronized void seedCurrentSpeed(double speed) {
        currentSpeed = Math.max(0.0, speed);
        speedUpdatedAtMillis = 0L;
    }

    synchronized void applyPlayerPush(double impulse, double maxSpeed) {
        currentSpeed = Math.min(Math.max(0.0, maxSpeed),
                Math.max(0.0, currentSpeed) + Math.max(0.0, impulse));
        speedUpdatedAtMillis = 0L;
        controlledSpeedTickMillis = 0L;
        playerPushActive = currentSpeed > 0.001;
    }

    synchronized void clearPlayerPush() {
        playerPushActive = false;
    }

    synchronized void seedEffectiveMaxSpeed(double speed) {
        effectiveMaxSpeed = Math.max(0.05, speed);
        effectiveMaxSpeedUpdatedAtMillis = 0L;
    }

    synchronized double updateEffectiveMaxSpeed(long nowMillis, double targetMaxSpeed, double changePerTick) {
        double target = Math.max(0.05, targetMaxSpeed);
        if (effectiveMaxSpeed <= 0.0) {
            effectiveMaxSpeed = target;
            effectiveMaxSpeedUpdatedAtMillis = nowMillis;
            return effectiveMaxSpeed;
        }

        long elapsedMillis = effectiveMaxSpeedUpdatedAtMillis <= 0L
                ? 50L
                : Math.max(0L, nowMillis - effectiveMaxSpeedUpdatedAtMillis);
        if (elapsedMillis < 10L) {
            return effectiveMaxSpeed;
        }

        double elapsedTicks = Math.max(0.2, elapsedMillis / 50.0);
        double step = Math.max(0.001, changePerTick) * elapsedTicks;
        double delta = target - effectiveMaxSpeed;
        effectiveMaxSpeed = Math.abs(delta) <= step
                ? target
                : Math.max(0.05, effectiveMaxSpeed + Math.copySign(step, delta));
        effectiveMaxSpeedUpdatedAtMillis = nowMillis;
        return effectiveMaxSpeed;
    }

    synchronized double updateCurrentSpeed(long nowMillis, double desiredSpeed, double accelerationPerTick,
            double decelerationPerTick, double emergencyDecelerationPerTick, boolean emergencyBrake) {
        double desired = Math.max(0.0, desiredSpeed);
        long elapsedMillis = speedUpdatedAtMillis <= 0L ? 50L : Math.max(0L, nowMillis - speedUpdatedAtMillis);
        if (elapsedMillis < 10L) {
            return currentSpeed;
        }

        double elapsedTicks = Math.max(0.2, elapsedMillis / 50.0);
        double delta = desired - currentSpeed;
        if (Math.abs(delta) <= 0.001) {
            currentSpeed = desired;
            speedUpdatedAtMillis = nowMillis;
            return currentSpeed;
        }

        double step = delta > 0.0
                ? accelerationPerTick
                : emergencyBrake ? emergencyDecelerationPerTick : decelerationPerTick;
        step *= elapsedTicks;
        currentSpeed = Math.abs(delta) <= step
                ? desired
                : Math.max(0.0, currentSpeed + Math.copySign(step, delta));
        speedUpdatedAtMillis = nowMillis;
        controlledSpeedTickMillis = nowMillis;
        return currentSpeed;
    }

    synchronized double updateDrivenSpeed(long nowMillis, double speedLimit, double powerAccelerationPerTick,
            double brakeDecelerationPerTick, double rollingResistancePerTick, double airResistanceFactor,
            double overspeedDecelerationPerTick, double emergencyDecelerationPerTick, boolean externalEmergencyBrake,
            boolean tractionAllowed) {
        long elapsedMillis = speedUpdatedAtMillis <= 0L ? 50L : Math.max(0L, nowMillis - speedUpdatedAtMillis);
        if (elapsedMillis < 10L) {
            return currentSpeed;
        }

        double elapsedTicks = Math.max(0.2, elapsedMillis / 50.0);
        double limit = Math.max(0.0, speedLimit);
        double power = tractionAllowed ? Math.max(0.0, powerAccelerationPerTick) : 0.0;
        double brake = Math.max(0.0, brakeDecelerationPerTick);
        if (externalEmergencyBrake || emergencyBrake) {
            brake = Math.max(brake, emergencyDecelerationPerTick);
        }
        if (currentSpeed > limit) {
            brake = Math.max(brake, overspeedDecelerationPerTick);
        }

        double resistance = Math.max(0.0, rollingResistancePerTick)
                + Math.max(0.0, airResistanceFactor) * currentSpeed * currentSpeed;
        double delta = power - brake - resistance;
        double nextSpeed = Math.max(0.0, currentSpeed + delta * elapsedTicks);
        if (currentSpeed <= limit && nextSpeed > limit) {
            nextSpeed = limit;
        } else if (currentSpeed > limit && nextSpeed < limit) {
            nextSpeed = limit;
        }
        currentSpeed = nextSpeed;
        if (currentSpeed < 0.001 && power <= 0.0) {
            currentSpeed = 0.0;
        }
        speedUpdatedAtMillis = nowMillis;
        controlledSpeedTickMillis = nowMillis;
        return currentSpeed;
    }

    synchronized double updateDrivenForceSpeed(long nowMillis, double speedLimit, double tractionForce,
            double brakeForce, double mass, double rollingResistanceForce, double airResistanceFactor,
            double gradeResistanceFactor, double directionY, double baseSpeed, double fieldWeakeningSpeed,
            double minHighSpeedTractionRatio, double overspeedDecelerationPerTick, double emergencyBrakeForce,
            boolean externalEmergencyBrake, boolean tractionAllowed) {
        long elapsedMillis = speedUpdatedAtMillis <= 0L ? 50L : Math.max(0L, nowMillis - speedUpdatedAtMillis);
        if (elapsedMillis < 10L) {
            return currentSpeed;
        }

        double elapsedTicks = Math.max(0.2, elapsedMillis / 50.0);
        double limit = Math.max(0.0, speedLimit);
        double safeMass = Math.max(1.0, mass);
        double power = tractionAllowed
                ? tractionForceAtSpeed(Math.max(0.0, tractionForce), currentSpeed, baseSpeed,
                        fieldWeakeningSpeed, minHighSpeedTractionRatio)
                : 0.0;
        double brake = Math.max(0.0, brakeForce);
        if (externalEmergencyBrake || emergencyBrake) {
            brake = Math.max(brake, Math.max(0.0, emergencyBrakeForce));
        }
        if (currentSpeed > limit) {
            brake = Math.max(brake, Math.max(0.0, overspeedDecelerationPerTick) * safeMass);
        }

        double resistance = Math.max(0.0, rollingResistanceForce)
                + Math.max(0.0, airResistanceFactor) * currentSpeed * currentSpeed
                + Math.max(0.0, gradeResistanceFactor) * directionY * safeMass;
        double acceleration = (power - brake - resistance) / safeMass;
        double nextSpeed = Math.max(0.0, currentSpeed + acceleration * elapsedTicks);
        if (currentSpeed <= limit && nextSpeed > limit) {
            nextSpeed = limit;
        } else if (currentSpeed > limit && nextSpeed < limit) {
            nextSpeed = limit;
        }
        currentSpeed = nextSpeed;
        if (currentSpeed < 0.001 && power <= 0.0) {
            currentSpeed = 0.0;
        }
        speedUpdatedAtMillis = nowMillis;
        controlledSpeedTickMillis = nowMillis;
        return currentSpeed;
    }

    private static double tractionForceAtSpeed(double force, double speed, double baseSpeed,
            double fieldWeakeningSpeed, double minHighSpeedTractionRatio) {
        if (force <= 0.0) {
            return 0.0;
        }
        double safeSpeed = Math.max(0.001, speed);
        double safeBaseSpeed = Math.max(0.001, baseSpeed);
        double effective = force;
        if (safeSpeed > safeBaseSpeed) {
            effective *= safeBaseSpeed / safeSpeed;
        }
        double safeWeakeningSpeed = Math.max(safeBaseSpeed, fieldWeakeningSpeed);
        if (safeSpeed > safeWeakeningSpeed) {
            effective *= safeWeakeningSpeed / safeSpeed;
        }
        double minimum = force * RailMath.clamp(minHighSpeedTractionRatio, 0.0, 1.0);
        return Math.max(minimum, effective);
    }

    void clearMemberSpeeds() {
        memberSpeeds.clear();
    }

    void memberSpeed(UUID entityId, double speed) {
        memberSpeeds.put(entityId, Math.max(0.0, speed));
    }

    synchronized boolean targetLayoutUpdatedRecently(long nowMillis) {
        return targetLayoutUpdatedAtMillis > 0L && nowMillis - targetLayoutUpdatedAtMillis < 40L;
    }

    synchronized double beginMotionFrame(long nowMillis) {
        long elapsedMillis = motionUpdatedAtMillis <= 0L
                ? 50L
                : Math.max(0L, nowMillis - motionUpdatedAtMillis);
        motionUpdatedAtMillis = nowMillis;
        return Math.max(0.0, Math.min(4.0, elapsedMillis / 50.0));
    }

    synchronized void publishMotionFrame(Map<UUID, TrainMemberTarget> targets, long nowMillis, long currentTick) {
        promotePendingFrame(currentTick);
        TrainMotionFrame frame = new TrainMotionFrame(targets, nowMillis, currentTick + 1L);
        if (motionFrame.isEmpty()) {
            motionFrame = frame;
        } else {
            pendingMotionFrame = frame;
        }
        targetLayoutUpdatedAtMillis = nowMillis;
    }

    synchronized TrainMemberTarget memberTarget(UUID entityId, long currentTick) {
        promotePendingFrame(currentTick);
        return motionFrame.target(entityId);
    }

    synchronized TrainMemberTarget latestMemberTarget(UUID entityId) {
        TrainMemberTarget target = pendingMotionFrame.target(entityId);
        return target == null ? motionFrame.target(entityId) : target;
    }

    private void promotePendingFrame(long currentTick) {
        if (!pendingMotionFrame.isEmpty() && pendingMotionFrame.applyTick <= currentTick) {
            motionFrame = pendingMotionFrame;
            pendingMotionFrame = TrainMotionFrame.empty();
        }
    }

    double maxMemberSpeed() {
        double speed = 0.0;
        for (double memberSpeed : memberSpeeds.values()) {
            speed = Math.max(speed, memberSpeed);
        }
        return speed;
    }

    void rememberDirection(Vector direction) {
        if (direction == null || direction.lengthSquared() < 0.0001) {
            return;
        }
        Vector normalized = direction.clone().normalize();
        directionX = normalized.getX();
        directionY = normalized.getY();
        directionZ = normalized.getZ();
    }

    Vector rememberedDirection() {
        Vector direction = new Vector(directionX, directionY, directionZ);
        return direction.lengthSquared() < 0.0001 ? new Vector() : direction;
    }

    void reverseRememberedDirection() {
        directionX = -directionX;
        directionY = -directionY;
        directionZ = -directionZ;
    }

    boolean removeMember(UUID entityId) {
        snapshots.remove(entityId);
        memberSpeeds.remove(entityId);
        return members.remove(entityId);
    }

    int indexOf(UUID entityId) {
        return members.indexOf(entityId);
    }

    UUID memberBefore(UUID entityId) {
        int index = members.indexOf(entityId);
        if (index <= 0) {
            return null;
        }
        return members.get(index - 1);
    }

    UUID memberAfter(UUID entityId) {
        int index = members.indexOf(entityId);
        if (index < 0 || index >= members.size() - 1) {
            return null;
        }
        return members.get(index + 1);
    }

    boolean contains(UUID entityId) {
        return members.contains(entityId);
    }

    boolean containsCart(org.bukkit.entity.Minecart cart) {
        return cart != null && members.contains(cart.getUniqueId());
    }

    TrainProperties properties() {
        return properties;
    }

    void snapshot(UUID entityId, MemberSnapshot snapshot) {
        snapshots.put(entityId, snapshot);
        memberEvidence.compute(entityId, (id, old) -> old != null && old.stateAtMillis() >= snapshot.timeMillis
                ? old : new MemberEvidence(snapshot, "OBSERVED", snapshot.timeMillis));
    }

    void recordMemberRemoval(UUID id, String state, long now) {
        memberEvidence.computeIfPresent(id, (key, old) -> new MemberEvidence(old.position(), state, now));
    }

    Map<UUID, MemberEvidence> memberEvidence() { return Map.copyOf(memberEvidence); }

    MemberSnapshot snapshot(UUID entityId) {
        return snapshots.get(entityId);
    }

    List<MemberSnapshot> snapshots() {
        return List.copyOf(snapshots.values());
    }

    boolean canTriggerSign(String key, long nowMillis, long cooldownMillis) {
        Long nextAllowed = signCooldowns.get(key);
        if (nextAllowed != null && nowMillis < nextAllowed) {
            return false;
        }
        signCooldowns.put(key, nowMillis + cooldownMillis);
        return true;
    }

    void blockSignUntil(String key, long blockedUntilMillis) {
        signCooldowns.merge(key, blockedUntilMillis, Math::max);
    }

    boolean stationLatched(String key) {
        return stationLatches.contains(key);
    }

    void latchStation(String key) {
        if (key != null && !key.isBlank()) {
            stationLatches.add(key);
        }
    }

    Set<String> stationLatches() {
        return Set.copyOf(stationLatches);
    }

    void releaseStation(String key) {
        stationLatches.remove(key);
        signCooldowns.remove(key);
    }

    static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    record MileagePersistence(String lineName, boolean known, double meters,
            int travelSign, Vector travelDirection, String lastBaliseName,
            double distanceSinceBaliseMeters, boolean inSignalRange) {
    }
}
