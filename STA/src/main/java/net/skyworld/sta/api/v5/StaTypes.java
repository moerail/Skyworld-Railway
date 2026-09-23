package net.skyworld.sta.api.v5;

/** Private STA v5 identifiers. ETCS-inspired numbering, never ETCS wire compatibility. */
public final class StaTypes {
    private StaTypes() {}
    public static final int VERSION = 5;
    public static final class Message {
        private Message() {}
        public static final int MOVEMENT_AUTHORITY = 1003;
        public static final int TRAIN_POSITION_REPORT = 1136;
        public static final int TRAIN_REMOVED = 2001;
        public static final int TRACK_REPORT = 2002;
        public static final int AUTHORITY_STATUS = 2003;
    }
    public static final class Packet {
        private Packet() {}
        public static final int MOVEMENT_AUTHORITY = 1015;
        public static final int PHYSICAL_OBSERVATION = 2001;
        public static final int GRAPH_POSITION = 2002;
    }
}
