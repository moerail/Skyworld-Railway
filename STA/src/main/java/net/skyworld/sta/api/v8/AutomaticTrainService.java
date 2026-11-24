package net.skyworld.sta.api.v8;

import java.util.*;

/** Immutable observations and fenced, expiring intents; no Bukkit objects cross this contract. */
public interface AutomaticTrainService {
    record View(UUID train, String name, List<UUID> members, String world, double x, double y, double z,
            double dx, double dy, double dz, double speedBlocksPerTick, double lengthBlocks,
            long observedAt, String integrity, String integrityReason, boolean integrityHeld,
            String mode, UUID owner, String status, double travelledBlocks, AtoProtocol.Header header) {
        public View { header.require(AtoProtocol.Packet.VEHICLE_FEEDBACK);members = List.copyOf(members); }
        public View(UUID train,String name,List<UUID> members,String world,double x,double y,double z,
                double dx,double dy,double dz,double speedBlocksPerTick,double lengthBlocks,long observedAt,
                String integrity,String integrityReason,boolean integrityHeld,String mode,UUID owner,String status,double travelledBlocks) {
            this(train,name,members,world,x,y,z,dx,dy,dz,speedBlocksPerTick,lengthBlocks,observedAt,integrity,
                integrityReason,integrityHeld,mode,owner,status,travelledBlocks,AtoProtocol.Packet.VEHICLE_FEEDBACK.header());
        }
    }
    record Intent(UUID owner, long sequence, long expiresAt, double remainingBlocks,
            double cruiseBlocksPerTick, boolean stopAtTarget, String line, String next, String phase, AtoProtocol.Header header) {
        public Intent {
            header.require(AtoProtocol.Packet.DRIVE_INTENT);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(line);Objects.requireNonNull(next);Objects.requireNonNull(phase);
            if (sequence < 0 || expiresAt < 0 || !Double.isFinite(remainingBlocks) || remainingBlocks < 0
                    || !Double.isFinite(cruiseBlocksPerTick) || cruiseBlocksPerTick < 0 || cruiseBlocksPerTick > 100)
                throw new IllegalArgumentException("Invalid automation intent");
        }
        public Intent(UUID owner,long sequence,long expiresAt,double remainingBlocks,double cruiseBlocksPerTick,
                boolean stopAtTarget,String line,String next,String phase) {
            this(owner,sequence,expiresAt,remainingBlocks,cruiseBlocksPerTick,stopAtTarget,line,next,phase,
                AtoProtocol.Packet.DRIVE_INTENT.header());
        }
    }
    Collection<View> trains();
    UUID trainForMember(UUID member);
    String claim(UUID train, UUID owner);
    boolean intent(UUID train, Intent intent);
    /** Releases only while physically stopped and complete. */
    String release(UUID train, UUID owner);
    String reverse(UUID train, UUID owner);
    String recover(UUID train, UUID owner);
    /** Stop/fence FS before requesting dispatcher-approved SR. Never acknowledges an arbitrary Trip. */
    String prepareSr(UUID train, UUID owner);
    /** A live, explicitly dispatcher-approved SR grant is required to acknowledge a stopped recovery Trip. */
    String acceptRecoverySr(UUID train, UUID owner, UUID grant);
    void message(UUID train, UUID owner, String text, double seconds);
    /** Reserve loaded track ahead independently of whether there are passengers. */
    void corridor(UUID train, UUID owner, List<Chunk> chunks);
    record Chunk(String world, int x, int z) {}
    record ParkingTarget(String world,double x,double y,double z,String berth,AtoProtocol.Header header) {
        public ParkingTarget {
            header.require(AtoProtocol.Packet.DEPOT_CAPTURE);Objects.requireNonNull(world);Objects.requireNonNull(berth);
            if(world.isBlank()||berth.isBlank()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid parking target");
        }
        public ParkingTarget(String world,double x,double y,double z,String berth){this(world,x,y,z,berth,AtoProtocol.Packet.DEPOT_CAPTURE.header());}
        public boolean contains(String w,double px,double py,double pz){return world.equals(w)&&Math.pow(px-x,2)+Math.pow(py-y,2)+Math.pow(pz-z,2)<=.75*.75;}
    }
    /** Captures an unowned, manually pushed consist at its registered berth. Never supplies traction or MA. */
    default String park(UUID train,UUID owner,ParkingTarget target){return "UNSUPPORTED";}

    record ManualDriver(UUID train,UUID driver,UUID lease) {
        public ManualDriver {Objects.requireNonNull(train);Objects.requireNonNull(driver);Objects.requireNonNull(lease);}
    }
    /** Receipt only for a successful explicit /st release, never seat loss, quit or expiry. */
    record ManualRelease(UUID train,UUID driver,UUID lease,UUID receipt,long releasedAt) {
        public ManualRelease {Objects.requireNonNull(train);Objects.requireNonNull(driver);Objects.requireNonNull(lease);Objects.requireNonNull(receipt);if(releasedAt<0)throw new IllegalArgumentException("release time");}
    }
    default Collection<ManualDriver> manualDrivers(){return List.of();}
    default Collection<ManualRelease> manualReleases(){return List.of();}
    /** Parked session only; actuator tick completes the stopped transition to manual. */
    default String releaseStandby(UUID train,UUID requester){return "UNSUPPORTED";}
    default String releaseStandby(AtoProtocol.Header header,UUID train,UUID requester) {
        header.require(AtoProtocol.Packet.DEPOT_MANUAL_RELEASE);return releaseStandby(train,requester);
    }
    /** Commit one unconsumed explicit release receipt, rechecking stop and berth under the ownership fence. */
    default String acceptDepotHandover(UUID train,UUID owner,ParkingTarget target,ManualRelease receipt){return "UNSUPPORTED";}
    default String acceptDepotHandover(AtoProtocol.Header header,UUID train,UUID owner,ParkingTarget target,ManualRelease receipt) {
        header.require(AtoProtocol.Packet.DEPOT_HANDOVER_COMMIT);return acceptDepotHandover(train,owner,target,receipt);
    }

    // Additive typed entry points. Legacy in-process implementations keep their physical ownership checks.
    default String claim(AtoProtocol.Header h,UUID train,UUID owner) {
        h.require(AtoProtocol.Packet.CLAIM);return claim(train,owner);
    }
    default String release(AtoProtocol.Header h,UUID train,UUID owner) {
        h.require(AtoProtocol.Packet.RELEASE);return release(train,owner);
    }
    default String reverse(AtoProtocol.Header h,UUID train,UUID owner) {
        h.require(AtoProtocol.Packet.REVERSE);return reverse(train,owner);
    }
    default String recover(AtoProtocol.Header h,UUID train,UUID owner) {
        h.require(AtoProtocol.Packet.RECOVER);return recover(train,owner);
    }
    default String prepareSr(AtoProtocol.Header h,UUID train,UUID owner) {
        h.require(AtoProtocol.Packet.PREPARE_SR);return prepareSr(train,owner);
    }
    default String acceptRecoverySr(AtoProtocol.Header h,UUID train,UUID owner,UUID grant) {
        h.require(AtoProtocol.Packet.ACCEPT_RECOVERY_SR);return acceptRecoverySr(train,owner,grant);
    }
    default void message(AtoProtocol.Header h,UUID train,UUID owner,String text,double seconds) {
        h.require(AtoProtocol.Packet.PASSENGER_MESSAGE);message(train,owner,text,seconds);
    }
    default void corridor(AtoProtocol.Header h,UUID train,UUID owner,List<Chunk> chunks) {
        h.require(AtoProtocol.Packet.CHUNK_CORRIDOR);corridor(train,owner,List.copyOf(chunks));
    }
}
