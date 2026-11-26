package net.skyworld.sta.api.v8;

import java.util.*;

/** Immutable observations and fenced, expiring intents; no Bukkit objects cross this contract. */
public interface AutomaticTrainService {
    /** Ordered, immutable body samples from the current leading member towards the rear. */
    record Position(String world,double x,double y,double z) {
        public Position {Objects.requireNonNull(world);if(world.isBlank()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Position");}
    }
    static Position reference(List<Position> body,int Q_TP_Alignment) {
        if(body.isEmpty())return null;
        if(Q_TP_Alignment==0)return body.getFirst();if(Q_TP_Alignment==2)return body.getLast();
        if(Q_TP_Alignment!=1)throw new IllegalArgumentException("Q_TP_Alignment");
        double length=0;for(int i=1;i<body.size();i++)length+=distance(body.get(i-1),body.get(i));
        double left=length/2;
        for(int i=1;i<body.size();i++){var a=body.get(i-1);var b=body.get(i);double d=distance(a,b);if(left<=d&&d>0){double t=left/d;return new Position(a.world(),a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);}left-=d;}
        return body.getLast();
    }
    private static double distance(Position a,Position b){if(!a.world().equals(b.world()))throw new IllegalArgumentException("Body worlds");return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}

    record View(UUID train, String name, List<UUID> members, String world, double x, double y, double z,
            double dx, double dy, double dz, double speedBlocksPerTick, double lengthBlocks,
            long observedAt, String integrity, String integrityReason, boolean integrityHeld,
            String mode, UUID owner, String status, double travelledBlocks, AtoProtocol.Header header,List<Position> body) {
        public View { header.require(AtoProtocol.Packet.VEHICLE_FEEDBACK);members = List.copyOf(members);body=List.copyOf(body); }
        public View(UUID train,String name,List<UUID> members,String world,double x,double y,double z,
                double dx,double dy,double dz,double speedBlocksPerTick,double lengthBlocks,long observedAt,
                String integrity,String integrityReason,boolean integrityHeld,String mode,UUID owner,String status,double travelledBlocks,AtoProtocol.Header header) {
            this(train,name,members,world,x,y,z,dx,dy,dz,speedBlocksPerTick,lengthBlocks,observedAt,integrity,integrityReason,integrityHeld,mode,owner,status,travelledBlocks,header,
                members.size()==1?List.of(new Position(world,x,y,z)):List.of());
        }
        public Position reference(int Q_TP_Alignment){return AutomaticTrainService.reference(body,Q_TP_Alignment);}
        public boolean bodyKnown(){return !body.isEmpty()&&body.size()==members.size()&&body.stream().allMatch(p->world.equals(p.world()));}
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
    /** SAL admin handoff after acknowledged MA release; STF verifies stop/ownership.
     * Preserves TIMS/Trip holds. Null owner is permitted only for parked standby. */
    default String handoffToManual(UUID train,UUID owner,UUID administrator){return "UNSUPPORTED";}
    default String handoffToManual(AtoProtocol.Header header,UUID train,UUID owner,UUID administrator){header.require(AtoProtocol.Packet.RELEASE);return handoffToManual(train,owner,administrator);}
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
    record ParkingTarget(String world,double x,double y,double z,String berth,AtoProtocol.Header header,int Q_TP_Alignment,double Q_Stop_Location_Tolerance) {
        public ParkingTarget {
            header.require(AtoProtocol.Packet.DEPOT_CAPTURE);
            if(Q_TP_Alignment!=1||!Double.isFinite(Q_Stop_Location_Tolerance)||Q_Stop_Location_Tolerance<=0||Q_Stop_Location_Tolerance>1)throw new IllegalArgumentException("Depot requires middle alignment and bounded tolerance");
            Objects.requireNonNull(world);Objects.requireNonNull(berth);
            if(world.isBlank()||berth.isBlank()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid parking target");
        }
        public ParkingTarget(String world,double x,double y,double z,String berth,AtoProtocol.Header header){this(world,x,y,z,berth,header,1,.75);}
        public boolean contains(View v){var p=v==null?null:v.reference(Q_TP_Alignment);return p!=null&&v.bodyKnown()&&contains(p.world(),p.x(),p.y(),p.z());}
        public ParkingTarget(String world,double x,double y,double z,String berth){this(world,x,y,z,berth,AtoProtocol.Packet.DEPOT_CAPTURE.header());}
        public boolean contains(String w,double px,double py,double pz){return world.equals(w)&&Math.pow(px-x,2)+Math.pow(py-y,2)+Math.pow(pz-z,2)<=Q_Stop_Location_Tolerance*Q_Stop_Location_Tolerance;}
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
