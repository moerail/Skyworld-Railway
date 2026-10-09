package net.skyworld.sta.api.v3;

import java.util.*;

/** Minecraft TIMS evidence; private packet 1000 is a nod to ETCS Packet 0, not ETCS encoding. */
public record TrainIntegrity(State state, String reason, long observedAtMillis,
        long confirmedAtMillis, List<UUID> expectedMembers, List<UUID> affectedMembers,
        boolean brakeHeld, int messageId, int packetId) {
    public TrainIntegrity(State state,String reason,long observedAtMillis,long confirmedAtMillis,
            List<UUID> expectedMembers,List<UUID> affectedMembers,boolean brakeHeld) {
        this(state,reason,observedAtMillis,confirmedAtMillis,expectedMembers,affectedMembers,brakeHeld,
                POSITION_REPORT_MESSAGE,INTEGRITY_PACKET);
    }
    public static final int POSITION_REPORT_MESSAGE = 1136;
    public static final int INTEGRITY_PACKET = 1000;
    public enum State { COMPLETE, UNKNOWN, LOST }
    public TrainIntegrity {
        Objects.requireNonNull(state); Objects.requireNonNull(reason);
        expectedMembers=List.copyOf(expectedMembers); affectedMembers=List.copyOf(affectedMembers);
        if(messageId!=POSITION_REPORT_MESSAGE || packetId!=INTEGRITY_PACKET)
            throw new IllegalArgumentException("Unsupported private TIMS protocol ID");
        if(observedAtMillis<0 || confirmedAtMillis<0) throw new IllegalArgumentException("Invalid TIMS time");
    }
    public static TrainIntegrity unknown() {
        return new TrainIntegrity(State.UNKNOWN,"NO_TIMS",0,0,List.of(),List.of(),true);
    }
    public boolean permitsClearance(List<UUID> expected, long now) {
        return state==State.COMPLETE && !brakeHeld && now>=observedAtMillis && now-observedAtMillis<=1000
                && now>=confirmedAtMillis && now-confirmedAtMillis<=1000
                && !expected.isEmpty() && expectedMembers.equals(expected);
    }
}
