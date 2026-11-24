package net.skyworld.sta.api.v8;

import java.util.Objects;

/** STA Java/JSON identifiers, NOT a UNISIG wire encoder. See STA-V8-ATO-PROTOCOL.md. */
public final class AtoProtocol {
    private AtoProtocol() {}
    public static final int VERSION = 8;

    /** Message and Packet have independent namespaces. This envelope is STA-private. */
    public static final class Message {
        private Message() {}
        public static final int ATO = 6508;
    }

    public enum Family { SUBSET_126, SUBSET_130, SUBSET_139, SKY_ATO }

    /** Standard names reserve conceptual +1000 homages; only marked runtime kinds are exchanged today. */
    public enum Packet {
        HANDSHAKE_REQUEST(Family.SUBSET_126,1000,false),
        HANDSHAKE_ACKNOWLEDGEMENT(Family.SUBSET_126,1001,false),
        HANDSHAKE_REJECT(Family.SUBSET_126,1002,false),
        JOURNEY_PROFILE_REQUEST(Family.SUBSET_126,1003,false),
        JOURNEY_PROFILE(Family.SUBSET_126,1004,false),
        JOURNEY_PROFILE_ACKNOWLEDGEMENT(Family.SUBSET_126,1005,false),
        SEGMENT_PROFILE_REQUEST(Family.SUBSET_126,1006,false),
        SEGMENT_PROFILE(Family.SUBSET_126,1007,false),
        STATUS_REPORT(Family.SUBSET_126,1008,false),
        STATUS_REPORT_ACKNOWLEDGEMENT(Family.SUBSET_126,1009,false),
        SESSION_TERMINATION_REQUEST(Family.SUBSET_126,1010,false),
        SESSION_TERMINATION(Family.SUBSET_126,1011,false),
        ATO_ETCS_STATUS(Family.SUBSET_130,1000,false),
        ATO_ETCS_DMI(Family.SUBSET_130,1001,false),
        ETCS_ATO_DYNAMIC(Family.SUBSET_130,1006,false),
        DRIVE_INTENT(Family.SUBSET_139,1031,true),
        VEHICLE_FEEDBACK(Family.SUBSET_139,1032,true),
        VEHICLE_SLOW_DATA(Family.SUBSET_139,1033,false),
        CLAIM(Family.SKY_ATO,5100,true),
        RELEASE(Family.SKY_ATO,5101,true),
        REVERSE(Family.SKY_ATO,5102,true),
        RECOVER(Family.SKY_ATO,5103,true),
        PREPARE_SR(Family.SKY_ATO,5104,true),
        ACCEPT_RECOVERY_SR(Family.SKY_ATO,5105,true),
        PASSENGER_MESSAGE(Family.SKY_ATO,5106,true),
        CHUNK_CORRIDOR(Family.SKY_ATO,5107,true),
        AUTHORITY_REQUEST(Family.SKY_ATO,5110,true),
        AUTHORITY_RELEASE(Family.SKY_ATO,5111,true),
        SR_REQUEST(Family.SKY_ATO,5112,true),
        FS_REQUEST(Family.SKY_ATO,5113,true),
        SWITCH_CHANGE(Family.SKY_ATO,5114,true),
        ROUTE_HEARTBEAT(Family.SKY_ATO,5115,true),
        DEPOT_CAPTURE(Family.SKY_ATO,5116,true),
        DEPOT_MANUAL_RELEASE(Family.SKY_ATO,5117,true),
        DEPOT_HANDOVER_COMMIT(Family.SKY_ATO,5118,true);

        private final Family family;
        private final int id;
        private final boolean runtime;
        Packet(Family family,int id,boolean runtime) { this.family=family;this.id=id;this.runtime=runtime; }
        public Family family() { return family; }
        public int id() { return id; }
        public boolean runtime() { return runtime; }
        public Header header() { return new Header(VERSION,Message.ATO,family,id); }
    }

    /** Family is mandatory: Packet 1000 in SUBSET-126 and SUBSET-130 are different types. */
    public record Header(int M_VERSION,int NID_MESSAGE,Family NID_INTERFACE,int NID_PACKET) {
        public Header {
            Objects.requireNonNull(NID_INTERFACE,"NID_INTERFACE");
            if(M_VERSION!=VERSION || NID_MESSAGE!=Message.ATO)
                throw new IllegalArgumentException("Unsupported ATO envelope version/message");
            boolean known=false;
            for(var packet:Packet.values())
                if(packet.family()==NID_INTERFACE && packet.id()==NID_PACKET) { known=true;break; }
            if(!known)throw new IllegalArgumentException("Unknown ATO interface/packet pair");
        }
        public void require(Packet expected) {
            Objects.requireNonNull(expected);
            if(NID_INTERFACE!=expected.family() || NID_PACKET!=expected.id() || !expected.runtime())
                throw new IllegalArgumentException("Unexpected or reserved ATO packet: "+expected);
        }
    }
}
